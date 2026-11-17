package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.update.ApkVerifier
import com.example.update.GitHubReleaseParser
import com.example.update.UpdateErrorType
import com.example.update.VerificationResult
import com.example.update.VersionComparator
import java.io.File
import java.security.MessageDigest
import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UpdateSystemUnitTest {

  @Test
  fun `version comparator strips v prefix and detects newer releases`() {
    assertTrue(VersionComparator.isNewerVersion("v1.2.0", "1.1.0"))
    assertTrue(VersionComparator.isNewerVersion("V2.0.0", "v1.9.9"))
    assertTrue(VersionComparator.isNewerVersion("v1.0.1", "1.0"))
    assertEquals(0, VersionComparator.compare("v1.2.0", "1.2.0"))
    assertEquals(0, VersionComparator.compare("v1.2.0+42", "1.2.0"))
  }

  @Test
  fun `version comparator never downgrades and orders prereleases below stable`() {
    assertFalse(VersionComparator.isNewerVersion("v1.0.0", "1.0.0"))
    assertFalse(VersionComparator.isNewerVersion("v0.9.9", "1.0.0"))
    assertFalse(VersionComparator.isNewerVersion("v1.2.0-rc.1", "1.2.0"))
    assertTrue(VersionComparator.isNewerVersion("v1.2.0", "1.2.0-rc.1"))
    assertTrue(VersionComparator.isNewerVersion("v1.2.0-rc.2", "1.2.0-rc.1"))
  }

  @Test
  fun `github release parser parses apk and sha256 assets and ignores invalid drafts`() {
    val payload =
      """
      [
        {
          "tag_name": "v1.3.0-beta.1",
          "name": "Beta 1",
          "body": "Preview build",
          "published_at": "2026-03-01T10:00:00Z",
          "draft": false,
          "prerelease": true,
          "html_url": "https://github.com/org/repo/releases/tag/v1.3.0-beta.1",
          "assets": []
        },
        {
          "tag_name": "v1.2.0",
          "name": "POV Survival v1.2.0",
          "body": "- New features\n- Performance improvements",
          "published_at": "2026-02-28T18:30:00Z",
          "draft": false,
          "prerelease": false,
          "html_url": "https://github.com/org/repo/releases/tag/v1.2.0",
          "assets": [
            {
              "name": "app-release.apk",
              "browser_download_url": "https://github.com/org/repo/releases/download/v1.2.0/app-release.apk",
              "size": 89128960,
              "content_type": "application/vnd.android.package-archive"
            },
            {
              "name": "app-release.apk.sha256",
              "browser_download_url": "https://github.com/org/repo/releases/download/v1.2.0/app-release.apk.sha256",
              "size": 82,
              "content_type": "text/plain"
            }
          ]
        }
      ]
      """.trimIndent()

    val releases = GitHubReleaseParser.parseReleasesArray(payload)
    assertEquals(2, releases.size)

    val eligible = releases.firstOrNull { !it.draft && !it.prerelease }
    assertNotNull(eligible)
    assertEquals("v1.2.0", eligible!!.tagName)

    val apk = eligible.findApkAsset()
    assertNotNull(apk)
    assertEquals("app-release.apk", apk!!.name)
    assertEquals(89128960L, apk.sizeBytes)

    val sha = eligible.findSha256Asset(apk.name)
    assertNotNull(sha)
    assertEquals("app-release.apk.sha256", sha!!.name)
  }

  @Test(expected = JSONException::class)
  fun `github release parser throws on malformed json`() {
    GitHubReleaseParser.parseReleasesArray("{not valid json")
  }

  @Test
  fun `apk verifier rejects checksum mismatch and deletes tampered file`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val verifier = ApkVerifier(context)
    val tempFile = File(context.cacheDir, "tampered-test.apk")
    tempFile.writeBytes(ByteArray(2048) { 0x5A })

    val wrongSha = "0".repeat(64)
    val result = verifier.verify(tempFile, "$wrongSha  app-release.apk")

    assertTrue(result is VerificationResult.Failure)
    assertEquals(UpdateErrorType.CHECKSUM_MISMATCH, (result as VerificationResult.Failure).errorType)
    assertFalse("Tampered APK must be deleted on mismatch", tempFile.exists())
  }

  @Test
  fun `apk verifier rejects invalid apk archive even when sha256 matches`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val verifier = ApkVerifier(context)
    val tempFile = File(context.cacheDir, "corrupt-archive.apk")
    val bytes = ByteArray(2048) { (it % 251).toByte() }
    tempFile.writeBytes(bytes)

    val matchingSha =
      MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    val result = verifier.verify(tempFile, "$matchingSha  app-release.apk")

    assertTrue(result is VerificationResult.Failure)
    assertEquals(UpdateErrorType.CORRUPTED_APK, (result as VerificationResult.Failure).errorType)
    assertFalse("Corrupted APK must be deleted", tempFile.exists())
  }

  @Test
  fun `apk verifier detects missing or tiny files`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val verifier = ApkVerifier(context)
    val missingFile = File(context.cacheDir, "does-not-exist.apk")
    assertNull(if (missingFile.exists()) missingFile else null)

    val result = verifier.verify(missingFile, null)
    assertTrue(result is VerificationResult.Failure)
    assertEquals(UpdateErrorType.CORRUPTED_APK, (result as VerificationResult.Failure).errorType)
  }
}
