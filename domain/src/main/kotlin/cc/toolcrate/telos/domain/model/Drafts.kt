package cc.toolcrate.telos.domain.model

/** A reminder to attach to a new task. */
sealed interface ReminderSpec {
    data class Before(val minutes: Int) : ReminderSpec
    data class At(val epochDay: Long, val minuteOfDay: Int) : ReminderSpec
}

/** A task about to be created, from quick add, the share sheet or an import. */
data class TaskDraft(
    val title: String,
    val notes: String = "",
    val projectId: String = Project.INBOX_ID,
    val sectionId: String? = null,
    val parentId: String? = null,
    val goalId: String? = null,
    val priority: Int = 4,
    val dueDate: Long? = null,
    val dueMinute: Int? = null,
    val recurrence: String? = null,
    /** Label names; missing labels are created. */
    val labels: List<String> = emptyList(),
    val reminders: List<ReminderSpec> = emptyList(),
)
