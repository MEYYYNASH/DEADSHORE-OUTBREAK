package com.example.update

import android.content.Context
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request

class DownloadException(
  val errorType: UpdateErrorType,
  override val message: String,
  cause: Throwable? = null,
) : IOException(message, cause)

data class DownloadedUpdateFiles(
  val apkFile: File,
  val expectedSha256: String?,
)

/**
 * Streams APK & optional SHA-256 checksum assets directly from GitHub Release URLs
 * into app-private cache/updates/ with storage checks and live byte-level progress.
 */
class ApkDownloader(
  private val context: Context,
  private val client: OkHttpClient = defaultDownloadClient(),
) {
  private val updatesDir: File
    get() = File(context.cacheDir, UpdateConfig.APK_CACHE_DIR).apply { mkdirs() }

  suspend fun downloadReleaseAssets(
    apkAsset: GitHubAsset,
    shaAsset: GitHubAsset?,
    onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
  ): DownloadedUpdateFiles {
    val dir = updatesDir
    val requiredBytes = apkAsset.sizeBytes.coerceAtLeast(1L) + UpdateConfig.STORAGE_BUFFER_BYTES
    val availableBytes = StatFs(dir.absolutePath).availableBytes
    if (availableBytes < requiredBytes) {
      throw DownloadException(
        UpdateErrorType.INSUFFICIENT_STORAGE,
        "Not enough free storage. Need ${formatMb(requiredBytes)}, available ${formatMb(availableBytes)}.",
      )
    }

    // Clear previous APK artifacts before downloading
    val finalApk = File(dir, UpdateConfig.APK_FILE_NAME)
    val tempApk = File(dir, "${UpdateConfig.APK_FILE_NAME}.tmp")
    if (tempApk.exists()) tempApk.delete()
    if (finalApk.exists()) finalApk.delete()

    downloadToFile(
      url = apkAsset.browserDownloadUrl,
      targetTempFile = tempApk,
      finalFile = finalApk,
      expectedSize = apkAsset.sizeBytes,
      onProgress = onProgress,
    )

    val expectedSha =
      if (shaAsset != null) {
        fetchSha256Text(shaAsset.browserDownloadUrl)
      } else {
        null
      }

    return DownloadedUpdateFiles(apkFile = finalApk, expectedSha256 = expectedSha)
  }

  private suspend fun downloadToFile(
    url: String,
    targetTempFile: File,
    finalFile: File,
    expectedSize: Long,
    onProgress: (Long, Long) -> Unit,
  ) {
    val request =
      Request.Builder()
        .url(url)
        .header("Accept", "application/octet-stream")
        .header("User-Agent", UpdateConfig.USER_AGENT)
        .build()

    val response =
      try {
        client.newCall(request).execute()
      } catch (e: SocketTimeoutException) {
        throw DownloadException(UpdateErrorType.TIMEOUT, "Download timed out.", e)
      } catch (e: UnknownHostException) {
        throw DownloadException(UpdateErrorType.NO_INTERNET, "Lost internet connection during download.", e)
      } catch (e: IOException) {
        throw DownloadException(UpdateErrorType.DOWNLOAD_FAILED, "Network failure while downloading APK.", e)
      }

    response.use { resp ->
      if (!resp.isSuccessful) {
        throw DownloadException(
          UpdateErrorType.DOWNLOAD_FAILED,
          "APK download failed with HTTP ${resp.code}.",
        )
      }
      val body =
        resp.body
          ?: throw DownloadException(UpdateErrorType.DOWNLOAD_FAILED, "Empty response body for APK asset.")
      val totalBytes =
        when {
          body.contentLength() > 0L -> body.contentLength()
          expectedSize > 0L -> expectedSize
          else -> -1L
        }

      try {
        body.byteStream().use { input ->
          FileOutputStream(targetTempFile).use { output ->
            val buffer = ByteArray(8192)
            var downloaded = 0L
            var lastReportMs = 0L
            onProgress(0L, totalBytes)
            while (true) {
              coroutineContext.ensureActive()
              val read = input.read(buffer)
              if (read == -1) break
              output.write(buffer, 0, read)
              downloaded += read
              val now = System.currentTimeMillis()
              if (now - lastReportMs >= 80L || downloaded == totalBytes) {
                lastReportMs = now
                onProgress(downloaded, totalBytes)
              }
            }
            output.flush()
          }
        }
        if (!targetTempFile.renameTo(finalFile)) {
          targetTempFile.copyTo(finalFile, overwrite = true)
          targetTempFile.delete()
        }
      } catch (e: IOException) {
        targetTempFile.delete()
        throw DownloadException(
          UpdateErrorType.DOWNLOAD_FAILED,
          "Failed writing APK: ${e.localizedMessage ?: "I/O error"}",
          e,
        )
      }
    }
  }

  private fun fetchSha256Text(url: String): String? {
    val request =
      Request.Builder()
        .url(url)
        .header("User-Agent", UpdateConfig.USER_AGENT)
        .build()
    return try {
      client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) {
          throw DownloadException(
            UpdateErrorType.DOWNLOAD_FAILED,
            "Failed to download SHA-256 checksum (HTTP ${resp.code}).",
          )
        }
        resp.body?.string()?.trim()
      }
    } catch (e: DownloadException) {
      throw e
    } catch (e: IOException) {
      throw DownloadException(
        UpdateErrorType.DOWNLOAD_FAILED,
        "Unable to download checksum file: ${e.localizedMessage ?: "Network error"}",
        e,
      )
    }
  }

  companion object {
    fun formatMb(bytes: Long): String {
      if (bytes <= 0L) return "0 MB"
      val mb = bytes / (1024.0 * 1024.0)
      return if (mb >= 10.0) "${mb.toInt()} MB" else "${"%.1f".format(mb)} MB"
    }

    fun defaultDownloadClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
  }
}
