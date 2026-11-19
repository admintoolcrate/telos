package cc.toolcrate.telos

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import cc.toolcrate.telos.ui.AppViewModel
import cc.toolcrate.telos.ui.TelosRoot
import cc.toolcrate.telos.ui.theme.TelosTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()
    private var openTask by mutableStateOf<String?>(null)
    private var startRoute: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) readIntent(intent)
        setContent {
            val state by vm.state.collectAsState()
            TelosTheme(
                mode = state?.settings?.theme ?: cc.toolcrate.telos.ui.theme.ThemeMode.System,
                reduceMotionOverride = state?.settings?.reduceMotion ?: false,
            ) {
                TelosRoot(vm, startRoute = startRoute, openTaskId = openTask)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_TASK)?.let { openTask = it }
        intent.getStringExtra(EXTRA_ROUTE)?.let { startRoute = it }
    }

    companion object {
        const val EXTRA_TASK = "cc.toolcrate.telos.TASK"
        const val EXTRA_ROUTE = "cc.toolcrate.telos.ROUTE"
    }
}
