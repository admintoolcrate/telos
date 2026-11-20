package cc.toolcrate.telos.data

import android.content.Context
import cc.toolcrate.telos.domain.ops.Stamp
import java.util.UUID

/** A random id for this install, used only to break ties when merging backups. Never sent anywhere. */
class DeviceIdentity(context: Context, private val time: TimeSource) {
    private val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)

    val deviceId: String = prefs.getString(KEY, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY, it).apply()
    }

    fun stamp(): Stamp = Stamp(time.clock(), deviceId)

    private companion object {
        const val KEY = "id"
    }
}
