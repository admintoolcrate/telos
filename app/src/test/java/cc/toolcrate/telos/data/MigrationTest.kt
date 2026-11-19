package cc.toolcrate.telos.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Every schema version must open and migrate to the current one. When the version is bumped,
 * add a migration to [TelosDatabase.MIGRATIONS] and a test here that inserts rows at the old
 * version and checks them after migrating.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TelosDatabase::class.java)

    @Test
    fun `every version has an exported schema`() {
        val dir = File("schemas/${TelosDatabase::class.java.name}")
        for (v in 1..TelosDatabase.VERSION) assertTrue("missing schema $v", File(dir, "$v.json").exists())
    }

    @Test
    fun `oldest schema migrates to the current one`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """INSERT INTO projects (id, name, viewMode, orderKey, isInbox, createdAt, updatedAt, deviceId)
                   VALUES ('inbox', 'Inbox', 'LIST', '0', 1, 0, 0, 'd')"""
            )
            db.execSQL(
                """INSERT INTO tasks (id, title, notes, projectId, priority, orderKey, collapsed, pinnedNext, createdAt, updatedAt, deviceId)
                   VALUES ('t', 'Kept', '', 'inbox', 4, 'V', 0, 0, 0, 0, 'd')"""
            )
        }
        helper.runMigrationsAndValidate(DB, TelosDatabase.VERSION, true, *TelosDatabase.MIGRATIONS).use { db ->
            db.query("SELECT title FROM tasks WHERE id = 't'").use { c ->
                assertTrue(c.moveToFirst() && c.getString(0) == "Kept")
            }
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
