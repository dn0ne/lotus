package com.dn0ne.player.app.presentation

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.dn0ne.player.EqualizerController
import com.dn0ne.player.R
import com.dn0ne.player.app.data.LyricsReader
import com.dn0ne.player.app.data.SavedPlayerState
import com.dn0ne.player.app.data.remote.lyrics.LyricsProvider
import com.dn0ne.player.app.data.remote.metadata.MetadataProvider
import com.dn0ne.player.app.data.repository.LyricsRepository
import com.dn0ne.player.app.data.repository.PlaylistRepository
import com.dn0ne.player.app.data.repository.TrackRepository
import com.dn0ne.player.app.domain.lyrics.Lyrics
import com.dn0ne.player.app.domain.lyrics.toSyncedLyrics
import com.dn0ne.player.app.domain.metadata.Metadata
import com.dn0ne.player.app.domain.playback.PlaybackMode
import com.dn0ne.player.app.domain.playback.playbackMode
import com.dn0ne.player.app.domain.playback.setPlaybackMode
import com.dn0ne.player.app.domain.result.DataError
import com.dn0ne.player.app.domain.result.Result
import com.dn0ne.player.app.domain.sort.sortedBy
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.domain.track.format
import com.dn0ne.player.app.presentation.PlayerScreenEvent.*
import com.dn0ne.player.app.presentation.components.playback.PlaybackState
import com.dn0ne.player.app.presentation.components.settings.SettingsSheetState
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarController
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarEvent
import com.dn0ne.player.app.presentation.components.trackinfo.ChangesSheetState
import com.dn0ne.player.app.presentation.components.trackinfo.InfoSearchSheetState
import com.dn0ne.player.app.presentation.components.trackinfo.LyricsControlSheetState
import com.dn0ne.player.app.presentation.components.trackinfo.ManualInfoEditSheetState
import com.dn0ne.player.app.presentation.components.trackinfo.TrackInfoSheetState
import com.dn0ne.player.core.data.MusicScanner
import com.dn0ne.player.core.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(
    private val savedPlayerState: SavedPlayerState,
    private val trackRepository: TrackRepository,
    private val metadataProvider: MetadataProvider,
    private val lyricsProvider: LyricsProvider,
    private val lyricsRepository: LyricsRepository,
    private val lyricsReader: LyricsReader,
    private val playlistRepository: PlaylistRepository,
    private val unsupportedArtworkEditFormats: List<String>,
    val settings: Settings,
    private val musicScanner: MusicScanner,
    private val equalizerController: EqualizerController
) : ViewModel() {
    var player: Player? = null

    private val _settingsSheetState = MutableStateFlow(
        SettingsSheetState(
            settings = settings,
            musicScanner = musicScanner,
            equalizerController = equalizerController
        )
    )
    val settingsSheetState = _settingsSheetState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = _settingsSheetState.value
    )

    private val _trackSort = MutableStateFlow(settings.trackSort)
    val trackSort = _trackSort.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = _trackSort.value
    )

    private val _trackSortOrder = MutableStateFlow(settings.trackSortOrder)
    val trackSortOrder = _trackSortOrder.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = _trackSortOrder.value
    )

    private val _playlistSort = MutableStateFlow(settings.playlistSort)
    val playlistSort = _playlistSort.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = _playlistSort.value
    )

    private val _playlistSortOrder = MutableStateFlow(settings.playlistSortOrder)
    val playlistSortOrder = _playlistSortOrder.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = _playlistSortOrder.value
    )

    private val _trackList = MutableStateFlow(emptyList<Track>())
    val trackList = _trackList.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val albumPlaylists = _trackList.map {
        it.groupBy { it.album }.entries.map {
            Playlist(
                name = it.key,
                trackList = it.value
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )
    val artistPlaylists = _trackList.map {
        it.groupBy { it.artist }.entries.map {
            Playlist(
                name = it.key,
                trackList = it.value
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )
    val genrePlaylists = _trackList.map {
        it.groupBy { it.genre }.entries.map {
            Playlist(
                name = it.key,
                trackList = it.value
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )
    val folderPlaylists = _trackList.map {
        it.groupBy { it.data.substringBeforeLast('/') }.entries.map {
            Playlist(
                name = it.key.substringAfterLast('/'),
                trackList = it.value
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    val playlists = playlistRepository.getPlaylists().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist = _selectedPlaylist.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = null
    )

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState = _playbackState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = PlaybackState()
        )

    private var positionUpdateJob: Job? = null

    private val _infoSearchSheetState = MutableStateFlow(InfoSearchSheetState())
    private val _changesSheetState = MutableStateFlow(ChangesSheetState())
    private val _manualInfoEditSheetState = MutableStateFlow(ManualInfoEditSheetState())
    private val _lyricsControlSheetState = MutableStateFlow(LyricsControlSheetState())
    private val _trackInfoSheetState = MutableStateFlow(
        TrackInfoSheetState(
            showRisksOfMetadataEditingDialog = !settings.areRisksOfMetadataEditingAccepted
        )
    )
    val trackInfoSheetState = combine(
        _trackInfoSheetState,
        _infoSearchSheetState,
        _changesSheetState,
        _manualInfoEditSheetState,
        _lyricsControlSheetState
    ) { trackInfoSheetState,
        infoSearchSheetState,
        changesSheetState,
        manualInfoEditSheetState,
        lyricsControlSheetState ->

        trackInfoSheetState.copy(
            infoSearchSheetState = infoSearchSheetState,
            changesSheetState = changesSheetState,
            manualInfoEditSheetState = manualInfoEditSheetState,
            lyricsControlSheetState = lyricsControlSheetState
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = TrackInfoSheetState()
        )

    private val _pendingMetadata = Channel<Pair<Track, Metadata>>()
    val pendingMetadata = _pendingMetadata.receiveAsFlow()

    private val _pendingTrackUris = Channel<Uri>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val tracks = trackRepository.getTracks()

                if (_trackList.value.size != tracks.size || !_trackList.value.containsAll(tracks)) {
                    _trackList.update {
                        tracks.sortedBy(_trackSort.value, _trackSortOrder.value)
                    }

                    if (_trackInfoSheetState.value.track != null) {
                        _trackInfoSheetState.update {
                            it.copy(
                                track = _trackList.value.fastFirstOrNull { track -> it.track?.uri == track.uri }
                            )
                        }

                        _playbackState.update {
                            PlaybackState()
                        }

                        withContext(Dispatchers.Main) {
                            player?.stop()
                            player?.clearMediaItems()
                        }
                    }

                }
                delay(5000L)
            }
        }

        viewModelScope.launch {
            while (player == null) delay(500L)

            val playlist = savedPlayerState.playlist
            playlist?.let { playlist ->
                val trackMediaItem = player?.currentMediaItem ?: savedPlayerState.track?.mediaItem
                val index = playlist.trackList.indexOfFirst { trackMediaItem == it.mediaItem }
                val track = playlist.trackList.getOrNull(index)

                if (player?.mediaItemCount == 0) {
                    player?.addMediaItems(playlist.trackList.fastMap { it.mediaItem })
                    if (index >= 0) {
                        player?.seekTo(index, 0L)
                    }
                }

                _playbackState.update {
                    it.copy(
                        playlist = playlist,
                        currentTrack = track,
                        isPlaying = player!!.isPlaying,
                        position = player!!.currentPosition
                    )
                }

                if (player!!.isPlaying) {
                    positionUpdateJob = startPositionUpdate()
                }
            }

            val playbackMode = savedPlayerState.playbackMode
            setPlayerPlaybackMode(playbackMode)
            _playbackState.update {
                it.copy(
                    playbackMode = playbackMode
                )
            }

            player?.addListener(
                object : Player.Listener {
                    override fun onRepeatModeChanged(repeatMode: Int) {
                        onPlayerPlaybackModeChanged()
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        onPlayerPlaybackModeChanged()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _playbackState.update {
                            it.copy(
                                isPlaying = isPlaying
                            )
                        }

                        positionUpdateJob?.cancel()
                        if (isPlaying) {
                            positionUpdateJob = startPositionUpdate()
                        }
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int
                    ) {
                        // The queue may have been replaced by another controller (e.g. Android Auto),
                        // which saves it before starting playback
                        val playlist = _playbackState.value.playlist
                            ?.takeIf { playlist ->
                                mediaItem == null || playlist.trackList.fastAny { it.mediaItem == mediaItem }
                            }
                            ?: savedPlayerState.playlist
                        val currentTrack = playlist?.trackList?.fastFirstOrNull {
                            it.mediaItem == mediaItem
                        }
                        savedPlayerState.track = currentTrack

                        _playbackState.update {
                            it.copy(
                                playlist = playlist,
                                currentTrack = currentTrack,
                                position = 0L
                            )
                        }

                        if (_playbackState.value.isLyricsSheetExpanded) {
                            loadLyrics()
                        }

                        positionUpdateJob?.cancel()
                        positionUpdateJob = startPositionUpdate()
                    }
                }
            )

        }

        viewModelScope.launch {
            while (_trackList.value.isEmpty() || player == null) delay(500)

            _pendingTrackUris.receiveAsFlow().collectLatest { uri ->
                val path = "/storage" + Uri.decode(uri.toString().substringAfter("storage"))
                val track = _trackList.value.fastFirstOrNull { it.data == path || it.uri == uri }
                track?.let {
                    onEvent(
                        OnTrackClick(
                            track = it,
                            playlist = Playlist(
                                name = null,
                                trackList = _trackList.value
                            )
                        )
                    )
                } ?: run {
                    SnackbarController.sendEvent(
                        SnackbarEvent(
                            message = R.string.track_is_not_found_in_media_store
                        )
                    )
                }
            }
        }
    }

    fun onEvent(event: PlayerScreenEvent) {

        when (event) {
            is OnTrackClick -> {
                player?.let { player ->
                    if (_playbackState.value.playlist != event.playlist) {
                        player.clearMediaItems()
                        player.addMediaItems(
                            event.playlist.trackList.fastMap { track -> track.mediaItem }
                        )
                        player.prepare()
                    }
                    player.seekTo(
                        event.playlist.trackList.indexOfFirst { it == event.track },
                        0L
                    )
                    player.play()

                    _playbackState.update {
                        it.copy(
                            playlist = event.playlist,
                            currentTrack = event.track,
                            position = 0
                        )
                    }

                    viewModelScope.launch(Dispatchers.IO) {
                        savedPlayerState.playlist = event.playlist
                        savedPlayerState.track = event.track
                    }
                }
            }

            OnPauseClick -> {
                player?.run {
                    pause()
                }
            }

            OnPlayClick -> {
                player?.let { player ->
                    if (player.currentMediaItem == null) return

                    player.play()
                }
            }

            OnSeekToNextClick -> {
                player?.let { player ->
                    if (!player.hasNextMediaItem()) return

                    player.seekToNextMediaItem()
                }
            }

            OnSeekToPreviousClick -> {
                player?.let { player ->
                    if (settings.jumpToBeginning && player.currentPosition >= 3000) {
                        player.seekTo(0)
                        _playbackState.update {
                            it.copy(
                                position = 0
                            )
                        }
                    } else {
                        player.seekToPreviousMediaItem()
                    }
                }
            }

            is OnSeekTo -> {
                player?.let { player ->
                    if (player.currentMediaItem == null) return

                    player.seekTo(event.position)
                    _playbackState.update {
                        it.copy(
                            position = event.position
                        )
                    }
                }
            }

            OnResetPlayback -> {
                player?.clearMediaItems()
                _playbackState.update {
                    PlaybackState(
                        playbackMode = it.playbackMode
                    )
                }

                viewModelScope.launch(Dispatchers.IO) {
                    savedPlayerState.playlist = null
                    savedPlayerState.track = null
                }
            }

            OnPlaybackModeClick -> {
                val newPlaybackMode = _playbackState.value.playbackMode.let {
                    PlaybackMode.entries.nextAfterOrNull(it.ordinal)
                }
                newPlaybackMode?.let { mode ->
                    setPlayerPlaybackMode(mode)
                    _playbackState.update {
                        it.copy(
                            playbackMode = mode
                        )
                    }
                    savedPlayerState.playbackMode = mode
                }
            }

            is OnPlayerExpandedChange -> {
                _playbackState.update {
                    it.copy(
                        isPlayerExpanded = event.isExpanded,
                        isLyricsSheetExpanded = false
                    )
                }
            }

            is OnLyricsSheetExpandedChange -> {
                _playbackState.update {
                    it.copy(
                        isLyricsSheetExpanded = event.isExpanded
                    )
                }
            }

            OnLyricsClick -> {
                loadLyrics()
            }

            is OnRemoveFromQueueClick -> {
                player?.let { player ->
                    if (event.index == player.currentMediaItemIndex) {
                        onEvent(OnSeekToNextClick)
                    }

                    player.removeMediaItem(event.index)

                    _playbackState.update {
                        it.copy(
                            playlist = it.playlist?.copy(
                                trackList = it.playlist.trackList.toMutableList().apply {
                                    removeAt(event.index)
                                }
                            )
                        )
                    }

                    if (_playbackState.value.playlist?.trackList?.isEmpty() == true) {
                        _playbackState.update {
                            it.copy(
                                isPlayerExpanded = false,
                                isLyricsSheetExpanded = false
                            )
                        }
                    }

                    viewModelScope.launch(Dispatchers.IO) {
                        savedPlayerState.playlist = _playbackState.value.playlist
                    }
                }
            }

            is OnReorderingQueue -> {
                player?.let { player ->
                    player.moveMediaItem(event.from, event.to)

                    _playbackState.update {
                        it.copy(
                            playlist = it.playlist?.copy(
                                trackList = it.playlist.trackList.toMutableList().apply {
                                    add(event.to, removeAt(event.from))
                                }
                            )
                        )
                    }

                    viewModelScope.launch(Dispatchers.IO) {
                        savedPlayerState.playlist = _playbackState.value.playlist
                    }
                }
            }

            is OnPlayNextClick -> {
                if (_playbackState.value.currentTrack == event.track) return

                _playbackState.value.playlist?.let { playlist ->
                    val trackIndex = playlist.trackList.indexOf(event.track)
                    val currentTrackIndex = _playbackState.value.currentTrack?.let {
                        playlist.trackList.indexOf(it)
                    } ?: 0

                    if (trackIndex >= 0) {
                        onEvent(
                            OnReorderingQueue(
                                trackIndex,
                                (currentTrackIndex).coerceAtMost(playlist.trackList.lastIndex)
                            )
                        )
                        return
                    } else {
                        player?.let { player ->
                            player.addMediaItem(
                                player.currentMediaItemIndex + 1,
                                event.track.mediaItem
                            )

                            _playbackState.update {
                                it.copy(
                                    playlist = playlist.copy(
                                        trackList = playlist.trackList.toMutableList().apply {
                                            add(currentTrackIndex + 1, event.track)
                                        }
                                    )
                                )
                            }
                        }

                    }
                } ?: run {
                    onEvent(
                        OnTrackClick(
                            track = event.track,
                            playlist = Playlist(
                                name = null,
                                trackList = listOf(event.track)
                            )
                        )
                    )
                }

                viewModelScope.launch(Dispatchers.IO) {
                    savedPlayerState.playlist = _playbackState.value.playlist
                }
            }

            is OnAddToQueueClick -> {
                event.tracks.fastForEach { track ->
                    if (_playbackState.value.currentTrack != track) {
                        _playbackState.value.playlist?.let { playlist ->
                            val trackIndex = playlist.trackList.indexOf(track)

                            if (trackIndex < 0) {
                                player?.let { player ->
                                    player.addMediaItem(track.mediaItem)

                                    _playbackState.update {
                                        it.copy(
                                            playlist = playlist.copy(
                                                trackList = playlist.trackList.toMutableList()
                                                    .apply {
                                                        add(track)
                                                    }
                                            )
                                        )
                                    }
                                }
                            }
                        } ?: run {
                            onEvent(
                                OnTrackClick(
                                    track = track,
                                    playlist = Playlist(
                                        name = null,
                                        trackList = listOf(track)
                                    )
                                )
                            )
                        }
                    }
                }

                viewModelScope.launch(Dispatchers.IO) {
                    savedPlayerState.playlist = _playbackState.value.playlist
                }
            }

            is OnViewTrackInfoClick -> {
                _trackInfoSheetState.update {
                    it.copy(
                        isShown = true,
                        track = event.track,
                        isCoverArtEditable = event.track.format !in unsupportedArtworkEditFormats
                    )
                }

                _manualInfoEditSheetState.update {
                    it.copy(
                        pickedCoverArtBytes = null
                    )
                }
            }

            is OnGoToAlbumClick -> {
                _selectedPlaylist.update {
                    albumPlaylists.value.fastFirstOrNull {
                        it.name == event.track.album
                    }
                }
            }

            is OnGoToArtistClick -> {
                _selectedPlaylist.update {
                    artistPlaylists.value.fastFirstOrNull {
                        it.name == event.track.artist
                    }
                }
            }

            OnCloseTrackInfoSheetClick -> {
                _trackInfoSheetState.update {
                    it.copy(
                        isShown = false
                    )
                }
            }

            OnAcceptingRisksOfMetadataEditing -> {
                settings.areRisksOfMetadataEditingAccepted = true
                _trackInfoSheetState.update {
                    it.copy(
                        showRisksOfMetadataEditingDialog = false
                    )
                }
            }

            OnMatchDurationWhenSearchMetadataClick -> {
                settings.matchDurationWhenSearchMetadata = !settings.matchDurationWhenSearchMetadata
            }

            is OnSearchInfo -> {
                viewModelScope.launch {
                    _infoSearchSheetState.update {
                        it.copy(
                            isLoading = true
                        )
                    }

                    val result = metadataProvider.searchMetadata(
                        query = event.query,
                        trackDuration = _trackInfoSheetState.value.track?.duration?.toLong()
                            ?: return@launch
                    )
                    when (result) {
                        is Result.Error -> {
                            when (result.error) {
                                DataError.Network.BadRequest -> {
                                    SnackbarController.sendEvent(
                                        SnackbarEvent(
                                            message = R.string.query_was_corrupted
                                        )
                                    )
                                    Log.d("Metadata Search", "${result.error} - ${event.query}")
                                }

                                DataError.Network.InternalServerError -> {
                                    SnackbarController.sendEvent(
                                        SnackbarEvent(
                                            message = R.string.musicbrainz_server_error
                                        )
                                    )
                                }

                                DataError.Network.ServiceUnavailable -> {
                                    SnackbarController.sendEvent(
                                        SnackbarEvent(
                                            message = R.string.musicbrainz_is_unavailable
                                        )
                                    )
                                }

                                DataError.Network.ParseError -> {
                                    SnackbarController.sendEvent(
                                        SnackbarEvent(
                                            message = R.string.failed_to_parse_response
                                        )
                                    )
                                    Log.d("Metadata Search", "${result.error} - ${event.query}")
                                }

                                DataError.Network.NoInternet -> {
                                    SnackbarController.sendEvent(
                                        SnackbarEvent(
                                            message = R.string.no_internet
                                        )
                                    )
                                }

                                else -> {
                                    SnackbarController.sendEvent(
                                        SnackbarEvent(
                                            message = R.string.unknown_error_occurred
                                        )
                                    )
                                    Log.d("Metadata Search", "${result.error} - ${event.query}")
                                }
                            }
                        }

                        is Result.Success -> {
                            _infoSearchSheetState.update {
                                it.copy(
                                    searchResults = result.data
                                )
                            }
                        }
                    }

                    _infoSearchSheetState.update {
                        it.copy(
                            isLoading = false
                        )
                    }
                }
            }

            is OnMetadataSearchResultPick -> {
                viewModelScope.launch {
                    if (_trackInfoSheetState.value.isCoverArtEditable) {
                        _changesSheetState.update {
                            it.copy(
                                isLoadingArt = true
                            )
                        }
                        val result = metadataProvider.getCoverArtBytes(event.searchResult)
                        var coverArtBytes: ByteArray? = null
                        when (result) {
                            is Result.Success -> {
                                coverArtBytes = result.data
                                _changesSheetState.update {
                                    it.copy(
                                        isLoadingArt = false,
                                        metadata = it.metadata.copy(
                                            coverArtBytes = coverArtBytes
                                        )
                                    )
                                }
                            }

                            is Result.Error -> {
                                when (result.error) {
                                    DataError.Network.BadRequest -> {
                                        SnackbarController.sendEvent(
                                            SnackbarEvent(
                                                message = R.string.failed_to_load_cover_art_album_id_corrupted,
                                            )
                                        )
                                    }

                                    DataError.Network.NotFound -> {
                                        SnackbarController.sendEvent(
                                            SnackbarEvent(
                                                message = R.string.cover_art_not_found
                                            )
                                        )
                                    }

                                    DataError.Network.ServiceUnavailable -> {
                                        SnackbarController.sendEvent(
                                            SnackbarEvent(
                                                message = R.string.cover_art_archive_is_unavailable
                                            )
                                        )
                                    }

                                    DataError.Network.NoInternet -> {
                                        SnackbarController.sendEvent(
                                            SnackbarEvent(
                                                message = R.string.no_internet
                                            )
                                        )
                                    }

                                    DataError.Network.RequestTimeout -> {
                                        SnackbarController.sendEvent(
                                            SnackbarEvent(
                                                message = R.string.failed_to_load_cover_art_request_timeout
                                            )
                                        )
                                    }

                                    else -> {
                                        SnackbarController.sendEvent(
                                            SnackbarEvent(
                                                message = R.string.unknown_error_occurred
                                            )
                                        )
                                    }
                                }
                                _changesSheetState.update {
                                    it.copy(
                                        isLoadingArt = false
                                    )
                                }
                                return@launch
                            }
                        }
                    }
                }

                _changesSheetState.update {
                    it.copy(
                        metadata = Metadata(
                            title = event.searchResult.title,
                            album = event.searchResult.album,
                            artist = event.searchResult.artist,
                            albumArtist = event.searchResult.albumArtist,
                            genre = event.searchResult.genres?.joinToString(" / "),
                            year = event.searchResult.year,
                            trackNumber = event.searchResult.trackNumber
                        ),
                        isArtFromGallery = false
                    )
                }
            }

            is OnOverwriteMetadataClick -> {
                _manualInfoEditSheetState.update {
                    it.copy(
                        pickedCoverArtBytes = null
                    )
                }
                viewModelScope.launch {
                    _trackInfoSheetState.value.track?.let { track ->
                        _pendingMetadata.send(track to event.metadata)
                    }
                }
            }

            OnRestoreCoverArtClick -> {
                _manualInfoEditSheetState.update {
                    it.copy(
                        pickedCoverArtBytes = null
                    )
                }
            }

            is OnConfirmMetadataEditClick -> {
                _changesSheetState.update {
                    it.copy(
                        metadata = event.metadata,
                        isArtFromGallery = event.metadata.coverArtBytes != null
                    )
                }
            }

            is OnPlaylistSelection -> {
                _selectedPlaylist.update {
                    event.playlist
                }
            }

            is OnTrackSortChange -> {
                event.sort?.let { sort ->
                    settings.trackSort = sort
                    _trackSort.update {
                        sort
                    }
                }

                event.order?.let { order ->
                    settings.trackSortOrder = order
                    _trackSortOrder.update {
                        order
                    }
                }

                _trackList.update {
                    it.sortedBy(
                        sort = _trackSort.value,
                        order = _trackSortOrder.value
                    )
                }

                _selectedPlaylist.update {
                    it?.copy(
                        trackList = it.trackList.sortedBy(
                            sort = _trackSort.value,
                            order = _trackSortOrder.value
                        )
                    )
                }
            }

            is OnPlaylistSortChange -> {
                event.sort?.let { sort ->
                    settings.playlistSort = sort
                    _playlistSort.update {
                        sort
                    }
                }

                event.order?.let { order ->
                    settings.playlistSortOrder = order
                    _playlistSortOrder.update {
                        order
                    }
                }
            }

            is OnCreatePlaylistClick -> {
                viewModelScope.launch {
                    if (playlists.value.map { it.name }.contains(event.name)) return@launch
                    playlistRepository.insertPlaylist(
                        Playlist(
                            name = event.name,
                            trackList = emptyList()
                        )
                    )
                }
            }

            is OnRenamePlaylistClick -> {
                viewModelScope.launch {
                    if (playlists.value.map { it.name }.contains(event.name)) return@launch
                    playlistRepository.renamePlaylist(
                        playlist = event.playlist,
                        name = event.name
                    )

                    _selectedPlaylist.update {
                        it?.copy(
                            name = event.name
                        )
                    }
                }
            }

            is OnDeletePlaylistClick -> {
                viewModelScope.launch {
                    playlistRepository.deletePlaylist(
                        playlist = event.playlist
                    )

                    _selectedPlaylist.update { null }
                }
            }

            is OnAddToPlaylist -> {
                viewModelScope.launch {
                    if (event.playlist.trackList.any { it in event.tracks }) {
                        SnackbarController.sendEvent(
                            SnackbarEvent(
                                message = R.string.track_is_already_on_playlist
                            )
                        )
                    }

                    val newTrackList =
                        (event.playlist.trackList.toMutableSet() + event.tracks).toList()
                    playlistRepository.updatePlaylistTrackList(
                        playlist = event.playlist,
                        trackList = newTrackList
                    )
                }
            }

            is OnRemoveFromPlaylist -> {
                viewModelScope.launch {
                    val newTrackList = event.playlist.trackList.toMutableList().apply {
                        removeAll(event.tracks)
                    }

                    playlistRepository.updatePlaylistTrackList(
                        playlist = event.playlist,
                        trackList = newTrackList
                    )

                    _selectedPlaylist.update {
                        it?.copy(
                            trackList = newTrackList
                        )
                    }
                }
            }

            is OnPlaylistReorder -> {
                if (event.playlist.trackList != event.trackList) {
                    viewModelScope.launch {
                        playlistRepository.updatePlaylistTrackList(
                            playlist = event.playlist,
                            trackList = event.trackList
                        )
                    }

                    _selectedPlaylist.update {
                        it?.copy(
                            trackList = event.trackList
                        )
                    }
                }
            }

            OnSettingsClick -> {
                _settingsSheetState.update {
                    it.copy(
                        isShown = true
                    )
                }
            }

            OnCloseSettingsClick -> {
                _settingsSheetState.update {
                    it.copy(
                        isShown = false
                    )
                }
            }

            OnScanFoldersClick -> {
                _settingsSheetState.update {
                    it.copy(
                        foldersWithAudio = trackRepository.getFoldersWithAudio()
                    )
                }
            }

            OnLyricsControlClick -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val track = _trackInfoSheetState.value.track ?: return@launch

                    val lyricsFromRepository = lyricsRepository.getLyricsByUri(track.uri.toString())
                    var lyricsFromTag: Lyrics? = readLyricsFromTag(track)

                    _lyricsControlSheetState.update {
                        it.copy(
                            lyricsFromTag = lyricsFromTag,
                            lyricsFromRepository = lyricsFromRepository
                        )
                    }
                }
            }

            OnDeleteLyricsClick -> {
                viewModelScope.launch {
                    _lyricsControlSheetState.value.lyricsFromRepository?.let {
                        lyricsRepository.deleteLyrics(it)
                    }
                }

                _lyricsControlSheetState.update {
                    it.copy(
                        lyricsFromRepository = null
                    )
                }

                _playbackState.update {
                    it.copy(
                        lyrics = null
                    )
                }
            }

            OnCopyLyricsFromTagClick -> {
                _lyricsControlSheetState.value.lyricsFromTag?.let { lyrics ->
                    viewModelScope.launch {
                        lyricsRepository.insertLyrics(lyrics)
                    }

                    _lyricsControlSheetState.update {
                        it.copy(
                            lyricsFromRepository = lyrics
                        )
                    }

                    _playbackState.update {
                        it.copy(
                            lyrics = null
                        )
                    }
                }
            }

            OnWriteLyricsToTagClick -> {
                val track = _trackInfoSheetState.value.track ?: return

                _lyricsControlSheetState.value.lyricsFromRepository?.let { lyrics ->
                    val plain = lyrics.plain?.joinToString("\n")
                        ?: return

                    viewModelScope.launch {
                        _lyricsControlSheetState.update {
                            it.copy(
                                isWritingToTag = true
                            )
                        }
                        _pendingMetadata.send(
                            track to Metadata(lyrics = plain)
                        )

                        delay(5000)

                        val fromTag = readLyricsFromTag(track)
                        _lyricsControlSheetState.update {
                            it.copy(
                                lyricsFromTag = fromTag,
                                isWritingToTag = false
                            )
                        }
                    }
                }
            }

            OnFetchLyricsFromRemoteClick -> {
                val track = _trackInfoSheetState.value.track ?: return
                viewModelScope.launch {
                    _lyricsControlSheetState.update {
                        it.copy(
                            isFetchingFromRemote = true
                        )
                    }
                    val lyrics = fetchLyrics(track)

                    _lyricsControlSheetState.update {
                        it.copy(
                            lyricsFromRepository = lyrics ?: it.lyricsFromRepository,
                            isFetchingFromRemote = false
                        )
                    }

                    _playbackState.update {
                        it.copy(
                            lyrics = null
                        )
                    }
                }
            }

            OnPublishLyricsOnRemoteClick -> {
                val track = _trackInfoSheetState.value.track ?: return

                if (
                    track.title == null ||
                    track.artist == null ||
                    track.album == null ||
                    track.artist == "<unknown>" ||
                    track.title.contains(".mp3")
                ) {
                    viewModelScope.launch {
                        SnackbarController.sendEvent(
                            SnackbarEvent(
                                message = R.string.unable_to_publish
                            )
                        )
                    }
                    return
                }

                _lyricsControlSheetState.value.lyricsFromRepository?.let { lyrics ->
                    viewModelScope.launch(Dispatchers.IO) {
                        _lyricsControlSheetState.update {
                            it.copy(
                                isPublishingOnRemote = true
                            )
                        }

                        val result = lyricsProvider.postLyrics(track, lyrics)
                        when (result) {
                            is Result.Success -> {
                                SnackbarController.sendEvent(
                                    SnackbarEvent(
                                        message = R.string.published_successfully
                                    )
                                )
                            }

                            is Result.Error -> {
                                when (result.error) {
                                    DataError.Network.BadRequest -> {
                                        SnackbarController.sendEvent(
                                            SnackbarEvent(
                                                message = R.string.unable_to_publish
                                            )
                                        )
                                    }

                                    DataError.Network.ParseError -> {
                                        SnackbarController.sendEvent(
                                            SnackbarEvent(
                                                message = R.string.failed_to_parse_response
                                            )
                                        )
                                    }

                                    DataError.Network.NoInternet -> {
                                        SnackbarController.sendEvent(
                                            SnackbarEvent(
                                                message = R.string.no_internet
                                            )
                                        )
                                    }

                                    else -> {
                                        SnackbarController.sendEvent(
                                            SnackbarEvent(
                                                message = R.string.unknown_error_occurred
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        _lyricsControlSheetState.update {
                            it.copy(
                                isPublishingOnRemote = false
                            )
                        }
                    }
                }
            }
        }
    }

    fun playTrackFromUri(uri: Uri) {
        viewModelScope.launch {
            _pendingTrackUris.send(uri)
        }
    }

    /**
     * Plays music matching a voice search, e.g. "Play <query> on Lotus".
     * The query is resolved by the media session, the same way as for Android Auto.
     */
    fun playFromSearch(query: String, extras: Bundle?) {
        viewModelScope.launch {
            while (player == null) delay(500L)

            player?.run {
                setMediaItem(
                    MediaItem.Builder()
                        .setRequestMetadata(
                            MediaItem.RequestMetadata.Builder()
                                .setSearchQuery(query)
                                .setExtras(extras)
                                .build()
                        )
                        .build()
                )
                prepare()
                play()
            }
        }
    }

    fun setPickedCoverArtBytes(bytes: ByteArray) {
        _manualInfoEditSheetState.update {
            it.copy(
                pickedCoverArtBytes = bytes
            )
        }
    }

    fun onFolderPicked(path: String) {
        if (settings.isScanModeInclusive.value) {
            settings.updateExtraScanFolders(settings.extraScanFolders.value + path)
        } else {
            settings.updateExcludedScanFolders(settings.extraScanFolders.value + path)
        }
    }

    fun onLyricsPicked(lyrics: String) {
        _trackInfoSheetState.value.track?.let { track ->
            val lyrics = try {
                val syncedLyrics = lyrics.toSyncedLyrics()

                Lyrics(
                    uri = track.uri.toString(),
                    synced = syncedLyrics,
                    plain = syncedLyrics.map { it.second },
                    areFromRemote = false
                )
            } catch (_: IllegalArgumentException) {
                Lyrics(
                    uri = track.uri.toString(),
                    plain = lyrics.split('\n'),
                    areFromRemote = false
                )
            }
            viewModelScope.launch {
                lyricsRepository.insertLyrics(lyrics)
            }

            _lyricsControlSheetState.update {
                it.copy(
                    lyricsFromRepository = lyrics
                )
            }
        }
    }

    private fun startPositionUpdate(): Job {
        return viewModelScope.launch {
            player?.let { player ->
                while (_playbackState.value.isPlaying) {
                    _playbackState.update {
                        it.copy(
                            position = player.currentPosition
                        )
                    }
                    delay(50)
                }
            }
        }
    }

    private fun setPlayerPlaybackMode(playbackMode: PlaybackMode) {
        player?.setPlaybackMode(playbackMode)
    }

    private fun onPlayerPlaybackModeChanged() {
        val playbackMode = player?.playbackMode ?: return
        _playbackState.update {
            it.copy(playbackMode = playbackMode)
        }
    }

    private fun readLyricsFromTag(track: Track): Lyrics? {
        var lyrics: Lyrics? = null

        val readResult = lyricsReader.readFromTag(track)
        when (readResult) {
            is Result.Success -> {
                lyrics = readResult.data
            }

            is Result.Error -> {
                viewModelScope.launch {
                    when (readResult.error) {
                        DataError.Local.NoReadPermission -> {
                            SnackbarController.sendEvent(
                                SnackbarEvent(
                                    message = R.string.no_read_permission
                                )
                            )
                        }

                        DataError.Local.FailedToRead -> {
                            SnackbarController.sendEvent(
                                SnackbarEvent(
                                    message = R.string.failed_to_read
                                )
                            )
                        }

                        else -> {
                            SnackbarController.sendEvent(
                                SnackbarEvent(
                                    message = R.string.unknown_error_occurred
                                )
                            )
                        }
                    }
                }
            }
        }

        return lyrics
    }

    private suspend fun fetchLyrics(track: Track): Lyrics? {
        return withContext(Dispatchers.IO) {
            if (track.title == null || track.artist == null) {
                SnackbarController.sendEvent(
                    SnackbarEvent(
                        message = R.string.cant_look_for_lyrics_title_or_artist_is_missing
                    )
                )
                return@withContext null
            }

            var lyrics: Lyrics? = null
            val result = lyricsProvider.getLyrics(track)

            when (result) {
                is Result.Success -> {
                    lyrics = result.data
                    lyricsRepository.insertLyrics(lyrics)
                }

                is Result.Error -> {
                    when (result.error) {
                        DataError.Network.BadRequest -> {
                            SnackbarController.sendEvent(
                                SnackbarEvent(
                                    message = R.string.cant_look_for_lyrics_title_or_artist_is_missing
                                )
                            )
                        }

                        DataError.Network.NotFound -> {
                            SnackbarController.sendEvent(
                                SnackbarEvent(
                                    message = R.string.lyrics_not_found
                                )
                            )
                        }

                        DataError.Network.ParseError -> {
                            SnackbarController.sendEvent(
                                SnackbarEvent(
                                    message = R.string.failed_to_parse_response
                                )
                            )
                        }

                        DataError.Network.NoInternet -> {
                            SnackbarController.sendEvent(
                                SnackbarEvent(
                                    message = R.string.no_internet
                                )
                            )
                        }

                        else -> {
                            SnackbarController.sendEvent(
                                SnackbarEvent(
                                    message = R.string.unknown_error_occurred
                                )
                            )
                        }
                    }
                }
            }

            lyrics
        }
    }

    private fun loadLyrics() {
        _playbackState.value.currentTrack?.let { currentTrack ->
            if (currentTrack.uri.toString() == _playbackState.value.lyrics?.uri) return

            viewModelScope.launch {
                _playbackState.update {
                    it.copy(
                        lyrics = null,
                        isLoadingLyrics = true
                    )
                }

                var lyrics: Lyrics? = lyricsRepository.getLyricsByUri(currentTrack.uri.toString())
                    ?: fetchLyrics(currentTrack) ?: readLyricsFromTag(currentTrack)
                        ?.also { lyricsRepository.insertLyrics(it) }

                _playbackState.update {
                    it.copy(
                        lyrics = lyrics,
                        isLoadingLyrics = false
                    )
                }
            }
        }
    }

    fun parseM3U(playlistName: String, fileContent: String) {
        viewModelScope.launch {
            val paths = fileContent.lines().fastFilter { it.startsWith("/") }
            val tracks = paths.map { path ->
                _trackList.value.fastFirstOrNull { path == it.data }
            }.filterNotNull()
            val name = playlistName.filter { it.isDigit() || it.isLetter() || it.isWhitespace() }

            playlistRepository.insertPlaylist(
                Playlist(
                    name = name,
                    trackList = tracks
                )
            )

            SnackbarController.sendEvent(
                SnackbarEvent(
                    message = R.string.imported_successfully
                )
            )
        }
    }

    /**
     * Returns next element after [index]. If next element index is out of bounds returns first element.
     * If index is negative returns `null`
     */
    private fun <T> List<T>.nextAfterOrNull(index: Int): T? {
        return getOrNull((index + 1) % size)
    }
}