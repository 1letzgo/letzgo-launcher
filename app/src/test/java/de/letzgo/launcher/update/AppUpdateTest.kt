package de.letzgo.launcher.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppUpdateTest {

    private fun release(
        tag: String,
        apk: Boolean = true,
        draft: Boolean = false,
        prerelease: Boolean = false,
    ) =
        """
        {"tag_name": "$tag", "name": "Name $tag", "body": " Notizen ", "draft": $draft,
         "prerelease": $prerelease, "html_url": "https://example.com/$tag",
         "assets": [${if (apk) """{"name": "a.apk", "browser_download_url": "https://example.com/$tag.apk", "digest": "sha256:abc"}""" else ""}]}
        """

    @Test
    fun picksHighestNewerVersion() {
        val json =
            "[${release("launcher-android-12")}, ${release("launcher-android-15")}, ${release("launcher-android-9")}]"
        val newest = AppUpdate.newest(json, installedCode = 10)!!
        assertEquals(15, newest.versionCode)
        assertEquals("https://example.com/launcher-android-15.apk", newest.apkUrl)
        assertEquals("abc", newest.sha256)
        assertEquals("Notizen", newest.notes)
    }

    @Test
    fun ignoresOtherTagsDraftsPrereleasesAndMissingApk() {
        val json =
            "[${release("abstand-android-99")}, ${release("launcher-android-20", draft = true)}, " +
                "${release("launcher-android-21", prerelease = true)}, ${release("launcher-android-22", apk = false)}]"
        assertNull(AppUpdate.newest(json, installedCode = 10))
    }

    @Test
    fun noUpdateWhenInstalledIsCurrent() {
        assertNull(AppUpdate.newest("[${release("launcher-android-10")}]", installedCode = 10))
    }
}
