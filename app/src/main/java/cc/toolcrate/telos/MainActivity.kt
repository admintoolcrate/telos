package cc.toolcrate.telos

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import cc.toolcrate.telos.security.LockScreen
import kotlinx.coroutines.launch
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
                val locked by vm.lock.locked.collectAsState()
                val lockOn = state?.settings?.appLock == true
                androidx.compose.runtime.LaunchedEffect(state?.settings?.secureScreen) {
                    if (state?.settings?.secureScreen == true) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                androidx.compose.material3.Surface(color = cc.toolcrate.telos.ui.theme.LocalPaper.current.page) {
                    when {
                        state == null -> {}
                        lockOn && locked -> LockScreen(vm.lock, this@MainActivity)
                        else -> TelosRoot(vm, startRoute = startRoute, openTaskId = openTask)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch { vm.lock.onForeground() }
    }

    override fun onStop() {
        super.onStop()
        vm.lock.onBackground()
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
