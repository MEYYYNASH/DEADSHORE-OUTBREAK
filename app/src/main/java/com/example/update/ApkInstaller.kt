package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

sealed interface InstallLaunchResult {
  data object Launched : InstallLaunchResult
  data class NeedsUnknownSourcesPermission(val settingsIntent: Intent) : InstallLaunchResult
  data class Failed(val reason: String) : InstallLaunchResult
}

/**
 * Launches Android's system package installer using FileProvider content:// URIs
 * and handles Android 8.0+ "Allow from this source" permission checks.
 */
class ApkInstaller(private val context: Context) {

  fun canRequestPackageInstalls(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.packageManager.canRequestPackageInstalls()
    } else {
      true
    }
  }

  fun buildUnknownSourcesSettingsIntent(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
      )
    } else {
      Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
  }

  fun installApk(apkFile: File): InstallLaunchResult {
    if (!apkFile.exists()) {
      return InstallLaunchResult.Failed("Verified APK file no longer exists on disk.")
    }
    if (!canRequestPackageInstalls()) {
      return InstallLaunchResult.NeedsUnknownSourcesPermission(buildUnknownSourcesSettingsIntent())
    }

    return try {
      val contentUri: Uri =
        FileProvider.getUriForFile(
          context,
          "${context.packageName}.fileprovider",
          apkFile,
        )
      val intent =
        Intent(Intent.ACTION_VIEW).apply {
          setDataAndType(contentUri, "application/vnd.android.package-archive")
          addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
      context.startActivity(intent)
      InstallLaunchResult.Launched
    } catch (e: Exception) {
      InstallLaunchResult.Failed("Could not open Android Package Installer: ${e.localizedMessage ?: "Error"}")
    }
  }
}
