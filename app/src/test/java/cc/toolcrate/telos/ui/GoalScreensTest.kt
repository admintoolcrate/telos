package cc.toolcrate.telos.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.toolcrate.telos.data.Settings
import cc.toolcrate.telos.data.testAppViewModel
import cc.toolcrate.telos.domain.model.Goal
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.views.Index
import cc.toolcrate.telos.ui.goals.GoalsScreen
import cc.toolcrate.telos.ui.goals.ReviewScreen
import cc.toolcrate.telos.ui.screens.Nav
import cc.toolcrate.telos.ui.theme.TelosTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class GoalScreensTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.now()
    private val old = System.currentTimeMillis() - 90L * 86_400_000
    private val state = AppState(
        Index(Snapshot(
            projects = listOf(Project(Project.INBOX_ID, "Inbox", orderKey = "0", isInbox = true, createdAt = 0, updatedAt = 0, deviceId = "d")),
            goals = listOf(Goal("g", "Run a marathon", why = "To keep a long promise", orderKey = "a", createdAt = old, updatedAt = old, deviceId = "d")),
            tasks = listOf(
                Task("t1", "Buy shoes", goalId = "g", completedAt = old, orderKey = "a", createdAt = old, updatedAt = old, deviceId = "d"),
                Task("t2", "Run 5k", goalId = "g", orderKey = "b", createdAt = old, updatedAt = old, deviceId = "d"),
            ),
        )),
        today, Settings(),
    )
    private val nav = Nav({}, {}, {}, {}, {}, {}, {}, {})

    @Test
    fun `goal card shows the why, the next step and a described ring`() {
        compose.setContent { TelosTheme { GoalsScreen(state, testAppViewModel(), nav) } }
        compose.onNodeWithText("To keep a long promise").assertIsDisplayed()
        compose.onNodeWithText("Next: Run 5k").assertIsDisplayed()
        compose.onNodeWithContentDescription("1 of 2 tasks done").assertIsDisplayed()
    }

    @Test
    fun `the review names quiet goals without pressure`() {
        compose.setContent { TelosTheme { ReviewScreen(state, testAppViewModel(), nav) } }
        compose.onNodeWithText("Quiet for 30 days or more").assertIsDisplayed()
        compose.onNodeWithText("Is this still the right goal?").assertIsDisplayed()
        for (word in listOf("fail", "behind", "overdue", "streak", "missed")) {
            assertTrue(word, compose.onAllNodesWithText(word, substring = true, ignoreCase = true).fetchSemanticsNodes().isEmpty())
        }
    }
}
