package cc.toolcrate.telos.ui

import androidx.compose.runtime.Composable
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
)
