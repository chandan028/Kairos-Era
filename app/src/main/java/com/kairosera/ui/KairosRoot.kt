package com.kairosera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kairosera.R
import com.kairosera.core.settings.AppSettings
import com.kairosera.feature.home.HomeScreen
import com.kairosera.feature.more.AboutScreen
import com.kairosera.feature.more.HomeCardsScreen
import com.kairosera.feature.more.MoreScreen
import com.kairosera.feature.more.PrivacyScreen
import com.kairosera.feature.more.SettingsScreen
import com.kairosera.feature.more.TrashScreen
import com.kairosera.feature.onboarding.OnboardingScreen
import com.kairosera.feature.placeholder.ComingSoonScreen
import com.kairosera.feature.planner.PlannerScreen
import com.kairosera.feature.planner.RescheduleDialog
import com.kairosera.feature.tasks.TaskEditorScreen
import java.time.LocalDate

sealed interface LaunchRequest {
    data class OpenDay(val date: LocalDate) : LaunchRequest
    data class Reschedule(val taskId: Long, val date: LocalDate) : LaunchRequest
    data object QuickAdd : LaunchRequest
}

private enum class TopLevel(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    TODAY(Routes.TODAY, R.string.nav_today, Icons.Outlined.Today, Icons.Filled.Today),
    TRACK(Routes.TRACK, R.string.nav_track, Icons.Outlined.Insights, Icons.Filled.Insights),
    READ(Routes.READ, R.string.nav_read, Icons.Outlined.AutoStories, Icons.AutoMirrored.Outlined.MenuBook),
    MORE(Routes.MORE, R.string.nav_more, Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
}

object Routes {
    const val HOME = "home"
    const val TODAY = "today"
    const val TRACK = "track"
    const val READ = "read"
    const val MORE = "more"
    const val TASK = "task"
    const val SETTINGS = "settings"
    const val TRASH = "trash"
    const val PRIVACY = "privacy"
    const val ABOUT = "about"
    const val HOME_CARDS = "home_cards"

    fun today(date: LocalDate?) = if (date == null) TODAY else "$TODAY?date=${date.toEpochDay()}"
    fun task(id: Long? = null, date: LocalDate? = null) = "$TASK?id=${id ?: -1}&date=${date?.toEpochDay() ?: Long.MIN_VALUE}"
}

@Composable
fun KairosRoot(settings: AppSettings, launchRequest: LaunchRequest?, onLaunchRequestHandled: () -> Unit) {
    if (!settings.onboardingDone) {
        OnboardingScreen()
        return
    }
    val nav = rememberNavController()
    var reschedule by remember { mutableStateOf<LaunchRequest.Reschedule?>(null) }
    var quickAdd by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(launchRequest) {
        when (val r = launchRequest) {
            is LaunchRequest.OpenDay -> nav.navigateTopLevel(Routes.today(r.date))
            is LaunchRequest.Reschedule -> { nav.navigateTopLevel(Routes.today(r.date)); reschedule = r }
            LaunchRequest.QuickAdd -> quickAdd = true
            null -> Unit
        }
        if (launchRequest != null) onLaunchRequestHandled()
    }

    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route?.substringBefore('?')

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevel.entries.forEach { dest ->
                val selected = currentRoute == dest.route
                item(
                    selected = selected,
                    onClick = { if (!selected) nav.navigateTopLevel(dest.route) },
                    icon = { Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null) },
                    label = { Text(stringResource(dest.label)) },
                )
            }
        },
    ) {
        KairosNavHost(nav, settings, onQuickAdd = { quickAdd = true })
    }

    reschedule?.let { r -> RescheduleDialog(taskId = r.taskId, date = r.date, onDismiss = { reschedule = null }) }
    if (quickAdd) {
        QuickAddSheet(
            onDismiss = { quickAdd = false },
            onTask = { quickAdd = false; nav.navigate(Routes.task(date = LocalDate.now())) },
        )
    }
}

@Composable
private fun KairosNavHost(nav: NavHostController, settings: AppSettings, onQuickAdd: () -> Unit) {
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                settings = settings,
                onOpenToday = { nav.navigateTopLevel(Routes.TODAY) },
                onOpenTask = { id, date -> nav.navigate(Routes.task(id, date)) },
                onQuickAdd = onQuickAdd,
                onCustomize = { nav.navigate(Routes.HOME_CARDS) },
            )
        }
        composable(
            "${Routes.TODAY}?date={date}",
            arguments = listOf(navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE }),
        ) { entry ->
            val day = entry.arguments?.getLong("date") ?: Long.MIN_VALUE
            PlannerScreen(
                initialDate = if (day == Long.MIN_VALUE) null else LocalDate.ofEpochDay(day),
                onOpenTask = { id, date -> nav.navigate(Routes.task(id, date)) },
                onNewTask = { date -> nav.navigate(Routes.task(date = date)) },
            )
        }
        composable(Routes.TRACK) { ComingSoonScreen(kind = ComingSoonKind.TRACK) }
        composable(Routes.READ) { ComingSoonScreen(kind = ComingSoonKind.READ) }
        composable(Routes.MORE) {
            MoreScreen(
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onTrash = { nav.navigate(Routes.TRASH) },
                onPrivacy = { nav.navigate(Routes.PRIVACY) },
                onAbout = { nav.navigate(Routes.ABOUT) },
                onCalendar = { nav.navigateTopLevel(Routes.TODAY) },
            )
        }
        composable(
            "${Routes.TASK}?id={id}&date={date}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            val day = entry.arguments?.getLong("date") ?: Long.MIN_VALUE
            TaskEditorScreen(
                taskId = id,
                occurrenceDate = if (day == Long.MIN_VALUE) null else LocalDate.ofEpochDay(day),
                onClose = { nav.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onHomeCards = { nav.navigate(Routes.HOME_CARDS) }) }
        composable(Routes.TRASH) { TrashScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PRIVACY) { PrivacyScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.HOME_CARDS) { HomeCardsScreen(settings = settings, onBack = { nav.popBackStack() }) }
    }
}

enum class ComingSoonKind { TRACK, READ }

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = !route.contains('?')
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onTask: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.quick_add_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.quick_add_soon_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onTask, label = { Text(stringResource(R.string.quick_add_task)) }, leadingIcon = { Icon(Icons.Outlined.CheckCircle, null) })
                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.quick_add_tracker)) }, leadingIcon = { Icon(Icons.Outlined.Insights, null) })
                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.quick_add_book)) }, leadingIcon = { Icon(Icons.Outlined.AutoStories, null) })
                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.quick_add_journal)) }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) })
                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.quick_add_study)) }, leadingIcon = { Icon(Icons.Outlined.School, null) })
            }
        }
    }
}
