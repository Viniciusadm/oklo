package com.archieapps.oklo.ui.screen.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.Container
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.domain.DeckTree
import com.archieapps.oklo.domain.StatsCalculator
import com.archieapps.oklo.domain.StatsSummary
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

data class StatsUi(
    val summary: StatsSummary,
    val days: Int,
    val deckName: String?,
    val missingDeck: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(container: Container, private val deckId: String?) : ViewModel() {
    val days = MutableStateFlow(7)
    private val now = minuteTicker(60_000)
    private val since = now.map { LocalDay.window(it).date.minusDays(29) }
        .distinctUntilChanged()
        .map { LocalDay.of(it).start }
    private val reviews = since.flatMapLatest { container.study.reviewsSince(it) }

    val ui: StateFlow<StatsUi?> = combine(
        container.decks.decks,
        container.cards.stats,
        reviews,
        days,
        now,
    ) { decks, stats, reviewList, window, instant ->
        if (deckId == null) {
            StatsUi(StatsCalculator.compute(decks.size, stats, reviewList, instant, window), window, null, false)
        } else {
            val deck = decks.firstOrNull { it.id == deckId }
            val scope = DeckTree.subtreeIds(decks, deckId)
            val scopedCards = stats.filter { it.deckId in scope }
            val cardIds = scopedCards.map { it.id }.toSet()
            val scopedReviews = reviewList.filter { it.cardId in cardIds }
            StatsUi(
                StatsCalculator.compute(scope.size, scopedCards, scopedReviews, instant, window),
                window,
                deck?.name,
                deck == null,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
