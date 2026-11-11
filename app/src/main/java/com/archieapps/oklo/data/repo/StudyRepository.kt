package com.archieapps.oklo.data.repo

import androidx.room.withTransaction
import com.archieapps.oklo.core.fsrs.FsrsParameters
import com.archieapps.oklo.core.fsrs.Rating
import com.archieapps.oklo.core.sched.CardScheduler
import com.archieapps.oklo.core.sched.FsrsCardScheduler
import com.archieapps.oklo.core.sched.SchedulerKind
import com.archieapps.oklo.core.sched.SchedulingCard
import com.archieapps.oklo.core.sched.SchedulingOutcome
import com.archieapps.oklo.core.sched.Sm2Config
import com.archieapps.oklo.core.sched.Sm2Scheduler
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.db.CardEntity
import com.archieapps.oklo.data.db.OkloDatabase
import com.archieapps.oklo.data.db.ReviewEntity
import com.archieapps.oklo.data.db.toModel
import com.archieapps.oklo.data.model.Card
import com.archieapps.oklo.data.model.CardState
import com.archieapps.oklo.data.model.ReviewStat
import com.archieapps.oklo.data.model.StudySettings
import com.archieapps.oklo.domain.DailyLimits
import com.archieapps.oklo.domain.DailyProgress
import com.archieapps.oklo.domain.DeckTree
import com.archieapps.oklo.domain.QueueCard
import com.archieapps.oklo.domain.StudyPlan
import com.archieapps.oklo.domain.StudyPlanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object Schedulers {
    fun of(settings: StudySettings): CardScheduler = when (settings.scheduler) {
        SchedulerKind.Sm2 -> Sm2Scheduler(
            Sm2Config(
                learningStepsMinutes = settings.learningStepsMinutes,
                relearningStepsMinutes = settings.relearningStepsMinutes,
                graduatingIntervalDays = settings.graduatingIntervalDays,
                easyIntervalDays = settings.easyIntervalDays,
                startingEase = settings.startingEase,
                easyBonus = settings.easyBonus,
                intervalModifier = settings.intervalModifier,
                hardIntervalFactor = settings.hardIntervalFactor,
                newIntervalFactor = settings.newIntervalFactor,
                minimumIntervalDays = settings.minimumIntervalDays,
                maximumIntervalDays = settings.maximumIntervalDays,
                enableFuzz = settings.enableFuzz,
            ),
        )
        SchedulerKind.Fsrs -> FsrsCardScheduler(
            FsrsParameters(
                desiredRetention = settings.desiredRetention,
                learningStepsMinutes = settings.learningStepsMinutes,
                relearningStepsMinutes = settings.relearningStepsMinutes,
                maximumIntervalDays = settings.maximumIntervalDays,
                enableFuzz = settings.enableFuzz,
            ),
        )
    }

    fun input(card: Card): SchedulingCard = SchedulingCard(
        id = card.id,
        state = card.state,
        step = card.step,
        stability = card.stability,
        difficulty = card.difficulty,
        easeFactor = card.easeFactor,
        intervalDays = card.intervalDays,
        dueAt = card.dueAt,
        lastReviewAt = card.lastReviewAt,
        reps = card.reps,
    )

    fun displayInterval(outcome: SchedulingOutcome, now: Long): Long =
        if (outcome.state == CardState.Review) outcome.intervalDays * DAY_MS else (outcome.dueAt - now).coerceAtLeast(0)

    private const val DAY_MS = 86_400_000L
}

data class AnswerWrite(
    val card: CardEntity,
    val review: ReviewEntity,
)

object StudyWrites {
    fun answer(
        entity: CardEntity,
        rating: Rating,
        scheduler: CardScheduler,
        clockNow: Long,
        durationMs: Long,
        answeredCorrect: Boolean?,
        reviewId: String,
    ): AnswerWrite {
        val card = entity.toModel()
        val now = maxOf(clockNow, card.lastReviewAt ?: 0L)
        val next = scheduler.review(Schedulers.input(card), rating, now)
        val lapse = card.state == CardState.Review && rating == Rating.Again
        val updated = entity.copy(
            state = next.state.wire,
            step = next.step,
            stability = next.stability,
            difficulty = next.difficulty,
            easeFactor = next.easeFactor,
            intervalDays = next.intervalDays,
            dueAt = next.dueAt,
            lastReviewAt = now,
            reps = entity.reps + 1,
            lapses = entity.lapses + if (lapse) 1 else 0,
            updatedAt = Stamp.next(entity.updatedAt, now),
            dirty = true,
        )
        val review = ReviewEntity(
            id = reviewId,
            cardId = entity.id,
            rating = rating.value,
            stateBefore = card.state.wire,
            stabilityAfter = next.stability,
            difficultyAfter = next.difficulty,
            scheduledSeconds = ((next.dueAt - now) / 1000).coerceAtLeast(0),
            durationMs = durationMs.coerceAtLeast(0),
            answeredCorrect = answeredCorrect,
            reviewedAt = now,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            dirty = true,
        )
        return AnswerWrite(updated, review)
    }
}

data class AnswerResult(
    val card: Card,
    val scheduledMs: Long,
)

class StudyRepository(
    private val db: OkloDatabase,
    private val settingsRepository: SettingsRepository,
    private val time: TimeSource = TimeSource.System,
) {
    fun reviewsSince(since: Long): Flow<List<ReviewStat>> = db.reviews().observeSince(since).map { list -> list.map { it.toModel() } }

    val totalReviews: Flow<Int> = db.reviews().observeTotal()

    val deckLastStudied: Flow<Map<String, Long>> = db.reviews().observeDeckActivity()
        .map { rows -> rows.associate { it.deckId to it.lastReviewedAt } }

    suspend fun scopeDeckIds(deckId: String?): List<String>? {
        if (deckId == null) return null
        val decks = db.decks().alive().map { it.toModel() }
        return DeckTree.subtreeIds(decks, deckId).toList()
    }

    suspend fun plan(deckId: String?, now: Long): StudyPlan {
        val settings = settingsRepository.current()
        val window = LocalDay.window(now)
        val scope = scopeDeckIds(deckId)
        val cards = if (scope == null) {
            db.cards().studyableAll()
        } else {
            scope.chunked(500).flatMap { db.cards().studyableInDecks(it) }
        }
        val progress = DailyProgress.from(db.reviews().since(window.start).map { it.toModel() }, window)
        return StudyPlanner.plan(
            cards = cards.map { it.toQueueCard() },
            now = now,
            window = window,
            limits = DailyLimits(settings.newPerDay, settings.reviewsPerDay),
            progress = progress,
        )
    }

    suspend fun card(id: String): Card? = db.cards().get(id)?.takeIf { it.deletedAt == null }?.toModel()

    suspend fun scheduler(): CardScheduler = Schedulers.of(settingsRepository.current())

    fun previewIntervals(scheduler: CardScheduler, card: Card, now: Long): Map<Rating, Long> =
        scheduler.preview(Schedulers.input(card), now).mapValues { (_, next) -> Schedulers.displayInterval(next, now) }

    suspend fun answer(
        cardId: String,
        rating: Rating,
        durationMs: Long,
        answeredCorrect: Boolean?,
    ): AnswerResult? {
        val scheduler = scheduler()
        return db.withTransaction {
            val entity = db.cards().get(cardId)?.takeIf { it.deletedAt == null } ?: return@withTransaction null
            val write = StudyWrites.answer(entity, rating, scheduler, time.now(), durationMs, answeredCorrect, newId())
            db.cards().upsert(write.card)
            db.reviews().insert(write.review)
            AnswerResult(write.card.toModel(), (write.card.dueAt ?: write.review.reviewedAt) - write.review.reviewedAt)
        }
    }

    private fun CardEntity.toQueueCard(): QueueCard =
        QueueCard(id, CardState.of(state), dueAt, position, createdAt)
}
