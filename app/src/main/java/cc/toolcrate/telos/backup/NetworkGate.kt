package cc.toolcrate.telos.backup

import cc.toolcrate.telos.data.Prefs
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

class NetworkBlocked(message: String) : IOException(message)

class HttpError(val code: Int, message: String) : IOException(message)

/**
 * The only door to the network in Telos. It opens only while Google Drive backup is switched on,
 * and only to Google's Drive and OAuth hosts. NetworkIsolationTest fails the build if any other
 * file touches networking classes.
 */
@Singleton
class NetworkGate @Inject constructor(private val prefs: Prefs) {
    suspend fun request(method: String, url: String, headers: Map<String, String> = emptyMap(), body: ByteArray? = null): String {
        val s = prefs.current()
        if (!s.driveEnabled) throw NetworkBlocked("Drive backup is off, so Telos doesn't connect.")
        val u = URL(url)
        if (u.protocol != "https" || u.host !in ALLOWED_HOSTS) throw NetworkBlocked("Not an allowed host: ${u.host}")
        val c = u.openConnection() as HttpURLConnection
        try {
            c.requestMethod = if (method == "PATCH") "POST" else method
            if (method == "PATCH") c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            c.connectTimeout = 20_000
            c.readTimeout = 60_000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (code !in 200..299) throw HttpError(code, "Drive answered $code")
            return text
        } finally {
            c.disconnect()
        }
    }

    companion object {
        val ALLOWED_HOSTS = setOf("www.googleapis.com", "oauth2.googleapis.com")
    }
}
