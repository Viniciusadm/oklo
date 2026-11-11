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
import com.archieapps.oklo.ui.common.DeckOperations
import com.archieapps.oklo.ui.common.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class HomeDeckRow(
    val node: DeckNode,
    val newCount: Int,
    val learning: Int,
    val review: Int,
    val expanded: Boolean,
)

data class HomeUi(
    val roots: List<DeckNode>,
    val rows: List<HomeDeckRow>,
    val summary: DailySummary,
    val learningLaterCount: Int,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: Container) : ViewModel() {
    private val now = minuteTicker()
    private val dayStart = now.map { LocalDay.window(it).start }.distinctUntilChanged()
    private val reviewsToday = dayStart.flatMapLatest { container.study.reviewsSince(it) }
    private val collapsed = MutableStateFlow<Set<String>>(emptySet())

    val operations = DeckOperations(container = container, scope = viewModelScope)

    val ui: StateFlow<HomeUi?> = combine(
        container.decks.decks,
        container.cards.stats,
        container.settings.settings,
        combine(reviewsToday, collapsed) { reviews, hidden -> reviews to hidden },
        now,
    ) { decks, stats, settings, extra, instant ->
        val (reviews, hidden) = extra
        val window = LocalDay.window(instant)
        val queue = stats.filter { !it.suspended }.map { QueueCard(it.id, CardState.of(it.state), it.dueAt, 0, it.createdAt) }
        val limits = DailyLimits(settings.newPerDay, settings.reviewsPerDay)
        val progress = DailyProgress.from(reviews, window)
        val plan = StudyPlanner.plan(queue, instant, window, limits, progress)
        val reviewRoom = (limits.reviewsPerDay - progress.reviewsDone).coerceAtLeast(0)
        val newRoom = (limits.newPerDay - progress.newIntroduced).coerceAtLeast(0)
        val roots = DeckTree.build(decks, stats, window, settings.librarySort)
        val rows = DeckTree.visible(roots, DeckTree.flatten(roots).map { it.id }.toSet() - hidden).map { node ->
            val review = minOf(node.aggregate.reviewDue, reviewRoom)
            HomeDeckRow(
                node = node,
                newCount = minOf(node.aggregate.newCount, newRoom, (reviewRoom - review).coerceAtLeast(0)),
                learning = node.aggregate.learningDue,
                review = review,
                expanded = node.id !in hidden,
            )
        }
        HomeUi(
            roots = roots,
            rows = rows,
            summary = DailySummary(plan.reviews.size, plan.news.size, plan.learning.size + plan.learningLater.size),
            learningLaterCount = plan.learningLater.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(id: String) = collapsed.update { if (id in it) it - id else it + id }
}
