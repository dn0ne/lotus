package com.dn0ne.player.app.data.remote.metadata

import android.content.Context
import android.util.Log
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import com.dn0ne.player.app.domain.metadata.MetadataSearchResult
import com.dn0ne.player.app.domain.result.DataError
import com.dn0ne.player.app.domain.result.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.headers
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.SocketException
import java.nio.channels.UnresolvedAddressException
import com.dn0ne.player.R
import com.dn0ne.player.core.util.getAppVersionName

class MusicBrainzMetadataProvider(
    context: Context,
    private val client: HttpClient
) : MetadataProvider {
    private val logTag = "MBMetadataProvider"
    private val musicBrainzEndpoint = "https://musicbrainz.org/ws/2"
    private val coverArtArchiveEndpoint = "https://coverartarchive.org"
    private val userAgent =
        "${context.resources.getString(R.string.app_name)}/${context.getAppVersionName()} ( dev.dn0ne@gmail.com )"

    override suspend fun searchMetadata(
        query: String,
        trackDuration: Long
    ): Result<List<MetadataSearchResult>, DataError> {
        delay(1100)
        val query = query + if (trackDuration > 0) {
            " AND dur:[${trackDuration - 5000} TO ${trackDuration + 5000}]"
        } else ""

        val response = try {
            client.get(musicBrainzEndpoint) {
                url {
                    appendPathSegments("recording")
                    parameters.append("fmt", "json")
                    parameters.append("query", query)
                }
                headers {
                    append(HttpHeaders.Accept, ContentType.Application.Json.toString())
                    append(HttpHeaders.UserAgent, userAgent)
                }
            }
        } catch (e: UnresolvedAddressException) {
            Log.i(logTag, e.message.toString())
            return Result.Error(DataError.Network.NoInternet)
        } catch (_: HttpRequestTimeoutException) {
            return Result.Error(DataError.Network.RequestTimeout)
        } catch (_: SocketException) {
            return Result.Error(DataError.Network.Unknown)
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                try {
                    val searchResult: SearchResultDto = response.body()
                    return Result.Success(
                        data = searchResult.toMetadataSearchResultList()
                    )
                } catch (e: JsonConvertException) {
                    Log.d(logTag, e.message, e)
                    return Result.Error(DataError.Network.ParseError)
                }
            }

            HttpStatusCode.BadRequest -> {
                return Result.Error(DataError.Network.BadRequest)
            }

            HttpStatusCode.Unauthorized -> {
                return Result.Error(DataError.Network.Unauthorized)
            }

            HttpStatusCode.Forbidden -> {
                return Result.Error(DataError.Network.Forbidden)
            }

            HttpStatusCode.NotFound -> {
                return Result.Error(DataError.Network.NotFound)
            }

            HttpStatusCode.RequestTimeout -> {
                return Result.Error(DataError.Network.RequestTimeout)
            }

            HttpStatusCode.InternalServerError -> {
                return Result.Error(DataError.Network.InternalServerError)
            }

            HttpStatusCode.ServiceUnavailable -> {
                return Result.Error(DataError.Network.ServiceUnavailable)
            }

            else -> {
                return Result.Error(DataError.Network.Unknown)
            }
        }
    }

    override suspend fun getCoverArtBytes(searchResult: MetadataSearchResult): Result<ByteArray, DataError> {
        delay(1100)
        val response = try {
            client.get(coverArtArchiveEndpoint) {
                url {
                    appendPathSegments(
                        "release",
                        searchResult.albumId,
                        "front"
                    )
                }
                headers {
                    append(HttpHeaders.UserAgent, userAgent)
                }
            }
        } catch (e: UnresolvedAddressException) {
            Log.d(logTag, e.message.toString())
            return Result.Error(DataError.Network.NoInternet)
        } catch (_: HttpRequestTimeoutException) {
            return Result.Error(DataError.Network.RequestTimeout)
        }

        when (response.status) {
            HttpStatusCode.OK -> {
                try {
                    val bytes = response.body<ByteArray>()
                    return Result.Success(bytes)
                } catch (e: Exception) {
                    Log.d(logTag, e.message, e)
                    return Result.Error(DataError.Network.ParseError)
                }
            }

            HttpStatusCode.TemporaryRedirect -> {
                return Result.Error(DataError.Network.Unknown)
                Log.d(logTag, "Redirected: ${response.bodyAsText()}")
            }

            HttpStatusCode.BadRequest -> {
                return Result.Error(DataError.Network.BadRequest)
            }

            HttpStatusCode.NotFound -> {
                return Result.Error(DataError.Network.NotFound)
            }

            HttpStatusCode.ServiceUnavailable -> {
                return Result.Error(DataError.Network.ServiceUnavailable)
            }

            else -> return Result.Error(DataError.Network.Unknown)
        }
    }
}

@Serializable
private data class SearchResultDto(
    val recordings: List<Recording>
)

private fun SearchResultDto.toMetadataSearchResultList(): List<MetadataSearchResult> {
    var results = mutableListOf<MetadataSearchResult>()
    recordings.fastForEach { recording ->
        val artist = recording.artistCredit.map {
            it.name + (it.joinphrase ?: "")
        }.joinToString(separator = "")
        val genres = recording.tags?.map { it.name }

        recording.releases?.fastFilter { it.media != null }?.map { release ->
            val albumArtist = release.artistCredit?.map {
                it.name + (it.joinphrase ?: "")
            }?.joinToString(separator = "") ?: artist
            val trackNumber = release.media!!.first().track.first().number
            MetadataSearchResult(
                id = recording.id,
                title = recording.title,
                artist = artist,
                albumId = release.id,
                album = release.title,
                albumArtist = albumArtist,
                trackNumber = trackNumber,
                year = recording.firstReleaseDate,
                genres = genres,
                description = recording.disambiguation,
                albumDescription = release.disambiguation
            )
        }?.let { searchResults ->
            results += searchResults
        }
    }

    return results
}

@Serializable
private data class Recording(
    val id: String,
    val title: String,
    @SerialName("artist-credit")
    val artistCredit: List<Artist>,
    val disambiguation: String? = null,
    @SerialName("first-release-date")
    val firstReleaseDate: String? = null,
    val releases: List<Release>? = null,
    val tags: List<Tag>? = null
)

@Serializable
private data class Artist(
    val name: String,
    val joinphrase: String? = null
)

@Serializable
private data class Release(
    val id: String,
    val title: String,
    @SerialName("artist-credit")
    val artistCredit: List<Artist>? = null,
    val media: List<Media>? = null,
    val disambiguation: String? = null
)

@Serializable
private data class Media(
    val track: List<MediaTrack>
)

@Serializable
private data class MediaTrack(
    val number: String? = null
)

@Serializable
private data class Tag(
    val name: String
)