package cc.toolcrate.telos.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cc.toolcrate.telos.data.Settings
import cc.toolcrate.telos.data.testAppViewModel
import cc.toolcrate.telos.domain.model.Goal
import cc.toolcrate.telos.domain.model.Label
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.views.Index
import cc.toolcrate.telos.ui.goals.GoalsScreen
import cc.toolcrate.telos.ui.screens.InboxScreen
import cc.toolcrate.telos.ui.screens.Nav
import cc.toolcrate.telos.ui.screens.SelectionHandle
import cc.toolcrate.telos.ui.screens.SettingsScreen
import cc.toolcrate.telos.ui.screens.TodayScreen
import cc.toolcrate.telos.ui.theme.LocalPaper
import cc.toolcrate.telos.ui.theme.TelosTheme
import cc.toolcrate.telos.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Draws real screens in light and dark to app/build/screens/, to look at after UI changes
 * (the ToolCrate family look, docs/family-look.md). It asserts nothing.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h852dp-xxhdpi")
class RenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val today = LocalDate.now()
    private fun t(id: String, title: String, due: Long? = null, project: String = Project.INBOX_ID, goal: String? = null, p: Int = 4) =
        Task(id = id, title = title, dueDate = due, projectId = project, goalId = goal, priority = p, orderKey = id, createdAt = 0, updatedAt = 0, deviceId = "d")

    private val state = AppState(
        Index(Snapshot(
            projects = listOf(
                Project(Project.INBOX_ID, "Inbox", orderKey = "0", isInbox = true, createdAt = 0, updatedAt = 0, deviceId = "d"),
                Project("w", "The book", goalId = "g", orderKey = "1", createdAt = 0, updatedAt = 0, deviceId = "d"),
            ),
            goals = listOf(Goal("g", "Finish the book", why = "Because it matters to me", orderKey = "a", createdAt = 0, updatedAt = 0, deviceId = "d")),
            labels = listOf(Label("l", "phone", orderKey = "a", createdAt = 0, updatedAt = 0, deviceId = "d")),
            tasks = listOf(
                t("a", "Reply to Sam", today.minusDays(1).toEpochDay(), p = 2),
                t("b", "Draft chapter three", today.toEpochDay(), project = "w", goal = "g", p = 1),
                t("c", "Walk before work", today.toEpochDay()),
                t("d", "Buy stamps"),
                t("e", "Read the edits", today.plusDays(2).toEpochDay(), project = "w"),
            ),
        )),
        today, Settings(),
    )
    private val nav = Nav({}, {}, {}, {}, {}, {}, {}, {})
    private val sel = SelectionHandle(emptySet()) {}

    private fun draw(name: String, dark: Boolean, content: @Composable () -> Unit) {
        compose.setContent {
            TelosTheme(mode = if (dark) ThemeMode.Dark else ThemeMode.Light) {
                // As MainActivity does: a Surface in the page colour, which also sets ink as content colour.
                Surface(Modifier.fillMaxSize(), color = LocalPaper.current.page) { content() }
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name-${if (dark) "dark" else "light"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // One screen per test: the rule allows a single setContent.
    @Test fun todayLight() = draw("today", false) { TodayScreen(state, testAppViewModel(), nav, sel) }
    @Test fun todayDark() = draw("today", true) { TodayScreen(state, testAppViewModel(), nav, sel) }
    @Test fun inbox() = draw("inbox", false) { InboxScreen(state, testAppViewModel(), nav, sel) }
    @Test fun goals() = draw("goals", false) { GoalsScreen(state, testAppViewModel(), nav) }
    @Test fun settingsLight() { val vm = testAppViewModel(); draw("settings", false) { SettingsScreen(state, vm, nav, vm.lock) } }
    @Test fun settingsDark() { val vm = testAppViewModel(); draw("settings", true) { SettingsScreen(state, vm, nav, vm.lock) } }
}
