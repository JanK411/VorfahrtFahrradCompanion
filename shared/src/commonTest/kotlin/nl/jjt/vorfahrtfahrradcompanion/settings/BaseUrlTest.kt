package nl.jjt.vorfahrtfahrradcompanion.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class BaseUrlTest {

    @Test
    fun normalizes() {
        val cases = listOf(
            "192.168.178.42:8080" to "http://192.168.178.42:8080",
            "https://vorfahrt.example.com/api/" to "https://vorfahrt.example.com/api",
            "  http://host  " to "http://host",
            "HTTPS://host" to "https://host",
            "ftp://x" to null,
            "" to null,
            "   " to null,
            "http://" to null,
            "http:///api" to null,
        )
        cases.forEach { (input, expected) ->
            assertEquals(expected, normalizeBaseUrl(input), "input=$input")
        }
    }
}
