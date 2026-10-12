package com.dn0ne.player.auto

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import com.dn0ne.player.R
import com.dn0ne.player.app.data.repository.PlaylistRepository
import com.dn0ne.player.app.data.repository.TrackRepository
import com.dn0ne.player.app.domain.sort.PlaylistSort
import com.dn0ne.player.app.domain.sort.SortOrder
import com.dn0ne.player.app.domain.sort.sortedBy
import com.dn0ne.player.app.domain.track.Playlist
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.presentation.components.topbar.Tab
import com.dn0ne.player.core.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Media library exposed to Android Auto and other media browsers.
 *
 * The tree mirrors the app: the root holds the user's tabs in their configured order, and
 * every container resolves to the same [Playlist] the app itself would build, so a queue started
 * from the car is recognized by the phone UI and vice versa.
 *
 * Media id scheme:
 * - `root`, `more` and tab ids (`tracks`, `albums`, ...) for top level nodes
 * - `<type>/<encoded key>` for containers, e.g. `album/Abbey%20Road`
 * - `track|<container id>|<encoded track uri>` for a track played within a container
 * - `shuffle|<container id>` for shuffling a whole container
 */
@OptIn(UnstableApi::class)
class AutoLibrary(
    private val context: Context,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val settings: Settings
) {
    data class PlaybackRequest(
        val playlist: Playlist,
        val startIndex: Int,
        val shuffle: Boolean
    )

    private val tracksMutex = Mutex()
    private var cachedTracks: List<Track>? = null
    private var cachedTracksTimestamp = 0L

    private var rootChildrenLimit = DEFAULT_ROOT_CHILDREN_LIMIT

    val hasAudioPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        ) == PackageManager.PERMISSION_GRANTED

    fun invalidate() {
        cachedTracks = null
    }

    fun rootItem(rootHints: Bundle?): MediaItem {
        rootHints
            ?.getInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, DEFAULT_ROOT_CHILDREN_LIMIT)
            ?.takeIf { it > 0 }
            ?.let { rootChildrenLimit = it }

        return browsableItem(
            id = ROOT_ID,
            title = context.getString(R.string.app_name),
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        )
    }

    /**
     * Content style applied to the whole tree unless a node overrides it.
     */
    fun rootExtras(): Bundle = Bundle().apply {
        putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        )
        putInt(
            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        )
    }

    /**
     * @return children of [parentId] or null if there's no such node.
     */
    suspend fun getChildren(parentId: String): List<MediaItem>? {
        when (parentId) {
            ROOT_ID -> {
                val tabs = settings.tabs.value
                return if (tabs.size <= rootChildrenLimit) {
                    tabs.map(::tabItem)
                } else {
                    tabs.take(rootChildrenLimit - 1).map(::tabItem) + moreItem()
                }
            }

            MORE_ID -> {
                return settings.tabs.value.drop(rootChildrenLimit - 1).map(::tabItem)
            }
        }

        tabFromId(parentId)?.let { tab ->
            return getTabChildren(tab)
        }

        val container = Container.fromId(parentId) ?: return null
        val playlist = resolveContainer(container) ?: return null
        return getContainerChildren(container, playlist)
    }

    suspend fun getItem(mediaId: String): MediaItem? {
        when (mediaId) {
            ROOT_ID -> return rootItem(rootHints = null)
            MORE_ID -> return moreItem()
        }

        tabFromId(mediaId)?.let { return tabItem(it) }

        Container.fromId(mediaId)?.let { container ->
            val playlist = resolveContainer(container) ?: return null
            return containerItem(container, playlist)
        }

        parseShuffleId(mediaId)?.let { container ->
            val playlist = resolveContainer(container) ?: return null
            return shuffleItem(container, playlist)
        }

        parseTrackId(mediaId)?.let { (container, uri) ->
            val track = tracks().firstOrNull { it.uri.toString() == uri } ?: return null
            return trackItem(container, track)
        }

        val track = tracks().firstOrNull { it.uri.toString() == mediaId } ?: return null
        return trackItem(Container.AllTracks, track)
    }

    suspend fun search(query: String): List<MediaItem> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return emptyList()

        val tracks = tracks()
        val albums = tracks
            .groupBy { it.album }
            .filterKeys { it?.contains(trimmedQuery, ignoreCase = true) == true }
            .entries
            .take(MAX_SEARCH_GROUP_SIZE)
            .map { (album, trackList) ->
                containerItem(Container.Album(album), Playlist(album, trackList))
                    .withGroupTitle(context.getString(R.string.albums))
            }
        val artists = tracks
            .groupBy { it.artist }
            .filterKeys { it?.contains(trimmedQuery, ignoreCase = true) == true }
            .entries
            .take(MAX_SEARCH_GROUP_SIZE)
            .map { (artist, trackList) ->
                containerItem(Container.Artist(artist), Playlist(artist, trackList))
                    .withGroupTitle(context.getString(R.string.artists))
            }

        val searchContainer = Container.Search(trimmedQuery)
        val matchingTracks = resolveContainer(searchContainer)
            ?.trackList
            .orEmpty()
            .map {
                trackItem(searchContainer, it)
                    .withGroupTitle(context.getString(R.string.tracks))
            }

        return albums + artists + matchingTracks
    }

    /**
     * Turns items requested by a controller (which carry only a media id or a search query)
     * into a queue of playable tracks.
     */
    suspend fun resolvePlaybackRequest(mediaItems: List<MediaItem>): PlaybackRequest? {
        val mediaItem = mediaItems.singleOrNull()
            ?: return resolveTracks(mediaItems)
                .takeIf { it.isNotEmpty() }
                ?.let {
                    PlaybackRequest(
                        playlist = Playlist(name = null, trackList = it),
                        startIndex = 0,
                        shuffle = false
                    )
                }

        val searchQuery = mediaItem.requestMetadata.searchQuery
        if (mediaItem.mediaId.isEmpty() && searchQuery != null) {
            return resolveVoiceSearch(searchQuery, mediaItem.requestMetadata.extras)
        }

        val mediaId = mediaItem.mediaId

        parseShuffleId(mediaId)?.let { container ->
            val playlist = resolveContainer(container)
                ?.takeIf { it.trackList.isNotEmpty() }
                ?: return null
            return PlaybackRequest(
                playlist = playlist,
                startIndex = playlist.trackList.indices.random(),
                shuffle = true
            )
        }

        parseTrackId(mediaId)?.let { (container, uri) ->
            val playlist = resolveContainer(container)
            val index = playlist?.trackList?.indexOfFirst { it.uri.toString() == uri } ?: -1
            if (playlist != null && index >= 0) {
                return PlaybackRequest(playlist = playlist, startIndex = index, shuffle = false)
            }
        }

        Container.fromId(mediaId)?.let { container ->
            val playlist = resolveContainer(container)
                ?.takeIf { it.trackList.isNotEmpty() }
                ?: return null
            return PlaybackRequest(playlist = playlist, startIndex = 0, shuffle = false)
        }

        val track = trackForMediaId(mediaId) ?: return null
        val allTracks = Playlist(name = null, trackList = tracks())
        return PlaybackRequest(
            playlist = allTracks,
            startIndex = allTracks.trackList.indexOf(track).coerceAtLeast(0),
            shuffle = false
        )
    }

    /**
     * Shuffles the whole library, used when the user asks to play music
     * without saying what and there's nothing to resume.
     */
    suspend fun shuffleAll(): PlaybackRequest? {
        val playlist = resolveContainer(Container.AllTracks)
            ?.takeIf { it.trackList.isNotEmpty() }
            ?: return null
        return PlaybackRequest(
            playlist = playlist,
            startIndex = playlist.trackList.indices.random(),
            shuffle = true
        )
    }

    private suspend fun resolveVoiceSearch(query: String, extras: Bundle?): PlaybackRequest? {
        if (query.isBlank()) return shuffleAll()

        val tracks = tracks()
        val focus = extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)
        val requestedAlbum = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)
            ?.takeIf { focus == MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE }
        val requestedArtist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)
            ?.takeIf { focus == MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE }

        val album = (requestedAlbum ?: query).let { name ->
            tracks.firstOrNull { it.album.equals(name, ignoreCase = true) }?.album
        }
        val artist = (requestedArtist ?: query).let { name ->
            tracks.firstOrNull { it.artist.equals(name, ignoreCase = true) }?.artist
        }

        val container = when {
            requestedArtist != null && artist != null -> Container.Artist(artist)
            album != null -> Container.Album(album)
            artist != null -> Container.Artist(artist)
            else -> Container.Search(query.trim())
        }

        val playlist = resolveContainer(container)
            ?.takeIf { it.trackList.isNotEmpty() }
            ?: return null
        return PlaybackRequest(playlist = playlist, startIndex = 0, shuffle = false)
    }

    suspend fun resolveTracks(mediaItems: List<MediaItem>): List<Track> =
        mediaItems.mapNotNull { trackForMediaId(it.mediaId) }

    private suspend fun trackForMediaId(mediaId: String): Track? {
        val uri = parseTrackId(mediaId)?.second ?: mediaId
        return tracks().firstOrNull { it.uri.toString() == uri }
    }

    private suspend fun tracks(): List<Track> = tracksMutex.withLock {
        cachedTracks
            ?.takeIf { SystemClock.elapsedRealtime() - cachedTracksTimestamp < TRACKS_CACHE_TTL_MS }
            ?.let { return it }

        val tracks = withContext(Dispatchers.IO) {
            try {
                trackRepository.getTracks()
                    .sortedBy(settings.trackSort, settings.trackSortOrder)
            } catch (e: SecurityException) {
                emptyList()
            }
        }
        cachedTracks = tracks
        cachedTracksTimestamp = SystemClock.elapsedRealtime()
        tracks
    }

    private suspend fun playlists(): List<Playlist> = withContext(Dispatchers.IO) {
        playlistRepository.getPlaylists().first()
    }.sortedBy(settings.playlistSort, settings.playlistSortOrder)

    private suspend fun resolveContainer(container: Container): Playlist? {
        return when (container) {
            Container.AllTracks -> Playlist(name = null, trackList = tracks())

            is Container.Album -> tracks()
                .filter { it.album == container.name }
                .takeIf { it.isNotEmpty() }
                ?.let { Playlist(name = container.name, trackList = it) }

            is Container.Artist -> tracks()
                .filter { it.artist == container.name }
                .takeIf { it.isNotEmpty() }
                ?.let { Playlist(name = container.name, trackList = it) }

            is Container.Genre -> tracks()
                .filter { it.genre == container.name }
                .takeIf { it.isNotEmpty() }
                ?.let { Playlist(name = container.name, trackList = it) }

            is Container.Folder -> tracks()
                .filter { it.data.substringBeforeLast('/') == container.path }
                .takeIf { it.isNotEmpty() }
                ?.let {
                    Playlist(name = container.path.substringAfterLast('/'), trackList = it)
                }

            is Container.UserPlaylist -> playlists().firstOrNull { it.name == container.name }

            is Container.Search -> Playlist(
                name = null,
                trackList = tracks().filter { it.matches(container.query) }
            )
        }
    }

    private suspend fun getTabChildren(tab: Tab): List<MediaItem> {
        val playlistSort = settings.playlistSort
        val playlistSortOrder = settings.playlistSortOrder

        return when (tab) {
            Tab.Tracks -> getContainerChildren(
                container = Container.AllTracks,
                playlist = Playlist(name = null, trackList = tracks())
            )

            Tab.Albums -> tracks()
                .groupBy { it.album }
                .map { (album, trackList) -> Playlist(album, trackList) }
                .sortedBy(playlistSort, playlistSortOrder)
                .map { containerItem(Container.Album(it.name), it) }

            Tab.Artists -> tracks()
                .groupBy { it.artist }
                .map { (artist, trackList) -> Playlist(artist, trackList) }
                .sortedBy(playlistSort, playlistSortOrder)
                .map { containerItem(Container.Artist(it.name), it) }

            Tab.Genres -> tracks()
                .groupBy { it.genre }
                .map { (genre, trackList) -> Playlist(genre, trackList) }
                .sortedBy(playlistSort, playlistSortOrder)
                .map { containerItem(Container.Genre(it.name), it) }

            Tab.Folders -> {
                val comparator = when (playlistSort) {
                    PlaylistSort.Title -> compareBy<Pair<String, Playlist>> { it.second.name }
                    PlaylistSort.TrackCount -> compareBy { it.second.trackList.size }
                }
                tracks()
                    .groupBy { it.data.substringBeforeLast('/') }
                    .map { (path, trackList) ->
                        path to Playlist(path.substringAfterLast('/'), trackList)
                    }
                    .sortedWith(
                        if (playlistSortOrder == SortOrder.Descending) {
                            comparator.reversed()
                        } else comparator
                    )
                    .map { (path, playlist) -> containerItem(Container.Folder(path), playlist) }
            }

            Tab.Playlists -> playlists()
                .map { containerItem(Container.UserPlaylist(it.name), it) }
        }
    }

    private fun getContainerChildren(container: Container, playlist: Playlist): List<MediaItem> {
        val trackItems = playlist.trackList.map { trackItem(container, it) }
        return if (playlist.trackList.size > 1) {
            listOf(shuffleItem(container, playlist)) + trackItems
        } else trackItems
    }

    private fun tabItem(tab: Tab): MediaItem {
        val (iconRes, mediaType) = when (tab) {
            Tab.Playlists -> R.drawable.ic_auto_playlists to MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
            Tab.Tracks -> R.drawable.ic_auto_tracks to MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
            Tab.Albums -> R.drawable.ic_auto_albums to MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
            Tab.Artists -> R.drawable.ic_auto_artists to MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
            Tab.Genres -> R.drawable.ic_auto_genres to MediaMetadata.MEDIA_TYPE_FOLDER_GENRES
            Tab.Folders -> R.drawable.ic_auto_folders to MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
        }

        val browsableStyle = when (tab) {
            Tab.Albums, Tab.Artists -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
            Tab.Genres, Tab.Folders -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
            Tab.Playlists, Tab.Tracks -> MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
        }

        return browsableItem(
            id = tab.id,
            title = context.getString(tab.titleResId),
            artworkUri = resourceUri(iconRes),
            mediaType = mediaType,
            browsableChildrenStyle = browsableStyle
        )
    }

    private fun moreItem(): MediaItem = browsableItem(
        id = MORE_ID,
        title = context.getString(R.string.auto_more),
        artworkUri = resourceUri(R.drawable.ic_auto_more),
        mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        browsableChildrenStyle = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
    )

    private fun containerItem(container: Container, playlist: Playlist): MediaItem {
        val firstTrack = playlist.trackList.firstOrNull()
        val trackCount = trackCountText(playlist.trackList.size)

        return when (container) {
            Container.AllTracks -> tabItem(Tab.Tracks)

            is Container.Album -> browsableItem(
                id = container.id,
                title = container.name ?: context.getString(R.string.unknown_album),
                subtitle = firstTrack?.albumArtist
                    ?: firstTrack?.artist
                    ?: context.getString(R.string.unknown_artist),
                artworkUri = firstTrack?.mediaItem?.mediaMetadata?.artworkUri,
                mediaType = MediaMetadata.MEDIA_TYPE_ALBUM
            )

            is Container.Artist -> browsableItem(
                id = container.id,
                title = container.name ?: context.getString(R.string.unknown_artist),
                subtitle = trackCount,
                artworkUri = firstTrack?.mediaItem?.mediaMetadata?.artworkUri,
                mediaType = MediaMetadata.MEDIA_TYPE_ARTIST
            )

            is Container.Genre -> browsableItem(
                id = container.id,
                title = container.name ?: context.getString(R.string.unknown_genre),
                subtitle = trackCount,
                artworkUri = resourceUri(R.drawable.ic_auto_genres),
                mediaType = MediaMetadata.MEDIA_TYPE_GENRE
            )

            is Container.Folder -> browsableItem(
                id = container.id,
                title = playlist.name?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.unknown_folder),
                subtitle = trackCount,
                artworkUri = resourceUri(R.drawable.ic_auto_folders),
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
            )

            is Container.UserPlaylist -> browsableItem(
                id = container.id,
                title = container.name ?: context.getString(R.string.unknown),
                subtitle = trackCount,
                artworkUri = firstTrack?.mediaItem?.mediaMetadata?.artworkUri
                    ?: resourceUri(R.drawable.ic_auto_playlists),
                mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST
            )

            is Container.Search -> browsableItem(
                id = container.id,
                title = container.query,
                subtitle = trackCount,
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
            )
        }
    }

    private fun shuffleItem(container: Container, playlist: Playlist): MediaItem =
        MediaItem.Builder()
            .setMediaId("$SHUFFLE_PREFIX$SEPARATOR${container.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(context.getString(R.string.auto_shuffle))
                    .setDisplayTitle(context.getString(R.string.auto_shuffle))
                    .setSubtitle(trackCountText(playlist.trackList.size))
                    .setArtworkUri(resourceUri(R.drawable.ic_auto_shuffle))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .build()
            )
            .build()

    private fun trackItem(container: Container, track: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(
                "$TRACK_PREFIX$SEPARATOR${container.id}$SEPARATOR${Uri.encode(track.uri.toString())}"
            )
            .setMediaMetadata(track.mediaItem.mediaMetadata)
            .build()

    private fun browsableItem(
        id: String,
        title: String,
        subtitle: String? = null,
        artworkUri: Uri? = null,
        mediaType: Int,
        browsableChildrenStyle: Int? = null
    ): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .setSubtitle(subtitle)
                .setArtworkUri(artworkUri)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(mediaType)
                .setExtras(
                    Bundle().apply {
                        browsableChildrenStyle?.let {
                            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, it)
                        }
                        putInt(
                            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                            MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                        )
                    }
                )
                .build()
        )
        .build()

    private fun MediaItem.withGroupTitle(groupTitle: String): MediaItem {
        val extras = Bundle(mediaMetadata.extras ?: Bundle.EMPTY).apply {
            putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, groupTitle)
        }
        return buildUpon()
            .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
    }

    private fun trackCountText(count: Int): String =
        context.resources.getQuantityString(R.plurals.auto_track_count, count, count)

    private fun resourceUri(@DrawableRes resId: Int): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(context.resources.getResourceTypeName(resId))
        .appendPath(context.resources.getResourceEntryName(resId))
        .build()

    private fun Track.matches(query: String): Boolean {
        val fields = listOfNotNull(title, artist, album, albumArtist, genre)
        return query.split(' ')
            .filter { it.isNotBlank() }
            .all { word -> fields.any { it.contains(word, ignoreCase = true) } }
    }

    private sealed interface Container {
        val id: String

        data object AllTracks : Container {
            override val id = TRACKS_TAB_ID
        }

        data class Album(val name: String?) : Container {
            override val id = "album/${encodeKey(name)}"
        }

        data class Artist(val name: String?) : Container {
            override val id = "artist/${encodeKey(name)}"
        }

        data class Genre(val name: String?) : Container {
            override val id = "genre/${encodeKey(name)}"
        }

        data class Folder(val path: String) : Container {
            override val id = "folder/${encodeKey(path)}"
        }

        data class UserPlaylist(val name: String?) : Container {
            override val id = "playlist/${encodeKey(name)}"
        }

        data class Search(val query: String) : Container {
            override val id = "search/${encodeKey(query)}"
        }

        companion object {
            fun fromId(id: String): Container? {
                if (id == TRACKS_TAB_ID) return AllTracks

                val type = id.substringBefore('/', missingDelimiterValue = "")
                val key = decodeKey(id.substringAfter('/', missingDelimiterValue = ""))
                return when (type) {
                    "album" -> Album(key)
                    "artist" -> Artist(key)
                    "genre" -> Genre(key)
                    "folder" -> key?.let { Folder(it) }
                    "playlist" -> UserPlaylist(key)
                    "search" -> key?.let { Search(it) }
                    else -> null
                }
            }

            // Uri.encode() never emits a bare '%', so it can stand for a missing tag
            private fun encodeKey(key: String?): String = key?.let { Uri.encode(it) } ?: "%"
            private fun decodeKey(key: String): String? = if (key == "%") null else Uri.decode(key)
        }
    }

    private fun parseShuffleId(mediaId: String): Container? {
        val parts = mediaId.split(SEPARATOR)
        if (parts.size != 2 || parts[0] != SHUFFLE_PREFIX) return null
        return Container.fromId(parts[1])
    }

    private fun parseTrackId(mediaId: String): Pair<Container, String>? {
        val parts = mediaId.split(SEPARATOR)
        if (parts.size != 3 || parts[0] != TRACK_PREFIX) return null
        val container = Container.fromId(parts[1]) ?: return null
        return container to Uri.decode(parts[2])
    }

    private val Tab.id: String
        get() = when (this) {
            Tab.Tracks -> TRACKS_TAB_ID
            Tab.Playlists -> PLAYLISTS_ID
            else -> name.lowercase()
        }

    private fun tabFromId(id: String): Tab? = Tab.entries.firstOrNull { it.id == id }
        ?.takeIf { it != Tab.Tracks }

    companion object {
        const val ROOT_ID = "root"
        const val PLAYLISTS_ID = "playlists"
        private const val MORE_ID = "more"
        private const val TRACKS_TAB_ID = "tracks"
        private const val TRACK_PREFIX = "track"
        private const val SHUFFLE_PREFIX = "shuffle"
        private const val SEPARATOR = '|'

        private const val DEFAULT_ROOT_CHILDREN_LIMIT = 4
        private const val TRACKS_CACHE_TTL_MS = 30_000L
        private const val MAX_SEARCH_GROUP_SIZE = 10
    }
}
