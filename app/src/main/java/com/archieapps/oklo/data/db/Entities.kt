package com.archieapps.oklo.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "decks", indices = [Index("parentId"), Index("dirty")])
data class DeckEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val position: Int,
    val lastStudiedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val dirty: Boolean,
)

@Entity(tableName = "cards", indices = [Index("deckId"), Index("dueAt"), Index("dirty")])
data class CardEntity(
    @PrimaryKey val id: String,
    val deckId: String,
    val type: String,
    val front: String,
    val back: String?,
    val explanation: String?,
    val answer: Boolean?,
    val choicesJson: String?,
    val state: String,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val dueAt: Long?,
    val lastReviewAt: Long?,
    val reps: Int,
    val lapses: Int,
    val suspended: Boolean,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val dirty: Boolean,
)

@Entity(tableName = "reviews", indices = [Index("cardId"), Index("reviewedAt"), Index("dirty")])
data class ReviewEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val rating: Int,
    val stateBefore: String,
    val stabilityAfter: Double?,
    val difficultyAfter: Double?,
    val scheduledSeconds: Long,
    val durationMs: Long,
    val answeredCorrect: Boolean?,
    val reviewedAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val dirty: Boolean,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: String,
    val newPerDay: Int,
    val reviewsPerDay: Int,
    val desiredRetention: Double,
    val learningStepsMinutes: String,
    val relearningStepsMinutes: String,
    val maximumIntervalDays: Int,
    val enableFuzz: Boolean,
    val librarySort: String,
    val aiModel: String,
    val updatedAt: Long,
    val dirty: Boolean,
)

data class RowVersion(
    val id: String,
    val updatedAt: Long,
    val dirty: Boolean,
)

data class CardStatRow(
    val id: String,
    val deckId: String,
    val state: String,
    val dueAt: Long?,
    val suspended: Boolean,
    val createdAt: Long,
)

data class DeckActivityRow(
    val deckId: String,
    val lastReviewedAt: Long,
)

data class ReviewStatRow(
    val cardId: String,
    val rating: Int,
    val stateBefore: String,
    val reviewedAt: Long,
    val durationMs: Long,
)
