package cc.toolcrate.telos.data

import cc.toolcrate.telos.domain.model.Goal
import cc.toolcrate.telos.domain.model.OrderKeys
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
}
