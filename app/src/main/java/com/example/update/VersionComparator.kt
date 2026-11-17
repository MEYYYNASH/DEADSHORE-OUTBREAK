package com.example.update

/**
 * Semantic version comparator that strips leading "v"/"V" prefixes and build metadata ("+..."),
 * prevents downgrades, and handles multi-part numeric segments as well as pre-release qualifiers.
 */
object VersionComparator {

  data class ParsedVersion(
    val coreParts: List<Int>,
    val preReleaseParts: List<String>,
  )

  fun normalizeTag(rawVersion: String): String =
    rawVersion.trim().removePrefix("v").removePrefix("V").substringBefore('+').trim()

  fun parse(rawVersion: String): ParsedVersion {
    val clean = normalizeTag(rawVersion)
    if (clean.isEmpty()) return ParsedVersion(listOf(0), emptyList())

    val dashIdx = clean.indexOf('-')
    val coreStr = if (dashIdx >= 0) clean.substring(0, dashIdx) else clean
    val preStr = if (dashIdx >= 0) clean.substring(dashIdx + 1) else ""

    val coreParts =
      coreStr.split('.').map { seg ->
        seg.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
      }.ifEmpty { listOf(0) }

    val preParts =
      if (preStr.isBlank()) emptyList()
      else preStr.split('.').filter { it.isNotBlank() }

    return ParsedVersion(coreParts = coreParts, preReleaseParts = preParts)
  }

  /**
   * Returns > 0 if [remoteVersion] is newer than [installedVersion],
   * 0 if equal, < 0 if older (downgrade).
   */
  fun compare(remoteVersion: String, installedVersion: String): Int {
    val r = parse(remoteVersion)
    val c = parse(installedVersion)

    val maxLen = maxOf(r.coreParts.size, c.coreParts.size)
    for (i in 0 until maxLen) {
      val rv = r.coreParts.getOrElse(i) { 0 }
      val cv = c.coreParts.getOrElse(i) { 0 }
      if (rv != cv) return rv.compareTo(cv)
    }

    // Core parts match: stable release > pre-release (SemVer 2.0.0)
    if (r.preReleaseParts.isEmpty() && c.preReleaseParts.isEmpty()) return 0
    if (r.preReleaseParts.isEmpty()) return 1
    if (c.preReleaseParts.isEmpty()) return -1

    val preLen = maxOf(r.preReleaseParts.size, c.preReleaseParts.size)
    for (i in 0 until preLen) {
      val rp = r.preReleaseParts.getOrNull(i) ?: return -1
      val cp = c.preReleaseParts.getOrNull(i) ?: return 1
      val rn = rp.toIntOrNull()
      val cn = cp.toIntOrNull()
      val cmp =
        when {
          rn != null && cn != null -> rn.compareTo(cn)
          rn != null -> -1 // numeric identifiers have lower precedence than non-numeric
          cn != null -> 1
          else -> rp.compareTo(cp, ignoreCase = true)
        }
      if (cmp != 0) return cmp
    }
    return 0
  }

  /** Returns true strictly when remoteVersion is newer than installedVersion (never downgrades). */
  fun isNewerVersion(remoteVersion: String, installedVersion: String): Boolean =
    compare(remoteVersion, installedVersion) > 0
}
