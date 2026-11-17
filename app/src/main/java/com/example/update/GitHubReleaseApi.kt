package com.example.update

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException

class GitHubApiException(
  val errorType: UpdateErrorType,
  override val message: String,
  cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Fetches releases from GitHub REST API v3, filtering out drafts and (by default) prereleases.
 */
class GitHubReleaseApi(
  private val client: OkHttpClient = defaultClient(),
) {
  fun fetchLatestEligibleRelease(
    owner: String,
    repo: String,
    allowPrereleases: Boolean = UpdateConfig.ALLOW_PRERELEASES,
  ): GitHubRelease? {
    if (UpdateConfig.isPlaceholderRepo(owner, repo)) {
      throw GitHubApiException(
        UpdateErrorType.REPO_NOT_CONFIGURED,
        "Configure your GitHub Owner and Repository name in Update Settings or UpdateConfig.kt.",
      )
    }

    val url = "${UpdateConfig.API_BASE_URL}/repos/${owner.trim()}/${repo.trim()}/releases?per_page=10"
    val request =
      Request.Builder()
        .url(url)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", UpdateConfig.USER_AGENT)
        .build()

    val response =
      try {
        client.newCall(request).execute()
      } catch (e: SocketTimeoutException) {
        throw GitHubApiException(UpdateErrorType.TIMEOUT, "GitHub API request timed out.", e)
      } catch (e: UnknownHostException) {
        throw GitHubApiException(UpdateErrorType.NO_INTERNET, "No internet connection detected.", e)
      } catch (e: IOException) {
        throw GitHubApiException(UpdateErrorType.NO_INTERNET, "Network error: ${e.localizedMessage ?: "Offline"}", e)
      }

    response.use { resp ->
      val bodyStr = resp.body?.string().orEmpty()
      when (resp.code) {
        200 -> {
          val releases =
            try {
              GitHubReleaseParser.parseReleasesArray(bodyStr)
            } catch (e: JSONException) {
              throw GitHubApiException(UpdateErrorType.INVALID_RESPONSE, "Invalid JSON from GitHub Releases API.", e)
            } catch (e: IllegalArgumentException) {
              throw GitHubApiException(UpdateErrorType.INVALID_RESPONSE, e.message ?: "Malformed release payload.", e)
            }
          return releases.firstOrNull { !it.draft && (allowPrereleases || !it.prerelease) }
        }
        404 ->
          throw GitHubApiException(
            UpdateErrorType.GITHUB_404,
            "Repository $owner/$repo or releases endpoint not found (HTTP 404).",
          )
        403, 429 -> {
          val remaining = resp.header("X-RateLimit-Remaining")
          val msg =
            if (remaining == "0" || resp.code == 429) {
              "GitHub API rate limit reached. Please wait a few minutes and retry."
            } else {
              "GitHub API access denied (HTTP ${resp.code})."
            }
          throw GitHubApiException(UpdateErrorType.RATE_LIMIT, msg)
        }
        else ->
          throw GitHubApiException(
            UpdateErrorType.INVALID_RESPONSE,
            "Unexpected GitHub API response (HTTP ${resp.code}).",
          )
      }
    }
  }

  companion object {
    fun defaultClient(): OkHttpClient =
      OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
  }
}
