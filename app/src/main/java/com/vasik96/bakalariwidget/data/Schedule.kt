package com.vasik96.bakalariwidget.data

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

private val CS = Locale("cs", "CZ")

fun String.t(): LocalTime = LocalTime.parse(this)

fun String.shortDate(): String =
    runCatching { LocalDate.parse(take(10)).let { "${it.dayOfMonth}. ${it.monthValue}." } }.getOrDefault(this)

data class DaySnapshot(
    val day: DaySchedule,
    val date: LocalDate,
    val label: String,            // Dnes / Zítra / Pondělí…
    val isToday: Boolean,
    val lessons: List<Lesson>,    // lekce pro daný den (pro dnešek zbývající)
    val changesCount: Int,
)

data class Snapshot(
    val current: Lesson?,
    val next: Lesson?,
    val progress: Float,
    val minutesLeft: Int,
    val isToday: Boolean,
    val heroLabel: String,
    val days: List<DaySnapshot>,  // Všechny nadcházející dny
)

/** Vybere, co widget zobrazí: od dneška do konce týdne a příští týden. */
fun buildSnapshot(data: WidgetData, now: LocalDateTime = LocalDateTime.now()): Snapshot {
    val today = now.toLocalDate()
    val time = now.toLocalTime()

    // Najdeme aktuální probíhající hodinu (dnes)
    val todaySchedule = data.days.firstOrNull { it.date == today.toString() }
    val todayLessons = todaySchedule?.lessons.orEmpty()
    val liveToday = todayLessons.filter { it.status != LessonStatus.Canceled }

    val current = liveToday.firstOrNull { time >= it.start.t() && time < it.end.t() }

    // Dny pro seznam (od dneška dál)
    val daySnapshots = data.days.mapNotNull { daySchedule ->
        val date = runCatching { LocalDate.parse(daySchedule.date) }.getOrNull() ?: return@mapNotNull null
        if (date < today) return@mapNotNull null

        val isToday = (date == today)
        // Pro dnešek zobrazujeme jen lekce, které ještě neskončily
        val lessonsForDay = if (isToday) {
            daySchedule.lessons.filter { it.end.t() > time }
        } else {
            daySchedule.lessons
        }

        // Pokud pro dnešek už nezbývají žádné lekce, dnešek v seznamu vynecháme
        if (isToday && lessonsForDay.isEmpty()) return@mapNotNull null

        val label = when {
            isToday -> "Dnes"
            date == today.plusDays(1) -> "Zítra"
            else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, CS).replaceFirstChar { it.uppercase() }
        }

        val changesCount = daySchedule.lessons.count { it.status != LessonStatus.Normal }

        DaySnapshot(
            day = daySchedule,
            date = date,
            label = label,
            isToday = isToday,
            lessons = lessonsForDay,
            changesCount = changesCount,
        )
    }

    // Najdeme další nadcházející hodinu (pro Hero kartu / Dashboard)
    val nextLesson = if (current != null) {
        liveToday.firstOrNull { it.start.t() > time }
    } else {
        daySnapshots.flatMap { it.lessons.filter { l -> l.status != LessonStatus.Canceled } }.firstOrNull()
    }

    val firstDay = daySnapshots.firstOrNull()
    val isTodayActive = current != null || (firstDay?.isToday == true)
    val heroLabel = firstDay?.label ?: ""

    var progress = 0f
    var minutesLeft = 0
    if (current != null) {
        val total = Duration.between(current.start.t(), current.end.t()).seconds.toFloat()
        val done = Duration.between(current.start.t(), time).seconds.toFloat()
        progress = (done / total).coerceIn(0f, 1f)
        minutesLeft = ((Duration.between(time, current.end.t()).seconds + 59) / 60).toInt()
    }

    return Snapshot(
        current = current,
        next = nextLesson,
        progress = progress,
        minutesLeft = minutesLeft,
        isToday = isTodayActive,
        heroLabel = heroLabel,
        days = daySnapshots,
    )
}

/** Kdy se má widget příště překreslit (začátek/konec hodiny; během hodiny každých 5 min). */
fun nextBoundary(data: WidgetData, now: LocalDateTime): LocalDateTime {
    val today = now.toLocalDate()
    val boundary = data.days.firstOrNull { it.date == today.toString() }?.lessons.orEmpty()
        .flatMap { listOf(it.start, it.end) }
        .map { today.atTime(it.t()).plusSeconds(10) }
        .filter { it.isAfter(now) }
        .minOrNull() ?: today.plusDays(1).atTime(0, 5)
    return if (buildSnapshot(data, now).current != null) minOf(boundary, now.plusMinutes(5)) else boundary
}
