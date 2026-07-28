package nl.jjt.vorfahrtfahrradcompanion.net

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.Url

/** Thrown when a request would send plain http:// to a host outside the local network. */
class InsecureTransportException(host: String) : IllegalArgumentException(
    "Refusing plain http:// to $host — https:// is required outside your own network."
)

/**
 * True for hosts that can only be reached from the user's own network: loopback, RFC 1918 /
 * unique-local addresses, link-local addresses, and names only a LAN resolves (mDNS `.local`,
 * `home.arpa`, single-label hostnames).
 */
fun isLocalNetworkHost(host: String): Boolean {
    val h = host.trim().removeSurrounding("[", "]").substringBefore('%').lowercase()
    return when {
        h.isEmpty() -> false
        ':' in h -> isLocalIpv6(h)
        else -> ipv4Octets(h)?.let(::isLocalIpv4) ?: isLocalName(h)
    }
}

/** The transport policy: https everywhere, http only towards the local network. */
fun isAllowedUrl(url: String): Boolean = runCatching {
    with(Url(url)) { protocol.name != "http" || isLocalNetworkHost(host) }
}.getOrDefault(false)

/**
 * Enforces [isAllowedUrl] on every request, so settings stored before the policy existed — or any
 * future call site — can't leak credentials over the open internet in the clear.
 */
val InsecureTransportGuard = createClientPlugin("InsecureTransportGuard") {
    onRequest { request, _ ->
        if (request.url.protocol.name == "http" && !isLocalNetworkHost(request.url.host)) {
            throw InsecureTransportException(request.url.host)
        }
    }
}

private fun ipv4Octets(host: String): List<Int>? {
    val parts = host.split('.')
    if (parts.size != 4) return null
    return parts.map { it.toIntOrNull()?.takeIf { n -> n in 0..255 } ?: return null }
}

private fun isLocalIpv4(octets: List<Int>): Boolean {
    val (a, b) = octets
    return a == 10 ||                       // 10.0.0.0/8
            a == 127 ||                     // loopback
            (a == 172 && b in 16..31) ||    // 172.16.0.0/12
            (a == 192 && b == 168) ||       // 192.168.0.0/16
            (a == 169 && b == 254)          // link-local
}

private fun isLocalIpv6(host: String): Boolean =
    host == "::1" || host.startsWith("fc") || host.startsWith("fd") ||  // unique local
            host.take(3) in setOf("fe8", "fe9", "fea", "feb")           // link-local

private fun isLocalName(host: String): Boolean =
    host == "localhost" || host.endsWith(".local") || host.endsWith(".home.arpa") || '.' !in host
