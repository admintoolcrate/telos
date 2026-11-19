package cc.toolcrate.telos.capture

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val pm = app.packageManager

    private fun handlers(intent: Intent) = pm.queryIntentActivities(intent.setPackage(app.packageName), PackageManager.MATCH_DEFAULT_ONLY)
        .map { it.activityInfo.name }

    @Test
    fun `share sheet and selected text open quick add`() {
        assertTrue(handlers(Intent(Intent.ACTION_SEND).setType("text/plain")).any { it.endsWith("QuickAddActivity") })
        assertTrue(handlers(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")).any { it.endsWith("QuickAddActivity") })
    }

    @Test
    fun `a shared link becomes a reading task with the link in notes`() {
        val c = QuickAddActivity.captured(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "https://example.com/essay")
        )
        assertEquals("Read example.com/essay", c.title)
        assertEquals("https://example.com/essay", c.notes)
        val selected = QuickAddActivity.captured(Intent(Intent.ACTION_PROCESS_TEXT).putExtra(Intent.EXTRA_PROCESS_TEXT, "Renew passport"))
        assertEquals("Renew passport", selected.title)
    }

    @Test
    fun `tile, widgets and shortcuts are declared`() {
        val tile = pm.getServiceInfo(ComponentName(app, QuickAddTile::class.java), 0)
        assertEquals("android.permission.BIND_QUICK_SETTINGS_TILE", tile.permission)
        val receivers = pm.queryBroadcastReceivers(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(app.packageName), 0)
            .map { it.activityInfo.name.substringAfterLast('.') }.toSet()
        assertTrue(receivers.containsAll(setOf("TodayWidgetReceiver", "AddWidgetReceiver")))
        val main = pm.getActivityInfo(ComponentName(app, cc.toolcrate.telos.MainActivity::class.java), PackageManager.GET_META_DATA)
        assertTrue(main.metaData.containsKey("android.app.shortcuts"))
    }
}
