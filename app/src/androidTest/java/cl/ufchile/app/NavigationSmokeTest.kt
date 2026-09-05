package cl.ufchile.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that every tab renders on a real device, offline-safe: the
 * assertions target chrome and bundled-data content, never a value that
 * depends on a successful network call.
 */
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 15_000) {
        rule.waitUntil(timeoutMs) {
            rule.onAllNodesWithText(text, substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun the_app_launches_on_the_today_tab() {
        rule.onNodeWithText("Unidad de Fomento").assertIsDisplayed()
        rule.onNodeWithText("CONVERSOR").assertIsDisplayed()
    }

    @Test
    fun all_four_tabs_are_present() {
        listOf("Hoy", "Histórico", "Inflación", "Créditos").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test
    fun history_tab_opens_and_offers_a_date_lookup() {
        rule.onNodeWithText("Histórico").performClick()

        waitForText("CONSULTAR UNA FECHA")
        rule.onNodeWithText("CONSULTAR UNA FECHA").assertIsDisplayed()
        rule.onNodeWithText("Cambiar").assertIsDisplayed()
    }

    @Test
    fun inflation_tab_computes_from_bundled_data_without_network() {
        rule.onNodeWithText("Inflación").performClick()

        // The seed ships in the APK, so a result must appear regardless of
        // connectivity.
        waitForText("SEGÚN EL IPC")
        rule.onNodeWithText("SEGÚN EL IPC").assertIsDisplayed()
        rule.onNodeWithText("Factor de ajuste").assertIsDisplayed()
        rule.onNodeWithText("Inflación acumulada").assertIsDisplayed()
    }

    @Test
    fun credits_tab_opens_the_editor_and_computes_live() {
        rule.onNodeWithText("Créditos").performClick()
        waitForText("Créditos hipotecarios")

        rule.onNodeWithContentDescription("Nueva simulación").performClick()
        waitForText("Nueva simulación")

        rule.onNodeWithText("CRÉDITO").assertIsDisplayed()

        // The result card lives below the fold, and a LazyColumn does not
        // compose what is off screen, so it has to be scrolled into view.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("RESULTADO"))

        // Defaults alone must already produce a result.
        rule.onNodeWithText("RESULTADO").assertIsDisplayed()
        rule.onNodeWithText("Monto del crédito").assertIsDisplayed()
        rule.onNodeWithText("CAE").assertIsDisplayed()
    }

    @Test
    fun the_theme_toggle_does_not_break_the_screen() {
        rule.onNodeWithContentDescriptionSafe("Cambiar tema").performClick()
        rule.waitForIdle()

        rule.onNodeWithText("Unidad de Fomento").assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>
        .onNodeWithContentDescriptionSafe(description: String) =
        onNode(androidx.compose.ui.test.hasContentDescription(description))
}
