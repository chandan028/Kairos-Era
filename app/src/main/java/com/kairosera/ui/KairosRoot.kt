package com.kairosera.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.outlined.Add
import com.kairosera.core.ui.components.KCard
import com.kairosera.core.ui.components.ToneIcon
import com.kairosera.core.ui.theme.Tone
import com.kairosera.core.ui.components.KairosToastHost
import com.kairosera.core.ui.components.LocalToaster
import com.kairosera.core.ui.components.rememberToaster
import com.kairosera.core.ui.theme.Kairos
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import com.kairosera.domain.journal.Mood
import com.kairosera.feature.insights.AreaStat
import com.kairosera.feature.insights.DayDetailScreen
import com.kairosera.feature.insights.LifeCalendarScreen
import com.kairosera.feature.insights.StatisticsScreen
import com.kairosera.feature.journal.JournalEditorScreen
import com.kairosera.feature.journal.JournalScreen
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.kairosera.feature.home.QuoteScreen
import com.kairosera.feature.more.AboutScreen
import com.kairosera.feature.more.HomeCardsScreen
import com.kairosera.feature.more.MoreScreen
import com.kairosera.feature.more.PrivacyPolicyScreen
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
    data object NewTask : LaunchRequest
    data object OpenTrack : LaunchRequest
    data object OpenQuote : LaunchRequest
    data object NewJournal : LaunchRequest
}

private enum class TopLevel(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME(Routes.HOME, R.string.nav_home, Icons.Outlined.Home, Icons.Filled.Home),
    TODAY(Routes.TODAY, R.string.nav_today, Icons.Outlined.Today, Icons.Filled.Today),
    TRACK(Routes.TRACK, R.string.nav_track, Icons.Outlined.Insights, Icons.Filled.Insights),
    JOURNAL(Routes.JOURNAL, R.string.nav_journal, Icons.Outlined.EditNote, Icons.Filled.EditNote),
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
    const val BACKUP = "backup"
    const val DIAGNOSTICS = "diagnostics"
    const val ABOUT = "about"
    const val HOME_CARDS = "home_cards"
    const val TRACKER = "tracker"
    const val TRACKER_EDIT = "tracker_edit"
    const val BOOK = "book"
    const val BOOK_EDIT = "book_edit"
    const val QUOTE = "quote"
    const val JOURNAL = "journal"
    const val JOURNAL_EDIT = "journal_edit"
    const val STATS = "stats"
    const val CALENDAR = "calendar"
    const val DAY = "day"

    fun today(date: LocalDate?) = if (date == null) TODAY else "$TODAY?date=${date.toEpochDay()}"
    fun task(id: Long? = null, date: LocalDate? = null) = "$TASK?id=${id ?: -1}&date=${date?.toEpochDay() ?: Long.MIN_VALUE}"
    fun tracker(id: Long) = "$TRACKER/$id"
    fun trackerEdit(id: Long? = null, template: TrackerTemplate = TrackerTemplate.CUSTOM) = "$TRACKER_EDIT?id=${id ?: -1}&template=${template.name}"
    fun book(id: Long) = "$BOOK/$id"
    fun bookEdit(id: Long? = null) = "$BOOK_EDIT?id=${id ?: -1}"
    fun journalEdit(id: Long? = null, date: LocalDate? = null, mood: Mood? = null) =
        "$JOURNAL_EDIT?id=${id ?: -1}&date=${date?.toEpochDay() ?: Long.MIN_VALUE}&mood=${mood?.name.orEmpty()}"
    fun calendar(date: LocalDate? = null) = "$CALENDAR?date=${date?.toEpochDay() ?: Long.MIN_VALUE}"
    fun day(date: LocalDate) = "$DAY/${date.toEpochDay()}"
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
    CrashPrompt(onReview = { nav.navigate(Routes.DIAGNOSTICS) })

    LaunchedEffect(launchRequest) {
        when (val r = launchRequest) {
            is LaunchRequest.OpenDay -> nav.navigateTopLevel(Routes.today(r.date))
            is LaunchRequest.Reschedule -> { nav.navigateTopLevel(Routes.today(r.date)); reschedule = r }
            LaunchRequest.QuickAdd -> quickAdd = true
            LaunchRequest.NewTask -> nav.navigate(Routes.task(date = LocalDate.now()))
            LaunchRequest.OpenTrack -> nav.navigateTopLevel(Routes.TRACK)
            LaunchRequest.OpenQuote -> nav.navigate(Routes.QUOTE)
            LaunchRequest.NewJournal -> nav.navigate(Routes.journalEdit(date = LocalDate.now()))
            null -> Unit
        }
        if (launchRequest != null) onLaunchRequestHandled()
    }

    val backStack by nav.currentBackStackEntryAsState()
    // Detail screens keep their tab highlighted, so people always know where they are.
    val rawRoute = backStack?.destination?.route?.substringBefore('?')
    val currentRoute = when (val r = rawRoute?.substringBefore('/')) {
        Routes.TRACKER, Routes.TRACKER_EDIT -> Routes.TRACK
        Routes.BOOK, Routes.BOOK_EDIT -> Routes.TRACK
        Routes.JOURNAL_EDIT -> Routes.JOURNAL
        Routes.READ, Routes.SETTINGS, Routes.TRASH, Routes.PRIVACY, Routes.BACKUP, Routes.DIAGNOSTICS, Routes.ABOUT, Routes.STATS, Routes.CALENDAR, Routes.DAY -> Routes.MORE
        else -> r
    }

    val toaster = rememberToaster()
    val navColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            indicatorColor = Kairos.colors.brand,
            selectedIconColor = Kairos.colors.onBrand,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            unselectedIconColor = Kairos.colors.muted,
            unselectedTextColor = Kairos.colors.muted,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            indicatorColor = Kairos.colors.brand,
            selectedIconColor = Kairos.colors.onBrand,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            unselectedIconColor = Kairos.colors.muted,
            unselectedTextColor = Kairos.colors.muted,
        ),
    )
    // Full-screen moments (the daily thought) and focused editors hide the navigation.
    val immersive = rawRoute?.substringBefore('/') in setOf(Routes.QUOTE, Routes.TASK, Routes.TRACKER_EDIT, Routes.BOOK_EDIT, Routes.JOURNAL_EDIT)
    CompositionLocalProvider(LocalToaster provides toaster) {
        NavigationSuiteScaffold(
            layoutType = if (immersive) NavigationSuiteType.None else NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo()),
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = Kairos.colors.card,
                navigationRailContainerColor = MaterialTheme.colorScheme.background,
            ),
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
                        colors = navColors,
                    )
                }
            },
        ) {
            Box(Modifier.fillMaxSize()) {
                KairosNavHost(nav, settings, onQuickAdd = { quickAdd = true })
                KairosToastHost(toaster, Modifier.align(Alignment.BottomCenter))
            }
        }
    reschedule?.let { r -> RescheduleDialog(taskId = r.taskId, date = r.date, onDismiss = { reschedule = null }) }
    if (quickAdd) {
        QuickAddSheet(
            onDismiss = { quickAdd = false },
            onTask = { quickAdd = false; nav.navigate(Routes.task(date = LocalDate.now())) },
            onLog = { quickAdd = false; nav.navigateTopLevel(Routes.TRACK) },
            onTracker = { quickAdd = false; pickTemplate = true },
            onBook = { quickAdd = false; nav.navigate(Routes.bookEdit()) },
            onStudy = { quickAdd = false; nav.navigate(Routes.trackerEdit(template = TrackerTemplate.STUDY)) },
            onJournal = { quickAdd = false; nav.navigate(Routes.journalEdit(date = LocalDate.now())) },
        )
    }
    if (pickTemplate) {
        TemplatePickerSheet(onDismiss = { pickTemplate = false }, onPick = { pickTemplate = false; nav.navigate(Routes.trackerEdit(template = it)) })
    }
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
                onOpenQuote = { nav.navigate(Routes.QUOTE) },
            )
        }
        composable(Routes.QUOTE) { QuoteScreen(settings = settings, onClose = { nav.popBackStack() }) }
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
                onOpenBook = { nav.navigate(Routes.book(it)) },
            )
        }
        composable(Routes.READ) {
            ReadScreen(onOpenBook = { nav.navigate(Routes.book(it)) }, onNewBook = { nav.navigate(Routes.bookEdit()) }, onBack = { nav.popBackStack() })
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
                onBackup = { nav.navigate(Routes.BACKUP) },
                onDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                onCalendar = { nav.navigate(Routes.calendar()) },
                onStatistics = { nav.navigate(Routes.STATS) },
                onJournal = { nav.navigateTopLevel(Routes.JOURNAL) },
                onReading = { nav.navigate(Routes.READ) },
            )
        }
        composable(Routes.JOURNAL) {
            JournalScreen(
                onWrite = { date, mood -> nav.navigate(Routes.journalEdit(date = date, mood = mood)) },
                onOpen = { nav.navigate(Routes.journalEdit(id = it)) },
                onOpenCalendar = { nav.navigate(Routes.calendar()) },
            )
        }
        composable(
            "${Routes.JOURNAL_EDIT}?id={id}&date={date}&mood={mood}",
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                navArgument("mood") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            val day = entry.arguments?.getLong("date") ?: Long.MIN_VALUE
            val mood = entry.arguments?.getString("mood")?.let { m -> Mood.entries.firstOrNull { it.name == m } }
            JournalEditorScreen(
                entryId = id,
                date = if (day == Long.MIN_VALUE) null else LocalDate.ofEpochDay(day),
                presetMood = mood,
                onClose = { nav.popBackStack() },
            )
        }
        composable(Routes.STATS) {
            StatisticsScreen(
                onBack = { nav.popBackStack() },
                onOpenDay = { nav.navigate(Routes.calendar(it)) },
                onOpenCalendar = { nav.navigate(Routes.calendar()) },
                onOpenJournal = { nav.navigateTopLevel(Routes.JOURNAL) },
                onOpenArea = { a ->
                    when (a.kind) {
                        AreaStat.Kind.TRACKER -> a.tracker?.let { nav.navigate(Routes.tracker(it.id)) }
                        AreaStat.Kind.TASKS -> nav.navigateTopLevel(Routes.TODAY)
                        AreaStat.Kind.READING -> nav.navigate(Routes.READ)
                        AreaStat.Kind.JOURNAL -> nav.navigateTopLevel(Routes.JOURNAL)
                    }
                },
                onCreate = onQuickAdd,
            )
        }
        composable(
            "${Routes.CALENDAR}?date={date}",
            arguments = listOf(navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE }),
        ) { entry ->
            val day = entry.arguments?.getLong("date") ?: Long.MIN_VALUE
            LifeCalendarScreen(
                initial = if (day == Long.MIN_VALUE) null else LocalDate.ofEpochDay(day),
                onBack = { nav.popBackStack() },
                onViewDay = { nav.navigate(Routes.day(it)) },
                onOpenJournal = { nav.navigate(Routes.journalEdit(date = it)) },
                onOpenStatistics = { nav.navigate(Routes.STATS) },
                onOpenJournalList = { nav.navigateTopLevel(Routes.JOURNAL) },
            )
        }
        composable("${Routes.DAY}/{date}", arguments = listOf(navArgument("date") { type = NavType.LongType })) { entry ->
            val date = LocalDate.ofEpochDay(entry.arguments?.getLong("date") ?: LocalDate.now().toEpochDay())
            DayDetailScreen(
                date = date,
                onBack = { nav.popBackStack() },
                onOpenTask = { id, d -> nav.navigate(Routes.task(id, d)) },
                onOpenJournal = { nav.navigate(Routes.journalEdit(date = it)) },
                onOpenToday = { nav.navigateTopLevel(Routes.today(it)) },
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
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }, onHomeCards = { nav.navigate(Routes.HOME_CARDS) }, onPrivacy = { nav.navigate(Routes.PRIVACY) }, onBackup = { nav.navigate(Routes.BACKUP) }) }
        composable(Routes.TRASH) { TrashScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PRIVACY) { PrivacyPolicyScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.BACKUP) { com.kairosera.feature.backup.BackupScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.DIAGNOSTICS) { com.kairosera.feature.diagnostics.DiagnosticsScreen(onBack = { nav.popBackStack() }) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSheet(onDismiss: () -> Unit, onTask: () -> Unit, onLog: () -> Unit, onTracker: () -> Unit, onBook: () -> Unit, onStudy: () -> Unit, onJournal: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
            Text(stringResource(R.string.quick_add_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 4.dp))
            Spacer(Modifier.height(16.dp))
            val c = Kairos.colors
            val tiles = listOf(
                QuickTile(R.string.quick_add_task, R.string.quick_add_task_hint, Icons.Outlined.CheckCircle, c.info, onTask),
                QuickTile(R.string.quick_add_log, R.string.quick_add_log_hint, Icons.Outlined.Insights, c.success, onLog),
                QuickTile(R.string.quick_add_study, R.string.quick_add_study_hint, Icons.Outlined.School, c.learning, onStudy),
                QuickTile(R.string.quick_add_book, R.string.quick_add_book_hint, Icons.Outlined.AutoStories, c.motivation, onBook),
                QuickTile(R.string.quick_add_tracker, R.string.quick_add_tracker_hint, Icons.Outlined.Add, c.activity, onTracker),
                QuickTile(R.string.quick_add_journal, R.string.quick_add_journal_hint, Icons.Outlined.EditNote, c.learning, onJournal),
            )
            tiles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { t ->
                        KCard(Modifier.weight(1f).alpha(if (t.onClick == null) 0.55f else 1f), onClick = t.onClick, padding = 16.dp) {
                            ToneIcon(t.icon, t.tone, size = 40.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(t.label), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(t.hint), style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

private data class QuickTile(val label: Int, val hint: Int, val icon: ImageVector, val tone: Tone, val onClick: (() -> Unit)?)

/** Asks once, after a crash, whether to look at the report. Never over the app lock. */
@Composable
private fun CrashPrompt(onReview: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val locked by appContainer().lock.locked.collectAsStateWithLifecycle()
    var report by remember { mutableStateOf(com.kairosera.core.diagnostics.CrashReports.pending(context)?.takeIf { !it.prompted }) }
    if (report == null || locked) return
    val close = { com.kairosera.core.diagnostics.CrashReports.markPrompted(context); report = null }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = close,
        title = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.crash_prompt_title)) },
        text = { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.crash_prompt_body)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { close(); onReview() }) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.crash_prompt_review))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = close) { androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.crash_prompt_later)) }
        },
    )
}
