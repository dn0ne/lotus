package com.dn0ne.player.app.presentation

import android.net.Uri
import android.util.Log
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.fastMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.dn0ne.player.EqualizerController
import com.dn0ne.player.R
import com.dn0ne.player.app.data.SavedPlayerState
import com.dn0ne.player.app.data.remote.lyrics.LyricsProvider
import com.dn0ne.player.app.data.remote.metadata.MetadataProvider
import com.dn0ne.player.app.data.repository.LyricsRepository
import com.dn0ne.player.app.data.repository.PlaylistRepository
import com.dn0ne.player.app.data.repository.TrackRepository
import com.dn0ne.player.app.domain.lyrics.Lyrics
import com.dn0ne.player.app.domain.metadata.Metadata
import com.dn0ne.player.app.domain.playback.PlaybackMode
import com.dn0ne.player.app.domain.result.DataError
import com.dn0ne.player.app.domain.result.Result
import com.dn0ne.player.app.domain.sort.sortedBy
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.domain.track.format
import com.dn0ne.player.app.presentation.components.playback.PlaybackState
import com.dn0ne.player.app.presentation.components.settings.SettingsSheetState
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarController
import com.dn0ne.player.app.presentation.components.snackbar.SnackbarEvent
import com.dn0ne.player.app.presentation.components.trackinfo.ChangesSheetState
import com.dn0ne.player.app.presentation.components.trackinfo.InfoSearchSheetState
import com.dn0ne.player.app.presentation.components.trackinfo.ManualInfoEditSheetState
import com.dn0ne.player.app.presentation.components.trackinfo.TrackInfoSheetState
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
    private val playlistRepository: PlaylistRepository,
    private val unsupportedArtworkEditFormats: List<String>,
    val settings: Settings,
    private val equalizerController: EqualizerController
) : ViewModel() {
    var player: Player? = null

    private val _settingsSheetState = MutableStateFlow(
        SettingsSheetState(
            settings = settings,
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
    private val _trackInfoSheetState = MutableStateFlow(
        TrackInfoSheetState(
            showRisksOfMetadataEditingDialog = !settings.areRisksOfMetadataEditingAccepted
        )
    )
    val trackInfoSheetState = combine(
        _trackInfoSheetState, _infoSearchSheetState, _changesSheetState, _manualInfoEditSheetState
    ) { trackInfoSheetState, infoSearchSheetState, changesSheetState, manualInfoEditSheetState ->
        trackInfoSheetState.copy(
            infoSearchSheetState = infoSearchSheetState,
            changesSheetState = changesSheetState,
            manualInfoEditSheetState = manualInfoEditSheetState
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

            if (player?.currentMediaItem != null) {
                val playlist = savedPlayerState.playlist
                playlist?.let { playlist ->
                    _playbackState.update {
                        it.copy(
                            playlist = playlist,
                            currentTrack = playlist.trackList.fastFirstOrNull { player!!.currentMediaItem == it.mediaItem },
                            isPlaying = player!!.isPlaying,
                            position = player!!.currentPosition
                        )
                    }

                    if (player!!.isPlaying) {
                        positionUpdateJob = startPositionUpdate()
                    }
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
                        _playbackState.update {
                            it.copy(
                                currentTrack = it.playlist?.trackList?.fastFirstOrNull {
                                    it.mediaItem == mediaItem
                                },
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
                        PlayerScreenEvent.OnTrackClick(
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
            is PlayerScreenEvent.OnTrackClick -> {
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
                    }
                }
            }

            PlayerScreenEvent.OnPauseClick -> {
                player?.run {
                    pause()
                }
            }

            PlayerScreenEvent.OnPlayClick -> {
                player?.let { player ->
                    if (player.currentMediaItem == null) return

                    player.play()
                }
            }

            PlayerScreenEvent.OnSeekToNextClick -> {
                player?.let { player ->
                    if (!player.hasNextMediaItem()) return

                    player.seekToNextMediaItem()
                }
            }

            PlayerScreenEvent.OnSeekToPreviousClick -> {
                player?.let { player ->
                    if (player.hasPreviousMediaItem()) {
                        player.seekToPreviousMediaItem()
                    } else {
                        player.seekTo(0L)
                        _playbackState.update {
                            it.copy(
                                position = 0L
                            )
                        }
                    }
                }
            }

            is PlayerScreenEvent.OnSeekTo -> {
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

            PlayerScreenEvent.OnPlaybackModeClick -> {
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

            is PlayerScreenEvent.OnPlayerExpandedChange -> {
                _playbackState.update {
                    it.copy(
                        isPlayerExpanded = event.isExpanded,
                        isLyricsSheetExpanded = false
                    )
                }
            }

            is PlayerScreenEvent.OnLyricsSheetExpandedChange -> {
                _playbackState.update {
                    it.copy(
                        isLyricsSheetExpanded = event.isExpanded
                    )
                }
            }

            PlayerScreenEvent.OnLyricsClick -> {
                loadLyrics()
            }

            is PlayerScreenEvent.OnRemoveFromQueueClick -> {
                player?.let { player ->
                    if (event.index == player.currentMediaItemIndex) {
                        onEvent(PlayerScreenEvent.OnSeekToNextClick)
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

            is PlayerScreenEvent.OnReorderingQueue -> {
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

            is PlayerScreenEvent.OnPlayNextClick -> {
                if (_playbackState.value.currentTrack == event.track) return

                _playbackState.value.playlist?.let { playlist ->
                    val trackIndex = playlist.trackList.indexOf(event.track)
                    val currentTrackIndex = _playbackState.value.currentTrack?.let {
                        playlist.trackList.indexOf(it)
                    } ?: 0

                    if (trackIndex >= 0) {
                        onEvent(
                            PlayerScreenEvent.OnReorderingQueue(
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
                        PlayerScreenEvent.OnTrackClick(
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

            is PlayerScreenEvent.OnAddToQueueClick -> {
                if (_playbackState.value.currentTrack == event.track) return

                _playbackState.value.playlist?.let { playlist ->
                    val trackIndex = playlist.trackList.indexOf(event.track)

                    if (trackIndex >= 0) {
                        onEvent(
                            PlayerScreenEvent.OnReorderingQueue(
                                trackIndex,
                                playlist.trackList.lastIndex
                            )
                        )
                        return
                    } else {
                        player?.let { player ->
                            player.addMediaItem(event.track.mediaItem)

                            _playbackState.update {
                                it.copy(
                                    playlist = playlist.copy(
                                        trackList = playlist.trackList.toMutableList().apply {
                                            add(event.track)
                                        }
                                    )
                                )
                            }
                        }

                    }
                } ?: run {
                    onEvent(
                        PlayerScreenEvent.OnTrackClick(
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

            is PlayerScreenEvent.OnViewTrackInfoClick -> {
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

            PlayerScreenEvent.OnCloseTrackInfoSheetClick -> {
                _trackInfoSheetState.update {
                    it.copy(
                        isShown = false
                    )
                }
            }

            PlayerScreenEvent.OnAcceptingRisksOfMetadataEditing -> {
                settings.areRisksOfMetadataEditingAccepted = true
                _trackInfoSheetState.update {
                    it.copy(
                        showRisksOfMetadataEditingDialog = false
                    )
                }
            }

            PlayerScreenEvent.OnMatchDurationWhenSearchMetadataClick -> {
                settings.matchDurationWhenSearchMetadata = !settings.matchDurationWhenSearchMetadata
            }

            is PlayerScreenEvent.OnSearchInfo -> {
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

            is PlayerScreenEvent.OnMetadataSearchResultPick -> {
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

            is PlayerScreenEvent.OnOverwriteMetadataClick -> {
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

            PlayerScreenEvent.OnRestoreCoverArtClick -> {
                _manualInfoEditSheetState.update {
                    it.copy(
                        pickedCoverArtBytes = null
                    )
                }
            }

            is PlayerScreenEvent.OnConfirmMetadataEditClick -> {
                _changesSheetState.update {
                    it.copy(
                        metadata = event.metadata,
                        isArtFromGallery = event.metadata.coverArtBytes != null
                    )
                }
            }

            is PlayerScreenEvent.OnPlaylistSelection -> {
                _selectedPlaylist.update {
                    event.playlist
                }
            }

            is PlayerScreenEvent.OnTrackSortChange -> {
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

            is PlayerScreenEvent.OnPlaylistSortChange -> {
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

            is PlayerScreenEvent.OnCreatePlaylistClick -> {
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

            is PlayerScreenEvent.OnRenamePlaylistClick -> {
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

            is PlayerScreenEvent.OnDeletePlaylistClick -> {
                viewModelScope.launch {
                    playlistRepository.deletePlaylist(
                        playlist = event.playlist
                    )

                    _selectedPlaylist.update { null }
                }
            }

            is PlayerScreenEvent.OnAddToPlaylist -> {
                viewModelScope.launch {
                    if (event.playlist.trackList.contains(event.track)) {
                        SnackbarController.sendEvent(
                            SnackbarEvent(
                                message = R.string.track_is_already_on_playlist
                            )
                        )
                        return@launch
                    }

                    val newTrackList = event.playlist.trackList + event.track
                    playlistRepository.updatePlaylistTrackList(
                        playlist = event.playlist,
                        trackList = newTrackList
                    )
                }
            }

            is PlayerScreenEvent.OnRemoveFromPlaylist -> {
                viewModelScope.launch {
                    val newTrackList = event.playlist.trackList.toMutableList().apply {
                        remove(event.track)
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

            is PlayerScreenEvent.OnPlaylistReorder -> {
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

            PlayerScreenEvent.OnSettingsClick -> {
                _settingsSheetState.update {
                    it.copy(
                        isShown = true
                    )
                }
            }

            PlayerScreenEvent.OnCloseSettingsClick -> {
                _settingsSheetState.update {
                    it.copy(
                        isShown = false
                    )
                }
            }

            PlayerScreenEvent.OnScanFoldersClick -> {
                _settingsSheetState.update {
                    it.copy(
                        foldersWithAudio = trackRepository.getFoldersWithAudio()
                    )
                }
            }
        }
    }

    fun playTrackFromUri(uri: Uri) {
        viewModelScope.launch {
            _pendingTrackUris.send(uri)
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
        when (playbackMode) {
            PlaybackMode.Repeat -> {
                player?.repeatMode = Player.REPEAT_MODE_ALL
                player?.shuffleModeEnabled = false
            }

            PlaybackMode.RepeatOne -> {
                player?.repeatMode = Player.REPEAT_MODE_ONE
                player?.shuffleModeEnabled = false
            }

            PlaybackMode.Shuffle -> {
                player?.repeatMode = Player.REPEAT_MODE_ALL
                player?.shuffleModeEnabled = true
            }
        }
    }

    private fun loadLyrics() {
        _playbackState.value.currentTrack?.let { currentTrack ->
            if (currentTrack.uri.toString() == _playbackState.value.lyrics?.uri) return

            _playbackState.update {
                it.copy(
                    lyrics = null,
                    isLoadingLyrics = true
                )
            }

            var lyrics: Lyrics? = lyricsRepository.getLyricsByUri(currentTrack.uri.toString())

            if (lyrics == null) {
                if (currentTrack.title == null || currentTrack.artist == null) {
                    viewModelScope.launch {
                        SnackbarController.sendEvent(
                            SnackbarEvent(
                                message = R.string.cant_look_for_lyrics_title_or_artist_is_missing
                            )
                        )
                    }
                    return
                }

                viewModelScope.launch {
                    val result = lyricsProvider.getLyrics(currentTrack)

                    when (result) {
                        is Result.Success -> {
                            lyrics = result.data

                            lyricsRepository.insertLyrics(lyrics)

                            _playbackState.update {
                                it.copy(
                                    lyrics = lyrics,
                                    isLoadingLyrics = false
                                )
                            }
                        }

                        is Result.Error -> {
                            _playbackState.update {
                                it.copy(
                                    isLoadingLyrics = false
                                )
                            }
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
                }
            } else {
                _playbackState.update {
                    it.copy(
                        lyrics = lyrics,
                        isLoadingLyrics = false
                    )
                }
            }
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