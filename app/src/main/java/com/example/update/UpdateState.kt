package com.example.update

import java.io.File

enum class UpdateErrorType {
  NO_INTERNET,
  TIMEOUT,
  GITHUB_404,
  RATE_LIMIT,
  INVALID_RESPONSE,
  NO_APK_FOUND,
  DOWNLOAD_FAILED,
  INSUFFICIENT_STORAGE,
  CHECKSUM_MISMATCH,
  CORRUPTED_APK,
  INSTALL_PERMISSION_DENIED,
  REPO_NOT_CONFIGURED,
  UNKNOWN,
}

sealed interface UpdateState {
  data object Idle : UpdateState

  data object Checking : UpdateState

  data class UpToDate(
    val currentVersion: String,
    val latestTag: String?,
    val isOlderOnRemote: Boolean = false,
    val lastCheckedMs: Long,
  ) : UpdateState

  data class UpdateAvailable(
    val release: GitHubRelease,
    val apkAsset: GitHubAsset,
    val shaAsset: GitHubAsset?,
    val currentVersion: String,
  ) : UpdateState

  data class Downloading(
    val release: GitHubRelease,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progress: Float,
  ) : UpdateState

  data class Verifying(
    val release: GitHubRelease,
    val apkFile: File,
  ) : UpdateState

  data class ReadyToInstall(
    val release: GitHubRelease,
    val apkFile: File,
    val sha256Verified: Boolean,
    val computedSha256: String,
  ) : UpdateState

  data class PermissionRequired(
    val release: GitHubRelease,
    val apkFile: File,
    val sha256Verified: Boolean,
    val computedSha256: String,
  ) : UpdateState

  data class Error(
    val errorType: UpdateErrorType,
    val title: String,
    val message: String,
    val canRetry: Boolean = true,
  ) : UpdateState
}
