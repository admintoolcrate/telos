package cc.toolcrate.telos.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Argon2id settings, stored in the backup's header so a file always says how to open itself. */
@Serializable
data class KdfParams(
    val alg: String = "argon2id",
    /** Memory in KiB. */
    val m: Int = 65_536,
    val t: Int = 3,
    val p: Int = 1,
    val salt: String,
)

@Serializable
data class Header(
    val format: String = FORMAT,
    val version: Int = 1,
    val kdf: KdfParams,
    val cipher: String = "AES-256-GCM",
    val nonce: String,
    val createdAt: Long,
) {
    companion object {
        const val FORMAT = "telos-backup"
    }
}

/** A key derived from the passphrase, with the parameters that made it. Never written to the backup. */
class BackupKey(val bytes: ByteArray, val kdf: KdfParams)

class WrongPassphrase : Exception("That passphrase doesn't open this backup, or the file was changed.")

/**
 * Passphrase-encrypted backups. The file is one header line (JSON, plain) and one line of
 * base64 ciphertext: AES-256-GCM over the gzipped export, with the header line as associated
 * data, so changing any byte of either makes it refuse to open. The key comes from the
 * passphrase through Argon2id. Without the passphrase, nobody, including us, can read it.
 */
object Envelope {
    private val random = SecureRandom()
    private val b64 = Base64.getEncoder()
    private val unb64 = Base64.getDecoder()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun newSalt(): String = b64.encodeToString(ByteArray(16).also(random::nextBytes))

    fun deriveKey(passphrase: CharArray, params: KdfParams = KdfParams(salt = newSalt())): BackupKey {
        require(params.alg == "argon2id") { "Unsupported key derivation: ${params.alg}" }
        val gen = Argon2BytesGenerator()
        gen.init(
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(unb64.decode(params.salt))
                .withMemoryAsKB(params.m).withIterations(params.t).withParallelism(params.p)
                .build()
        )
        val out = ByteArray(32)
        gen.generateBytes(passphrase, out)
        return BackupKey(out, params)
    }

    fun seal(plain: String, key: BackupKey, createdAt: Long): String {
        val nonce = ByteArray(12).also(random::nextBytes)
        val header = json.encodeToString(Header.serializer(), Header(kdf = key.kdf, nonce = b64.encodeToString(nonce), createdAt = createdAt))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.bytes, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(header.toByteArray(Charsets.UTF_8))
        val data = cipher.doFinal(gzip(plain.toByteArray(Charsets.UTF_8)))
        return header + "\n" + b64.encodeToString(data) + "\n"
    }

    fun header(file: String): Header? = runCatching {
        json.decodeFromString(Header.serializer(), file.lineSequence().first()).takeIf { it.format == Header.FORMAT }
    }.getOrNull()

    fun looksLike(file: String) = header(file) != null

    /** Opens [file] with a passphrase. Throws [WrongPassphrase] if it doesn't fit or the file was altered. */
    fun open(file: String, passphrase: CharArray): String {
        val h = header(file) ?: throw IllegalArgumentException("Not a Telos backup")
        return open(file, deriveKey(passphrase, h.kdf))
    }

    fun open(file: String, key: BackupKey): String {
        val lines = file.lines()
        val headerLine = lines.first()
        val h = header(file) ?: throw IllegalArgumentException("Not a Telos backup")
        val data = runCatching { unb64.decode(lines.getOrElse(1) { "" }.trim()) }.getOrElse { throw WrongPassphrase() }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.bytes, "AES"), GCMParameterSpec(128, unb64.decode(h.nonce)))
        cipher.updateAAD(headerLine.toByteArray(Charsets.UTF_8))
        val plain = try {
            cipher.doFinal(data)
        } catch (e: AEADBadTagException) {
            throw WrongPassphrase()
        }
        return gunzip(plain).toString(Charsets.UTF_8)
    }

    private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(b) } }.toByteArray()
    private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}
