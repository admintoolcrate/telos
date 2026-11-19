package cc.toolcrate.telos.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import cc.toolcrate.telos.domain.backup.BackupKey
import cc.toolcrate.telos.domain.backup.KdfParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the key derived from the backup passphrase (never the passphrase itself), wrapped by a
 * key in the Android Keystore that can't leave this phone, so scheduled backups can run
 * without asking each time.
 */
@Singleton
class KeyVault @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()

    fun load(): BackupKey? = runCatching {
        val iv = unb64.decode(prefs.getString("iv", null) ?: return null)
        val data = unb64.decode(prefs.getString("key", null) ?: return null)
        val kdf = Json.decodeFromString(KdfParams.serializer(), prefs.getString("kdf", null) ?: return null)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, iv))
        BackupKey(cipher.doFinal(data), kdf)
    }.getOrNull()

    fun save(key: BackupKey) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        prefs.edit()
            .putString("iv", b64.encodeToString(cipher.iv))
            .putString("key", b64.encodeToString(cipher.doFinal(key.bytes)))
            .putString("kdf", Json.encodeToString(KdfParams.serializer(), key.kdf))
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()

    fun hasKey(): Boolean = prefs.contains("key")

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val ALIAS = "telos-backup-wrap"
    }
}
