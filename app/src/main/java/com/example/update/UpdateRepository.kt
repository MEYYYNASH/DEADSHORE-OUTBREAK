package com.example.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "pov_survival_update_prefs")

data class UpdatePreferencesSnapshot(
  val lastCheckedMs: Long = 0L,
  val dismissedTag: String = "",
  val cachedLatestTag: String = "",
  val repoOwner: String = UpdateConfig.DEFAULT_OWNER,
  val repoName: String = UpdateConfig.DEFAULT_REPO,
)

/**
 * Coordinates GitHub Releases checking, DataStore caching, APK download,
 * SHA-256 verification, and FileProvider installation.
 */
class UpdateRepository(
  private val context: Context,
  private val api: GitHubReleaseApi = GitHubReleaseApi(),
  private val downloader: ApkDownloader = ApkDownloader(context),
  private val verifier: ApkVerifier = ApkVerifier(context),
  val installer: ApkInstaller = ApkInstaller(context),
) {
  private object Keys {
    val LAST_CHECKED_MS = longPreferencesKey("last_checked_ms")
    val DISMISSED_TAG = stringPreferencesKey("dismissed_tag")
    val CACHED_LATEST_TAG = stringPreferencesKey("cached_latest_tag")
    val REPO_OWNER = stringPreferencesKey("repo_owner")
    val REPO_NAME = stringPreferencesKey("repo_name")
  }

  val preferencesFlow: Flow<UpdatePreferencesSnapshot> =
    context.updateDataStore.data.map { prefs ->
      UpdatePreferencesSnapshot(
        lastCheckedMs = prefs[Keys.LAST_CHECKED_MS] ?: 0L,
        dismissedTag = prefs[Keys.DISMISSED_TAG].orEmpty(),
        cachedLatestTag = prefs[Keys.CACHED_LATEST_TAG].orEmpty(),
        repoOwner = prefs[Keys.REPO_OWNER]?.takeIf { it.isNotBlank() } ?: UpdateConfig.DEFAULT_OWNER,
        repoName = prefs[Keys.REPO_NAME]?.takeIf { it.isNotBlank() } ?: UpdateConfig.DEFAULT_REPO,
      )
    }

  suspend fun saveRepoConfig(owner: String, repo: String) {
    context.updateDataStore.edit { prefs ->
      prefs[Keys.REPO_OWNER] = owner.trim().ifBlank { UpdateConfig.DEFAULT_OWNER }
      prefs[Keys.REPO_NAME] = repo.trim().ifBlank { UpdateConfig.DEFAULT_REPO }
      prefs[Keys.DISMISSED_TAG] = ""
    }
  }

  suspend fun dismissTag(tagName: String) {
    context.updateDataStore.edit { prefs ->
      prefs[Keys.DISMISSED_TAG] = tagName
    }
  }

  suspend fun checkForUpdate(force: Boolean): UpdateState =
    withContext(Dispatchers.IO) {
      val snapshot = preferencesFlow.first()
      val now = System.currentTimeMillis()
      val currentVersion = UpdateConfig.currentVersionName

      if (!force && snapshot.lastCheckedMs > 0L && now - snapshot.lastCheckedMs < UpdateConfig.CHECK_COOLDOWN_MS) {
        return@withContext UpdateState.UpToDate(
          currentVersion = currentVersion,
          latestTag = snapshot.cachedLatestTag.ifBlank { null },
          lastCheckedMs = snapshot.lastCheckedMs,
        )
      }

      try {
        val release =
          api.fetchLatestEligibleRelease(
            owner = snapshot.repoOwner,
            repo = snapshot.repoName,
            allowPrereleases = UpdateConfig.ALLOW_PRERELEASES,
          )

        context.updateDataStore.edit { prefs ->
          prefs[Keys.LAST_CHECKED_MS] = now
          if (release != null) prefs[Keys.CACHED_LATEST_TAG] = release.tagName
        }

        if (release == null) {
          return@withContext UpdateState.UpToDate(
            currentVersion = currentVersion,
            latestTag = null,
            lastCheckedMs = now,
          )
        }

        val cmp = VersionComparator.compare(release.tagName, currentVersion)
        if (cmp <= 0) {
          return@withContext UpdateState.UpToDate(
            currentVersion = currentVersion,
            latestTag = release.tagName,
            isOlderOnRemote = cmp < 0,
            lastCheckedMs = now,
          )
        }

        if (!force && snapshot.dismissedTag == release.tagName) {
          return@withContext UpdateState.UpToDate(
            currentVersion = currentVersion,
            latestTag = release.tagName,
            lastCheckedMs = now,
          )
        }

        val apkAsset =
          release.findApkAsset()
            ?: return@withContext UpdateState.Error(
              errorType = UpdateErrorType.NO_APK_FOUND,
              title = "APK Asset Missing",
              message = "Release ${release.tagName} was published without an .apk asset.",
            )

        val shaAsset = release.findSha256Asset(apkAsset.name)

        UpdateState.UpdateAvailable(
          release = release,
          apkAsset = apkAsset,
          shaAsset = shaAsset,
          currentVersion = currentVersion,
        )
      } catch (e: GitHubApiException) {
        UpdateState.Error(
          errorType = e.errorType,
          title = mapErrorTitle(e.errorType),
          message = e.message,
        )
      } catch (e: Exception) {
        UpdateState.Error(
          errorType = UpdateErrorType.UNKNOWN,
          title = "Update Check Failed",
          message = e.localizedMessage ?: "Unexpected error checking for updates.",
        )
      }
    }

  suspend fun downloadAndVerify(
    release: GitHubRelease,
    apkAsset: GitHubAsset,
    shaAsset: GitHubAsset?,
    onState: (UpdateState) -> Unit,
  ): UpdateState =
    withContext(Dispatchers.IO) {
      try {
        onState(UpdateState.Downloading(release, 0L, apkAsset.sizeBytes, 0f))
        val downloaded =
          downloader.downloadReleaseAssets(
            apkAsset = apkAsset,
            shaAsset = shaAsset,
          ) { bytes, total ->
            val progress = if (total > 0L) (bytes.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
            onState(UpdateState.Downloading(release, bytes, total, progress))
          }

        onState(UpdateState.Verifying(release, downloaded.apkFile))
        when (val result = verifier.verify(downloaded.apkFile, downloaded.expectedSha256)) {
          is VerificationResult.Success -> {
            if (installer.canRequestPackageInstalls()) {
              UpdateState.ReadyToInstall(
                release = release,
                apkFile = downloaded.apkFile,
                sha256Verified = result.sha256Verified,
                computedSha256 = result.computedSha256,
              )
            } else {
              UpdateState.PermissionRequired(
                release = release,
                apkFile = downloaded.apkFile,
                sha256Verified = result.sha256Verified,
                computedSha256 = result.computedSha256,
              )
            }
          }
          is VerificationResult.Failure -> {
            UpdateState.Error(
              errorType = result.errorType,
              title = mapErrorTitle(result.errorType),
              message = result.reason,
            )
          }
        }
      } catch (e: DownloadException) {
        UpdateState.Error(
          errorType = e.errorType,
          title = mapErrorTitle(e.errorType),
          message = e.message,
        )
      } catch (e: Exception) {
        UpdateState.Error(
          errorType = UpdateErrorType.DOWNLOAD_FAILED,
          title = "Download Failed",
          message = e.localizedMessage ?: "Unexpected download error.",
        )
      }
    }

  fun launchInstaller(apkFile: File): InstallLaunchResult = installer.installApk(apkFile)

  private fun mapErrorTitle(type: UpdateErrorType): String =
    when (type) {
      UpdateErrorType.NO_INTERNET -> "No Internet Connection"
      UpdateErrorType.TIMEOUT -> "Connection Timed Out"
      UpdateErrorType.GITHUB_404 -> "Release Not Found (404)"
      UpdateErrorType.RATE_LIMIT -> "GitHub Rate Limit Reached"
      UpdateErrorType.INVALID_RESPONSE -> "Invalid Release Response"
      UpdateErrorType.NO_APK_FOUND -> "No APK Asset Found"
      UpdateErrorType.DOWNLOAD_FAILED -> "Download Failed"
      UpdateErrorType.INSUFFICIENT_STORAGE -> "Insufficient Storage"
      UpdateErrorType.CHECKSUM_MISMATCH -> "Security Verification Failed"
      UpdateErrorType.CORRUPTED_APK -> "Corrupted APK File"
      UpdateErrorType.INSTALL_PERMISSION_DENIED -> "Install Permission Required"
      UpdateErrorType.REPO_NOT_CONFIGURED -> "GitHub Repo Not Configured"
      UpdateErrorType.UNKNOWN -> "Update Error"
    }
}
