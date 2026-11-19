package cc.toolcrate.telos.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.toolcrate.telos.data.FakeTime
import cc.toolcrate.telos.data.Prefs
import cc.toolcrate.telos.data.Settings
import cc.toolcrate.telos.data.testRepository
import cc.toolcrate.telos.data.testAppViewModel
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.views.Index
import cc.toolcrate.telos.ui.screens.FilterEditScreen
import cc.toolcrate.telos.ui.screens.Nav
import cc.toolcrate.telos.ui.theme.TelosTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class FilterEditorTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `shows the match count and points at errors as you type`() {
        val today = LocalDate.parse("2026-11-19")
        val state = AppState(
            Index(Snapshot(
                projects = listOf(Project(Project.INBOX_ID, "Inbox", orderKey = "0", isInbox = true, createdAt = 0, updatedAt = 0, deviceId = "d")),
                tasks = listOf(Task("a", "A", priority = 1, dueDate = today.toEpochDay(), orderKey = "a", createdAt = 0, updatedAt = 0, deviceId = "d")),
            )),
            today, Settings(),
        )
        val vm = testAppViewModel()
        compose.setContent { TelosTheme { FilterEditScreen(null, state, vm, Nav({}, {}, {}, {}, {}, {}, {}, {})) } }
        compose.onNodeWithText("1 open task matches").assertIsDisplayed()
        compose.onAllNodesWithText("today & p1")[0].performTextReplacement("today & bananas")
        compose.onNodeWithText("Not understood: “bananas” (at character 9)").assertIsDisplayed()
    }
}
