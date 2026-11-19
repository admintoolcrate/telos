package cc.toolcrate.telos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.ui.components.EmptyNote
import cc.toolcrate.telos.ui.quickadd.QuickAddPanel
import cc.toolcrate.telos.ui.quickadd.QuickAddViewModel
import cc.toolcrate.telos.ui.screens.BrowseScreen
import cc.toolcrate.telos.ui.screens.InboxScreen
import cc.toolcrate.telos.ui.screens.LabelScreen
import cc.toolcrate.telos.ui.screens.Nav
import cc.toolcrate.telos.ui.screens.ProjectScreen
import cc.toolcrate.telos.ui.screens.QuickAddTarget
import cc.toolcrate.telos.ui.screens.SelectionHandle
import cc.toolcrate.telos.ui.screens.TaskDetailScreen
import cc.toolcrate.telos.ui.screens.TodayScreen
import cc.toolcrate.telos.ui.screens.UpcomingScreen
import cc.toolcrate.telos.ui.theme.LocalPaper

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("today", "Today", Icons.Default.CheckCircle),
    Tab("upcoming", "Upcoming", Icons.Default.DateRange),
    Tab("inbox", "Inbox", Icons.Default.MailOutline),
    Tab("goals", "Goals", Icons.Default.Star),
    Tab("browse", "Browse", Icons.Default.Menu),
)

/**
 * Extra routes registered by later features (filters, goals, data, settings...). Each gets the
 * same arguments as the built-in screens.
 */
class RouteContext(val state: AppState, val vm: AppViewModel, val nav: Nav, val selection: SelectionHandle, val arg: String?)

object Routes {
    /** Feature screens beyond the tabs, keyed by route. */
    val extra: Map<String, @Composable (RouteContext) -> Unit> = featureRoutes()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelosRoot(
    vm: AppViewModel,
    startRoute: String? = null,
    openTaskId: String? = null,
    navController: NavHostController = rememberNavController(),
) {
    val state by vm.state.collectAsState()
    val paper = LocalPaper.current
    val snackbar = remember { SnackbarHostState() }
    var quickAdd by remember { mutableStateOf<QuickAddTarget?>(null) }
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    val selection = SelectionHandle(selected) { id -> selected = if (id in selected) selected - id else selected + id }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    LaunchedEffect(Unit) {
        vm.notices.collect { n ->
            val r = snackbar.showSnackbar(n.text, actionLabel = n.undo?.let { "Undo" }, duration = SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) n.undo?.let(vm::undo)
        }
    }
    LaunchedEffect(openTaskId) { openTaskId?.let { navController.navigate("task/$it") } }

    val nav = Nav(
        openTask = { navController.navigate("task/$it") },
        openProject = { if (it == Project.INBOX_ID) navController.navigate("inbox") else navController.navigate("project/$it") },
        openLabel = { navController.navigate("label/$it") },
        openFilter = { navController.navigate("filter/$it") },
        openGoal = { navController.navigate("goal/$it") },
        go = { navController.navigate(it) },
        back = { navController.popBackStack() },
        quickAdd = { quickAdd = it },
    )

    Scaffold(
        containerColor = paper.page,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (selected.isNotEmpty()) {
                state?.let { BulkBar(it, vm, selected, onDone = { selected = emptySet() }) }
            } else if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = paper.card) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = paper.accentSoft),
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (selected.isEmpty() && route != null && !route.startsWith("task/") && route !in setOf("settings", "data", "privacy", "search")) {
                FloatingActionButton(
                    onClick = {
                        val arg = entry?.arguments?.getString("id")
                        quickAdd = when {
                            route == "project/{id}" -> QuickAddTarget(projectId = arg)
                            route == "today" -> QuickAddTarget(date = state?.today)
                            route == "goal/{id}" -> QuickAddTarget(goalId = arg)
                            else -> QuickAddTarget()
                        }
                    },
                    containerColor = paper.accent, contentColor = paper.page,
                ) { Icon(Icons.Default.Add, contentDescription = "Add task") }
            }
        },
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize())
            return@Scaffold
        }
        NavHost(
            navController,
            startDestination = startRoute ?: s.settings.startTab,
            modifier = Modifier.padding(padding),
        ) {
            composable("today") { TodayScreen(s.copyLatest(vm), vm, nav, selection) }
            composable("upcoming") { UpcomingScreen(s.copyLatest(vm), vm, nav, selection) }
            composable("inbox") { InboxScreen(s.copyLatest(vm), vm, nav, selection) }
            composable("browse") { BrowseScreen(s.copyLatest(vm), vm, nav) }
            composable("project/{id}") { e -> ProjectScreen(e.arguments?.getString("id")!!, s.copyLatest(vm), vm, nav, selection) }
            composable("label/{id}") { e -> LabelScreen(e.arguments?.getString("id")!!, s.copyLatest(vm), vm, nav, selection) }
            composable("task/{id}") { e -> TaskDetailScreen(e.arguments?.getString("id")!!, s.copyLatest(vm), vm, nav) { t -> TaskDetailExtras(t, s.copyLatest(vm), vm) } }
            Routes.extra.forEach { (r, content) ->
                composable(r) { e -> content(RouteContext(s.copyLatest(vm), vm, nav, selection, e.arguments?.getString("id"))) }
            }
            for (r in listOf("goals", "search", "completed", "archive", "trash", "settings", "data", "privacy", "filter/{id}", "goal/{id}")) {
                if (r !in Routes.extra) composable(r) { EmptyNote("Coming soon.") }
            }
        }
    }

    quickAdd?.let { target ->
        val qvm: QuickAddViewModel = hiltViewModel()
        LaunchedEffect(target) { qvm.start(target.projectId, target.sectionId, target.date, target.parentId, target.goalId) }
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { quickAdd = null }, sheetState = sheet, containerColor = paper.card) {
            Column(Modifier.imePadding().windowInsetsPadding(WindowInsets.safeDrawing)) {
                QuickAddPanel(
                    vm = qvm,
                    projects = state?.let { st -> listOfNotNull(st.index.projects[Project.INBOX_ID]) + st.index.projectTree().map { it.first } }.orEmpty(),
                    today = state?.today ?: java.time.LocalDate.now(),
                    onSaved = { t ->
                        quickAdd = null
                        val where = state?.index?.projects?.get(t.projectId)?.name ?: "Inbox"
                        vm.notify("Added to $where")
                    },
                )
            }
        }
    }
}

/** The latest state (the composable may hold an older capture). */
private fun AppState.copyLatest(vm: AppViewModel): AppState = vm.state.value ?: this

/** Sections added to the task screen by later features (reminders). */
object TaskDetailSlots {
    val slots = mutableListOf<@Composable (cc.toolcrate.telos.domain.model.Task, AppState, AppViewModel) -> Unit>(
        { t, s, vm -> cc.toolcrate.telos.reminders.ReminderSection(t, s, vm) },
    )
}

@Composable
private fun TaskDetailExtras(task: cc.toolcrate.telos.domain.model.Task, state: AppState, vm: AppViewModel) {
    TaskDetailSlots.slots.forEach { it(task, state, vm) }
}
