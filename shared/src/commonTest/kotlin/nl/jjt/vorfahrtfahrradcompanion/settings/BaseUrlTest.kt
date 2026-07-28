package nl.jjt.vorfahrtfahrradcompanion.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BaseUrlTest {

    @Test
    fun normalizes() {
        val cases = listOf(
            "192.168.178.42:8080" to "http://192.168.178.42:8080",
            "vorfahrt.example.com" to "https://vorfahrt.example.com",
            "https://vorfahrt.example.com/api/" to "https://vorfahrt.example.com/api",
            "http://vorfahrt.example.com" to "http://vorfahrt.example.com",
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

    @Test
    fun rejectsPlainHttpOutsideTheLocalNetwork() {
        assertNull(SettingsUiState(baseUrl = "http://vorfahrt.example.com").normalizedBaseUrl)
        assertTrue(SettingsUiState(baseUrl = "http://vorfahrt.example.com").isBaseUrlInsecure)
        assertEquals(
            "http://192.168.178.42:8080",
            SettingsUiState(baseUrl = "192.168.178.42:8080").normalizedBaseUrl,
        )
    }
}
