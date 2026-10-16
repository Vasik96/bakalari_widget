package com.vasik96.bakalariwidget.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ======== Bakaláři API v3 DTO ========
@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
)

@Serializable
data class NamedDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Abbrev") val abbrev: String = "",
    @SerialName("Name") val name: String = "",
)

@Serializable
data class HourDto(
    @SerialName("Id") val id: Int,
    @SerialName("Caption") val caption: String = "",
    @SerialName("BeginTime") val begin: String,
    @SerialName("EndTime") val end: String,
)

@Serializable
data class ChangeDto(
    @SerialName("ChangeSubject") val changeSubject: String? = null,
    @SerialName("ChangeType") val changeType: String? = null,
    @SerialName("Description") val description: String? = null,
)

@Serializable
data class AtomDto(
    @SerialName("HourId") val hourId: Int,
    @SerialName("SubjectId") val subjectId: String? = null,
    @SerialName("TeacherId") val teacherId: String? = null,
    @SerialName("RoomId") val roomId: String? = null,
    @SerialName("Group") val group: String? = null,
    @SerialName("Change") val change: ChangeDto? = null,
)

@Serializable
data class DayDto(
    @SerialName("Atoms") val atoms: List<AtomDto> = emptyList(),
    @SerialName("Date") val date: String,
    @SerialName("DayDescription") val dayDescription: String? = null,
)

@Serializable
data class TimetableDto(
    @SerialName("Hours") val hours: List<HourDto> = emptyList(),
    @SerialName("Days") val days: List<DayDto> = emptyList(),
    @SerialName("Rooms") val rooms: List<NamedDto> = emptyList(),
    @SerialName("Subjects") val subjects: List<NamedDto> = emptyList(),
    @SerialName("Teachers") val teachers: List<NamedDto> = emptyList(),
)

@Serializable
data class MarkDto(
    @SerialName("MarkDate") val markDate: String = "",
    @SerialName("Caption") val caption: String = "",
    @SerialName("Theme") val theme: String = "",
    @SerialName("MarkText") val markText: String = "",
    @SerialName("Weight") val weight: Int = 0,
    @SerialName("IsNew") val isNew: Boolean = false,
    @SerialName("IsPoints") val isPoints: Boolean = false,
    @SerialName("PointsText") val pointsText: String = "",
    @SerialName("MaxPoints") val maxPoints: Int = 0,
)

@Serializable
data class MarkSubjectDto(
    @SerialName("Marks") val marks: List<MarkDto> = emptyList(),
    @SerialName("SubjectAbbrev") val abbrev: String = "",
    @SerialName("SubjectName") val name: String = "",
)

@Serializable
data class MarksDto(@SerialName("Subjects") val subjects: List<MarkSubjectDto> = emptyList())

// ======== Doména (to, co se cachuje a kreslí widget) ========
@Serializable
enum class LessonStatus { Normal, Canceled, Substitution, RoomChange, Added }

@Serializable
data class Lesson(
    val hour: Int,
    val caption: String,
    val start: String,   // HH:mm
    val end: String,     // HH:mm
    val subject: String,
    val subjectFull: String,
    val room: String,
    val teacher: String,
    val group: String? = null,
    val status: LessonStatus,
    val note: String? = null,
)

@Serializable
data class DaySchedule(val date: String, val lessons: List<Lesson>, val note: String? = null)

@Serializable
data class MarkItem(
    val subject: String,
    val subjectFull: String,
    val value: String,
    val caption: String,
    val date: String,
    val isNew: Boolean,
    val grade: Int?,
    val weight: Int,
)

@Serializable
data class WidgetData(
    val updatedAt: Long,
    val days: List<DaySchedule>,
    val marks: List<MarkItem>,
    val newMarks: Int,
)
