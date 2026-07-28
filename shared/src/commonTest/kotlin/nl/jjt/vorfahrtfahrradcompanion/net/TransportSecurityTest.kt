package nl.jjt.vorfahrtfahrradcompanion.net

import kotlin.test.Test
import kotlin.test.assertEquals

class TransportSecurityTest {

    @Test
    fun classifiesHosts() {
        val cases = listOf(
            "192.168.178.42" to true,
            "10.0.0.1" to true,
            "172.16.0.1" to true,
            "172.31.255.255" to true,
            "172.15.0.1" to false,
            "172.32.0.1" to false,
            "127.0.0.1" to true,
            "169.254.1.1" to true,
            "8.8.8.8" to false,
            "192.168.178.999" to false,   // not an IP, and not a resolvable local name either
            "::1" to true,
            "[fd12::1]" to true,
            "fe80::1%wlan0" to true,
            "2001:db8::1" to false,
            "localhost" to true,
            "raspberrypi" to true,
            "nas.local" to true,
            "server.home.arpa" to true,
            "vorfahrt.example.com" to false,
            "" to false,
        )
        cases.forEach { (host, expected) ->
            assertEquals(expected, isLocalNetworkHost(host), "host=$host")
        }
    }

    @Test
    fun allowsHttpsEverywhereAndHttpOnlyLocally() {
        val cases = listOf(
            "https://vorfahrt.example.com" to true,
            "https://192.168.178.42:8080" to true,
            "http://192.168.178.42:8080" to true,
            "http://localhost:8080/api" to true,
            "http://vorfahrt.example.com" to false,
            "http://8.8.8.8" to false,
        )
        cases.forEach { (url, expected) ->
            assertEquals(expected, isAllowedUrl(url), "url=$url")
        }
    }
}
