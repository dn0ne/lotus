package com.dn0ne.player.app.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.util.fastForEach
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.dn0ne.player.app.domain.track.Track
import com.dn0ne.player.app.domain.track.buildTrackMediaItem
import com.dn0ne.player.core.data.Settings
import java.util.concurrent.TimeUnit

class TrackRepositoryImpl(
    private val context: Context,
    private val settings: Settings,
) : TrackRepository {
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

        val projection = arrayOf(
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
        )

        val isScanModeInclusive = settings.isScanModeInclusive.value
        val scanMusicFolder = settings.scanMusicFolder.value
        val extraScanFolders = settings.extraScanFolders.value
        val excludedScanFolders = settings.excludedScanFolders.value
        val ignoreShortTracks = settings.ignoreShortTracks

        val selection = buildString {
            if (ignoreShortTracks) {
                append("${MediaStore.Audio.Media.DURATION} >= ? AND ")
            }

            append("(")
            var scanFilter = ""
            scanFilter = if (isScanModeInclusive) {
                (listOf(scanMusicFolder).filter { it } + extraScanFolders)
                    .joinToString(" OR ") {
                        "${MediaStore.Audio.Media.DATA} LIKE ?"
                    }
            } else {
                excludedScanFolders.joinToString(" AND ") {
                    "${MediaStore.Audio.Media.DATA} NOT LIKE ?"
                }
            }
            append(scanFilter.ifBlank { if (isScanModeInclusive) 0 else 1 })
            append(")")
        }

        val selectionArgs = mutableListOf<String>().apply {
            if (ignoreShortTracks) {
                add(TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS).toString())
            }

            if (isScanModeInclusive) {
                if (scanMusicFolder) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.path?.let {
                        add("$it/%")
                    }
                }
                addAll(extraScanFolders.map { "$it/%" })
            } else {
                addAll(excludedScanFolders.map { "$it/%" })
            }
        }.toTypedArray()

        val query = context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            null
        )

        val tracks = mutableListOf<Track>()
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val dateModifiedColumn =
                cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)

            val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumArtistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
            val yearColumn = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
            val trackNumberColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)

            while (cursor.moveToNext()) {
                val id = cursor.getLongOrNull(idColumn) ?: continue
                val data = cursor.getStringOrNull(dataColumn) ?: continue
                val duration = cursor.getIntOrNull(durationColumn) ?: continue
                val albumId = cursor.getLongOrNull(albumIdColumn) ?: continue
                val size = cursor.getLongOrNull(sizeColumn) ?: continue
                val dateModified = cursor.getLongOrNull(dateModifiedColumn) ?: continue

                val title = cursor.getStringOrNull(titleColumn)
                val album = cursor.getStringOrNull(albumColumn)
                val artist = cursor.getStringOrNull(artistColumn)
                val albumArtist = cursor.getStringOrNull(albumArtistColumn)
                val year = cursor.getStringOrNull(yearColumn)
                val trackNumber = cursor.getStringOrNull(trackNumberColumn)
                val genre = trackIdToGenre.getOrDefault(id, null)
                val bitrate = calcBitrate(size = size, duration = duration)?.toString()

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
                val mediaItem = buildTrackMediaItem(
                    uri = uri,
                    coverArtUri = albumArtUri,
                    duration = duration,
                    data = data,
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtist = albumArtist,
                    genre = genre,
                    year = year,
                    trackNumber = trackNumber
                )

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

    override fun getFoldersWithAudio(): Set<String> {
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL
                )
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

        val projection = arrayOf(
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.DURATION} >= ?"

        val selectionArgs = arrayOf(
            TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS).toString()
        )

        val query = context.contentResolver.query(
            collection,
            projection,
            selection.takeIf { settings.ignoreShortTracks },
            selectionArgs. takeIf { settings.ignoreShortTracks },
            null
        )

        val paths = mutableSetOf<String>()
        query?.use { cursor ->
            val dataColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            if (dataColumn < 0) return emptySet()

            while (cursor.moveToNext()) {
                val data = cursor.getStringOrNull(dataColumn) ?: continue
                paths += data.substringBeforeLast('/')
            }
        }

        return paths
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

    fun calcBitrate(size: Long, duration: Int): Int? {
        if (duration <= 0) return null
        return ((size * 8) / duration).toInt()
    }
}

data class Genre(
    val id: Long,
    val name: String
)