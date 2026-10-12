package com.dn0ne.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.audiofx.Equalizer
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.ui.util.fastForEach
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.dn0ne.player.app.data.SavedPlayerState
import com.dn0ne.player.app.data.repository.PlaylistRepository
import com.dn0ne.player.app.domain.playback.PlaybackMode
import com.dn0ne.player.app.domain.playback.next
import com.dn0ne.player.app.domain.playback.playbackMode
import com.dn0ne.player.app.domain.playback.setPlaybackMode
import com.dn0ne.player.auto.ArtworkProvider
import com.dn0ne.player.auto.AutoLibrary
import com.dn0ne.player.core.data.Settings
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.koin.android.ext.android.get
import java.lang.Exception

private class EqualizerSettings(context: Context) {
    private val sharedPreferences =
        context.getSharedPreferences("equalizer-settings", Context.MODE_PRIVATE)

    private val isEnabledKey = "is-equalizer-enabled"
    private val bandFrequenciesKey = "band-frequencies"
    private val lowerLevelLimitKey = "lower-level-limit"
    private val upperLevelLimitKey = "upper-level-limit"
    private val bandLevelsKey = "band-levels"

    var isEnabled: Boolean
        get() = sharedPreferences.getBoolean(isEnabledKey, false)
        set(value) {
            with(sharedPreferences.edit()) {
                putBoolean(isEnabledKey, value)
                apply()
            }
        }

    var bandFrequencies: List<String>?
        get() = sharedPreferences.getString(bandFrequenciesKey, null)
            ?.let { Json.decodeFromString(it) }
        set(value) {
            with(sharedPreferences.edit()) {
                putString(bandFrequenciesKey, value?.let { Json.encodeToString(it) })
                apply()
            }
        }

    var lowerLevelLimit: Int
        get() = sharedPreferences.getInt(lowerLevelLimitKey, 0)
        set(value) {
            with(sharedPreferences.edit()) {
                putInt(lowerLevelLimitKey, value)
                apply()
            }
        }

    var upperLevelLimit: Int
        get() = sharedPreferences.getInt(upperLevelLimitKey, 0)
        set(value) {
            with(sharedPreferences.edit()) {
                putInt(upperLevelLimitKey, value)
                apply()
            }
        }

    var bandLevels: List<Short>?
        get() = sharedPreferences.getString(bandLevelsKey, null)
            ?.let { Json.decodeFromString(it) }
        set(value) {
            with(sharedPreferences.edit()) {
                putString(bandLevelsKey, value?.let { Json.encodeToString(it) })
                apply()
            }
        }
}

class EqualizerController(context: Context) {
    private val _settings = EqualizerSettings(context)
    private var _audioSessionId: Int? = null
    var equalizer: Equalizer? = null
        private set

    init {
        firstLaunchInit()
    }

    private val _isEqEnabled = MutableStateFlow(_settings.isEnabled)
    val isEqEnabled = _isEqEnabled.asStateFlow()
    fun updateIsEqEnabled(value: Boolean) {
        _isEqEnabled.update { value }
        _settings.isEnabled = value

        _audioSessionId?.let {
            updateEqualizer(it)
        }
    }

    private val _bandFrequencies = MutableStateFlow<List<String>?>(_settings.bandFrequencies)
    val bandFrequencies = _bandFrequencies.asStateFlow()

    private val _lowerLevelLimit = MutableStateFlow<Short>(_settings.lowerLevelLimit.toShort())
    val lowerLevelLimit = _lowerLevelLimit.asStateFlow()

    private val _upperLevelLimit = MutableStateFlow<Short>(_settings.upperLevelLimit.toShort())
    val upperLevelLimit = _upperLevelLimit.asStateFlow()

    private val _bandLevels = MutableStateFlow(_settings.bandLevels)
    val bandLevels = _bandLevels.asStateFlow()
    fun updateBandLevels(levels: List<Short>) {
        _bandLevels.update { levels }
        _settings.bandLevels = levels

        _audioSessionId?.let {
            updateEqualizer(it)
        }
    }

    fun updateEqualizer(audioSessionId: Int) {
        equalizer?.release()

        _audioSessionId = audioSessionId

        if (!_settings.isEnabled) return

        equalizer = Equalizer(Int.MAX_VALUE, audioSessionId).apply {
            enabled = true

            (0 until numberOfBands).forEach {
                println("BAND FREQ RANGE: ${getBandFreqRange(it.toShort()).joinToString("..")}")
            }
            _settings.bandLevels?.forEachIndexed { band, level ->
                setBandLevel(band.toShort(), level)
            }
        }
    }

    fun releaseEqualizer() {
        equalizer?.release()
        equalizer = null
    }

    fun resetBandLevels() {
        _settings.bandLevels?.let { bandLevels ->
            val defaultLevels = List<Short>(bandLevels.size) { 0 }
            _settings.bandLevels = defaultLevels
            _bandLevels.update {
                defaultLevels
            }
        }

        _audioSessionId?.let {
            updateEqualizer(it)
        }
    }

    private fun firstLaunchInit() {
        if (_settings.bandFrequencies == null || _settings.bandLevels == null) {
            try {
                val audioTrack = AudioTrack.Builder()
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(44100)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT))
                    .build()

                Equalizer(0, audioTrack.audioSessionId).run {
                    enabled = false

                    val defaultLevels = List<Short>(numberOfBands.toInt()) { 0 }
                    _settings.bandLevels = defaultLevels

                    val bandFreqs = List(numberOfBands.toInt()) {
                        getBandFreqRange(it.toShort()).joinToString("-") {
                            (it / 1000).toString()
                        } + "Hz"
                    }
                    _settings.bandFrequencies = bandFreqs

                    val lowerLevelLimit = bandLevelRange[0]
                    _settings.lowerLevelLimit = lowerLevelLimit.toInt()

                    val upperLevelLimit = bandLevelRange[1]
                    _settings.upperLevelLimit = upperLevelLimit.toInt()

                    release()
                }

                audioTrack.release()
            } catch (e: Exception) {
                Log.e("FIRST LAUNCH EQUALIZER INITIALIZATION", e.message, e)
            }
        }
    }
}

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private val equalizerController = get<EqualizerController>()
    private val savedPlayerState = get<SavedPlayerState>()
    private val autoLibrary = get<AutoLibrary>()
    private val playlistRepository = get<PlaylistRepository>()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var sessionActivityIntent: PendingIntent

    override fun onCreate() {
        super.onCreate()

        val shouldHandleAudioFocus = get<Settings>().handleAudioFocus
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, shouldHandleAudioFocus)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Controllers like Android Auto may start playback before the app UI ever connects,
        // so the saved playback mode has to be applied here as well
        player.setPlaybackMode(savedPlayerState.playbackMode)

        player.addListener(object : Player.Listener {
            @OptIn(UnstableApi::class)
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING) {
                    val audioSessionId = player.audioSessionId
                    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                        equalizerController.updateEqualizer(audioSessionId)
                    }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                onPlaybackModeChanged(player.playbackMode)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                onPlaybackModeChanged(player.playbackMode)
            }
        })

        SleepTimer.addOnFinishCallback {
            player.stop()
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setSessionActivity(sessionActivityIntent)
            .setCustomLayout(listOf(playbackModeButton(player.playbackMode)))
            .build()

        // Keep the car's playlist list in sync with changes made in the app
        serviceScope.launch {
            playlistRepository.getPlaylists().drop(1).collect { playlists ->
                mediaSession?.notifyChildrenChanged(AutoLibrary.PLAYLISTS_ID, playlists.size, null)
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player!!

        if (!player.playWhenReady
            || player.mediaItemCount == 0
            || player.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        equalizerController.releaseEqualizer()
        SleepTimer.stop()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    private fun onPlaybackModeChanged(playbackMode: PlaybackMode) {
        savedPlayerState.playbackMode = playbackMode
        mediaSession?.setCustomLayout(listOf(playbackModeButton(playbackMode)))
    }

    private fun playbackModeButton(playbackMode: PlaybackMode): CommandButton {
        val (icon, nameResId) = when (playbackMode) {
            PlaybackMode.Repeat -> CommandButton.ICON_REPEAT_ALL to R.string.auto_playback_mode_repeat
            PlaybackMode.RepeatOne -> CommandButton.ICON_REPEAT_ONE to R.string.auto_playback_mode_repeat_one
            PlaybackMode.Shuffle -> CommandButton.ICON_SHUFFLE_ON to R.string.auto_playback_mode_shuffle
        }

        return CommandButton.Builder(icon)
            .setDisplayName(getString(nameResId))
            .setSessionCommand(cyclePlaybackModeCommand)
            .build()
    }

    /**
     * Grants the controller read access to album art served by [ArtworkProvider].
     */
    private fun grantArtworkAccess(controller: MediaSession.ControllerInfo) {
        if (controller.packageName == packageName) return

        try {
            grantUriPermission(
                controller.packageName,
                ArtworkProvider.ROOT_URI,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Legacy controllers may report a placeholder package name
            Log.w(TAG, "Couldn't grant artwork access to ${controller.packageName}", e)
        }
    }

    /**
     * Media3 shows this error on the car screen (with a button that opens the app on the phone)
     * until a later library request succeeds. Only the "authentication expired" code is
     * replicated that way, so it's used for the missing permission as well.
     */
    private fun permissionError(): LibraryResult<ImmutableList<MediaItem>> {
        val extras = Bundle().apply {
            putString(
                MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_LABEL_COMPAT,
                getString(R.string.auto_open_app)
            )
            putParcelable(
                MediaConstants.EXTRAS_KEY_ERROR_RESOLUTION_ACTION_INTENT_COMPAT,
                sessionActivityIntent
            )
        }

        return LibraryResult.ofError(
            SessionError(
                SessionError.ERROR_SESSION_AUTHENTICATION_EXPIRED,
                getString(R.string.auto_permission_required),
                extras
            )
        )
    }

    private suspend fun savedQueue(): MediaSession.MediaItemsWithStartPosition? {
        val (playlist, track) = withContext(Dispatchers.IO) {
            savedPlayerState.playlist to savedPlayerState.track
        }
        val trackList = playlist?.trackList?.takeIf { it.isNotEmpty() } ?: return null
        val startIndex = trackList.indexOfFirst { it.mediaItem == track?.mediaItem }

        return MediaSession.MediaItemsWithStartPosition(
            trackList.map { it.mediaItem },
            startIndex.coerceAtLeast(0),
            0L
        )
    }

    /**
     * Saves the queue the same way the app does, so the app picks it up when it's opened.
     */
    private suspend fun startPlaybackRequest(
        request: AutoLibrary.PlaybackRequest
    ): MediaSession.MediaItemsWithStartPosition {
        val trackList = request.playlist.trackList

        withContext(Dispatchers.IO) {
            savedPlayerState.playlist = request.playlist
            savedPlayerState.track = trackList[request.startIndex]
        }

        if (request.shuffle) {
            mediaSession?.player?.setPlaybackMode(PlaybackMode.Shuffle)
        }

        return MediaSession.MediaItemsWithStartPosition(
            trackList.map { it.mediaItem },
            request.startIndex,
            0L
        )
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            grantArtworkAccess(controller)

            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                    .add(cyclePlaybackModeCommand)
                    .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_CYCLE_PLAYBACK_MODE) {
                session.player.setPlaybackMode(session.player.playbackMode.next())
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (params?.isRecent == true) {
                // Recent media is served through onPlaybackResumption instead
                return Futures.immediateFuture(
                    LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED)
                )
            }

            grantArtworkAccess(browser)
            autoLibrary.invalidate()

            return Futures.immediateFuture(
                LibraryResult.ofItem(
                    autoLibrary.rootItem(params?.extras),
                    LibraryParams.Builder().setExtras(autoLibrary.rootExtras()).build()
                )
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            if (!autoLibrary.hasAudioPermission) return@future permissionError()

            val children = autoLibrary.getChildren(parentId)
                ?: return@future LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            LibraryResult.ofItemList(children.page(page, pageSize), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            autoLibrary.getItem(mediaId)
                ?.let { LibraryResult.ofItem(it, null) }
                ?: LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = serviceScope.future {
            val results = autoLibrary.search(query)
            lastSearch = query to results
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val results = lastSearch
                ?.takeIf { it.first == query }
                ?.second
                ?: autoLibrary.search(query)
            LibraryResult.ofItemList(results.page(page, pageSize), params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            // Items from the app already carry their uri
            if (mediaItems.all { it.localConfiguration != null }) {
                return super.onAddMediaItems(mediaSession, controller, mediaItems)
            }

            return serviceScope.future {
                autoLibrary.resolveTracks(mediaItems)
                    .takeIf { it.isNotEmpty() }
                    ?.map { it.mediaItem }
                    ?: throw UnsupportedOperationException("No playable items")
            }
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (mediaItems.all { it.localConfiguration != null }) {
                return super.onSetMediaItems(
                    mediaSession,
                    controller,
                    mediaItems,
                    startIndex,
                    startPositionMs
                )
            }

            return serviceScope.future {
                val isPlayAnythingRequest = mediaItems.singleOrNull()?.let {
                    it.mediaId.isEmpty() && it.requestMetadata.searchQuery.isNullOrBlank()
                } == true

                if (isPlayAnythingRequest) {
                    savedQueue()?.let { return@future it }
                }

                val request = if (isPlayAnythingRequest) {
                    autoLibrary.shuffleAll()
                } else {
                    autoLibrary.resolvePlaybackRequest(mediaItems)
                } ?: throw UnsupportedOperationException("Nothing to play")

                startPlaybackRequest(request)
            }
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            savedQueue() ?: throw UnsupportedOperationException("Nothing to resume")
        }
    }

    private var lastSearch: Pair<String, List<MediaItem>>? = null

    private companion object {
        const val TAG = "PlaybackService"
        const val ACTION_CYCLE_PLAYBACK_MODE = "com.dn0ne.player.CYCLE_PLAYBACK_MODE"
        val cyclePlaybackModeCommand = SessionCommand(ACTION_CYCLE_PLAYBACK_MODE, Bundle.EMPTY)
    }
}

private fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
    if (page < 0 || pageSize <= 0) return emptyList()
    val from = page.toLong() * pageSize
    if (from >= size) return emptyList()
    val to = minOf(from + pageSize, size.toLong())
    return subList(from.toInt(), to.toInt())
}

/**
 * Bridges a coroutine to the [ListenableFuture]s expected by media session callbacks.
 */
private fun <T> CoroutineScope.future(block: suspend CoroutineScope.() -> T): ListenableFuture<T> {
    val future = SettableFuture.create<T>()
    val job = launch {
        try {
            future.set(block())
        } catch (e: CancellationException) {
            future.cancel(false)
            throw e
        } catch (e: Exception) {
            future.setException(e)
        }
    }
    future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
    return future
}

object SleepTimer {
    var timer: CountDownTimer? = null
    private val finishCallbacks = mutableListOf<() -> Unit>()

    private val _minutesLeft = MutableStateFlow(1)
    val minutesLeft = _minutesLeft.asStateFlow()
    fun updateMinutesLeft(value: Int) {
        _minutesLeft.update { value }
    }

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    fun start() {
        stop()
        timer = object : CountDownTimer(_minutesLeft.value.toLong() * 60000L, 60000L) {
            override fun onTick(millisUntilFinished: Long) {
                _minutesLeft.update { millisUntilFinished.toInt() / 60000 + 1 }
            }

            override fun onFinish() {
                finishCallbacks.fastForEach { it.invoke() }
                _isRunning.update { false }
            }

        }.start()

        _isRunning.update { true }
    }

    fun stop() {
        timer?.cancel()

        _isRunning.update { false }
    }

    fun addOnFinishCallback(callback: () -> Unit) {
        finishCallbacks += callback
    }
}