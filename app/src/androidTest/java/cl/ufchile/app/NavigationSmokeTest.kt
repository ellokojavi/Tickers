package cl.ufchile.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
    }

    @Test
    fun all_three_tabs_are_present() {
        listOf("Valor UF", "Inflación", "Créditos").forEach {
            rule.onNodeWithText(it).assertIsDisplayed()
        }
    }

    /** The history is part of the screen, not a mode to switch into. */
    @Test
    fun the_history_and_its_ranges_are_visible_without_any_interaction() {
        rule.onNodeWithText("HISTÓRICO").assertIsDisplayed()
        rule.onNodeWithText("1M").assertIsDisplayed()
        rule.onNodeWithText("Máx").assertIsDisplayed()
    }

    @Test
    fun the_date_lookup_is_present_on_arrival() {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("CONSULTAR UNA FECHA"))

        rule.onNodeWithText("CONSULTAR UNA FECHA").assertIsDisplayed()
        rule.onNodeWithText("Cambiar").assertIsDisplayed()
    }

    /** Hundreds of rows stay folded away until the user asks for them. */
    @Test
    fun the_daily_detail_starts_collapsed_and_expands_on_demand() {
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("DETALLE DIARIO"))
        rule.onNodeWithText("DETALLE DIARIO").assertIsDisplayed()
        rule.onAllNodesWithContentDescription("Ocultar el detalle diario").assertCountEquals(0)

        rule.onNodeWithContentDescription("Mostrar el detalle diario").performClick()
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Ocultar el detalle diario").assertIsDisplayed()
    }

    @Test
    fun inflation_tab_computes_from_bundled_data_without_network() {
        rule.onNodeWithText("Inflación").performClick()

        // The whole series ships in the APK, so a result must appear
        // regardless of connectivity.
        waitForText("Calculadora de inflación")
        rule.onNodeWithText("Desde").assertIsDisplayed()
        rule.onNodeWithText("Hasta").assertIsDisplayed()

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Reajuste"))
        rule.onNodeWithText("Reajuste").assertIsDisplayed()
        rule.onNodeWithText("Variación acumulada").assertIsDisplayed()
        rule.onNodeWithText("Equivalente anual").assertIsDisplayed()
    }

    /** Dates, not months: the point of the rewrite. */
    @Test
    fun the_inflation_dates_are_chosen_by_day() {
        rule.onNodeWithText("Inflación").performClick()
        waitForText("Calculadora de inflación")

        // A full date, not a month and a year.
        rule.onAllNodesWithText("1 de enero de 1990").assertCountEquals(1)
        rule.onAllNodesWithText("Cambiar").assertCountEquals(2)
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
