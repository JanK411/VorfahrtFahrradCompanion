package nl.jjt.vorfahrtfahrradcompanion.settings

import nl.jjt.vorfahrtfahrradcompanion.net.isLocalNetworkHost

/**
 * Normalizes a user-entered base URL — a full URL (origin plus optional path prefix), not a hostname.
 *
 * Input without a scheme defaults to `http://` for local-network servers (which rarely have a
 * certificate) and to `https://` for everything else.
 *
 * Returns `null` when the input cannot be a valid base URL. Whether the result may actually be
 * used is a separate question — see `isAllowedUrl`.
 */
fun normalizeBaseUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val explicitScheme = if ("://" in trimmed) trimmed.substringBefore("://").lowercase() else null
    if (explicitScheme != null && explicitScheme != "http" && explicitScheme != "https") return null

    val rest = trimmed.substringAfter("://").trimEnd('/')
    val host = hostOf(rest.substringBefore('/')) ?: return null

    return "${explicitScheme ?: if (isLocalNetworkHost(host)) "http" else "https"}://$rest"
}

/** The bare host of an `[user:password@]host[:port]` authority, or `null` if there is none. */
private fun hostOf(authority: String): String? {
    val hostAndPort = authority.substringAfterLast('@')
    val host =
        if (hostAndPort.startsWith('[')) hostAndPort.substringAfter('[').substringBefore(']')
        else hostAndPort.substringBefore(':')
    return host.ifEmpty { null }
}
