package com.kairosera.feature.home

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairosera.R
import com.kairosera.core.settings.AppSettings
import com.kairosera.core.ui.components.Hairline
import com.kairosera.core.ui.components.KairosLogo
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.core.ui.theme.OverlineStyle
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.domain.quote.DailyQuote
import com.kairosera.ui.appContainer
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.compose.runtime.rememberCoroutineScope

/** Meditative full screen for today's thought. Same language as the motivation widget. */
@Composable
fun QuoteScreen(settings: AppSettings, onClose: () -> Unit) {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    var quote by remember { mutableStateOf<DailyQuote?>(null) }
    LaunchedEffect(today) { quote = runCatching { c.quotes.forDate(today) }.getOrNull() }
    val monthFmt = rememberDateFormatter("MMMM")
    val navy = Kairos.colors.brand
    val cream = Kairos.colors.onBrand
    val sun = Kairos.colors.sun
    val shareTitle = stringResource(R.string.quote_share)

    Box(Modifier.fillMaxSize().background(navy), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 560.dp).fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KairosLogo(Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text("KAIROS", style = OverlineStyle.copy(letterSpacing = 4.sp), color = cream.copy(alpha = 0.8f))
            }
            Spacer(Modifier.height(48.dp))
            Text(
                today.dayOfMonth.toString(),
                style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Normal, fontSize = 88.sp, lineHeight = 92.sp),
                color = cream,
            )
            Text(monthFmt(today).uppercase(), style = OverlineStyle.copy(letterSpacing = 4.sp), color = sun)
            Spacer(Modifier.height(40.dp))
            val q = quote
            if (q != null) {
                Text(
                    "“${q.quote}”",
                    style = TextStyle(fontFamily = SerifFamily, fontSize = 24.sp, lineHeight = 36.sp),
                    color = cream,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(40.dp))
                Hairline(Modifier.width(48.dp).background(sun))
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.quote_todays_action).uppercase(), style = OverlineStyle, color = sun)
                Spacer(Modifier.height(8.dp))
                Text(q.actionPrompt, style = MaterialTheme.typography.bodyLarge, color = cream.copy(alpha = 0.9f), textAlign = TextAlign.Center)
                val favorite = q.dayOfYear in settings.favoriteQuotes
                Row(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QuoteAction(
                        if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        stringResource(if (favorite) R.string.quote_saved else R.string.quote_save),
                        sun,
                        cream,
                    ) { scope.launch { c.settings.toggleFavoriteQuote(q.dayOfYear) } }
                    QuoteAction(Icons.Outlined.Share, stringResource(R.string.quote_share), cream, cream) {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "“${q.quote}”\n\n— Kairos Era")
                        runCatching { context.startActivity(Intent.createChooser(send, shareTitle)) }
                    }
                    QuoteAction(Icons.Outlined.Close, stringResource(R.string.close), cream, cream, onClose)
                }
            }
        }
    }
}

@Composable
private fun QuoteAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color, text: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = text)
    }
}
