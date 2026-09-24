package com.kairosera.feature.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoAccounts
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kairosera.R
import com.kairosera.core.settings.FocusArea
import com.kairosera.core.settings.OnboardingStep
import com.kairosera.core.settings.ReminderChoice
import com.kairosera.core.ui.components.KTimePickerDialog
import com.kairosera.core.ui.components.KairosLogo
import com.kairosera.core.ui.components.SunriseScene
import com.kairosera.core.ui.components.rememberTimeFormatter
import com.kairosera.core.ui.theme.SerifFamily
import com.kairosera.core.ui.theme.Stage
import com.kairosera.ui.kairosViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val STEPS = 5

/**
 * First run, in five calm steps on the night stage:
 * understand (what Kairos Era is), personalize (what matters), experience (one thing for today),
 * enable (reminders, asked only on tap), preview (your day). Everything is optional but the first
 * and last tap, and nothing needs a network or an account.
 */
@Composable
fun OnboardingScreen() {
    val vm = kairosViewModel { OnboardingViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state
    Box(Modifier.fillMaxSize().background(Stage.night0)) {
        if (s == null) return@Box
        BackHandler(enabled = s.step != OnboardingStep.WELCOME) { vm.back() }
        val reduceMotion = rememberReduceMotion()
        AnimatedContent(
            targetState = s.step,
            transitionSpec = { stepTransition(initialState, targetState, reduceMotion) },
            label = "onboarding",
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> Welcome(reduceMotion, onBegin = vm::next)
                OnboardingStep.FOCUS -> Focus(s, vm)
                OnboardingStep.FIRST_ACTION -> FirstAction(s, vm)
                OnboardingStep.REMINDERS -> Reminders(s, vm)
                OnboardingStep.READY -> Ready(s, vm)
            }
        }
    }
}

private fun stepTransition(from: OnboardingStep, to: OnboardingStep, reduceMotion: Boolean): ContentTransform {
    if (reduceMotion) return fadeIn(tween(0)) togetherWith fadeOut(tween(0))
    val forward = to.ordinal > from.ordinal
    val dir = if (forward) 1 else -1
    return (slideInHorizontally(tween(360)) { it / 6 * dir } + fadeIn(tween(360))) togetherWith
        (slideOutHorizontally(tween(260)) { -it / 8 * dir } + fadeOut(tween(200)))
}

@Composable
private fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
}

/**
 * Shared frame for every step: header with back and progress, scrolling content, and the actions
 * pinned at the bottom so they are always in reach on small phones and with large fonts.
 */
@Composable
private fun StepFrame(
    step: Int,
    onBack: (() -> Unit)?,
    background: (@Composable () -> Unit)? = null,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        background?.invoke()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            KairosStepHeader(step, STEPS, onBack)
            Column(
                Modifier.weight(1f).widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                content = content,
            )
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = actions,
            )
        }
    }
}

@Composable
private fun Heading(text: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) {
    Text(text, style = StageType.display.copy(textAlign = align), modifier = modifier.fillMaxWidth().semantics { heading() })
}

/* ---------------------------------------------------------------- 1. Welcome */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(reduceMotion: Boolean, onBegin: () -> Unit) {
    val glow = if (reduceMotion) 1f else {
        val t = rememberInfiniteTransition(label = "glow")
        t.animateFloat(0.7f, 1f, infiniteRepeatable(tween(4200), RepeatMode.Reverse), label = "glow").value
    }
    StepFrame(
        step = 1,
        onBack = null,
        actions = {
            KairosPrimaryButton(stringResource(R.string.onb_lets_begin), onClick = onBegin)
            Text(stringResource(R.string.onb_change_later), style = StageType.small, modifier = Modifier.padding(top = 12.dp))
        },
    ) {
        Box(Modifier.fillMaxWidth().height(290.dp)) {
            SunriseScene(Modifier.fillMaxSize(), horizon = 0.8f, glow = glow, fadeTop = false)
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(104.dp).clip(CircleShape).background(Stage.night0.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    KairosLogo(Modifier.size(100.dp), line = Stage.cream, sun = Stage.gold)
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.app_name_display), style = StageType.brand, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.tagline), style = StageType.overline.copy(color = Stage.cream.copy(alpha = 0.8f)))
            }
        }
        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Heading(stringResource(R.string.onboarding_welcome), align = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.onb_welcome_body), style = StageType.body, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tile = Modifier.weight(1f).fillMaxHeight()
                KairosFeatureTile(Icons.Outlined.EventAvailable, Stage.plan, stringResource(R.string.onb_feat_plan), stringResource(R.string.onb_feat_plan_sub), tile)
                KairosFeatureTile(Icons.AutoMirrored.Outlined.MenuBook, Stage.learn, stringResource(R.string.onb_feat_learn), stringResource(R.string.onb_feat_learn_sub), tile)
                KairosFeatureTile(Icons.Outlined.BarChart, Stage.track, stringResource(R.string.onb_feat_track), stringResource(R.string.onb_feat_track_sub), tile)
                KairosFeatureTile(Icons.Outlined.Spa, Stage.reflect, stringResource(R.string.onb_feat_reflect), stringResource(R.string.onb_feat_reflect_sub), tile)
            }
            Spacer(Modifier.height(14.dp))
            val privacy = stringResource(R.string.onb_priv_a11y)
            FlowRow(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Stage.night1).border(1.dp, Stage.line, RoundedCornerShape(16.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp).clearAndSetSemantics { contentDescription = privacy },
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                KairosPrivacyItem(Icons.Outlined.Lock, stringResource(R.string.onb_priv_private))
                KairosPrivacyItem(Icons.Outlined.PhoneAndroid, stringResource(R.string.onb_priv_local))
                KairosPrivacyItem(Icons.Outlined.NoAccounts, stringResource(R.string.onb_priv_no_account))
                KairosPrivacyItem(Icons.Outlined.CloudOff, stringResource(R.string.onb_priv_no_cloud))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ---------------------------------------------------------------- 2. What matters */

private data class FocusOption(val area: FocusArea, val icon: ImageVector, val tint: Color, val title: Int, val sub: Int)

private val FOCUS_OPTIONS = listOf(
    FocusOption(FocusArea.GET_THINGS_DONE, Icons.Outlined.TaskAlt, Stage.blue, R.string.onb_focus_tasks, R.string.onb_focus_tasks_sub),
    FocusOption(FocusArea.LEARN, Icons.Outlined.School, Stage.learn, R.string.onb_focus_learn, R.string.onb_focus_learn_sub),
    FocusOption(FocusArea.HABITS, Icons.Outlined.Autorenew, Stage.reflect, R.string.onb_focus_habits, R.string.onb_focus_habits_sub),
    FocusOption(FocusArea.HEALTH, Icons.Outlined.DirectionsRun, Stage.health, R.string.onb_focus_health, R.string.onb_focus_health_sub),
    FocusOption(FocusArea.READ, Icons.Outlined.AutoStories, Stage.read, R.string.onb_focus_read, R.string.onb_focus_read_sub),
    FocusOption(FocusArea.GOALS, Icons.Outlined.TrackChanges, Stage.goals, R.string.onb_focus_goals, R.string.onb_focus_goals_sub),
)

@Composable
private fun Focus(s: OnboardingState, vm: OnboardingViewModel) {
    StepFrame(
        step = 2,
        onBack = { vm.back() },
        actions = {
            KairosPrimaryButton(stringResource(R.string.onb_continue), onClick = vm::next)
            KairosSecondaryButton(stringResource(R.string.onb_skip), onClick = vm::skipFocus)
        },
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Heading(stringResource(R.string.onb_focus_title))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.onb_focus_sub), style = StageType.body)
            Spacer(Modifier.height(24.dp))
            FOCUS_OPTIONS.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { o ->
                        KairosFocusCard(
                            icon = o.icon, tint = o.tint, title = stringResource(o.title), description = stringResource(o.sub),
                            selected = o.area in s.draft.focus, onToggle = { vm.toggle(o.area) },
                            modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 132.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/* ---------------------------------------------------------------- 3. One thing */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstAction(s: OnboardingState, vm: OnboardingViewModel) {
    val timeFmt = rememberTimeFormatter()
    var menu by remember { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
    val ideas = listOf(R.string.onb_idea_1, R.string.onb_idea_2, R.string.onb_idea_3, R.string.onb_idea_4).map { stringResource(it) }
    StepFrame(
        step = 3,
        onBack = { vm.back() },
        actions = {
            KairosPrimaryButton(stringResource(R.string.onb_add_to_day), onClick = vm::next)
            KairosSecondaryButton(stringResource(R.string.onb_later), onClick = vm::skipFirstThing)
        },
    ) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(16.dp))
            Heading(stringResource(R.string.onb_first_title))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.onb_first_sub), style = StageType.body)
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = s.draft.firstThing,
                onValueChange = vm::setFirstThing,
                placeholder = { Text(stringResource(R.string.onb_first_hint), style = StageType.body.copy(color = Stage.muted.copy(alpha = 0.8f))) },
                supportingText = { Text(stringResource(R.string.onb_first_example), style = StageType.small.copy(fontSize = 12.sp)) },
                textStyle = StageType.body.copy(color = Stage.cream, fontSize = 17.sp),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.next() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Stage.night3, unfocusedContainerColor = Stage.night2,
                    focusedBorderColor = Stage.gold.copy(alpha = 0.8f), unfocusedBorderColor = Stage.line,
                    cursorColor = Stage.gold, focusedTextColor = Stage.cream, unfocusedTextColor = Stage.cream,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.onb_when), style = StageType.title)
            Spacer(Modifier.height(8.dp))
            Box {
                val label = s.draft.time?.let { timeFmt(it) } ?: stringResource(R.string.onb_anytime)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(Stage.night2)
                        .border(1.dp, Stage.line, RoundedCornerShape(16.dp)).clickable { menu = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = Stage.cream)
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = StageType.body.copy(color = Stage.cream), modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Stage.muted)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.onb_anytime)) }, onClick = { vm.setTime(null); menu = false })
                    listOf(R.string.onb_morning to LocalTime.of(8, 0), R.string.onb_afternoon to LocalTime.of(13, 0), R.string.onb_evening to LocalTime.of(18, 0)).forEach { (name, t) ->
                        DropdownMenuItem(text = { Text("${stringResource(name)} · ${timeFmt(t)}") }, onClick = { vm.setTime(t); menu = false })
                    }
                    DropdownMenuItem(text = { Text(stringResource(R.string.onb_pick_time)) }, onClick = { menu = false; picker = true })
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.onb_quick_ideas), style = StageType.small)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ideas.forEach { idea ->
                    Box(
                        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(Stage.night2)
                            .border(1.dp, if (s.draft.firstThing == idea) Stage.gold else Stage.line, RoundedCornerShape(20.dp))
                            .clickable { vm.setFirstThing(idea) }.padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(idea, style = StageType.small.copy(color = Stage.cream, fontSize = 14.sp)) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SunriseScene(Modifier.fillMaxWidth().height(180.dp), horizon = 0.55f, sunX = 0.62f, path = true)
    }
    if (picker) {
        KTimePickerDialog(initial = s.draft.time ?: LocalTime.of(9, 0), onDismiss = { picker = false }, onPick = { vm.setTime(it); picker = false })
    }
}

/* ---------------------------------------------------------------- 4. Reminders */

@Composable
private fun Reminders(s: OnboardingState, vm: OnboardingViewModel) {
    val context = LocalContext.current
    val timeFmt = rememberTimeFormatter()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.reminders(if (granted) ReminderChoice.ALLOWED else ReminderChoice.DECLINED)
    }
    fun allow() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        when {
            granted -> vm.reminders(ReminderChoice.ALLOWED)
            // Asked before and refused, or turned off in system settings: never nag, just move on.
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || s.draft.permissionAsked -> vm.reminders(ReminderChoice.DECLINED)
            else -> {
                vm.markPermissionAsked()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    val exampleTitle = s.draft.firstThing.trim().ifBlank { stringResource(R.string.onb_rem_example_task) }
    val exampleTime = timeFmt(s.draft.time ?: LocalTime.of(8, 0))
    StepFrame(
        step = 4,
        onBack = { vm.back() },
        actions = {
            KairosPrimaryButton(stringResource(R.string.onboarding_allow_reminders), onClick = ::allow, arrow = false, enabled = !s.finishing)
            KairosSecondaryButton(stringResource(R.string.onboarding_not_now), onClick = { vm.reminders(ReminderChoice.DECLINED) }, underline = true)
        },
    ) {
        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Heading(stringResource(R.string.onb_rem_title), align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.onb_rem_sub), style = StageType.body, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            KairosNotificationPreview(time = exampleTime, title = exampleTitle)
            Spacer(Modifier.height(24.dp))
            StageInfoRow(Icons.Outlined.NotificationsNone, stringResource(R.string.onb_rem_why1))
            StageInfoRow(Icons.Outlined.PhoneAndroid, stringResource(R.string.onb_rem_why2))
            StageInfoRow(Icons.Outlined.Settings, stringResource(R.string.onb_rem_why3))
            Spacer(Modifier.height(8.dp))
        }
    }
}

/* ---------------------------------------------------------------- 5. Ready */

@Composable
private fun Ready(s: OnboardingState, vm: OnboardingViewModel) {
    val context = LocalContext.current
    val timeFmt = rememberTimeFormatter()
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val dateText = remember(locale) { DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", locale).format(LocalDate.now()) }
    StepFrame(
        step = 5,
        onBack = { vm.back() },
        background = { SunriseScene(Modifier.fillMaxWidth().height(320.dp), horizon = 0.62f, sunX = 0.66f) },
        actions = {
            KairosPrimaryButton(stringResource(R.string.onb_open), onClick = { vm.finish(context) }, enabled = !s.finishing)
            Text(stringResource(R.string.onb_customize), style = StageType.small, modifier = Modifier.padding(top = 12.dp))
        },
    ) {
        Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Heading(stringResource(R.string.onb_ready_title), align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.onb_ready_sub), style = StageType.body.copy(color = Stage.cream.copy(alpha = 0.85f)), textAlign = TextAlign.Center)
            Spacer(Modifier.height(120.dp))

            StageCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.today), style = TextStyle(fontFamily = SerifFamily, fontSize = 22.sp, color = Stage.cream), modifier = Modifier.weight(1f))
                        Text(dateText, style = StageType.small)
                    }
                    Spacer(Modifier.height(8.dp))
                    val first = s.draft.firstThing.trim()
                    if (first.isNotBlank()) {
                        val remind = s.draft.time != null && s.draft.reminders == ReminderChoice.ALLOWED
                        PreviewRow(first, trailing = s.draft.time?.let(timeFmt) ?: stringResource(R.string.onb_anytime), bell = remind)
                    }
                    s.starters.forEach { st ->
                        PreviewRow("${st.icon}  ${stringResource(st.title)}", subtitle = stringResource(st.subtitle), pill = stringResource(R.string.onb_optional))
                    }
                    if (first.isBlank() && s.starters.isEmpty()) {
                        Text(stringResource(R.string.onb_nothing_yet), style = StageType.small, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    if (first.isNotBlank() && s.draft.time != null && s.draft.reminders == ReminderChoice.DECLINED) {
                        Text(stringResource(R.string.onb_reminders_off), style = StageType.small.copy(fontSize = 12.sp), modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            s.quote?.let { KairosQuoteCard(it.quote); Spacer(Modifier.height(12.dp)) }
            StageCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Stage.night3), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Stage.cream)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(stringResource(R.string.onb_stored), style = StageType.title)
                        Text(stringResource(R.string.onb_stored_sub), style = StageType.small)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PreviewRow(title: String, trailing: String? = null, bell: Boolean = false, subtitle: String? = null, pill: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(20.dp).border(1.5.dp, Stage.muted, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = StageType.body.copy(color = Stage.cream, fontSize = 15.sp), modifier = Modifier.weight(1f, fill = false))
                if (pill != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        pill,
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Stage.blue),
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Stage.blue.copy(alpha = 0.16f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (subtitle != null) Text(subtitle, style = StageType.small.copy(fontSize = 12.sp))
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = StageType.small)
        }
        if (bell) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.has_reminder), tint = Stage.gold, modifier = Modifier.size(18.dp))
        }
    }
}
