package nl.jjt.vorfahrtfahrradcompanion.navigation

import kotlinx.serialization.Serializable

@Serializable
data object CriteriaRoute

@Serializable
data object RideRoute

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
data object PatchNotesRoute : SubPage {
    override val title = "What's New"
}

val subPages: List<SubPage> = listOf(ServerConnectionRoute, PatchNotesRoute)
