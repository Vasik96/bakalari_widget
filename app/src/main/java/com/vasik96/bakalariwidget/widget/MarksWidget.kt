package com.vasik96.bakalariwidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.vasik96.bakalariwidget.R
import com.vasik96.bakalariwidget.data.Cached
import com.vasik96.bakalariwidget.data.MarkItem
import com.vasik96.bakalariwidget.data.Repo
import com.vasik96.bakalariwidget.data.shortDate

private val SMALL = DpSize(110.dp, 110.dp)
private val WIDE = DpSize(250.dp, 110.dp)
private val LARGE = DpSize(250.dp, 250.dp)

class MarksWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val cached = Repo(context).loadCache()
        provideContent { GlanceTheme { MarksContent(cached) } }
    }
}

class MarksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = MarksWidget()
}

@Composable
private fun CenterText(text: String, bold: Boolean = true) {
    val c = GlanceTheme.colors
    Column(
        GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { Text(text, style = ts(c.onSurface, 15, if (bold) FontWeight.Bold else FontWeight.Normal)) }
}

@Composable
private fun MarksContent(c: Cached) {
    val size = LocalSize.current
    val data = c.data
    val col = GlanceTheme.colors
    Box(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(col.widgetBackground)
            .cornerRadius(28.dp).padding(8.dp).clickable(actionRunCallback<OpenBakalariAction>()),
    ) {
        when {
            data == null || c.needsLogin -> CenterText(if (c.needsLogin) "Přihlas se znovu" else "Bakaláři")
            data.marks.isEmpty() -> CenterText("Zatím žádné známky", bold = false)
            size.width < 200.dp -> {
                val m = data.marks.first()
                Column(
                    GlanceModifier.fillMaxSize().background(col.tertiaryContainer).cornerRadius(32.dp).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (data.newMarks > 0) "${data.newMarks} nových" else "Poslední známka",
                        style = ts(col.onTertiaryContainer, 12, FontWeight.Medium),
                    )
                    Text(m.value, style = ts(col.onTertiaryContainer, 38, FontWeight.Bold), maxLines = 1)
                    Text(m.subject, style = ts(col.onTertiaryContainer, 14, FontWeight.Bold), maxLines = 1)
                    Text(m.date.shortDate(), style = ts(col.onTertiaryContainer, 12))
                }
            }
            size.height < 200.dp -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                data.marks.take(4).forEach { m ->
                    Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                        MarkBubble(m, 52)
                        Spacer(GlanceModifier.height(4.dp))
                        Text(m.subject, style = ts(col.onSurface, 12, FontWeight.Medium), maxLines = 1)
                    }
                }
            }
            else -> Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Známky", style = ts(col.onSurface, 18, FontWeight.Bold),
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    if (data.newMarks > 0) {
                        Box(
                            GlanceModifier.background(col.tertiaryContainer).cornerRadius(50.dp)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) { Text("${data.newMarks} nových", style = ts(col.onTertiaryContainer, 12, FontWeight.Medium)) }
                        Spacer(GlanceModifier.width(6.dp))
                    }
                    Box(
                        GlanceModifier.size(36.dp).background(col.secondaryContainer).cornerRadius(18.dp)
                            .clickable(actionRunCallback<RefreshCallback>()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            ImageProvider(R.drawable.ic_refresh), "Obnovit",
                            modifier = GlanceModifier.size(20.dp),
                            colorFilter = ColorFilter.tint(col.onSecondaryContainer),
                        )
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
                LazyColumn { items(data.marks) { MarkRow(it) } }
            }
        }
    }
}

@Composable
private fun gradeColors(grade: Int?): Pair<ColorProvider, ColorProvider> {
    val c = GlanceTheme.colors
    return when {
        grade == null -> c.secondaryContainer to c.onSecondaryContainer
        grade <= 2 -> c.primaryContainer to c.onPrimaryContainer
        grade == 3 -> c.tertiaryContainer to c.onTertiaryContainer
        else -> c.errorContainer to c.onErrorContainer
    }
}

@Composable
private fun MarkBubble(m: MarkItem, dp: Int) {
    val (bg, fg) = gradeColors(m.grade)
    Box(
        GlanceModifier.size(dp.dp).background(bg).cornerRadius((dp * 0.34f).dp),
        contentAlignment = Alignment.Center,
    ) { Text(m.value, style = ts(fg, if (m.value.length > 2) 13 else 20, FontWeight.Bold), maxLines = 1) }
}

@Composable
private fun MarkRow(m: MarkItem) {
    val c = GlanceTheme.colors
    Box(GlanceModifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(
            GlanceModifier.fillMaxWidth().background(c.surfaceVariant).cornerRadius(24.dp).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MarkBubble(m, 44)
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(m.subjectFull.ifBlank { m.subject }, style = ts(c.onSurfaceVariant, 14, FontWeight.Bold), maxLines = 1)
                Text(m.caption.ifBlank { "váha ${m.weight}" }, style = ts(c.onSurfaceVariant, 11), maxLines = 1)
            }
            if (m.isNew) {
                Box(GlanceModifier.background(c.tertiary).cornerRadius(50.dp).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("nová", style = ts(c.onTertiary, 10, FontWeight.Bold))
                }
                Spacer(GlanceModifier.width(6.dp))
            }
            Text(m.date.shortDate(), style = ts(c.onSurfaceVariant, 12))
        }
    }
}
