package com.dn0ne.player.app.domain.track

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import com.dn0ne.player.auto.ArtworkProvider

/**
 * Builds the [MediaItem] that is handed to the player for a track.
 *
 * The media id and metadata are what external controllers (Android Auto, system media controls,
 * Bluetooth) see, so the item must be fully described up front instead of relying on tags
 * extracted after the file is loaded. The artwork points to [ArtworkProvider] so that controllers
 * running in another process are able to load it.
 */
@OptIn(UnstableApi::class)
fun buildTrackMediaItem(
    uri: Uri,
    coverArtUri: Uri,
    duration: Int,
    data: String,
    title: String?,
    artist: String?,
    album: String?,
    albumArtist: String?,
    genre: String?,
    year: String?,
    trackNumber: String?
): MediaItem = MediaItem.Builder()
    .setMediaId(uri.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title ?: data.substringAfterLast('/').substringBeforeLast('.'))
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(albumArtist)
            .setGenre(genre)
            .setRecordingYear(year?.take(4)?.toIntOrNull())
            .setTrackNumber(trackNumber?.substringBefore('/')?.toIntOrNull())
            .setDurationMs(duration.toLong())
            .setArtworkUri(ArtworkProvider.artworkUriFor(coverArtUri))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .build()
    )
    .build()
