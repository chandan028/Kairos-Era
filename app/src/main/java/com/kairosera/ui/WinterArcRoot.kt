package com.kairosera.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kairosera.MainActivity
import com.kairosera.R
import com.kairosera.core.settings.AppSettings
import com.kairosera.core.ui.components.KairosToastHost
import com.kairosera.core.ui.components.LocalToaster
import com.kairosera.core.ui.components.rememberToaster
import com.kairosera.core.ui.theme.Kairos
import com.kairosera.domain.winterarc.StudyTask
import com.kairosera.feature.more.HomeCardsScreen
import com.kairosera.feature.more.PrivacyPolicyScreen
import com.kairosera.feature.more.SettingsScreen
import com.kairosera.feature.winterarc.Arc
import com.kairosera.feature.winterarc.Arc90Screen
import com.kairosera.feature.winterarc.ArcCalendarScreen
import com.kairosera.feature.winterarc.ArcFocusScreen
import com.kairosera.feature.winterarc.ArcHabitsScreen
import com.kairosera.feature.winterarc.ArcHomeScreen
import com.kairosera.feature.winterarc.ArcSettingsScreen
import com.kairosera.feature.winterarc.ArcSpeakScreen
import com.kairosera.feature.winterarc.ArcStatsScreen
import com.kairosera.feature.winterarc.ArcStudyScreen
import com.kairosera.feature.winterarc.ArcViewModel
import com.kairosera.feature.winterarc.StepSensorEffect
import java.time.LocalDate

private object ArcRoutes {
    const val HOME = "arc_home"
    const val HABITS = "arc_habits"
    const val STUDY = "arc_study"
    const val CALENDAR = "arc_calendar"
    const val STATS = "arc_stats"
    const val NINETY = "arc_90"
    const val SETTINGS = "arc_settings"
    const val FOCUS = "arc_focus"
    const val SPEAK = "arc_speak"
    const val APP_SETTINGS = "arc_app_settings"
    const val PRIVACY = "arc_privacy"
    const val BACKUP = "arc_backup"
    const val HOME_CARDS = "arc_home_cards"

    private fun d(date: LocalDate?) = date?.toEpochDay() ?: Long.MIN_VALUE
    fun habits(date: LocalDate?) = "$HABITS?date=${d(date)}"
    fun study(date: LocalDate?) = "$STUDY?date=${d(date)}"
    fun calendar(date: LocalDate?) = "$CALENDAR?date=${d(date)}"
    fun focus(study: Boolean, task: StudyTask?) = "$FOCUS?study=$study&task=${task?.id ?: -1}"
}

private enum class ArcTab(val route: String, val base: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(ArcRoutes.HOME, ArcRoutes.HOME, R.string.wa_nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    HABITS(ArcRoutes.habits(null), ArcRoutes.HABITS, R.string.wa_nav_habits, Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
    STUDY(ArcRoutes.study(null), ArcRoutes.STUDY, R.string.wa_nav_study, Icons.Outlined.School, Icons.Filled.School),
    CALENDAR(ArcRoutes.calendar(null), ArcRoutes.CALENDAR, R.string.wa_nav_calendar, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    STATS(ArcRoutes.STATS, ArcRoutes.STATS, R.string.wa_nav_stats, Icons.Outlined.Insights, Icons.Filled.Insights),
}

/**
 * Winter Arc Mode: its own five tabs over the same app. The everyday app stays one tap away
 * (Settings → Open everyday Kairos Era) and nothing in it changes.
 */
@Composable
fun WinterArcRoot(
    settings: AppSettings,
    launchRequest: LaunchRequest?,
    onLaunchRequestHandled: () -> Unit,
    onOpenEveryday: () -> Unit,
) {
    val vm = kairosViewModel("winter_arc") { ArcViewModel(it) }
    val nav = rememberNavController()
    val arcSettings by vm.settings.collectAsStateWithLifecycle()
    StepSensorEffect(arcSettings?.stepSensor == true)

    LaunchedEffect(launchRequest) {
        val r = launchRequest as? LaunchRequest.OpenArc ?: return@LaunchedEffect
        when (r.target) {
            MainActivity.ARC_STUDY -> nav.navigateArcTab(ArcRoutes.study(null))
            MainActivity.ARC_FOCUS -> nav.navigate(ArcRoutes.focus(false, null))
            MainActivity.ARC_SPEAK -> nav.navigate(ArcRoutes.SPEAK)
            MainActivity.ARC_CALENDAR -> nav.navigateArcTab(ArcRoutes.calendar(null))
            MainActivity.ARC_WATER, MainActivity.ARC_READING -> nav.navigateArcTab(ArcRoutes.habits(null))
            else -> nav.navigateArcTab(ArcRoutes.HOME)
        }
        onLaunchRequestHandled()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val raw = backStack?.destination?.route?.substringBefore('?')
    val current = when (raw) {
        ArcRoutes.NINETY -> ArcRoutes.STATS
        ArcRoutes.SETTINGS, ArcRoutes.APP_SETTINGS, ArcRoutes.PRIVACY, ArcRoutes.BACKUP, ArcRoutes.HOME_CARDS, ArcRoutes.SPEAK -> ArcRoutes.HOME
        else -> raw
    }
    val immersive = raw == ArcRoutes.FOCUS
    val toaster = rememberToaster()
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            indicatorColor = Arc.primarySoft, selectedIconColor = Arc.primary, selectedTextColor = Arc.primary,
            unselectedIconColor = Kairos.colors.muted, unselectedTextColor = Kairos.colors.muted,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            indicatorColor = Arc.primarySoft, selectedIconColor = Arc.primary, selectedTextColor = Arc.primary,
            unselectedIconColor = Kairos.colors.muted, unselectedTextColor = Kairos.colors.muted,
        ),
    )
    CompositionLocalProvider(LocalToaster provides toaster) {
        NavigationSuiteScaffold(
            layoutType = if (immersive) NavigationSuiteType.None else NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = Kairos.colors.card,
                navigationRailContainerColor = MaterialTheme.colorScheme.background,
            ),
            navigationSuiteItems = {
                ArcTab.entries.forEach { tab ->
                    val selected = current == tab.base
                    item(
                        selected = selected,
                        onClick = {
                            if (selected && raw == tab.base) return@item
                            nav.navigateArcTab(tab.route)
                        },
                        icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label), maxLines = 1) },
                        colors = itemColors,
                    )
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                ArcNavHost(nav, vm, settings, onOpenEveryday)
                KairosToastHost(toaster, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

private fun dateArg(v: Long?): LocalDate? = v?.takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)

@Composable
private fun ArcNavHost(nav: NavHostController, vm: ArcViewModel, settings: AppSettings, onOpenEveryday: () -> Unit) {
    val back: () -> Unit = { nav.popBackStack() }
    val dateArgs = listOf(navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE })
    val openFocus = { study: Boolean, task: StudyTask? -> nav.navigate(ArcRoutes.focus(study, task)) }
    NavHost(navController = nav, startDestination = ArcRoutes.HOME) {
        composable(ArcRoutes.HOME) {
            ArcHomeScreen(
                vm,
                onOpenCalendar = { nav.navigateArcTab(ArcRoutes.calendar(null)) },
                onOpenSettings = { nav.navigate(ArcRoutes.SETTINGS) },
                onOpenStudy = { nav.navigateArcTab(ArcRoutes.study(null)) },
                onOpenFocus = { openFocus(false, null) },
                onOpenSpeak = { nav.navigate(ArcRoutes.SPEAK) },
            )
        }
        composable("${ArcRoutes.HABITS}?date={date}", arguments = dateArgs) { e ->
            val date = dateArg(e.arguments?.getLong("date"))
            ArcHabitsScreen(
                vm, initialDate = date,
                onBack = if (date != null) back else null,
                onOpenStudy = { d -> nav.navigate(ArcRoutes.study(d)) },
                onOpenFocus = { openFocus(false, null) },
                onOpenSpeak = { nav.navigate(ArcRoutes.SPEAK) },
            )
        }
        composable("${ArcRoutes.STUDY}?date={date}", arguments = dateArgs) { e ->
            val date = dateArg(e.arguments?.getLong("date"))
            ArcStudyScreen(vm, initialDate = date, onBack = if (date != null) back else null, onStartFocus = { t -> openFocus(true, t) })
        }
        composable("${ArcRoutes.CALENDAR}?date={date}", arguments = dateArgs) { e ->
            val date = dateArg(e.arguments?.getLong("date"))
            ArcCalendarScreen(vm, initialDate = date, onBack = if (date != null) back else null, onEditDay = { d -> nav.navigate(ArcRoutes.habits(d)) })
        }
        composable(ArcRoutes.STATS) { ArcStatsScreen(vm, onBack = null, onOpen90 = { nav.navigate(ArcRoutes.NINETY) }) }
        composable(ArcRoutes.NINETY) { Arc90Screen(vm, onBack = back, onOpenDay = { d -> nav.navigate(ArcRoutes.calendar(d)) }) }
        composable(ArcRoutes.SPEAK) { ArcSpeakScreen(vm, onBack = back) }
        composable(
            "${ArcRoutes.FOCUS}?study={study}&task={task}",
            arguments = listOf(
                navArgument("study") { type = NavType.BoolType; defaultValue = false },
                navArgument("task") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { e ->
            val study = e.arguments?.getBoolean("study") ?: false
            val taskId = e.arguments?.getLong("task")?.takeIf { it > 0 }
            // Wait for the task (if any) so the timer opens with its title and length.
            val loaded by produceState<Pair<Boolean, StudyTask?>>(Pair(taskId == null, null), taskId) { if (taskId != null) value = true to vm.studyTask(taskId) }
            val (ready, task) = loaded
            if (ready) {
                ArcFocusScreen(vm, isStudy = study, studyTaskId = task?.id, studyTitle = task?.title, studyMinutes = task?.durationMinutes, onClose = back)
            }
        }
        composable(ArcRoutes.SETTINGS) {
            ArcSettingsScreen(
                vm, onBack = back,
                onOpenStudy = { nav.navigateArcTab(ArcRoutes.study(null)) },
                onOpenEveryday = onOpenEveryday,
                onOpenAppSettings = { nav.navigate(ArcRoutes.APP_SETTINGS) },
            )
        }
        composable(ArcRoutes.APP_SETTINGS) {
            SettingsScreen(onBack = back, onHomeCards = { nav.navigate(ArcRoutes.HOME_CARDS) }, onPrivacy = { nav.navigate(ArcRoutes.PRIVACY) }, onBackup = { nav.navigate(ArcRoutes.BACKUP) })
        }
        composable(ArcRoutes.PRIVACY) { PrivacyPolicyScreen(onBack = back) }
        composable(ArcRoutes.BACKUP) { com.kairosera.feature.backup.BackupScreen(onBack = back) }
        composable(ArcRoutes.HOME_CARDS) { HomeCardsScreen(settings = settings, onBack = back) }
    }
}

private fun NavHostController.navigateArcTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = !route.contains('?')
    }
}
