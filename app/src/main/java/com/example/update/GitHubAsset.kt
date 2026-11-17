package com.example.update

/**
 * Represents a single downloadable binary/checksum asset attached to a GitHub Release.
 */
data class GitHubAsset(
  val name: String,
  val browserDownloadUrl: String,
  val sizeBytes: Long,
  val contentType: String,
)
