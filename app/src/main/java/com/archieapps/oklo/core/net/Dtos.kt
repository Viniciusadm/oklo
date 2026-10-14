package com.archieapps.oklo.core.net

import com.archieapps.oklo.data.model.Choice
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Envelope<T>(
    val message: String? = null,
    val body: T? = null,
    val misc: JsonElement? = null,
    val error: JsonElement? = null,
    val success: Boolean = false,
)

@Serializable
data class DeckDto(
    val id: String,
    val parentId: String? = null,
    val name: String,
    val position: Int = 0,
    val lastStudiedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class CardDto(
    val id: String,
    val deckId: String,
    val type: String,
    val front: String,
    val back: String? = null,
    val explanation: String? = null,
    val answer: Boolean? = null,
    val choices: List<Choice>? = null,
    val state: String = "new",
    val step: Int? = null,
    val stability: Double? = null,
    val difficulty: Double? = null,
    val dueAt: Long? = null,
    val lastReviewAt: Long? = null,
    val reps: Int = 0,
    val lapses: Int = 0,
    val suspended: Boolean = false,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val lessonTarget: String? = null,
)

@Serializable
data class LessonDto(
    val id: String,
    val deckId: String,
    val source: String,
    val version: Int = 1,
    val ribaltaVersion: String,
    val origin: String = "manual",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class ReviewDto(
    val id: String,
    val cardId: String,
    val rating: Int,
    val stateBefore: String,
    val stabilityAfter: Double? = null,
    val difficultyAfter: Double? = null,
    val scheduledSeconds: Long = 0,
    val durationMs: Long = 0,
    val answeredCorrect: Boolean? = null,
    val reviewedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
data class SettingsDto(
    val id: String = "global",
    val newPerDay: Int = 20,
    val reviewsPerDay: Int = 200,
    val desiredRetention: Double = 0.9,
    val learningStepsMinutes: List<Int> = listOf(1, 10),
    val relearningStepsMinutes: List<Int> = listOf(10),
    val maximumIntervalDays: Int = 36500,
    val enableFuzz: Boolean = true,
    val librarySort: String = "name",
    val aiModel: String = "flash",
    val updatedAt: Long,
)

@Serializable
data class ChangeSet(
    val decks: List<DeckDto> = emptyList(),
    val cards: List<CardDto> = emptyList(),
    val reviews: List<ReviewDto> = emptyList(),
    val lessons: List<LessonDto> = emptyList(),
    val settings: SettingsDto? = null,
) {
    val size: Int get() = decks.size + cards.size + reviews.size + lessons.size + (if (settings != null) 1 else 0)

    fun isEmpty(): Boolean = size == 0
}

@Serializable
data class PushRequest(
    val deviceId: String,
    val changes: ChangeSet,
)

@Serializable
data class AppliedCounts(
    val decks: Int = 0,
    val cards: Int = 0,
    val reviews: Int = 0,
    val settings: Int = 0,
    val lessons: Int = 0,
)

@Serializable
data class RowRef(
    val entity: String,
    val id: String,
)

@Serializable
data class RejectedRow(
    val entity: String,
    val id: String? = null,
    val reason: String? = null,
)

@Serializable
data class PushBody(
    val usn: Long,
    val applied: AppliedCounts = AppliedCounts(),
    val skipped: List<RowRef> = emptyList(),
    val rejected: List<RejectedRow> = emptyList(),
)

@Serializable
data class PullBody(
    val changes: ChangeSet = ChangeSet(),
    val usn: Long,
    val nextCursor: String? = null,
)

@Serializable
data class ServerCounts(
    val decks: Int = 0,
    val cards: Int = 0,
    val reviews: Int = 0,
)

@Serializable
data class SyncStatusBody(
    val usn: Long = 0,
    val counts: ServerCounts = ServerCounts(),
    val lastSyncAt: String? = null,
)

@Serializable
data class AiStatusBody(
    val configured: Boolean = false,
    val models: Map<String, String> = emptyMap(),
)

@Serializable
data class DraftRequest(
    val text: String,
    val deckPath: String? = null,
    val maxCards: Int? = null,
    val difficulty: String = "auto",
    val types: List<String> = emptyList(),
    val model: String = "flash",
)

@Serializable
data class DraftCardDto(
    val type: String,
    val front: String,
    val back: String? = null,
    val explanation: String? = null,
    val answer: Boolean? = null,
    val choices: List<Choice>? = null,
)

@Serializable
data class DraftBody(
    val cards: List<DraftCardDto> = emptyList(),
)

@Serializable
data class DraftMisc(
    val model: String? = null,
    val discarded: Int = 0,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class UserDto(
    val id: Long = 0,
    val name: String? = null,
    val email: String? = null,
    val blocked: Int = 0,
)

@Serializable
data class LoginBody(
    val user: UserDto,
    val token: String,
    val type: String? = null,
)
