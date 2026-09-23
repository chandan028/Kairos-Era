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
import com.kairosera.feature.planner.PlannerScreen
import com.kairosera.feature.planner.RescheduleDialog
import com.kairosera.feature.read.BookDetailScreen
import com.kairosera.feature.read.BookEditorScreen
import com.kairosera.feature.read.ReadScreen
import com.kairosera.feature.track.TemplatePickerSheet
import com.kairosera.feature.track.TrackScreen
import com.kairosera.feature.track.TrackerDetailScreen
import com.kairosera.feature.track.TrackerEditorScreen
import com.kairosera.domain.tracker.TrackerTemplate
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
    const val TRACKER = "tracker"
    const val TRACKER_EDIT = "tracker_edit"
    const val BOOK = "book"
    const val BOOK_EDIT = "book_edit"

    fun today(date: LocalDate?) = if (date == null) TODAY else "$TODAY?date=${date.toEpochDay()}"
    fun task(id: Long? = null, date: LocalDate? = null) = "$TASK?id=${id ?: -1}&date=${date?.toEpochDay() ?: Long.MIN_VALUE}"
    fun tracker(id: Long) = "$TRACKER/$id"
    fun trackerEdit(id: Long? = null, template: TrackerTemplate = TrackerTemplate.CUSTOM) = "$TRACKER_EDIT?id=${id ?: -1}&template=${template.name}"
    fun book(id: Long) = "$BOOK/$id"
    fun bookEdit(id: Long? = null) = "$BOOK_EDIT?id=${id ?: -1}"
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
    var pickTemplate by rememberSaveable { mutableStateOf(false) }

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
    // Detail screens keep their tab highlighted, so people always know where they are.
    val rawRoute = backStack?.destination?.route?.substringBefore('?')
    val currentRoute = when (val r = rawRoute?.substringBefore('/')) {
        Routes.TRACKER, Routes.TRACKER_EDIT -> Routes.TRACK
        Routes.BOOK, Routes.BOOK_EDIT -> Routes.READ
        Routes.SETTINGS, Routes.TRASH, Routes.PRIVACY, Routes.ABOUT -> Routes.MORE
        else -> r
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevel.entries.forEach { dest ->
                val selected = currentRoute == dest.route
                item(
                    selected = selected,
                    onClick = {
                        when {
                            rawRoute == dest.route -> Unit
                            // Re-tapping the current tab from a detail screen goes back to the tab's root.
                            selected -> if (!nav.popBackStack(dest.route, inclusive = false)) nav.navigateTopLevel(dest.route)
                            else -> nav.navigateTopLevel(dest.route)
                        }
                    },
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
            onTracker = { quickAdd = false; pickTemplate = true },
            onBook = { quickAdd = false; nav.navigate(Routes.bookEdit()) },
            onStudy = { quickAdd = false; nav.navigate(Routes.trackerEdit(template = TrackerTemplate.STUDY)) },
        )
    }
    if (pickTemplate) {
        TemplatePickerSheet(onDismiss = { pickTemplate = false }, onPick = { pickTemplate = false; nav.navigate(Routes.trackerEdit(template = it)) })
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
                onOpenTracker = { nav.navigate(Routes.tracker(it)) },
                onOpenTrack = { nav.navigateTopLevel(Routes.TRACK) },
                onOpenBook = { nav.navigate(Routes.book(it)) },
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
        composable(Routes.TRACK) {
            TrackScreen(
                onOpenTracker = { nav.navigate(Routes.tracker(it)) },
                onNewTracker = { nav.navigate(Routes.trackerEdit(template = it)) },
            )
        }
        composable(Routes.READ) {
            ReadScreen(onOpenBook = { nav.navigate(Routes.book(it)) }, onNewBook = { nav.navigate(Routes.bookEdit()) })
        }
        composable("${Routes.TRACKER}/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            TrackerDetailScreen(trackerId = id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.trackerEdit(id)) })
        }
        composable(
            "${Routes.TRACKER_EDIT}?id={id}&template={template}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("template") { type = NavType.StringType; defaultValue = TrackerTemplate.CUSTOM.name },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            val template = TrackerTemplate.entries.firstOrNull { it.name == entry.arguments?.getString("template") } ?: TrackerTemplate.CUSTOM
            TrackerEditorScreen(
                trackerId = id,
                template = template,
                onClose = { nav.popBackStack() },
                onSaved = { savedId, isNew ->
                    nav.popBackStack()
                    if (isNew) nav.navigate(Routes.tracker(savedId))
                },
            )
        }
        composable("${Routes.BOOK}/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: -1L
            BookDetailScreen(bookId = id, onBack = { nav.popBackStack() }, onEdit = { nav.navigate(Routes.bookEdit(id)) })
        }
        composable("${Routes.BOOK_EDIT}?id={id}", arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            BookEditorScreen(
                bookId = id,
                onClose = { nav.popBackStack() },
                onSaved = { savedId, isNew ->
                    nav.popBackStack()
                    if (isNew) nav.navigate(Routes.book(savedId))
                },
            )
        }
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

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = !route.contains('?')
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onTask: () -> Unit, onTracker: () -> Unit, onBook: () -> Unit, onStudy: () -> Unit) {
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
                AssistChip(onClick = onTracker, label = { Text(stringResource(R.string.quick_add_tracker)) }, leadingIcon = { Icon(Icons.Outlined.Insights, null) })
                AssistChip(onClick = onBook, label = { Text(stringResource(R.string.quick_add_book)) }, leadingIcon = { Icon(Icons.Outlined.AutoStories, null) })
                AssistChip(onClick = {}, enabled = false, label = { Text(stringResource(R.string.quick_add_journal)) }, leadingIcon = { Icon(Icons.Outlined.EditNote, null) })
                AssistChip(onClick = onStudy, label = { Text(stringResource(R.string.quick_add_study)) }, leadingIcon = { Icon(Icons.Outlined.School, null) })
            }
        }
    }
}
