package cc.toolcrate.telos.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Only backup/NetworkGate.kt may touch the network. This reads every source file in the app
 * and domain modules and fails if anything else uses networking classes.
 */
class NetworkIsolationTest {
    private val banned = listOf(
        Regex("""import java\.net\.(URL|URLConnection|HttpURLConnection|Socket|ServerSocket|DatagramSocket|InetAddress|URI\b.*toURL)$"""),
        Regex("""HttpURLConnection|HttpsURLConnection|openConnection\(|okhttp3|Socket\(|java\.net\.URL\("""),
        Regex("""import (retrofit2|io\.ktor|com\.android\.volley)"""),
    )

    @Test
    fun `only NetworkGate touches the network`() {
        val roots = listOf(File("src/main"), File("../domain/src/main"))
        assertTrue("run from the app module", roots.all { it.isDirectory })
        val offenders = roots.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension in setOf("kt", "java") && it.name != "NetworkGate.kt" }.flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    if (banned.any { it.containsMatchIn(line.trim()) }) "${f.path}:${i + 1}: ${line.trim()}" else null
                }
            }.toList()
        }
        assertEquals("Network use outside NetworkGate:\n" + offenders.joinToString("\n"), emptyList<String>(), offenders)
    }

    @Test
    fun `the gate itself is where the connection lives`() {
        val gate = File("src/main/java/cc/toolcrate/telos/backup/NetworkGate.kt").readText()
        assertTrue("HttpURLConnection" in gate && "driveEnabled" in gate && "ALLOWED_HOSTS" in gate)
    }
}
