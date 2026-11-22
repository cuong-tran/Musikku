package com.maxrave.simpmusic.utils

import com.maxrave.simpmusic.utils.update.GithubAsset
import com.maxrave.simpmusic.utils.update.GithubRelease
import com.maxrave.simpmusic.utils.update.parseGithubReleases
import com.maxrave.simpmusic.utils.update.pickLatestRelease
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MusikkuReleasesTest {
    private fun release(
        tag: String,
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<String> = emptyList(),
    ) = GithubRelease(
        tagName = tag,
        draft = draft,
        prerelease = prerelease,
        publishedAt = "2026-11-20T10:00:00Z",
        body = "notes for $tag",
        assets =
            assets.map {
                GithubAsset(it, "https://github.com/cuong-tran/Musikku/releases/download/$tag/$it", 1234)
            },
    )

    @Test
    fun `picks the highest version, not the first listed`() {
        val picked = pickLatestRelease(listOf(release("2.5.0"), release("2.7.0"), release("2.6.0")), "2.4.0", null)
        assertEquals("2.7.0", picked?.tagName)
    }

    @Test
    fun `compares numbers, not text`() {
        assertEquals("2.10.0", pickLatestRelease(listOf(release("2.9.0"), release("2.10.0")), "2.4.0", null)?.tagName)
    }

    @Test
    fun `a four-part tag is newer than its three-part base`() {
        assertEquals("2.6.0.1", pickLatestRelease(listOf(release("2.6.0"), release("2.6.0.1")), "2.6.0", null)?.tagName)
    }

    @Test
    fun `ignores drafts, prereleases, upstream tags and suffixes`() {
        val releases =
            listOf(
                release("2.5.0"),
                release("3.0.0", draft = true),
                release("2.9.0", prerelease = true),
                release("v2.9.0"),
                release("2.6.0-beta"),
            )
        assertEquals("2.5.0", pickLatestRelease(releases, "2.4.0", null)?.tagName)
    }

    @Test
    fun `nothing when this build is the latest or ahead of it`() {
        assertNull(pickLatestRelease(listOf(release("2.5.0")), "2.5.0", null))
        assertNull(pickLatestRelease(listOf(release("2.5.0")), "2.6.0", null))
        assertNull(pickLatestRelease(emptyList(), "2.5.0", null))
    }

    @Test
    fun `matches this build's asset by suffix`() {
        val picked =
            pickLatestRelease(
                listOf(
                    release(
                        "2.6.0",
                        assets =
                            listOf(
                                "musikku-2.6.0-full-universal.apk",
                                "musikku-2.6.0-full-arm64-v8a.apk",
                                "musikku-2.6.0-full-vivo-arm64-v8a.apk",
                                "musikku-2.6.0-foss-arm64-v8a.apk",
                            ),
                    ),
                ),
                "2.5.0",
                "-full-arm64-v8a.apk",
            )
        assertEquals("musikku-2.6.0-full-arm64-v8a.apk", picked?.asset?.name)
        assertEquals(1234L, picked?.asset?.size)
    }

    @Test
    fun `no asset when none fits or the build cannot update itself`() {
        val releases = listOf(release("2.6.0", assets = listOf("musikku-2.6.0-linux-x86_64.AppImage")))
        assertNull(pickLatestRelease(releases, "2.5.0", "-mac-aarch64.zip")?.asset)
        assertNull(pickLatestRelease(releases, "2.5.0", null)?.asset)
        assertEquals("2.6.0", pickLatestRelease(releases, "2.5.0", null)?.tagName)
    }

    @Test
    fun `nothing when the asset carries this build's version under a newer tag`() {
        val releases = listOf(release("2.6.0.1", assets = listOf("musikku-2.6.0-full-arm64-v8a.apk")))
        assertNull(pickLatestRelease(releases, "2.6.0", "-full-arm64-v8a.apk"))
    }

    @Test
    fun `offered when the asset carries the newer version`() {
        val releases = listOf(release("2.6.0.1", assets = listOf("musikku-2.6.0.1-full-arm64-v8a.apk")))
        val picked = pickLatestRelease(releases, "2.6.0", "-full-arm64-v8a.apk")
        assertEquals("2.6.0.1", picked?.tagName)
        assertEquals("musikku-2.6.0.1-full-arm64-v8a.apk", picked?.asset?.name)
    }

    @Test
    fun `parses GitHub's release list`() {
        val json =
            """
            [
              {
                "url": "https://api.github.com/repos/cuong-tran/Musikku/releases/1",
                "tag_name": "2.6.0",
                "name": "2.6.0",
                "draft": false,
                "prerelease": false,
                "published_at": "2026-11-20T10:00:00Z",
                "author": { "login": "someone" },
                "body": "## Musikku\n- A fix",
                "assets": [
                  {
                    "name": "musikku-2.6.0-full-arm64-v8a.apk",
                    "size": 45678901,
                    "content_type": "application/vnd.android.package-archive",
                    "browser_download_url": "https://github.com/cuong-tran/Musikku/releases/download/2.6.0/musikku-2.6.0-full-arm64-v8a.apk"
                  }
                ]
              },
              { "tag_name": "2.5.0", "draft": true, "body": null, "assets": [] }
            ]
            """.trimIndent()
        val releases = parseGithubReleases(json)
        assertEquals(2, releases.size)
        val picked = pickLatestRelease(releases, "2.5.0", "-full-arm64-v8a.apk")
        assertEquals("2.6.0", picked?.tagName)
        assertEquals("## Musikku\n- A fix", picked?.body)
        assertEquals(45678901L, picked?.asset?.size)
        assertEquals("2026-11-20T10:00:00Z", picked?.releaseTime)
    }
}
