package cc.toolcrate.telos.capture

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import cc.toolcrate.telos.data.Prefs
import cc.toolcrate.telos.data.Repository
import cc.toolcrate.telos.domain.capture.SharedText
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.views.Index
import cc.toolcrate.telos.ui.quickadd.QuickAddPanel
import cc.toolcrate.telos.ui.quickadd.QuickAddViewModel
import cc.toolcrate.telos.ui.theme.LocalPaper
import cc.toolcrate.telos.ui.theme.TelosTheme
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import javax.inject.Inject

/**
 * Quick add over whatever you were doing: from the home-screen widget, the quick-settings tile,
 * the launcher shortcut, the share sheet (text or a link) and "Add to Telos" on selected text.
 */
@AndroidEntryPoint
class QuickAddActivity : FragmentActivity() {
    private val vm: QuickAddViewModel by viewModels()
    @Inject lateinit var repo: Repository
    @Inject lateinit var prefs: Prefs
    @Inject lateinit var lock: cc.toolcrate.telos.security.AppLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            val captured = captured(intent)
            vm.start(text = captured.title, notes = captured.notes)
        }
        setContent {
            val settings by prefs.settings.collectAsState(initial = null)
            val snapshot by repo.snapshot.collectAsState(initial = Snapshot())
            val index = remember(snapshot) { Index(snapshot) }
            TelosTheme(mode = settings?.theme ?: cc.toolcrate.telos.ui.theme.ThemeMode.System) {
                val paper = LocalPaper.current
                // Tapping outside the card closes it.
                Box(
                    Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .imePadding()
                            .background(paper.card, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text("Add to Telos", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp).semantics { heading() })
                        QuickAddPanel(
                            vm = vm,
                            // While the app is locked, capture still works but doesn't list your projects.
                            projects = listOfNotNull(index.projects[Project.INBOX_ID]) +
                                if (settings?.appLock == true && lock.locked.collectAsState().value) emptyList() else index.projectTree().map { it.first },
                            today = LocalDate.now(),
                            onSaved = { t ->
                                val where = index.projects[t.projectId]?.name ?: "Inbox"
                                Toast.makeText(this@QuickAddActivity, "Added to $where", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                        )
                    }
                }
                LaunchedEffect(Unit) { repo.ensureInbox() }
            }
        }
    }

    companion object {
        /** What the launching app handed over, as a title and notes. Never fetched. */
        fun captured(intent: Intent?): cc.toolcrate.telos.domain.capture.Captured = when (intent?.action) {
            Intent.ACTION_SEND -> SharedText.capture(
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE),
            )
            Intent.ACTION_PROCESS_TEXT -> SharedText.capture(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString())
            else -> SharedText.capture(null)
        }
    }
}
