package cl.ufchile.app.core.locale

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * Pins the app to es-CL regardless of the phone's language.
 *
 * Setting Locale.setDefault alone is not enough: Compose reads the locale from
 * the context configuration, so on a device set to English the Material date
 * picker rendered its month names, weekday initials and headline in English,
 * and formatted the headline month-day-year. This app is Chile-only and every
 * date it writes itself is day-month-year, so the picker must agree.
 *
 * Applied in attachBaseContext of both the Application and the Activity: the
 * Activity's configuration is what reaches LocalConfiguration in Compose.
 */
fun Context.withChileanLocale(): Context {
    val locale = Locale.forLanguageTag("es-CL")
    Locale.setDefault(locale)

    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLocales(LocaleList(locale))
    config.setLayoutDirection(locale)
    return createConfigurationContext(config)
}
