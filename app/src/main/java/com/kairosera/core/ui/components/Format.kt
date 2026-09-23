package com.kairosera.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun currentLocale(): Locale {
    val config = LocalConfiguration.current
    return remember(config) { ConfigurationCompat.getLocales(config)[0] ?: Locale.getDefault() }
}

@Composable
fun rememberTimeFormatter(): (LocalTime) -> String {
    val locale = currentLocale()
    val is24h = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24h) {
        val f = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale)
        return@remember { t: LocalTime -> f.format(t) }
    }
}

@Composable
fun rememberDateFormatter(pattern: String): (LocalDate) -> String {
    val locale = currentLocale()
    return remember(locale, pattern) {
        val f = DateTimeFormatter.ofPattern(pattern, locale)
        return@remember { d: LocalDate -> f.format(d) }
    }
}

@Composable
fun rememberMediumDateFormatter(): (LocalDate) -> String {
    val locale = currentLocale()
    return remember(locale) {
        val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        return@remember { d: LocalDate -> f.format(d) }
    }
}
