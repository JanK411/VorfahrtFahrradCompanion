package nl.jjt.vorfahrtfahrradcompanion.service.http

import nl.jjt.vorfahrtfahrradcompanion.domain.settings.Settings
import nl.jjt.vorfahrtfahrradcompanion.service.http.isLocalNetworkHost

/**
 * The same settings with a base URL that can be requested, or an exception naming what is missing.
 *
 * Settings nobody has filled in yet are the ordinary state of a fresh install, and they have to fail
 * as the configuration problem they are: Ktor reads a blank URL as no URL at all and leaves the
 * request pointing at `http://localhost`, which then fails as a connection error and blames the
 * network for something the rider can only fix under Settings.
 *
 * Whatever was saved has already been normalized ([normalizeBaseUrl] is idempotent), so the parse
 * here is what catches the URL that was never entered rather than a second opinion on a stored one.
 */
fun Settings.requireConfigured(): Settings {
    val url = normalizeBaseUrl(baseUrl) ?: error("No server configured — fill it in under Settings")
    if (username.isBlank()) error("No username configured — fill it in under Settings")
    return copy(baseUrl = url)
}

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
