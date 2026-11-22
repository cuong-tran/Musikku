package com.maxrave.simpmusic.utils.update

import com.maxrave.logger.Logger
import com.maxrave.simpmusic.expect.updateAssetSuffix
import com.maxrave.simpmusic.utils.VersionManager
import com.maxrave.simpmusic.utils.compareVersions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** A Musikku release this build can update to. [asset] is the file for this build, or null when none fits. */
data class AppRelease(
    val tagName: String,
    val releaseTime: String?,
    val body: String,
    val asset: ReleaseAsset?,
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
)

internal const val MUSIKKU_RELEASES_PAGE = "https://github.com/cuong-tran/Musikku/releases"
internal const val MUSIKKU_RELEASE_DOWNLOAD_PREFIX = "https://github.com/cuong-tran/Musikku/releases/download/"
// Only one page is read, so ask for GitHub's maximum.
private const val MUSIKKU_RELEASES_API = "https://api.github.com/repos/cuong-tran/Musikku/releases?per_page=100"
private const val TAG = "Update"

/** Musikku's own tags: "2.6.0" or "2.6.0.1". Upstream's "v2.6.0" and anything with a suffix are not ours. */
private val RELEASE_TAG = Regex("""^\d+\.\d+\.\d+(\.\d+)?$""")

private const val ASSET_PREFIX = "musikku-"

private val releasesJson = Json { ignoreUnknownKeys = true }

@Serializable
internal data class GithubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    val body: String? = null,
    val assets: List<GithubAsset> = emptyList(),
)

@Serializable
internal data class GithubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0,
)

internal fun parseGithubReleases(json: String): List<GithubRelease> = releasesJson.decodeFromString(json)

/**
 * The newest published Musikku release, when it is newer than [current]. Drafts, prereleases and tags
 * that are not plain dotted numbers are skipped; versions compare number by number, so the order
 * GitHub lists them in does not matter. [assetSuffix] picks this build's file from the release.
 *
 * The file is named after the versionName it was built with, which can trail the tag (tag 2.6.0.1
 * built from 2.6.0). When that version is not newer than [current], installing it would leave this
 * build where it is and the same release would be offered on every launch, so nothing is offered.
 */
internal fun pickLatestRelease(
    releases: List<GithubRelease>,
    current: String,
    assetSuffix: String?,
): AppRelease? {
    val latest =
        releases
            .filter { !it.draft && !it.prerelease && RELEASE_TAG.matches(it.tagName) }
            .maxWithOrNull { a, b -> compareVersions(a.tagName, b.tagName) }
            ?: return null
    if (compareVersions(current, latest.tagName) >= 0) return null
    val asset =
        assetSuffix?.let { suffix ->
            latest.assets.firstOrNull { it.name.endsWith(suffix) && it.browserDownloadUrl.isNotBlank() }
        }
    if (asset != null && assetSuffix != null) {
        val assetVersion = asset.name.removePrefix(ASSET_PREFIX).removeSuffix(assetSuffix)
        if (asset.name.startsWith(ASSET_PREFIX) &&
            RELEASE_TAG.matches(assetVersion) &&
            compareVersions(current, assetVersion) >= 0
        ) {
            return null
        }
    }
    return AppRelease(
        tagName = latest.tagName,
        releaseTime = latest.publishedAt,
        body = latest.body.orEmpty(),
        asset = asset?.let { ReleaseAsset(name = it.name, downloadUrl = it.browserDownloadUrl, size = it.size) },
    )
}

/**
 * Asks GitHub for Musikku's releases. Success with null means this build is up to date. [assetSuffix]
 * runs on the IO thread: on Android it opens and walks the installed APK.
 */
suspend fun fetchLatestMusikkuRelease(
    assetSuffix: () -> String? = { updateAssetSuffix() },
    current: String = VersionManager.getVersionName(),
): Result<AppRelease?> =
    withContext(Dispatchers.IO) {
        runCatching {
            val suffix = assetSuffix()
            val connection = URL(MUSIKKU_RELEASES_API).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", UPDATE_USER_AGENT)
                val code = connection.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("GitHub answered HTTP $code")
                }
                val text = connection.inputStream.bufferedReader().use { it.readText() }
                val releases = parseGithubReleases(text)
                pickLatestRelease(releases, current, suffix).also {
                    Logger.i(
                        TAG,
                        "Musikku releases: ${releases.size} listed, current $current, " +
                            "latest newer ${it?.tagName ?: "none"}, asset ${it?.asset?.name ?: "none"} (suffix $suffix)",
                    )
                }
            } finally {
                connection.disconnect()
            }
        }
    }

internal const val UPDATE_USER_AGENT = "Musikku"

/** Raised once this process has told the user about a newer upstream SimpMusic release: one notification per app start. */
internal val upstreamUpdateNotified = AtomicBoolean(false)
