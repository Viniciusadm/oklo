package com.archieapps.oklo.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.Container
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardState
import com.archieapps.oklo.domain.DailyLimits
import com.archieapps.oklo.domain.DailyProgress
import com.archieapps.oklo.domain.DailySummary
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.domain.DeckTree
import com.archieapps.oklo.domain.QueueCard
import com.archieapps.oklo.domain.StudyPlanner
import com.archieapps.oklo.ui.common.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeUi(
    val today: LocalDate,
    val summary: DailySummary,
    val continueDecks: List<DeckNode>,
    val hasDecks: Boolean,
    val hasCards: Boolean,
    val learningLaterCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: Container) : ViewModel() {
    private val now = minuteTicker()
    private val dayStart = now.map { LocalDay.window(it).start }.distinctUntilChanged()
    private val reviewsToday = dayStart.flatMapLatest { container.study.reviewsSince(it) }

    val ui: StateFlow<HomeUi?> = combine(
        container.decks.decks,
        container.cards.stats,
        container.settings.settings,
        combine(reviewsToday, container.study.deckLastStudied) { reviews, studied -> reviews to studied },
        now,
    ) { decks, stats, settings, activity, instant ->
        val (reviews, studied) = activity
        val window = LocalDay.window(instant)
        val queue = stats.filter { !it.suspended }.map { QueueCard(it.id, CardState.of(it.state), it.dueAt, 0, it.createdAt) }
        val limits = DailyLimits(settings.newPerDay, settings.reviewsPerDay)
        val progress = DailyProgress.from(reviews, window)
        val plan = StudyPlanner.plan(queue, instant, window, limits, progress)
        val tree = DeckTree.build(decks, stats, window, settings.librarySort, studied)
        val continueDecks = DeckTree.flatten(tree)
            .filter { it.own.pending > 0 || it.own.newCount > 0 }
            .sortedWith(
                compareByDescending<DeckNode> { it.lastStudied ?: 0L }
                    .thenByDescending { it.own.pending }
                    .thenBy { it.pathLabel },
            )
            .take(3)
        HomeUi(
            today = window.date,
            summary = DailySummary(plan.reviews.size, plan.news.size, plan.learning.size + plan.learningLater.size),
            continueDecks = continueDecks,
            hasDecks = decks.isNotEmpty(),
            hasCards = stats.isNotEmpty(),
            learningLaterCount = plan.learningLater.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
