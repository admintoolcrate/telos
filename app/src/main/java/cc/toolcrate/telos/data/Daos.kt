package cc.toolcrate.telos.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
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
import kotlinx.coroutines.flow.Flow

// Reads skip tombstones (deletedAt). Trash and archive are filtered by the repositories, which
// need both sides. Writes are upserts: rows are never hard-deleted (see CLAUDE.md).

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects WHERE deletedAt IS NULL ORDER BY orderKey")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE deletedAt IS NULL ORDER BY orderKey")
    suspend fun all(): List<Project>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): Project?

    @Query("SELECT * FROM projects")
    suspend fun everything(): List<Project>

    @Upsert
    suspend fun upsert(vararg p: Project)

    @Upsert
    suspend fun upsertAll(p: List<Project>)
}

@Dao
interface SectionDao {
    @Query("SELECT * FROM sections WHERE deletedAt IS NULL ORDER BY orderKey")
    fun observeAll(): Flow<List<Section>>

    @Query("SELECT * FROM sections WHERE projectId = :projectId AND deletedAt IS NULL ORDER BY orderKey")
    suspend fun forProject(projectId: String): List<Section>

    @Query("SELECT * FROM sections WHERE id = :id")
    suspend fun get(id: String): Section?

    @Query("SELECT * FROM sections")
    suspend fun everything(): List<Section>

    @Upsert
    suspend fun upsert(vararg s: Section)

    @Upsert
    suspend fun upsertAll(s: List<Section>)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL")
    suspend fun all(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: String): Task?

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: String): Flow<Task?>

    @Query("SELECT * FROM tasks WHERE parentId = :parentId AND deletedAt IS NULL ORDER BY orderKey")
    suspend fun children(parentId: String): List<Task>

    @Query("SELECT * FROM tasks")
    suspend fun everything(): List<Task>

    @Query("SELECT * FROM tasks WHERE trashedAt IS NOT NULL AND trashedAt < :before AND deletedAt IS NULL")
    suspend fun trashedBefore(before: Long): List<Task>

    @Query(
        """SELECT tasks.* FROM tasks JOIN tasks_fts ON tasks.rowid = tasks_fts.rowid
           WHERE tasks_fts MATCH :query AND tasks.deletedAt IS NULL AND tasks.trashedAt IS NULL"""
    )
    suspend fun search(query: String): List<Task>

    @Upsert
    suspend fun upsert(vararg t: Task)

    @Upsert
    suspend fun upsertAll(t: List<Task>)
}

@Dao
interface LabelDao {
    @Query("SELECT * FROM labels WHERE deletedAt IS NULL ORDER BY orderKey")
    fun observeAll(): Flow<List<Label>>

    @Query("SELECT * FROM labels WHERE deletedAt IS NULL ORDER BY orderKey")
    suspend fun all(): List<Label>

    @Query("SELECT * FROM labels")
    suspend fun everything(): List<Label>

    @Query("SELECT * FROM task_labels WHERE deletedAt IS NULL")
    fun observeLinks(): Flow<List<TaskLabel>>

    @Query("SELECT * FROM task_labels WHERE taskId = :taskId AND deletedAt IS NULL")
    suspend fun linksFor(taskId: String): List<TaskLabel>

    @Query("SELECT * FROM task_labels")
    suspend fun everyLink(): List<TaskLabel>

    @Upsert
    suspend fun upsert(vararg l: Label)

    @Upsert
    suspend fun upsertAll(l: List<Label>)

    @Upsert
    suspend fun upsertLinks(l: List<TaskLabel>)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE deletedAt IS NULL")
    suspend fun all(): List<Reminder>

    @Query("SELECT * FROM reminders WHERE taskId = :taskId AND deletedAt IS NULL")
    suspend fun forTask(taskId: String): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: String): Reminder?

    @Query("SELECT * FROM reminders")
    suspend fun everything(): List<Reminder>

    @Upsert
    suspend fun upsert(vararg r: Reminder)

    @Upsert
    suspend fun upsertAll(r: List<Reminder>)
}

@Dao
interface CompletionDao {
    @Query("SELECT * FROM completions WHERE deletedAt IS NULL ORDER BY completedAt DESC")
    fun observeAll(): Flow<List<Completion>>

    @Query("SELECT * FROM completions")
    suspend fun everything(): List<Completion>

    @Upsert
    suspend fun upsert(vararg c: Completion)

    @Upsert
    suspend fun upsertAll(c: List<Completion>)
}

@Dao
interface FilterDao {
    @Query("SELECT * FROM filters WHERE deletedAt IS NULL ORDER BY orderKey")
    fun observeAll(): Flow<List<SavedFilter>>

    @Query("SELECT * FROM filters WHERE id = :id")
    suspend fun get(id: String): SavedFilter?

    @Query("SELECT * FROM filters")
    suspend fun everything(): List<SavedFilter>

    @Upsert
    suspend fun upsert(vararg f: SavedFilter)

    @Upsert
    suspend fun upsertAll(f: List<SavedFilter>)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE deletedAt IS NULL ORDER BY orderKey")
    fun observeAll(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE id = :id")
    suspend fun get(id: String): Goal?

    @Query("SELECT * FROM goals")
    suspend fun everything(): List<Goal>

    @Upsert
    suspend fun upsert(vararg g: Goal)

    @Upsert
    suspend fun upsertAll(g: List<Goal>)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_entries WHERE deletedAt IS NULL ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ReviewEntry>>

    @Query("SELECT * FROM review_entries")
    suspend fun everything(): List<ReviewEntry>

    @Upsert
    suspend fun upsert(vararg r: ReviewEntry)

    @Upsert
    suspend fun upsertAll(r: List<ReviewEntry>)
}
