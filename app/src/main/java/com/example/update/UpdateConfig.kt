package com.example.update

import com.example.BuildConfig

/**
 * Central configuration for GitHub Releases self-update checks, caching, and APK verification.
 * Update DEFAULT_OWNER and DEFAULT_REPO to match your public GitHub repository, or override
 * them at runtime from the In-App Update screen.
 */
object UpdateConfig {
  const val DEFAULT_OWNER = "MEYYYNASH"
  const val DEFAULT_REPO = "DEADSHORE-OUTBREAK"
  const val API_BASE_URL = "https://api.github.com"
  const val USER_AGENT = "POVSurvival-Android-Updater/1.0"
  const val ALLOW_PRERELEASES = false
  const val CHECK_COOLDOWN_MS = 6 * 60 * 60 * 1000L // 6 hours
  const val APK_CACHE_DIR = "updates"
  const val APK_FILE_NAME = "app-release.apk"
  const val SHA256_ASSET_NAME = "app-release.apk.sha256"
  const val STORAGE_BUFFER_BYTES = 20L * 1024L * 1024L // 20 MB safety buffer

  val currentVersionName: String
    get() = BuildConfig.VERSION_NAME

  val currentVersionCode: Int
    get() = BuildConfig.VERSION_CODE

  fun isPlaceholderRepo(owner: String, repo: String): Boolean =
    owner.isBlank() || repo.isBlank() || owner == "your-github-username" || repo == "your-repo-name"
}
