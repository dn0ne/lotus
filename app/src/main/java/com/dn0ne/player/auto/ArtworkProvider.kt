package com.dn0ne.player.auto

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.ParcelFileDescriptor
import com.dn0ne.player.BuildConfig
import java.io.FileNotFoundException

/**
 * Serves album art to media controllers that live in other processes (Android Auto, Android
 * Automotive OS, system UI). They usually can't read MediaStore album art themselves because they
 * don't hold the audio permission, so the art is proxied through this provider.
 *
 * The provider isn't exported: [PlaybackService][com.dn0ne.player.PlaybackService] grants
 * read access to [ROOT_URI] to each browser that connects to the media library.
 */
class ArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("Artwork is read-only")

        val albumId = uri.pathSegments
            .takeIf { it.size == 2 && it[0] == PATH_ALBUM_ART }
            ?.get(1)
            ?.toLongOrNull()
            ?: throw FileNotFoundException(uri.toString())

        val source = ContentUris.withAppendedId(MEDIA_STORE_ALBUM_ART_URI, albumId)

        // Read the art as this app rather than as the calling controller,
        // which doesn't have the audio permission
        val identity = Binder.clearCallingIdentity()
        return try {
            context?.contentResolver?.openFileDescriptor(source, "r")
        } catch (e: Exception) {
            null
        } finally {
            Binder.restoreCallingIdentity(identity)
        } ?: throw FileNotFoundException(uri.toString())
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val AUTHORITY = "${BuildConfig.APPLICATION_ID}.artwork"
        private const val PATH_ALBUM_ART = "albumart"
        private val MEDIA_STORE_ALBUM_ART_URI = Uri.parse("content://media/external/audio/albumart")

        val ROOT_URI: Uri = Uri.Builder()
            .scheme(ContentResolver.SCHEME_CONTENT)
            .authority(AUTHORITY)
            .appendPath(PATH_ALBUM_ART)
            .build()

        /**
         * Maps a MediaStore album art uri (`content://media/external/audio/albumart/<id>`)
         * to the matching uri served by this provider.
         */
        fun artworkUriFor(coverArtUri: Uri): Uri {
            val albumId = coverArtUri.lastPathSegment ?: return coverArtUri
            return ROOT_URI.buildUpon().appendPath(albumId).build()
        }
    }
}
