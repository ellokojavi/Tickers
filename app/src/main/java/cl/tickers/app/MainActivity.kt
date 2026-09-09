package cl.tickers.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cl.tickers.app.core.locale.withChileanLocale
import cl.tickers.app.data.prefs.ThemeMode
import cl.tickers.app.ui.credit.CreditEditorScreen
import cl.tickers.app.ui.credit.CreditListScreen
import cl.tickers.app.ui.inflation.InflationScreen
import cl.tickers.app.ui.nav.Routes
import cl.tickers.app.ui.nav.Tab
import cl.tickers.app.ui.theme.TickersTheme
import cl.tickers.app.ui.value.UfValueScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // The Activity's configuration is the one Compose reads, so the locale has
    // to be pinned here too, not only on the Application.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withChileanLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { TickersRoot() }
    }
}

@Composable
private fun TickersRoot() {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TickersApp
    val themeMode by app.container.settings.theme.collectAsState(initial = ThemeMode.SYSTEM)
    val scope = rememberCoroutineScope()

    TickersTheme(themeMode = themeMode) {
        val navController = rememberNavController()
        val backStack by navController.currentBackStackEntryAsState()
        val route = backStack?.destination?.route

        // The editor is a full-screen task; the tab bar would only be a way to
        // lose unsaved work.
        val showBar = Tab.entries.any { it.route == route }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBar) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    if (route != tab.route) {
                                        navController.navigate(tab.route) {
                                            popUpTo(Tab.VALUE.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Tab.VALUE.route,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                composable(Tab.VALUE.route) {
                    UfValueScreen(
                        onCycleTheme = {
                            scope.launch {
                                app.container.settings.setTheme(
                                    when (themeMode) {
                                        ThemeMode.SYSTEM -> ThemeMode.LIGHT
                                        ThemeMode.LIGHT -> ThemeMode.DARK
                                        ThemeMode.DARK -> ThemeMode.SYSTEM
                                    }
                                )
                            }
                        },
                        themeMode = themeMode,
                    )
                }
                composable(Tab.INFLATION.route) { InflationScreen() }
                composable(Tab.CREDITS.route) {
                    CreditListScreen(onOpen = { id ->
                        navController.navigate(Routes.creditEditor(id))
                    })
                }
                composable(
                    route = "${Routes.CREDIT_EDITOR}/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    CreditEditorScreen(
                        simulationId = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
