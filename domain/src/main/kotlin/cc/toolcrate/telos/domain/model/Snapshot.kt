package cc.toolcrate.telos.domain.model

import kotlinx.serialization.Serializable

/**
 * Every record, tombstones included. The unit of export, backup and merge, and (filtered) the
 * in-memory model the views and filters work on.
 */
@Serializable
data class Snapshot(
    val projects: List<Project> = emptyList(),
    val sections: List<Section> = emptyList(),
    val tasks: List<Task> = emptyList(),
    val labels: List<Label> = emptyList(),
    val taskLabels: List<TaskLabel> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val completions: List<Completion> = emptyList(),
    val filters: List<SavedFilter> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val reviews: List<ReviewEntry> = emptyList(),
) {
    val recordCount: Int
        get() = projects.size + sections.size + tasks.size + labels.size + taskLabels.size +
            reminders.size + completions.size + filters.size + goals.size + reviews.size
}
