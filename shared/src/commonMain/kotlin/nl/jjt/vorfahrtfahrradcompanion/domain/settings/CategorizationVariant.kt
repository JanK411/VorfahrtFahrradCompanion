package nl.jjt.vorfahrtfahrradcompanion.domain.settings

/**
 * Which design of the criteria screen the rider is riding with. The two are being compared against
 * each other on the road, so this is temporary: it goes when one of them has won.
 *
 * [label] is what Settings calls it — the designs are named after who they came from, not after what
 * they do, because what they do is the same thing twice.
 */
enum class CategorizationVariant(val label: String) {
    JAN("Jan"),
    TILL("Till"),
}
