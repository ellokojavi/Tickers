package cl.ufchile.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.ufchile.app.core.format.Fmt
import java.time.LocalDate
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
    fun the_app_launches_showing_todays_value() {
        rule.onNodeWithText("Unidad de Fomento").assertIsDisplayed()
        rule.onNodeWithText("CONVERSOR").assertIsDisplayed()
        rule.onNodeWithText("ÚLTIMOS 60 DÍAS").assertIsDisplayed()
    }

    @Test
    fun all_three_tabs_are_present() {
        listOf("Valor UF", "Inflación", "Créditos").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    /** History is opt-in, so none of its controls may be visible up front. */
    @Test
    fun history_controls_stay_hidden_until_requested() {
        rule.onAllNodesWithText("CONSULTAR UNA FECHA").assertCountEquals(0)
        rule.onAllNodesWithText("1M").assertCountEquals(0)
        rule.onNodeWithText("Ver histórico").assertIsDisplayed()
    }

    @Test
    fun expanding_history_reveals_ranges_and_the_date_lookup() {
        rule.onNodeWithText("Ver histórico").performClick()
        rule.waitForIdle()

        // These land above the fold, so expanding is immediately useful.
        rule.onNodeWithText("HISTÓRICO").assertIsDisplayed()
        rule.onNodeWithText("1M").assertIsDisplayed()
        rule.onNodeWithText("Máx").assertIsDisplayed()

        // The date lookup sits just below it and has to be scrolled to.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("CONSULTAR UNA FECHA"))
        rule.onNodeWithText("CONSULTAR UNA FECHA").assertIsDisplayed()
        rule.onNodeWithText("Cambiar").assertIsDisplayed()

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Ocultar"))
        // Collapsing puts the screen back to the at-a-glance state.
        rule.onNodeWithText("Ocultar").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("1M").assertCountEquals(0)
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

    /**
     * A new simulation leads with the form; a saved one leads with the result.
     * Opening something that already exists is a consultation, not an act of
     * creation, and the ordering has to reflect that.
     */
    @Test
    fun a_saved_simulation_opens_on_its_result_not_its_form() {
        rule.onNodeWithText("Créditos").performClick()
        waitForText("Créditos hipotecarios")
        rule.onNodeWithContentDescription("Nueva simulación").performClick()
        waitForText("Nueva simulación")

        // A new simulation shows the form first, so the result is off screen.
        rule.onNodeWithText("CRÉDITO").assertIsDisplayed()
        rule.onAllNodesWithText("RESULTADO").assertCountEquals(0)

        // A unique name keeps the test independent of anything already saved.
        val name = "QA ${System.currentTimeMillis()}"
        rule.onNodeWithText("Mi simulación").performTextReplacement(name)

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Guardar"))
        rule.onNodeWithText("Guardar").performClick()
        waitForText("Créditos hipotecarios")

        rule.onNodeWithText(name).performClick()
        waitForText("Editar simulación")

        // Reopened, the numbers and the table link come first, with no scroll.
        rule.onNodeWithText("RESULTADO").assertIsDisplayed()
        rule.onNodeWithText("Ver tabla de pagos").assertIsDisplayed()
        rule.onNodeWithText("Costo total").assertIsDisplayed()

        // The editable form and its update action live below.
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Actualizar"))
        rule.onNodeWithText("Actualizar").assertIsDisplayed()

        // Leave no residue behind, so the suite can run repeatedly.
        rule.onNodeWithContentDescription("Eliminar").performClick()
        rule.onNodeWithText("Eliminar").performClick()
        waitForText("Créditos hipotecarios")
        rule.onAllNodesWithText(name).assertCountEquals(0)
    }

    /** The schedule must start on the date the user actually picked. */
    @Test
    fun the_first_instalment_uses_the_chosen_due_date() {
        rule.onNodeWithText("Créditos").performClick()
        waitForText("Créditos hipotecarios")
        rule.onNodeWithContentDescription("Nueva simulación").performClick()
        waitForText("Nueva simulación")

        rule.onNode(hasScrollAction())
            .performScrollToNode(hasText("Vencimiento de la primera cuota"))
        rule.onNodeWithText("Vencimiento de la primera cuota").assertIsDisplayed()
        rule.onNodeWithText("Cambiar").assertIsDisplayed()

        val expected = Fmt.shortDate(LocalDate.now().plusMonths(1))
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Ver tabla de pagos"))
        rule.onNodeWithText("Ver tabla de pagos").performClick()
        waitForText("Tabla de pagos")

        rule.onNodeWithText(expected).assertIsDisplayed()
    }
}
