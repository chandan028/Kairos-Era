package com.kairosera.feature.winterarc

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kairosera.R
import com.kairosera.core.ui.components.KDatePickerDialog
import com.kairosera.core.ui.components.currentLocale
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.data.winterarc.WinterArcRepository
import com.kairosera.domain.winterarc.HabitKind
import com.kairosera.ui.appContainer
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * First switch-on: four calm steps over the sunrise. Start, choose the start date, choose the
 * daily commitments, confirm. Nothing is saved until "Enter Winter Arc".
 */
@Composable
fun ArcOnboarding(onCancel: () -> Unit, onDone: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var start by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var enabled by rememberSaveable { mutableStateOf(HabitKind.entries.map { it.name }.toSet()) }
    var busy by remember { mutableStateOf(false) }
    BackHandler { if (step == 0) onCancel() else step-- }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B1322))) {
        Image(
            painterResource(R.drawable.onboarding_sunrise), null,
            contentScale = ContentScale.Crop, alignment = BiasAlignment(0f, 0.2f), modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    if (step == 0) listOf(Color(0xAA0B1322), Color(0x220B1322), Color(0xEE0B1322))
                    else listOf(Color(0xDD0B1322), Color(0xCC0B1322), Color(0xF20B1322)),
                ),
            ),
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)
                .widthIn(max = 560.dp).align(Alignment.TopCenter),
        ) {
            IconButton(onClick = { if (step == 0) onCancel() else step-- }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                when (step) {
                    0 -> Welcome()
                    1 -> DateStep(LocalDate.ofEpochDay(start)) { start = it.toEpochDay() }
                    2 -> CommitStep(enabled) { k, on -> enabled = if (on) enabled + k.name else enabled - k.name }
                    else -> ReadyStep(LocalDate.ofEpochDay(start))
                }
            }
            Spacer(Modifier.height(12.dp))
            val label = when (step) {
                0 -> R.string.wa_ob_start
                3 -> R.string.wa_ob_enter
                else -> R.string.wa_ob_continue
            }
            ArcButton(
                stringResource(label),
                enabled = !busy && (step != 2 || enabled.isNotEmpty()),
                onClick = {
                    if (step < 3) { step++; return@ArcButton }
                    busy = true
                    scope.launch {
                        val kinds = enabled.mapNotNull { HabitKind.fromKey(it) }.toSet()
                        runCatching { c.winterArc.startArc(LocalDate.ofEpochDay(start), kinds) }
                        c.winterPrefs.setOnboarded()
                        c.winterReminders.rebuild()
                        busy = false
                        onDone()
                    }
                },
            )
            if (step == 2 && enabled.isEmpty()) {
                Text(stringResource(R.string.wa_ob_need_one), color = Color.White.copy(alpha = 0.8f), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun Welcome() {
    Spacer(Modifier.height(48.dp))
    Text(
        stringResource(R.string.wa_ob_title), color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 6.sp, modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(20.dp))
    Text(stringResource(R.string.wa_ob_lines), color = Color.White, fontFamily = SerifFamily, fontSize = 28.sp, lineHeight = 40.sp)
}

@Composable
private fun DateStep(start: LocalDate, onPick: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val locale = currentLocale()
    val fmt = remember(locale) { DateTimeFormatter.ofPattern("EEE, d MMMM yyyy", locale) }
    Spacer(Modifier.height(24.dp))
    Text(stringResource(R.string.wa_ob_date_title), color = Color.White, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(8.dp))
    Text(fmt.format(start), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(20.dp))
    Choice(stringResource(R.string.wa_ob_date_today), start == today) { onPick(today) }
    val plan = WinterArcRepository.PLAN_START
    if (!plan.isBefore(today)) Choice(stringResource(R.string.wa_ob_date_plan), start == plan) { onPick(plan) }
    Choice(stringResource(R.string.wa_ob_date_pick), start != today && start != plan) { picking = true }
    if (picking) KDatePickerDialog(initial = start, onDismiss = { picking = false }, onPick = { picking = false; onPick(it) })
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
        contentColor = if (selected) Color(0xFF0B1322) else Color.White,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp))
    }
}

@Composable
private fun CommitStep(enabled: Set<String>, onToggle: (HabitKind, Boolean) -> Unit) {
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.wa_ob_commit_title), color = Color.White, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.wa_ob_commit_body), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(12.dp))
    HabitKind.entries.forEach { k ->
        val on = k.name in enabled
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(k, it) })
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = Color(0xFF8DB6F5), uncheckedColor = Color.White.copy(alpha = 0.7f), checkmarkColor = Color(0xFF0B1322)))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(k.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(k.commitLabel()), color = Color.White, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun ReadyStep(start: LocalDate) {
    val locale = currentLocale()
    val fmt = remember(locale) { DateTimeFormatter.ofPattern("d MMMM yyyy", locale) }
    Spacer(Modifier.height(56.dp))
    Text(stringResource(R.string.wa_ob_ready_title), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(10.dp))
    Text(fmt.format(start), color = Color.White, fontFamily = SerifFamily, fontSize = 36.sp, lineHeight = 44.sp, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(18.dp))
    Text(stringResource(R.string.wa_day_short, 1, 90), color = Color(0xFF8DB6F5), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Start)
}
