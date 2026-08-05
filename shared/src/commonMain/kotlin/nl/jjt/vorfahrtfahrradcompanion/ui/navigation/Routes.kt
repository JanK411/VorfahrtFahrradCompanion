package nl.jjt.vorfahrtfahrradcompanion.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object CriteriaRoute

@Serializable
data object RidesRoute

@Serializable
data object SettingsRoute

/** A sub-page pushed onto the back stack from a tab; each owns the title shown in the top bar. */
sealed interface SubPage {
    val title: String
}

@Serializable
data object ServerConnectionRoute : SubPage {
    override val title = "Server connection"
}

@Serializable
data object LocationRoute : SubPage {
    override val title = "Location"
}

@Serializable
data object PatchNotesRoute : SubPage {
    override val title = "What's New"
}

val subPages: List<SubPage> = listOf(ServerConnectionRoute, LocationRoute, PatchNotesRoute)
