package cc.toolcrate.telos.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import cc.toolcrate.telos.ChangeHook
import cc.toolcrate.telos.MainActivity
import cc.toolcrate.telos.R
import cc.toolcrate.telos.capture.QuickAddActivity
import cc.toolcrate.telos.data.Repository
import cc.toolcrate.telos.data.TelosDatabase
import cc.toolcrate.telos.data.TimeSource
import cc.toolcrate.telos.domain.capture.WidgetRow
import cc.toolcrate.telos.domain.capture.WidgetRows
import cc.toolcrate.telos.domain.model.Snapshot
import cc.toolcrate.telos.domain.views.Index
import cc.toolcrate.telos.ui.Format
import cc.toolcrate.telos.ui.theme.DarkPaper
import cc.toolcrate.telos.ui.theme.LightPaper
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntry {
    fun db(): TelosDatabase
    fun repo(): Repository
    fun time(): TimeSource
}

private fun entry(context: Context) = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntry::class.java)

private val ink = ColorProvider(day = LightPaper.ink, night = DarkPaper.ink)
private val muted = ColorProvider(day = LightPaper.muted, night = DarkPaper.muted)
private val page = ColorProvider(day = LightPaper.page, night = DarkPaper.page)
private val accent = ColorProvider(day = LightPaper.accent, night = DarkPaper.accent)

private fun priorityInk(p: Int) = when (p) {
    1 -> ColorProvider(day = LightPaper.p1, night = DarkPaper.p1)
    2 -> ColorProvider(day = LightPaper.p2, night = DarkPaper.p2)
    3 -> ColorProvider(day = LightPaper.p3, night = DarkPaper.p3)
    else -> muted
}

/** Today's tasks (earlier ones first), with a tick to complete each. Private tasks show their cover title. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val e = entry(context)
        val db = e.db()
        val snapshot = Snapshot(
            projects = db.projects().all(), sections = db.sections().everything(), tasks = db.tasks().all(),
            labels = db.labels().all(), reminders = db.reminders().all(),
        )
        val today = e.time().today()
        val rows = WidgetRows.today(Index(snapshot), today, timeText = Format::time)
        provideContent { TodayContent(context, rows, Format.longDay(today)) }
    }
}

@androidx.compose.runtime.Composable
private fun TodayContent(context: Context, rows: List<WidgetRow>, dateText: String) {
    Column(GlanceModifier.fillMaxSize().background(page).cornerRadius(20.dp).padding(12.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "today")))) {
                Text("Today", style = TextStyle(color = ink, fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium))
                Text(dateText, style = TextStyle(color = muted, fontSize = 12.sp))
            }
            Image(
                ImageProvider(R.drawable.ic_widget_add), contentDescription = "Add task",
                modifier = GlanceModifier.size(36.dp).clickable(actionStartActivity(Intent(context, QuickAddActivity::class.java))),
            )
        }
        Spacer(GlanceModifier.size(6.dp))
        if (rows.isEmpty()) {
            Text("Nothing scheduled for today.", style = TextStyle(color = muted, fontSize = 14.sp, fontFamily = FontFamily.Serif))
        } else {
            LazyColumn {
                items(rows, itemId = { it.id.hashCode().toLong() }) { row ->
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            GlanceModifier.size(32.dp)
                                .clickable(actionRunCallback<CompleteTask>(actionParametersOf(TaskKey to row.id)))
                                .semantics { contentDescription = "Complete ${row.title}" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("○", style = TextStyle(color = priorityInk(row.priority), fontSize = 20.sp))
                        }
                        Spacer(GlanceModifier.width(4.dp))
                        Column(
                            GlanceModifier.defaultWeight().clickable(
                                actionStartActivity(Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TASK, row.id).setAction("open-${row.id}"))
                            ),
                        ) {
                            Text(row.title, style = TextStyle(color = ink, fontSize = 15.sp), maxLines = 2)
                            val meta = listOfNotNull(if (row.earlier) "Earlier" else null, row.meta, if (row.priority < 4) "P${row.priority}" else null)
                            if (meta.isNotEmpty()) Text(meta.joinToString(" · "), style = TextStyle(color = muted, fontSize = 12.sp))
                        }
                    }
                }
            }
        }
    }
}

private val TaskKey = ActionParameters.Key<String>("taskId")

class CompleteTask : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskKey] ?: return
        entry(context).repo().complete(id)
        TodayWidget().updateAll(context)
    }
}

/** One tap to add a task. */
class AddWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(
                GlanceModifier.fillMaxSize().background(page).cornerRadius(20.dp)
                    .clickable(actionStartActivity(Intent(context, QuickAddActivity::class.java))),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(ImageProvider(R.drawable.ic_widget_add), contentDescription = "Add task", modifier = GlanceModifier.size(36.dp))
                    Text("Add", style = TextStyle(color = accent, fontSize = 12.sp))
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

class AddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AddWidget()
}

/** Keeps the Today widget current whenever tasks change. */
@Module
@InstallIn(SingletonComponent::class)
object WidgetModule {
    @Provides
    @IntoSet
    fun refreshToday(@ApplicationContext context: Context): ChangeHook = ChangeHook { TodayWidget().updateAll(context) }
}

