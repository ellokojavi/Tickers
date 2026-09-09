package cl.tickers.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cl.tickers.app.TickersApp
import cl.tickers.app.di.AppContainer

/** Builds a ViewModel from the app's manual dependency container. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = (LocalContext.current.applicationContext as TickersApp).container
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container) } },
    )
}
