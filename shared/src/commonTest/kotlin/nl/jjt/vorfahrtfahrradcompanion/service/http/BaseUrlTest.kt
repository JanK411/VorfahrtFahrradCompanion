package nl.jjt.vorfahrtfahrradcompanion.service.http

import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsStore
import nl.jjt.vorfahrtfahrradcompanion.domain.settings.Settings
import nl.jjt.vorfahrtfahrradcompanion.ui.settings.ServerConnectionUiState
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

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
        assertNull(ServerConnectionUiState(baseUrl = "http://vorfahrt.example.com").normalizedBaseUrl)
        assertTrue(ServerConnectionUiState(baseUrl = "http://vorfahrt.example.com").isBaseUrlInsecure)
        assertEquals(
            "http://192.168.178.42:8080",
            ServerConnectionUiState(baseUrl = "192.168.178.42:8080").normalizedBaseUrl,
        )
    }

    @Test
    fun refusesSettingsNobodyFilledIn() {
        val message = assertFailsWith<IllegalStateException> { SettingsStore.EMPTY.requireConfigured() }.message
        assertTrue(message!!.contains("Settings"), "message=$message")
    }

    @Test
    fun refusesCredentialsWithoutAUser() {
        assertFailsWith<IllegalStateException> {
            Settings(baseUrl = "https://vorfahrt.example.com", username = "", password = "secret").requireConfigured()
        }
    }

    @Test
    fun passesConfiguredSettingsThroughNormalized() {
        assertEquals(
            Settings("http://192.168.178.42:8080", "rider", "secret"),
            Settings("192.168.178.42:8080/", "rider", "secret").requireConfigured(),
        )
    }
}
