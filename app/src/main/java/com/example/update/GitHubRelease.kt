package com.example.update

import org.json.JSONArray
import org.json.JSONObject

/**
 * Represents a GitHub Release metadata object returned by the GitHub REST Releases API.
 */
data class GitHubRelease(
  val tagName: String,
  val name: String,
  val body: String,
  val publishedAt: String,
  val draft: Boolean,
  val prerelease: Boolean,
  val htmlUrl: String,
  val assets: List<GitHubAsset>,
) {
  fun findApkAsset(): GitHubAsset? =
    assets.firstOrNull { it.name.equals(UpdateConfig.APK_FILE_NAME, ignoreCase = true) }
      ?: assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }

  fun findSha256Asset(apkName: String): GitHubAsset? =
    assets.firstOrNull { it.name.equals("$apkName.sha256", ignoreCase = true) }
      ?: assets.firstOrNull { it.name.equals(UpdateConfig.SHA256_ASSET_NAME, ignoreCase = true) }
      ?: assets.firstOrNull { it.name.endsWith(".sha256", ignoreCase = true) }
}

object GitHubReleaseParser {
  fun parseReleasesArray(jsonText: String): List<GitHubRelease> {
    val arr = JSONArray(jsonText)
    val result = ArrayList<GitHubRelease>(arr.length())
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: continue
      result.add(parseReleaseObject(obj))
    }
    return result
  }

  fun parseReleaseObject(obj: JSONObject): GitHubRelease {
    val tagName = obj.optString("tag_name", "").trim()
    require(tagName.isNotEmpty()) { "Missing tag_name in GitHub Release JSON" }

    val assetsArr = obj.optJSONArray("assets") ?: JSONArray()
    val assets = ArrayList<GitHubAsset>(assetsArr.length())
    for (i in 0 until assetsArr.length()) {
      val a = assetsArr.optJSONObject(i) ?: continue
      val aName = a.optString("name", "")
      val aUrl = a.optString("browser_download_url", "")
      if (aName.isNotEmpty() && aUrl.isNotEmpty()) {
        assets.add(
          GitHubAsset(
            name = aName,
            browserDownloadUrl = aUrl,
            sizeBytes = a.optLong("size", 0L),
            contentType = a.optString("content_type", ""),
          )
        )
      }
    }

    return GitHubRelease(
      tagName = tagName,
      name = obj.optString("name", tagName).ifBlank { tagName },
      body = obj.optString("body", ""),
      publishedAt = obj.optString("published_at", ""),
      draft = obj.optBoolean("draft", false),
      prerelease = obj.optBoolean("prerelease", false),
      htmlUrl = obj.optString("html_url", ""),
      assets = assets,
    )
  }
}
