package cc.toolcrate.telos.data

import androidx.room.Database
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import cc.toolcrate.telos.domain.model.Completion
import cc.toolcrate.telos.domain.model.Goal
import cc.toolcrate.telos.domain.model.Label
import cc.toolcrate.telos.domain.model.Project
import cc.toolcrate.telos.domain.model.Reminder
import cc.toolcrate.telos.domain.model.ReviewEntry
import cc.toolcrate.telos.domain.model.SavedFilter
import cc.toolcrate.telos.domain.model.Section
import cc.toolcrate.telos.domain.model.Task
import cc.toolcrate.telos.domain.model.TaskLabel

/** Full-text index over task titles and notes, kept in step with `tasks` by Room's triggers. */
@Fts4(contentEntity = Task::class)
@Entity(tableName = "tasks_fts")
data class TaskFts(val title: String, val notes: String)

/** Alarms currently registered with AlarmManager, so a reconcile can cancel stale ones. Device-local. */
@Entity(tableName = "scheduled_alarms")
data class ScheduledAlarm(
    @PrimaryKey val reminderId: String,
    val requestCode: Int,
    val triggerAt: Long,
)

@Database(
    version = TelosDatabase.VERSION,
    entities = [
        Project::class, Section::class, Task::class, Label::class, TaskLabel::class, Reminder::class,
        Completion::class, SavedFilter::class, Goal::class, ReviewEntry::class, TaskFts::class,
        ScheduledAlarm::class,
    ],
    exportSchema = true,
)
abstract class TelosDatabase : RoomDatabase() {
    abstract fun projects(): ProjectDao
    abstract fun sections(): SectionDao
    abstract fun tasks(): TaskDao
    abstract fun labels(): LabelDao
    abstract fun reminders(): ReminderDao
    abstract fun completions(): CompletionDao
    abstract fun filters(): FilterDao
    abstract fun goals(): GoalDao
    abstract fun reviews(): ReviewDao
    abstract fun alarms(): AlarmDao

    companion object {
        const val VERSION = 1
        const val NAME = "telos.db"

        /**
         * Every schema change adds its migration here, plus a test in MigrationTest.
         * (Room's AutoMigration is fine for additive changes; list it on @Database instead.)
         */
        val MIGRATIONS: Array<Migration> = arrayOf()
    }
}

@androidx.room.Dao
interface AlarmDao {
    @androidx.room.Query("SELECT * FROM scheduled_alarms")
    suspend fun all(): List<ScheduledAlarm>

    @androidx.room.Upsert
    suspend fun upsertAll(a: List<ScheduledAlarm>)

    @androidx.room.Query("DELETE FROM scheduled_alarms WHERE reminderId IN (:ids)")
    suspend fun delete(ids: List<String>)
}
