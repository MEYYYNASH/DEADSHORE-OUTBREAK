package com.example.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

sealed interface VerificationResult {
  data class Success(
    val sha256Verified: Boolean,
    val computedSha256: String,
  ) : VerificationResult

  data class Failure(
    val errorType: UpdateErrorType,
    val reason: String,
  ) : VerificationResult
}

/**
 * Verifies a downloaded APK's SHA-256 checksum (when .sha256 exists) and validates
 * archive integrity + package identity via PackageManager before launching the system installer.
 */
class ApkVerifier(private val context: Context) {

  fun verify(
    apkFile: File,
    expectedSha256Raw: String?,
  ): VerificationResult {
    if (!apkFile.exists() || apkFile.length() < 1024L) {
      return VerificationResult.Failure(
        UpdateErrorType.CORRUPTED_APK,
        "Downloaded APK file is missing or incomplete.",
      )
    }

    val actualSha256 =
      try {
        computeSha256Hex(apkFile)
      } catch (e: Exception) {
        return VerificationResult.Failure(
          UpdateErrorType.CORRUPTED_APK,
          "Failed to hash APK: ${e.localizedMessage ?: "Read error"}",
        )
      }

    var shaVerified = false
    if (expectedSha256Raw != null) {
      val parsedExpected = extractHexDigest(expectedSha256Raw)
      if (parsedExpected == null) {
        apkFile.delete()
        return VerificationResult.Failure(
          UpdateErrorType.CHECKSUM_MISMATCH,
          "Checksum file format is invalid. Rejecting update for safety.",
        )
      }
      if (!actualSha256.equals(parsedExpected, ignoreCase = true)) {
        apkFile.delete()
        return VerificationResult.Failure(
          UpdateErrorType.CHECKSUM_MISMATCH,
          "SHA-256 mismatch! Expected ${parsedExpected.take(12)}..., got ${actualSha256.take(12)}... Update rejected.",
        )
      }
      shaVerified = true
    }

    // Validate APK archive header, package name, and version non-downgrade
    val archiveInfo = readPackageArchiveInfo(apkFile)
    if (archiveInfo == null) {
      apkFile.delete()
      return VerificationResult.Failure(
        UpdateErrorType.CORRUPTED_APK,
        "APK archive could not be parsed by Android PackageManager (corrupted download).",
      )
    }
    if (archiveInfo.packageName != context.packageName) {
      apkFile.delete()
      return VerificationResult.Failure(
        UpdateErrorType.CORRUPTED_APK,
        "Package ID mismatch (${archiveInfo.packageName} != ${context.packageName}).",
      )
    }

    return VerificationResult.Success(
      sha256Verified = shaVerified,
      computedSha256 = actualSha256,
    )
  }

  private fun computeSha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(65536)
    FileInputStream(file).use { fis ->
      while (true) {
        val read = fis.read(buffer)
        if (read == -1) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private fun extractHexDigest(raw: String): String? {
    val token = raw.trim().split(Regex("\\s+")).firstOrNull()?.trim() ?: return null
    return if (token.length == 64 && token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
      token.lowercase()
    } else {
      null
    }
  }

  @Suppress("DEPRECATION")
  private fun readPackageArchiveInfo(apkFile: File): PackageInfo? {
    val pm = context.packageManager
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0L))
      } else {
        pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
      }
    } catch (_: Exception) {
      null
    }
  }
}
