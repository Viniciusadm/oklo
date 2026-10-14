package com.archieapps.oklo.ui.screen.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.Container
import com.archieapps.oklo.core.fsrs.FsrsScheduler
import com.archieapps.oklo.core.fsrs.Rating
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.Card
import com.archieapps.oklo.data.model.CardType
import com.archieapps.oklo.domain.StudyPlan
import com.archieapps.oklo.domain.StudySession
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Response {
    data class TrueFalse(val chosen: Boolean) : Response
    data class Choice(val index: Int) : Response
}

data class SessionTotals(
    val cardsSeen: Int = 0,
    val reviews: Int = 0,
    val timeMs: Long = 0,
)

sealed interface StudyUi {
    data object Loading : StudyUi

    data class NothingDue(
        val deckName: String?,
        val learningLater: List<Long>,
        val reviewsCapped: Boolean,
        val newsCapped: Boolean,
    ) : StudyUi

    data class Question(
        val deckName: String?,
        val card: Card,
        val position: Int,
        val total: Int,
        val revealed: Boolean,
        val response: Response?,
        val answeredCorrect: Boolean?,
        val intervals: Map<Rating, Long>,
        val busy: Boolean,
        val choiceOrder: List<Int> = emptyList(),
    ) : StudyUi

    data class Done(
        val deckName: String?,
        val totals: SessionTotals,
        val canContinue: Boolean,
        val learningLater: List<Long>,
        val reviewsCapped: Boolean,
        val newsCapped: Boolean,
    ) : StudyUi
}

class StudyViewModel(private val container: Container, private val deckId: String?) : ViewModel() {
    private val _ui = MutableStateFlow<StudyUi>(StudyUi.Loading)
    val ui: StateFlow<StudyUi> = _ui.asStateFlow()

    private var session: StudySession? = null
    private var scheduler: FsrsScheduler? = null
    private var deckName: String? = null
    private var shownAt: Long = 0
    private var totals = SessionTotals()
    private var finished = false

    init {
        start()
    }

    val hasProgress: Boolean
        get() = totals.reviews > 0 || (_ui.value as? StudyUi.Question)?.revealed == true

    private fun start() {
        viewModelScope.launch {
            _ui.value = StudyUi.Loading
            deckName = deckId?.let { container.decks.get(it)?.name }
            scheduler = container.study.scheduler()
            val now = System.currentTimeMillis()
            val plan = container.study.plan(deckId, now)
            session = StudySession(plan)
            finished = false
            if (plan.sessionSize == 0) {
                _ui.value = StudyUi.NothingDue(deckName, session?.laterToday(now).orEmpty(), plan.reviewsCapped, plan.newsCapped)
            } else {
                advance()
            }
        }
    }

    private suspend fun advance() {
        val active = session ?: return
        while (true) {
            val now = System.currentTimeMillis()
            val id = active.next(now)
            if (id == null) {
                complete()
                return
            }
            val card = container.study.card(id)
            if (card == null || card.suspended) {
                active.skip(id)
                continue
            }
            val engine = scheduler ?: container.study.scheduler().also { scheduler = it }
            shownAt = now
            totals = totals.copy(cardsSeen = active.seenCount)
            _ui.value = StudyUi.Question(
                deckName = deckName,
                card = card,
                position = active.answered + 1,
                total = active.answered + 1 + active.remaining(now),
                revealed = false,
                response = null,
                answeredCorrect = null,
                intervals = container.study.previewIntervals(engine, card, now),
                busy = false,
                choiceOrder = card.content.choices.orEmpty().indices.shuffled(),
            )
            return
        }
    }

    fun reveal() {
        _ui.update { current ->
            if (current is StudyUi.Question && current.card.content.type in setOf(CardType.Basic, CardType.Qa)) {
                current.copy(revealed = true)
            } else {
                current
            }
        }
    }

    fun respondTrueFalse(chosen: Boolean) {
        _ui.update { current ->
            if (current !is StudyUi.Question || current.revealed) return@update current
            val expected = current.card.content.answer
            current.copy(
                revealed = true,
                response = Response.TrueFalse(chosen),
                answeredCorrect = expected?.let { it == chosen },
            )
        }
    }

    fun respondChoice(index: Int) {
        _ui.update { current ->
            if (current !is StudyUi.Question || current.revealed) return@update current
            val choice = current.card.content.choices?.getOrNull(index) ?: return@update current
            current.copy(
                revealed = true,
                response = Response.Choice(index),
                answeredCorrect = choice.correct,
            )
        }
    }

    fun rate(rating: Rating) {
        val current = _ui.value as? StudyUi.Question ?: return
        if (!current.revealed || current.busy) return
        _ui.value = current.copy(busy = true)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val duration = (now - shownAt).coerceIn(0, MAX_ANSWER_MS)
            val result = container.study.answer(
                cardId = current.card.id,
                rating = rating,
                durationMs = duration,
                answeredCorrect = current.answeredCorrect,
            )
            val active = session
            if (result != null && active != null) {
                active.answered(current.card.id, result.card.state, result.card.dueAt ?: now, LocalDay.window(now).end)
                totals = totals.copy(reviews = totals.reviews + 1, timeMs = totals.timeMs + duration, cardsSeen = active.seenCount)
            } else {
                active?.skip(current.card.id)
            }
            advance()
        }
    }

    fun suspendCurrent() {
        val current = _ui.value as? StudyUi.Question ?: return
        viewModelScope.launch {
            container.cards.setSuspended(current.card.id, true)
            session?.skip(current.card.id)
            advance()
        }
    }

    fun continueStudying() {
        totals = SessionTotals()
        start()
    }

    private suspend fun complete() {
        val now = System.currentTimeMillis()
        val active = session
        val plan: StudyPlan = container.study.plan(deckId, now)
        _ui.value = StudyUi.Done(
            deckName = deckName,
            totals = totals.copy(cardsSeen = active?.seenCount ?: totals.cardsSeen),
            canContinue = plan.sessionSize > 0,
            learningLater = (plan.learningLater.mapNotNull { it.dueAt }).sorted(),
            reviewsCapped = plan.reviewsCapped,
            newsCapped = plan.newsCapped,
        )
        finish()
    }

    fun finish() {
        if (finished) return
        finished = true
        if (totals.reviews == 0) return
        container.appScope.launch {
            withContext(NonCancellable) {
                container.sync.syncIfSignedIn()
            }
        }
    }

    override fun onCleared() {
        finish()
    }

    companion object {
        const val MAX_ANSWER_MS = 120_000L
    }
}
