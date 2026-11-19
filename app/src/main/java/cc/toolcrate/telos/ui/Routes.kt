package cc.toolcrate.telos.ui

import androidx.compose.runtime.Composable
import cc.toolcrate.telos.ui.goals.GoalDetailScreen
import cc.toolcrate.telos.ui.goals.GoalEditScreen
import cc.toolcrate.telos.ui.goals.GoalsScreen
import cc.toolcrate.telos.ui.goals.NextStepsScreen
import cc.toolcrate.telos.ui.goals.ReviewScreen
import cc.toolcrate.telos.ui.screens.ArchiveScreen
import cc.toolcrate.telos.ui.screens.CompletedScreen
import cc.toolcrate.telos.ui.screens.FilterScreen
import cc.toolcrate.telos.ui.screens.SearchScreen
import cc.toolcrate.telos.ui.screens.TrashScreen

/** Every screen reached from Browse, a filter, a goal or a link. */
internal fun featureRoutes(): Map<String, @Composable (RouteContext) -> Unit> = linkedMapOf(
    "filter/{id}" to { c -> FilterScreen(c.arg ?: "new", c.state, c.vm, c.nav, c.selection) },
    "search" to { c -> SearchScreen(c.state, c.vm, c.nav, c.selection) },
    "completed" to { c -> CompletedScreen(c.state, c.vm, c.nav) },
    "archive" to { c -> ArchiveScreen(c.state, c.vm, c.nav) },
    "trash" to { c -> TrashScreen(c.state, c.vm, c.nav) },
    "goals" to { c -> GoalsScreen(c.state, c.vm, c.nav) },
    "goal/{id}" to { c -> GoalDetailScreen(c.arg!!, c.state, c.vm, c.nav, c.selection) },
    "goaledit/{id}" to { c -> GoalEditScreen(c.arg, c.state, c.vm, c.nav) },
    "next" to { c -> NextStepsScreen(c.state, c.vm, c.nav) },
    "review" to { c -> ReviewScreen(c.state, c.vm, c.nav) },
)
