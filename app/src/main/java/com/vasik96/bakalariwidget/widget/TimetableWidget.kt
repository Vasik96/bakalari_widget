package com.vasik96.bakalariwidget.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.unit.ColorProvider
import com.vasik96.bakalariwidget.R
import com.vasik96.bakalariwidget.data.Cached
import com.vasik96.bakalariwidget.data.DaySnapshot
import com.vasik96.bakalariwidget.data.Lesson
import com.vasik96.bakalariwidget.data.LessonStatus
import com.vasik96.bakalariwidget.data.Repo
import com.vasik96.bakalariwidget.data.Snapshot
import com.vasik96.bakalariwidget.data.buildSnapshot
import com.vasik96.bakalariwidget.MainActivity

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)
private val LARGE = DpSize(250.dp, 250.dp)

private enum class LessonPosition { Single, First, Middle, Last }

class TimetableWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cached = Repo(context).loadCache()
        provideContent { GlanceTheme { TimetableContent(cached) } }
    }
}

class TimetableWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TimetableWidget()
}

class RefreshCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        SyncWorker.syncNow(context)
    }
}

@Composable
private fun TimetableContent(c: Cached) {
    val size = LocalSize.current
    val data = c.data
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(28.dp).padding(8.dp),
    ) {
        if (data == null || c.needsLogin) {
            EmptyState(
                if (c.needsLogin) "Přihlas se znovu" else "Bakaláři",
                if (c.needsLogin) "Platnost přihlášení vypršela" else "Klepni a přihlas se",
            )
        } else {
            val s = buildSnapshot(data)
            when {
                s.days.isEmpty() -> EmptyState("Volno 🎉", "V rozvrhu nic nenašel")
                size.width < 200.dp -> Hero(s, GlanceModifier.fillMaxSize())
                size.height < 200.dp -> Row(GlanceModifier.fillMaxSize()) {
                    Hero(s, GlanceModifier.width(120.dp).fillMaxHeight())
                    Spacer(GlanceModifier.width(8.dp))
                    Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                        s.days.flatMap { it.lessons }.take(3).forEach { LessonRow(it, it == s.current, compact = true) }
                    }
                }
                else -> Column(GlanceModifier.fillMaxSize()) {
                    TopHeader(c.data.updatedAt)
                    Spacer(GlanceModifier.height(4.dp))
                    LazyColumn {
                        for (ds in s.days) {
                            item {
                                DayHeader(ds)
                            }
                            if (ds.lessons.isEmpty()) {
                                item {
                                    EmptyDayRow(ds)
                                }
                            } else {
                                itemsIndexed(ds.lessons) { index, lesson ->
                                    val pos = when {
                                        ds.lessons.size == 1 -> LessonPosition.Single
                                        index == 0 -> LessonPosition.First
                                        index == ds.lessons.size - 1 -> LessonPosition.Last
                                        else -> LessonPosition.Middle
                                    }
                                    LessonRow(lesson, lesson == s.current, compact = false, position = pos)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, sub: String) {
    val ctx = LocalContext.current
    Column(
        GlanceModifier.fillMaxSize().clickable(actionStartActivity(Intent(ctx, MainActivity::class.java))),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = ts(GlanceTheme.colors.onSurface, 16, FontWeight.Bold))
        Text(sub, style = ts(GlanceTheme.colors.onSurfaceVariant, 12))
    }
}

/** Velká „hero“ karta: právě probíhající hodina s progress barem, jinak další hodina. */
@Composable
private fun Hero(s: Snapshot, modifier: GlanceModifier) {
    val cur = s.current
    val l = cur ?: s.next
    val c = GlanceTheme.colors
    val bg = if (cur != null) c.primary else c.secondaryContainer
    val fg = if (cur != null) c.onPrimary else c.onSecondaryContainer

    Column(
        modifier.background(bg).cornerRadius(32.dp).padding(14.dp)
            .clickable(actionRunCallback<OpenBakalariAction>()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (l == null) {
            Text("Hotovo 🎉", style = ts(fg, 20, FontWeight.Bold))
            Text("Dnes už žádná výuka", style = ts(fg, 12))
        } else {
            val tag = when {
                cur != null -> "Právě teď"
                s.isToday -> "Další · ${l.start}"
                else -> "${s.heroLabel} · ${l.start}"
            }
            Text(tag, style = ts(fg, 12, FontWeight.Medium))
            Text(l.subject, style = ts(fg, 28, FontWeight.Bold), maxLines = 1)
            val sub = listOf(l.room, l.teacher, l.group).filter { !it.isNullOrBlank() }.joinToString(" · ")
            if (sub.isNotBlank()) Text(sub, style = ts(fg, 13), maxLines = 1)

            if (cur != null) {
                Spacer(GlanceModifier.height(8.dp))
                LinearProgressIndicator(
                    progress = s.progress, modifier = GlanceModifier.fillMaxWidth(),
                    color = c.onPrimary, backgroundColor = c.primaryContainer,
                )
                Spacer(GlanceModifier.height(4.dp))
                Text("zbývá ${s.minutesLeft} min", style = ts(fg, 12))
            }
        }
    }
}

@Composable
private fun TopHeader(updatedAt: Long?) {
    val c = GlanceTheme.colors
    val timeStr = updatedAt?.let {
        java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it))
    }

    Row(
        GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            GlanceModifier.defaultWeight().clickable(actionRunCallback<OpenBakalariAction>())
        ) {
            Text("Rozvrh", style = ts(c.onSurface, 18, FontWeight.Bold))
            if (timeStr != null) {
                Text("Aktualizováno $timeStr", style = ts(c.onSurfaceVariant, 10))
            }
        }
        Box(
            GlanceModifier.size(32.dp).background(c.secondaryContainer).cornerRadius(16.dp)
                .clickable(actionRunCallback<RefreshCallback>()),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(R.drawable.ic_refresh), "Obnovit",
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(c.onSecondaryContainer),
            )
        }
    }
}

@Composable
private fun DayHeader(ds: DaySnapshot) {
    val c = GlanceTheme.colors
    Row(
        GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${ds.label} · ${ds.date.dayOfMonth}. ${ds.date.monthValue}.",
            style = ts(c.onSurface, 14, FontWeight.Bold),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (ds.changesCount > 0) {
            Box(
                GlanceModifier.background(c.tertiaryContainer).cornerRadius(50.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) { Text("${ds.changesCount} změn", style = ts(c.onTertiaryContainer, 11, FontWeight.Medium)) }
        }
    }
}

@Composable
private fun EmptyDayRow(ds: DaySnapshot) {
    val c = GlanceTheme.colors
    val note = ds.day.note?.takeIf { it.isNotBlank() } ?: "Volno 🎉"
    Box(
        GlanceModifier.fillMaxWidth().padding(bottom = 2.dp).background(c.surfaceVariant).cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(actionRunCallback<OpenBakalariAction>()),
    ) {
        Text(note, style = ts(c.onSurfaceVariant, 12))
    }
}

@Composable
private fun statusColors(s: LessonStatus, current: Boolean): Pair<ColorProvider, ColorProvider> {
    val c = GlanceTheme.colors
    return when {
        s == LessonStatus.Canceled -> c.errorContainer to c.onErrorContainer
        current -> c.primary to c.onPrimary
        s == LessonStatus.Substitution || s == LessonStatus.Added -> c.tertiaryContainer to c.onTertiaryContainer
        s == LessonStatus.RoomChange -> c.secondaryContainer to c.onSecondaryContainer
        else -> c.surfaceVariant to c.onSurface
    }
}

@Composable
private fun LessonRow(
    l: Lesson,
    current: Boolean,
    compact: Boolean,
    position: LessonPosition = LessonPosition.Single,
) {
    val (bg, fg) = statusColors(l.status, current)
    val deco = if (l.status == LessonStatus.Canceled) TextDecoration.LineThrough else null
    val title = l.subject
    val details = listOf(l.room, l.teacher, l.group)
        .filter { !it.isNullOrBlank() }
        .joinToString(" · ")

    val sub = when (l.status) {
        LessonStatus.Substitution -> details
        else -> l.note?.takeIf { it.isNotBlank() } ?: details
    }

    val bgDrawable = when (position) {
        LessonPosition.Single -> R.drawable.bg_card_single
        LessonPosition.First -> R.drawable.bg_card_top
        LessonPosition.Middle -> R.drawable.bg_card_middle
        LessonPosition.Last -> R.drawable.bg_card_bottom
    }
    val bottomMargin = if (position == LessonPosition.Last || position == LessonPosition.Single) 6.dp else 2.dp

    Box(GlanceModifier.fillMaxWidth().padding(bottom = bottomMargin)) {
        Row(
            GlanceModifier.fillMaxWidth()
                .background(ImageProvider(bgDrawable), colorFilter = ColorFilter.tint(bg))
                .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 8.dp)
                .clickable(actionRunCallback<OpenBakalariAction>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(l.caption, style = ts(fg, 12, FontWeight.Bold), modifier = GlanceModifier.width(18.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    title,
                    style = ts(
                        fg,
                        if (compact) 14 else 16,
                        FontWeight.Bold,
                        deco
                    ),
                    maxLines = 1
                )
                if (!compact && sub.isNotBlank()) Text(sub, style = ts(fg, 11), maxLines = 1)
            }
            Text(l.start, style = ts(fg, 12, FontWeight.Medium))
        }
    }
}
