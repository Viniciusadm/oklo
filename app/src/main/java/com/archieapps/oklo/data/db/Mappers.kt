package com.archieapps.oklo.data.db

import com.archieapps.oklo.core.net.CardDto
import com.archieapps.oklo.core.net.DeckDto
import com.archieapps.oklo.core.net.OkloJson
import com.archieapps.oklo.core.net.ReviewDto
import com.archieapps.oklo.core.net.SettingsDto
import com.archieapps.oklo.data.model.AiModel
import com.archieapps.oklo.data.model.Card
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardState
import com.archieapps.oklo.data.model.CardStat
import com.archieapps.oklo.data.model.CardType
import com.archieapps.oklo.data.model.Choice
import com.archieapps.oklo.data.model.Deck
import com.archieapps.oklo.data.model.LibrarySort
import com.archieapps.oklo.data.model.ReviewStat
import com.archieapps.oklo.data.model.StudySettings
import kotlinx.serialization.builtins.ListSerializer

private val choiceListSerializer = ListSerializer(Choice.serializer())

fun encodeChoices(choices: List<Choice>?): String? =
    choices?.let { OkloJson.instance.encodeToString(choiceListSerializer, it) }

fun decodeChoices(raw: String?): List<Choice>? =
    raw?.let { runCatching { OkloJson.instance.decodeFromString(choiceListSerializer, it) }.getOrNull() }

fun encodeSteps(steps: List<Int>): String = steps.joinToString(",")

fun decodeSteps(raw: String): List<Int> =
    raw.split(",").mapNotNull { it.trim().toIntOrNull() }

fun DeckEntity.toModel(): Deck = Deck(id, parentId, name, position, lastStudiedAt, createdAt, updatedAt)

fun DeckEntity.toDto(): DeckDto = DeckDto(id, parentId, name, position, lastStudiedAt, createdAt, updatedAt, deletedAt)

fun DeckDto.toEntity(dirty: Boolean): DeckEntity =
    DeckEntity(id, parentId, name, position, lastStudiedAt, createdAt, updatedAt, deletedAt, dirty)

fun CardEntity.toModel(): Card = Card(
    id = id,
    deckId = deckId,
    content = CardContent(
        type = CardType.of(type),
        front = front,
        back = back,
        explanation = explanation,
        answer = answer,
        choices = decodeChoices(choicesJson),
    ),
    state = CardState.of(state),
    step = step,
    stability = stability,
    difficulty = difficulty,
    dueAt = dueAt,
    lastReviewAt = lastReviewAt,
    reps = reps,
    lapses = lapses,
    suspended = suspended,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CardEntity.toDto(): CardDto = CardDto(
    id = id,
    deckId = deckId,
    type = type,
    front = front,
    back = back,
    explanation = explanation,
    answer = answer,
    choices = decodeChoices(choicesJson),
    state = state,
    step = step,
    stability = stability,
    difficulty = difficulty,
    dueAt = dueAt,
    lastReviewAt = lastReviewAt,
    reps = reps,
    lapses = lapses,
    suspended = suspended,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun CardDto.toEntity(dirty: Boolean): CardEntity = CardEntity(
    id = id,
    deckId = deckId,
    type = type,
    front = front,
    back = back,
    explanation = explanation,
    answer = answer,
    choicesJson = encodeChoices(choices),
    state = state,
    step = step,
    stability = stability,
    difficulty = difficulty,
    dueAt = dueAt,
    lastReviewAt = lastReviewAt,
    reps = reps,
    lapses = lapses,
    suspended = suspended,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    dirty = dirty,
)

fun ReviewEntity.toDto(): ReviewDto = ReviewDto(
    id, cardId, rating, stateBefore, stabilityAfter, difficultyAfter, scheduledSeconds, durationMs,
    answeredCorrect, reviewedAt, createdAt, updatedAt, deletedAt,
)

fun ReviewDto.toEntity(dirty: Boolean): ReviewEntity = ReviewEntity(
    id, cardId, rating, stateBefore, stabilityAfter, difficultyAfter, scheduledSeconds, durationMs,
    answeredCorrect, reviewedAt, createdAt, updatedAt, deletedAt, dirty,
)

fun SettingsEntity.toModel(): StudySettings = StudySettings(
    newPerDay = newPerDay,
    reviewsPerDay = reviewsPerDay,
    desiredRetention = desiredRetention,
    learningStepsMinutes = decodeSteps(learningStepsMinutes),
    relearningStepsMinutes = decodeSteps(relearningStepsMinutes),
    maximumIntervalDays = maximumIntervalDays,
    enableFuzz = enableFuzz,
    librarySort = LibrarySort.of(librarySort),
    aiModel = AiModel.of(aiModel),
    updatedAt = updatedAt,
)

fun StudySettings.toEntity(dirty: Boolean): SettingsEntity = SettingsEntity(
    id = "global",
    newPerDay = newPerDay,
    reviewsPerDay = reviewsPerDay,
    desiredRetention = desiredRetention,
    learningStepsMinutes = encodeSteps(learningStepsMinutes),
    relearningStepsMinutes = encodeSteps(relearningStepsMinutes),
    maximumIntervalDays = maximumIntervalDays,
    enableFuzz = enableFuzz,
    librarySort = librarySort.wire,
    aiModel = aiModel.wire,
    updatedAt = updatedAt,
    dirty = dirty,
)

fun SettingsEntity.toDto(): SettingsDto = SettingsDto(
    id = "global",
    newPerDay = newPerDay,
    reviewsPerDay = reviewsPerDay,
    desiredRetention = desiredRetention,
    learningStepsMinutes = decodeSteps(learningStepsMinutes),
    relearningStepsMinutes = decodeSteps(relearningStepsMinutes),
    maximumIntervalDays = maximumIntervalDays,
    enableFuzz = enableFuzz,
    librarySort = librarySort,
    aiModel = aiModel,
    updatedAt = updatedAt,
)

fun SettingsDto.toEntity(dirty: Boolean): SettingsEntity = SettingsEntity(
    id = "global",
    newPerDay = newPerDay,
    reviewsPerDay = reviewsPerDay,
    desiredRetention = desiredRetention,
    learningStepsMinutes = encodeSteps(learningStepsMinutes),
    relearningStepsMinutes = encodeSteps(relearningStepsMinutes),
    maximumIntervalDays = maximumIntervalDays,
    enableFuzz = enableFuzz,
    librarySort = librarySort,
    aiModel = aiModel,
    updatedAt = updatedAt,
    dirty = dirty,
)

fun CardStatRow.toModel(): CardStat = CardStat(id, deckId, state, dueAt, suspended, createdAt)

fun ReviewStatRow.toModel(): ReviewStat = ReviewStat(cardId, rating, stateBefore, reviewedAt, durationMs)
