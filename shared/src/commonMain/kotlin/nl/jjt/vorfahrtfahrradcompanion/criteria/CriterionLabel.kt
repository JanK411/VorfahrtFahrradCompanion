package nl.jjt.vorfahrtfahrradcompanion.criteria

// TODO: have the catalogue endpoint send display labels for criteria *and* values, falling back to this.
//  Deriving a label from an id can only ever be a stopgap: it cannot know that "W_0_5" means "0.5 m wide",
//  which is exactly the kind of thing a rider has to recognise at a glance.

/**
 * A criterion id turned into something readable at arm's length — "ALLOWED_USERS" becomes
 * "Allowed users", "surfaceType" becomes "Surface type". Display only; the id is what gets stored.
 */
fun Criterion.label(): String = buildString {
    id.forEachIndexed { i, char ->
        when {
            char == '_' || char == '-' -> append(' ')
            // A camelCase hump starts a new word.
            char.isUpperCase() && i > 0 && id[i - 1].isLowerCase() -> append(' ').append(char.lowercaseChar())
            else -> append(if (isEmpty()) char.uppercaseChar() else char.lowercaseChar())
        }
    }
}
