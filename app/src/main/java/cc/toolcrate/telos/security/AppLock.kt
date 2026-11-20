package cc.toolcrate.telos.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import cc.toolcrate.telos.data.Prefs
import cc.toolcrate.telos.data.TimeSource
import cc.toolcrate.telos.domain.backup.KdfParams
import cc.toolcrate.telos.domain.security.Pin
import cc.toolcrate.telos.domain.security.PinRecord
import cc.toolcrate.telos.domain.security.pinBackoffMillis
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PinResult {
    data object Ok : PinResult
    data object Wrong : PinResult
    data class Wait(val millis: Long) : PinResult
}

/**
 * The app lock. Locks when Telos has been in the background longer than the chosen timeout;
 * unlocks with the phone's biometrics or screen lock, or with a Telos PIN (hashed with
 * Argon2id, slowed down after wrong guesses).
 */
@Singleton
class AppLock @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: Prefs,
    private val time: TimeSource,
) {
    private val store = context.getSharedPreferences("lock", Context.MODE_PRIVATE)
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundedAt: Long? = null
    private var firstStart = true

    suspend fun onForeground() {
        val s = prefs.current()
        if (!s.appLock) { _locked.value = false; firstStart = false; return }
        val away = backgroundedAt?.let { time.now() - it }
        if (firstStart || away == null || away >= s.lockTimeoutSeconds * 1000L) _locked.value = true
        firstStart = false
    }

    fun onBackground() { backgroundedAt = time.now() }

    fun unlock() { _locked.value = false }

    fun lockNow() { _locked.value = true }

    /** Whether the phone can confirm it's you (fingerprint, face, or its own PIN/pattern). */
    fun deviceCanAuthenticate(): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    fun promptDevice(activity: FragmentActivity, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { unlock(); onSuccess() }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Telos")
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build()
        )
    }

    fun hasPin(): Boolean = store.contains("pin")

    suspend fun setPin(pin: String) = withContext(Dispatchers.Default) {
        val r = Pin.create(pin)
        store.edit().putString("pin", r.hash).putString("kdf", Json.encodeToString(KdfParams.serializer(), r.kdf)).putInt("fails", 0).putLong("until", 0).apply()
    }

    fun clearPin() = store.edit().remove("pin").remove("kdf").putInt("fails", 0).apply()

    suspend fun checkPin(pin: String): PinResult = withContext(Dispatchers.Default) {
        val wait = store.getLong("until", 0) - time.now()
        if (wait > 0) return@withContext PinResult.Wait(wait)
        val record = PinRecord(store.getString("pin", null) ?: return@withContext PinResult.Wrong,
            Json.decodeFromString(KdfParams.serializer(), store.getString("kdf", null) ?: return@withContext PinResult.Wrong))
        if (Pin.verify(pin, record)) {
            store.edit().putInt("fails", 0).putLong("until", 0).apply()
            unlock()
            PinResult.Ok
        } else {
            val fails = store.getInt("fails", 0) + 1
            val backoff = pinBackoffMillis(fails)
            store.edit().putInt("fails", fails).putLong("until", time.now() + backoff).apply()
            if (backoff > 0) PinResult.Wait(backoff) else PinResult.Wrong
        }
    }
}
