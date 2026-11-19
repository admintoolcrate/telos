package cc.toolcrate.telos.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.toolcrate.telos.data.Settings
import cc.toolcrate.telos.data.testAppViewModel
import cc.toolcrate.telos.domain.model.Goal
import cc.toolcrate.telos.domain.model.Label
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.views.Index
import cc.toolcrate.telos.ui.goals.GoalDetailScreen
import cc.toolcrate.telos.ui.goals.GoalsScreen
import cc.toolcrate.telos.ui.screens.BrowseScreen
import cc.toolcrate.telos.ui.screens.InboxScreen
import cc.toolcrate.telos.ui.screens.Nav
import cc.toolcrate.telos.ui.screens.ProjectScreen
import cc.toolcrate.telos.ui.screens.SelectionHandle
import cc.toolcrate.telos.ui.screens.SettingsScreen
import cc.toolcrate.telos.ui.screens.TaskDetailScreen
import cc.toolcrate.telos.ui.screens.TodayScreen
import cc.toolcrate.telos.ui.screens.UpcomingScreen
import cc.toolcrate.telos.ui.theme.TelosTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Renders each main screen (at normal and double text size) and fails if any control TalkBack can
 * reach has no name: every clickable thing must have text or a content description.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.now()
    private fun t(id: String, title: String, due: Long? = null, project: String = Project.INBOX_ID, parent: String? = null, goal: String? = null, p: Int = 4) =
        Task(id = id, title = title, dueDate = due, projectId = project, parentId = parent, goalId = goal, priority = p, orderKey = id, createdAt = 0, updatedAt = 0, deviceId = "d")

    private val state = AppState(
        Index(Snapshot(
            projects = listOf(
                Project(Project.INBOX_ID, "Inbox", orderKey = "0", isInbox = true, createdAt = 0, updatedAt = 0, deviceId = "d"),
                Project("w", "Work", goalId = "g", orderKey = "1", createdAt = 0, updatedAt = 0, deviceId = "d"),
            ),
            goals = listOf(Goal("g", "Ship the book", why = "Because it matters", orderKey = "a", createdAt = 0, updatedAt = 0, deviceId = "d")),
            labels = listOf(Label("l", "phone", orderKey = "a", createdAt = 0, updatedAt = 0, deviceId = "d")),
            tasks = listOf(
                t("a", "Earlier thing", today.minusDays(2).toEpochDay(), p = 1),
                t("b", "Today thing", today.toEpochDay()),
                t("c", "Parent", project = "w"), t("d", "Child", project = "w", parent = "c"),
                t("e", "Inbox item"), t("f", "Next week", today.plusDays(3).toEpochDay(), goal = "g"),
            ),
        )),
        today, Settings(),
    )
    private val nav = Nav({}, {}, {}, {}, {}, {}, {}, {})
    private val sel = SelectionHandle(emptySet()) {}

    private fun unlabeled(): List<String> {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = false).fetchSemanticsNodes()
        return nodes.filter { n -> !named(n) }.map { it.config.toString() }
    }

    private fun named(n: SemanticsNode): Boolean {
        fun SemanticsNode.has(): Boolean =
            !config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() ||
                !config.getOrNull(SemanticsProperties.Text).isNullOrEmpty() ||
                !config.getOrNull(SemanticsProperties.EditableText)?.text.isNullOrEmpty()
        return n.has() || n.children.any { it.has() || it.children.any { c -> c.has() } }
    }

    private fun check(name: String, content: @Composable () -> Unit) {
        val scale = mutableFloatStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale.floatValue)) { TelosTheme { content() } }
        }
        for (s in listOf(1f, 2f)) {
            scale.floatValue = s
            compose.waitForIdle()
            assertEquals("$name at ${s}x has unnamed controls", emptyList<String>(), unlabeled())
        }
    }

    @Test fun today() { val vm = testAppViewModel(); check("Today") { TodayScreen(state, vm, nav, sel) } }
    @Test fun upcoming() { val vm = testAppViewModel(); check("Upcoming") { UpcomingScreen(state, vm, nav, sel) } }
    @Test fun inbox() { val vm = testAppViewModel(); check("Inbox") { InboxScreen(state, vm, nav, sel) } }
    @Test fun browse() { val vm = testAppViewModel(); check("Browse") { BrowseScreen(state, vm, nav) } }
    @Test fun project() { val vm = testAppViewModel(); check("Project") { ProjectScreen("w", state, vm, nav, sel) } }
    @Test fun task() { val vm = testAppViewModel(); check("Task") { TaskDetailScreen("c", state, vm, nav) } }
    @Test fun goals() { val vm = testAppViewModel(); check("Goals") { GoalsScreen(state, vm, nav) } }
    @Test fun goal() { val vm = testAppViewModel(); check("Goal") { GoalDetailScreen("g", state, vm, nav, sel) } }
    @Test fun settings() { val vm = testAppViewModel(); check("Settings") { SettingsScreen(state, vm, nav, vm.lock) } }
}
