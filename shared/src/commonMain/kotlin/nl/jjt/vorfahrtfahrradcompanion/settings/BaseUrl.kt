package nl.jjt.vorfahrtfahrradcompanion.settings

/**
 * Normalizes a user-entered base URL — a full URL (origin plus optional path prefix), not a hostname.
 *
 * Returns `null` when the input cannot be a valid base URL.
 */
fun normalizeBaseUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
    val scheme = withScheme.substringBefore("://").lowercase()
    if (scheme != "http" && scheme != "https") return null

    val rest = withScheme.substringAfter("://").trimEnd('/')
    if (rest.substringBefore('/').isEmpty()) return null

    return "$scheme://$rest"
}
