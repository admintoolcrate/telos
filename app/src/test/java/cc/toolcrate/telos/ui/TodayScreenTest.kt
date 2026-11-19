package cc.toolcrate.telos.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.toolcrate.telos.data.Settings
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.views.Index
import cc.toolcrate.telos.ui.screens.Nav
import cc.toolcrate.telos.ui.screens.SelectionHandle
import cc.toolcrate.telos.ui.screens.TodayScreen
import cc.toolcrate.telos.ui.theme.TelosTheme
import cc.toolcrate.telos.data.FakeTime
import cc.toolcrate.telos.data.Prefs
import cc.toolcrate.telos.data.testRepository
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TodayScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.parse("2026-11-19")

    private fun task(id: String, title: String, due: LocalDate, p: Int = 4) = Task(
        id = id, title = title, dueDate = due.toEpochDay(), priority = p, orderKey = id,
        createdAt = 0, updatedAt = 0, deviceId = "d",
    )

    private val state = AppState(
        index = Index(
            Snapshot(
                projects = listOf(Project(Project.INBOX_ID, "Inbox", orderKey = "0", isInbox = true, createdAt = 0, updatedAt = 0, deviceId = "d")),
                tasks = listOf(task("a", "File taxes", today.minusDays(4), p = 1), task("b", "Water plants", today)),
            )
        ),
        today = today,
        settings = Settings(),
    )

    private val vm = AppViewModel(testRepository(time = FakeTime()), Prefs(ApplicationProvider.getApplicationContext()), FakeTime())
    private val nav = Nav({}, {}, {}, {}, {}, {}, {}, {})

    private fun show(fontScale: Float = 1f) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                TelosTheme { TodayScreen(state, vm, nav, SelectionHandle(emptySet()) {}) }
            }
        }
    }

    @Test
    fun `overdue work sits under a neutral Earlier heading`() {
        show()
        compose.onNodeWithText("Earlier").assertIsDisplayed()
        compose.onNodeWithText("Move to today").assertIsDisplayed()
        // No guilt copy anywhere.
        for (word in listOf("Overdue", "missed", "late")) {
            assertTrue(compose.onAllNodesWithText(word, substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        }
    }

    @Test
    fun `rows describe themselves to TalkBack with actions`() {
        show()
        val rows = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNodes()
        assertTrue(rows.size >= 2)
        val descriptions = rows.flatMap { it.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() } }
        assertTrue(descriptions.any { it.startsWith("File taxes") && "Priority 1" in it })
        val actions = rows.first().config[SemanticsActions.CustomActions].map { it.label }
        assertTrue("Complete" in actions)
    }

    @Test
    fun `large text still shows every task`() {
        show(fontScale = 2f)
        compose.onNodeWithText("Earlier").assertIsDisplayed()
        assertTrue(compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNodes().size >= 2)
    }
}
