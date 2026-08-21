package nl.jjt.vorfahrtfahrradcompanion.ui.criteria

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jjt.vorfahrtfahrradcompanion.db.settings.SettingsStore
import nl.jjt.vorfahrtfahrradcompanion.domain.settings.CategorizationVariant
import nl.jjt.vorfahrtfahrradcompanion.ui.criteria.jan.JanCriteriaScreen
import nl.jjt.vorfahrtfahrradcompanion.ui.criteria.till.TillCriteriaScreen
import org.koin.compose.koinInject

/**
 * The criteria tab is being designed twice, and the rider picks which design they get under Settings.
 * Both record through the same recorders, so the choice costs nothing but a scroll position — there is
 * nothing to save on the way over and nothing to guard.
 *
 * Nothing is drawn until the choice has been read back. Putting one design up for a frame and swapping
 * it would build that design's ViewModel and set it fetching the catalogue, for a screen nobody asked
 * for. A rider who has never picked gets Jan, so `null` only ever means "not read yet".
 */
@Composable
fun CriteriaScreen(modifier: Modifier = Modifier, settings: SettingsStore = koinInject()) {
    val variant: CategorizationVariant? by settings.categorization.collectAsStateWithLifecycle(null)

    when (variant) {
        CategorizationVariant.JAN -> JanCriteriaScreen(modifier)
        CategorizationVariant.TILL -> TillCriteriaScreen(modifier)
        null -> Box(modifier)
    }
}
