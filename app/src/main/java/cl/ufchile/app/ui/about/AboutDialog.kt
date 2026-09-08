package cl.ufchile.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cl.ufchile.app.BuildConfig
import cl.ufchile.app.ui.components.AppCard
import cl.ufchile.app.ui.components.SectionTitle

/**
 * Independence notice, source attribution and the financial disclaimer.
 *
 * Reached from the footer of the main screen and from the data-source badge,
 * never pushed at the user: nothing here interrupts, and there is no
 * acknowledge-to-continue gate. It exists because the app shows official
 * figures under a Chilean flag, which could otherwise be read as a government
 * service, and because the CMF's terms of use require the source to be named
 * with a link to its site wherever its data is republished.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val uris = LocalUriHandler.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize().safeDrawingPadding(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Acerca de", style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                        }
                    },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AppCard {
                        SectionTitle("Aplicación independiente")
                        Text(
                            "UF Chile es una aplicación independiente. No está afiliada, " +
                                "patrocinada ni respaldada por la Comisión para el Mercado " +
                                "Financiero (CMF), el Banco Central de Chile, el Instituto " +
                                "Nacional de Estadísticas (INE), ni por ningún banco o " +
                                "institución financiera.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                item {
                    AppCard {
                        SectionTitle("Fuentes de los datos")
                        Text(
                            "El valor de la UF proviene de la Comisión para el Mercado " +
                                "Financiero, a través de su API CMF Bancos, y de mindicador.cl " +
                                "como respaldo, que replica los datos del Banco Central de Chile.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinkRow("Comisión para el Mercado Financiero", "https://www.cmfchile.cl") {
                            uris.openUri(it)
                        }
                        LinkRow("API CMF Bancos", "https://api.cmfchile.cl") { uris.openUri(it) }
                        LinkRow("mindicador.cl", "https://mindicador.cl") { uris.openUri(it) }
                    }
                }

                item {
                    AppCard {
                        SectionTitle("Cómo se calcula")
                        Text(
                            "La serie diaria completa de la UF, desde agosto de 1977, viene " +
                                "incluida en la aplicación, por lo que todo funciona sin " +
                                "conexión. Los reajustes se calculan convirtiendo el monto a UF " +
                                "en la fecha inicial y de vuelta a pesos en la final.\n\n" +
                                "Los valores que la fuente entrega con errores evidentes se " +
                                "descartan y se reconstruyen desde su propio período de " +
                                "reajuste. Ningún valor se inventa ni se proyecta: si un dato " +
                                "no existe, la aplicación lo dice.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                item {
                    AppCard {
                        SectionTitle("Aviso")
                        Text(
                            "Las cifras se entregan solo con fines informativos y no " +
                                "constituyen asesoría financiera ni de inversión.\n\n" +
                                "Las simulaciones de crédito son modelos, no cotizaciones. Cada " +
                                "banco aplica sus propias convenciones de tasa, comisiones y " +
                                "seguros. Confirma siempre las condiciones con la institución " +
                                "antes de tomar una decisión.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                item {
                    AppCard {
                        SectionTitle("Versión")
                        Text(
                            "UF Chile ${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinkRow("Código fuente y licencia MIT", "https://github.com/ellokojavi/UFChile") {
                            uris.openUri(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, url: String, onOpen: (String) -> Unit) {
    TextButton(
        onClick = { onOpen(url) },
        // Zero horizontal padding clipped the first character of the URL.
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
