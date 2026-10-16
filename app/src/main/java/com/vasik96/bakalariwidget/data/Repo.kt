
package com.vasik96.bakalariwidget.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.time.LocalDate
import java.util.Locale

private val Context.store by preferencesDataStore("bakalari")

private object K {
    val BASE = stringPreferencesKey("base")
    val ACCESS = stringPreferencesKey("access")
    val REFRESH = stringPreferencesKey("refresh")
    val EXPIRES = longPreferencesKey("expires")
    val CACHE = stringPreferencesKey("cache")
    val NEEDS_LOGIN = booleanPreferencesKey("needs_login")
}

data class Cached(
    val data: WidgetData?,
    val needsLogin: Boolean,
    val loggedIn: Boolean
)

class Repo(context: Context) {
    private val ds = context.applicationContext.store
    private val json = BakalariApi.json

    suspend fun loadCache(): Cached {
        val p = ds.data.first()
        val data = p[K.CACHE]?.let {
            runCatching {
                json.decodeFromString<WidgetData>(it)
            }.getOrNull()
        }

        return Cached(
            data = data,
            needsLogin = p[K.NEEDS_LOGIN] == true,
            loggedIn = p[K.REFRESH] != null
        )
    }

    suspend fun login(school: String, user: String, pass: String) =
        mutex.withLock {
            val base = BakalariApi.normalizeBase(school)
            saveTokens(base, BakalariApi.login(base, user.trim(), pass))
        }

    suspend fun logout() = ds.edit { it.clear() }

    private suspend fun saveTokens(base: String, t: TokenResponse) =
        ds.edit {
            it[K.BASE] = base
            it[K.ACCESS] = t.accessToken
            it[K.REFRESH] = t.refreshToken
            it[K.EXPIRES] =
                System.currentTimeMillis() + t.expiresIn * 1000 - 60_000
            it[K.NEEDS_LOGIN] = false
        }

    /**
     * Zajistí platný access token.
     * Refresh token se může měnit, proto se používá mutex.
     */
    private suspend fun <T> authed(
        block: suspend (base: String, token: String) -> T
    ): T = mutex.withLock {
        val p = ds.data.first()
        val base = p[K.BASE]
            ?: throw AuthException("Nejsi přihlášen")

        suspend fun doRefresh(): String {
            val rt = p[K.REFRESH]
                ?: throw AuthException("Nejsi přihlášen")

            val t = BakalariApi.refresh(base, rt)
            saveTokens(base, t)
            return t.accessToken
        }

        var access = p[K.ACCESS]

        if (
            access == null ||
            System.currentTimeMillis() >= (p[K.EXPIRES] ?: 0L)
        ) {
            access = doRefresh()
        }

        try {
            block(base, access)
        } catch (e: AuthException) {
            block(base, doRefresh())
        }
    }

    /**
     * Stáhne rozvrh od dneška do konce následujících dvou týdnů
     * a známky. Diagnostika místností se vypisuje do Logcatu.
     */
    suspend fun sync(): WidgetData {
        try {
            val data = authed { base, token ->
                val today = LocalDate.now()
                val mondayThisWeek = today.minusDays(
                    (today.dayOfWeek.value - 1).toLong()
                )

                val targetWeeks = listOf(
                    mondayThisWeek,
                    mondayThisWeek.plusWeeks(1),
                    mondayThisWeek.plusWeeks(2)
                )

                val days = targetWeeks
                    .mapIndexed { i, d ->
                        val result = runCatching {
                            val raw = BakalariApi.get(
                                base = base,
                                path = "timetable/actual",
                                token = token,
                                query = mapOf("date" to d.toString())
                            )

                            val timetable =
                                json.decodeFromString<TimetableDto>(raw)

                            // Diagnostika: seznam místností vrácených API.
                            Log.d(
                                "BakalariRooms",
                                "Rooms: ${
                                    timetable.rooms.map {
                                        "id=${it.id}, abbrev=${it.abbrev}, name=${it.name}"
                                    }
                                }"
                            )

                            // Diagnostika: ID místností a změny jednotlivých hodin.
                            Log.d(
                                "BakalariRooms",
                                "Atoms: ${
                                    timetable.days
                                        .flatMap { it.atoms }
                                        .take(20)
                                        .map { atom ->
                                            "hour=${atom.hourId}, " +
                                                    "roomId=${atom.roomId}, " +
                                                    "group=${atom.group}, " +
                                                    "change=${atom.change}"
                                        }
                                }"
                            )

                            timetable.toDays()
                        }

                        if (i == 0) {
                            result.getOrThrow()
                        } else {
                            result.getOrDefault(emptyList())
                        }
                    }
                    .flatten()
                    .distinctBy { it.date }
                    .filter { it.date >= today.toString() }

                val marks = runCatching {
                    json.decodeFromString<MarksDto>(
                        BakalariApi.get(base, "marks", token)
                    ).toItems()
                }.getOrDefault(emptyList())

                WidgetData(
                    System.currentTimeMillis(),
                    days,
                    marks.take(12),
                    marks.count { it.isNew }
                )
            }

            ds.edit {
                it[K.CACHE] = json.encodeToString(data)
                it[K.NEEDS_LOGIN] = false
            }

            return data
        } catch (e: AuthException) {
            ds.edit { it[K.NEEDS_LOGIN] = true }
            throw e
        }
    }

    companion object {
        private val mutex = Mutex()
    }
}

// ---------- Mapování DTO -> doména ----------

private fun String.toHm(): String {
    val p = split(":")
    return String.format(
        Locale.ROOT,
        "%02d:%02d",
        p[0].toInt(),
        p[1].toInt()
    )
}

private fun TimetableDto.toDays(): List<DaySchedule> {
    val hourById = hours.associateBy { it.id }
    val subj = subjects.associateBy { it.id }
    val roomMap = rooms.associateBy { it.id }
    val teachMap = teachers.associateBy { it.id }

    return days.map { d ->
        val lessons = d.atoms.mapNotNull { a ->
            val h = hourById[a.hourId] ?: return@mapNotNull null
            val ch = a.change

            val status = when (ch?.changeType) {
                "Canceled", "Removed" -> LessonStatus.Canceled
                "Added" -> LessonStatus.Added
                "Substitution" -> LessonStatus.Substitution
                "RoomChanged" -> LessonStatus.RoomChange
                else -> LessonStatus.Normal
            }

            val s = subj[a.subjectId]

            val fullSubject =
                s?.name?.takeIf { it.isNotBlank() }
                    ?: ch?.changeSubject?.takeIf { it.isNotBlank() }
                    ?: s?.abbrev
                    ?: "—"

            // Zatím zobrazujeme zkratku záznamu přiřazeného přes RoomId.
            // Diagnostický výpis ověří, zda toto ID opravdu označuje učebnu.
            val fullRoom = a.roomId
                ?.let { id ->
                    roomMap[id]?.abbrev?.takeIf { it.isNotBlank() }
                }
                .orEmpty()

            val fullTeacher =
                teachMap[a.teacherId]?.name?.takeIf { it.isNotBlank() }
                    ?: teachMap[a.teacherId]?.abbrev.orEmpty()

            Lesson(
                hour = h.id,
                caption = h.caption,
                start = h.begin.toHm(),
                end = h.end.toHm(),
                subject = fullSubject,
                subjectFull = fullSubject,
                room = fullRoom,
                teacher = fullTeacher,
                group = a.group?.takeIf { it.isNotBlank() },
                status = status,
                note = ch?.description?.takeIf { it.isNotBlank() }
            )
        }
            .groupBy { it.hour }
            .map { (_, group) ->
                group.firstOrNull {
                    it.status != LessonStatus.Normal
                } ?: group.first()
            }
            .sortedBy { it.hour }

        DaySchedule(
            d.date.take(10),
            lessons,
            d.dayDescription?.takeIf { it.isNotBlank() }
        )
    }
}

private fun MarksDto.toItems(): List<MarkItem> =
    subjects
        .flatMap { s ->
            s.marks.map { m ->
                MarkItem(
                    subject = s.name.ifBlank { s.abbrev },
                    subjectFull = s.name,
                    value = if (m.isPoints) m.pointsText else m.markText,
                    caption = m.caption.ifBlank { m.theme },
                    date = m.markDate.take(10),
                    isNew = m.isNew,
                    grade = if (m.isPoints) {
                        null
                    } else {
                        m.markText.firstOrNull()?.digitToIntOrNull()
                    },
                    weight = m.weight
                )
            }
        }
        .sortedByDescending { it.date }
