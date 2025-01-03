package com.dn0ne.player.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.util.fastForEach
import androidx.media3.common.MediaItem
import com.dn0ne.player.app.domain.track.Track
import java.util.concurrent.TimeUnit

class TrackRepositoryImpl(
    private val context: Context
): TrackRepository {
    override fun getTracks(): List<Track> {
        val trackIdToGenre = getTrackIdToGenreMap()

        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL
                )
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,

            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.BITRATE)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(
            TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS).toString()
        )

        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val query = context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )


        val tracks = mutableListOf<Track>()
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateModifiedColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumArtistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val trackNumberColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val bitrateColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.BITRATE)
            } else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val data = cursor.getString(dataColumn)
                val duration = cursor.getInt(durationColumn)
                val albumId = cursor.getLong(albumIdColumn)
                val size = cursor.getLong(sizeColumn)
                val dateModified = cursor.getLong(dateModifiedColumn)

                val title = cursor.getString(titleColumn)
                val album = cursor.getString(albumColumn)
                val artist = cursor.getString(artistColumn)
                val albumArtist = cursor.getString(albumArtistColumn)
                val year = cursor.getString(yearColumn)
                val trackNumber = cursor.getString(trackNumberColumn)
                val genre = trackIdToGenre.getOrDefault(id, null)
                val bitrate = if (bitrateColumn >= 0) {
                    cursor.getString(bitrateColumn)
                } else null

                val uri: Uri = ContentUris.withAppendedId(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        MediaStore.Audio.Media.getContentUri(
                            MediaStore.VOLUME_EXTERNAL
                        )
                    } else {
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    },
                    id
                )

                val albumArtUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                val mediaItem = MediaItem.fromUri(uri)

                tracks += Track(
                    uri = uri,
                    mediaItem = mediaItem,
                    coverArtUri = albumArtUri,
                    duration = duration,
                    size = size,
                    dateModified = dateModified,
                    data = data,

                    title = title,
                    album = album,
                    artist = artist,
                    albumArtist = albumArtist,
                    genre = genre,
                    year = year,
                    trackNumber = trackNumber,
                    bitrate = bitrate
                )
            }
        }

        return tracks
    }

    fun getGenres(): List<Genre> {
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Genres.getContentUri(
                    MediaStore.VOLUME_EXTERNAL
                )
            } else {
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI
            }

        val projection = arrayOf(
            MediaStore.Audio.Genres._ID,
            MediaStore.Audio.Genres.NAME
        )

        val query = context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            null
        )

        val genres = mutableListOf<Genre>()
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "<unknown>"

                genres += Genre(
                    id = id,
                    name = name
                )
            }
        }

        return genres
    }

    fun getTrackIdToGenreMap(): Map<Long, String> {
        val genres = getGenres()

        val trackIdToGenreMap = mutableMapOf<Long, String>()
        genres.fastForEach { genre ->
            val collection = MediaStore.Audio.Genres.Members.getContentUri(
                "external",
                genre.id
            )

            val projection = arrayOf(
                MediaStore.Audio.Genres.Members.AUDIO_ID
            )


            val query = context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                null
            )

            query?.use { cursor ->
                val audioIdColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Genres.Members.AUDIO_ID)

                while (cursor.moveToNext()) {
                    val audioId = cursor.getLong(audioIdColumn)

                    trackIdToGenreMap += audioId to genre.name
                }
            }
        }

        return trackIdToGenreMap
    }
}

data class Genre(
    val id: Long,
    val name: String
)