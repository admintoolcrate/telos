package cc.toolcrate.telos.data

import cc.toolcrate.telos.domain.model.Goal
import cc.toolcrate.telos.domain.model.GoalStatus
import cc.toolcrate.telos.domain.model.OrderKeys
import cc.toolcrate.telos.domain.model.ReviewEntry
import javax.inject.Inject
import javax.inject.Singleton

/** Writes to goals and review notes. Same rules as [Repository]: stamp every write, never hard-delete. */
@Singleton
class GoalRepository @Inject constructor(
    private val db: TelosDatabase,
    private val device: DeviceIdentity,
) {
    suspend fun add(title: String, why: String = "", targetDate: Long? = null): Goal {
        val s = device.stamp()
        val now = s.now()
        val goal = Goal(
            id = s.newId(), title = title.trim(), why = why.trim(), targetDate = targetDate,
            orderKey = OrderKeys.between(db.goals().everything().filter { it.deletedAt == null }.maxOfOrNull { it.orderKey }, null),
            createdAt = now, updatedAt = now, deviceId = s.deviceId,
        )
        db.goals().upsert(goal)
        return goal
    }

    suspend fun save(goal: Goal) {
        val s = device.stamp()
        db.goals().upsert(goal.copy(updatedAt = s.now(), deviceId = s.deviceId))
    }

    suspend fun get(id: String): Goal? = db.goals().get(id)

    suspend fun setStatus(id: String, status: GoalStatus) {
        val g = get(id) ?: return
        val now = device.stamp().now()
        save(g.copy(status = status, achievedAt = if (status == GoalStatus.ACHIEVED) (g.achievedAt ?: now) else null))
    }

    suspend fun archive(id: String, archived: Boolean) {
        val g = get(id) ?: return
        save(g.copy(archivedAt = if (archived) device.stamp().now() else null))
    }

    /** Trashes a goal; its projects and tasks stay, just unlinked from it in views. */
    suspend fun trash(id: String) {
        val g = get(id) ?: return
        save(g.copy(trashedAt = device.stamp().now()))
    }

    suspend fun restore(id: String) {
        val g = get(id) ?: return
        save(g.copy(trashedAt = null))
    }

    suspend fun addReview(quarter: String, note: String): ReviewEntry {
        val s = device.stamp()
        val now = s.now()
        val entry = ReviewEntry(id = s.newId(), quarter = quarter, note = note.trim(), createdAt = now, updatedAt = now, deviceId = s.deviceId)
        db.reviews().upsert(entry)
        return entry
    }
}
