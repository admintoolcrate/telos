package cc.toolcrate.telos.domain.io

import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.model.Syncable

/** How many records each side contributed. */
data class MergeResult(val snapshot: Snapshot, val fromIncoming: Int, val fromLocal: Int)

/**
 * Merges two copies record by record: the later `updatedAt` wins, ties broken by `deviceId`
 * (so every device picks the same winner). Tombstones are records too, so a deletion on one
 * device isn't undone by an older copy elsewhere. Commutative and idempotent.
 */
object Merge {
    fun merge(local: Snapshot, incoming: Snapshot): MergeResult {
        var fromIncoming = 0
        fun <T : Syncable> pick(a: List<T>, b: List<T>): List<T> {
            val out = LinkedHashMap<String, T>()
            a.forEach { out[it.id] = it }
            b.forEach { other ->
                val mine = out[other.id]
                if (mine == null || wins(other, mine)) {
                    fromIncoming++
                    out[other.id] = other
                }
            }
            return out.values.toList()
        }
        val s = Snapshot(
            projects = pick(local.projects, incoming.projects),
            sections = pick(local.sections, incoming.sections),
            tasks = pick(local.tasks, incoming.tasks),
            labels = pick(local.labels, incoming.labels),
            taskLabels = pick(local.taskLabels, incoming.taskLabels),
            reminders = pick(local.reminders, incoming.reminders),
            completions = pick(local.completions, incoming.completions),
            filters = pick(local.filters, incoming.filters),
            goals = pick(local.goals, incoming.goals),
            reviews = pick(local.reviews, incoming.reviews),
        )
        return MergeResult(s, fromIncoming, s.recordCount - fromIncoming)
    }

    /** Whether [a] should replace [b]. */
    fun wins(a: Syncable, b: Syncable): Boolean = when {
        a.updatedAt != b.updatedAt -> a.updatedAt > b.updatedAt
        else -> a.deviceId > b.deviceId
    }
}
