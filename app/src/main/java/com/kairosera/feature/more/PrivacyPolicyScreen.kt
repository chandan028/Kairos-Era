package com.kairosera.feature.more

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kairosera.R
import com.kairosera.core.ui.components.ContentMaxWidth
import com.kairosera.core.ui.components.Hairline
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.ScreenHeader
import com.kairosera.core.ui.components.SectionLabel
import com.kairosera.core.ui.components.rememberDateFormatter
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.core.ui.theme.Kairos
import java.time.LocalDate

/** Keep in step with PRIVACY_UPDATED in tools/strings_source.py, which dates the HTML copy. */
private val PolicyUpdated: LocalDate = LocalDate.of(2026, 9, 25)
private const val PolicyContact = "wondersparksmedia@gmail.com"

/** Heading and body of each section, in reading order. The HTML copy is generated from the same strings. */
private val PolicySections = listOf(
    R.string.pp_h_stored to R.string.pp_b_stored,
    R.string.pp_h_where to R.string.pp_b_where,
    R.string.pp_h_network to R.string.pp_b_network,
    R.string.pp_h_permissions to R.string.pp_b_permissions,
    R.string.pp_h_visible to R.string.pp_b_visible,
    R.string.pp_h_sharing to R.string.pp_b_sharing,
    R.string.pp_h_backup to R.string.pp_b_backup,
    R.string.pp_h_logs to R.string.pp_b_logs,
    R.string.pp_h_control to R.string.pp_b_control,
    R.string.pp_h_children to R.string.pp_b_children,
    R.string.pp_h_changes to R.string.pp_b_changes,
)

/** The full privacy policy, bundled with the app so it reads offline and follows the app language. */
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val date = rememberDateFormatter("d MMMM yyyy")
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.privacy_policy), onBack = onBack)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = ContentMaxWidth).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 112.dp),
            ) {
                SectionLabel(stringResource(R.string.pp_updated, date(PolicyUpdated)), color = Kairos.colors.accentText)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.pp_intro), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(20.dp))
                KCard(color = Kairos.colors.brand) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Kairos.colors.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.pp_h_summary),
                            style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily),
                            color = Kairos.colors.onBrand,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PolicyBody(stringResource(R.string.pp_b_summary), color = Kairos.colors.onBrand, bullet = Kairos.colors.accent)
                }
                PolicySections.forEach { (h, b) ->
                    Spacer(Modifier.height(28.dp))
                    PolicyHeading(stringResource(h))
                    Spacer(Modifier.height(10.dp))
                    PolicyBody(stringResource(b))
                }
                Spacer(Modifier.height(28.dp))
                PolicyHeading(stringResource(R.string.pp_h_contact))
                Spacer(Modifier.height(10.dp))
                ContactCard()
                Spacer(Modifier.height(28.dp))
                Hairline()
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.privacy_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = Kairos.colors.muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PolicyHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge.copy(fontFamily = SerifFamily),
        modifier = Modifier.semantics { heading() },
    )
}

/** Plain paragraphs, with lines that start with "• " drawn as list items. */
@Composable
private fun PolicyBody(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    bullet: androidx.compose.ui.graphics.Color = Kairos.colors.accentText,
) {
    Column {
        text.split('\n').filter { it.isNotBlank() }.forEachIndexed { i, line ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            if (line.startsWith("• ")) {
                Row {
                    Text("•", style = MaterialTheme.typography.bodyLarge, color = bullet, modifier = Modifier.width(18.dp))
                    Text(line.removePrefix("• "), style = MaterialTheme.typography.bodyLarge, color = color)
                }
            } else {
                Text(line, style = MaterialTheme.typography.bodyLarge, color = color)
            }
        }
    }
}

@Composable
private fun ContactCard() {
    val context = LocalContext.current
    val subject = stringResource(R.string.pp_email_subject)
    KCard(
        onClick = {
            // Hands off to the person's email app; nothing is sent unless they send it.
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$PolicyContact"))
                .putExtra(Intent.EXTRA_EMAIL, arrayOf(PolicyContact))
                .putExtra(Intent.EXTRA_SUBJECT, subject)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                // No email app: the address is on screen to copy by hand.
            }
        },
        padding = 16.dp,
    ) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.MailOutline, contentDescription = null, tint = Kairos.colors.accentText, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.pp_b_contact, PolicyContact), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
