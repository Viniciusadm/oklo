package com.archieapps.oklo.domain

import com.archieapps.oklo.core.time.DayWindow
import com.archieapps.oklo.data.model.CardStat
import com.archieapps.oklo.data.model.Deck
import com.archieapps.oklo.data.model.LibrarySort
import java.text.Collator
import java.util.Locale

data class DeckCounts(
    val total: Int = 0,
    val newCount: Int = 0,
    val learningDue: Int = 0,
    val reviewDue: Int = 0,
    val suspended: Int = 0,
) {
    val pending: Int get() = learningDue + reviewDue

    operator fun plus(other: DeckCounts): DeckCounts = DeckCounts(
        total = total + other.total,
        newCount = newCount + other.newCount,
        learningDue = learningDue + other.learningDue,
        reviewDue = reviewDue + other.reviewDue,
        suspended = suspended + other.suspended,
    )
}

data class DeckNode(
    val deck: Deck,
    val depth: Int,
    val path: List<String>,
    val children: List<DeckNode>,
    val own: DeckCounts,
    val aggregate: DeckCounts,
    val descendantCount: Int,
    val lastActivity: Long,
    val lastStudied: Long?,
) {
    val id: String get() = deck.id
    val pathLabel: String get() = path.joinToString(" / ")
    val hasChildren: Boolean get() = children.isNotEmpty()
}

enum class DeckFilter(val label: String) {
    All("Todos"),
    Pending("Com revisões pendentes"),
    WithNew("Com cards novos"),
    Recent("Criados recentemente"),
}

object DeckTree {
    const val RECENT_WINDOW_MS: Long = 7L * 24 * 60 * 60 * 1000

    private val collator: Collator = Collator.getInstance(Locale.forLanguageTag("pt-BR")).apply {
        strength = Collator.PRIMARY
    }

    fun countsFor(stats: List<CardStat>, window: DayWindow): Map<String, DeckCounts> =
        stats.groupBy { it.deckId }.mapValues { (_, cards) -> countCards(cards, window) }

    fun countCards(cards: List<CardStat>, window: DayWindow): DeckCounts {
        var total = 0
        var newCount = 0
        var learning = 0
        var review = 0
        var suspended = 0
        cards.forEach { card ->
            total++
            if (card.suspended) {
                suspended++
                return@forEach
            }
            val dueToday = (card.dueAt ?: Long.MIN_VALUE) < window.end
            when (card.state) {
                "new" -> newCount++
                "learning", "relearning" -> if (dueToday) learning++
                "review" -> if (dueToday) review++
            }
        }
        return DeckCounts(total, newCount, learning, review, suspended)
    }

    fun parentOf(decks: List<Deck>): Map<String, String?> {
        val byId = decks.associateBy { it.id }
        val result = HashMap<String, String?>()
        decks.forEach { deck ->
            result[deck.id] = effectiveParent(deck, byId)
        }
        return result
    }

    private fun effectiveParent(deck: Deck, byId: Map<String, Deck>): String? {
        val parentId = deck.parentId ?: return null
        if (!byId.containsKey(parentId)) return null
        val visited = HashSet<String>()
        visited.add(deck.id)
        var cursor: String? = parentId
        while (cursor != null) {
            if (!visited.add(cursor)) return null
            val next = byId[cursor] ?: break
            cursor = next.parentId?.takeIf { byId.containsKey(it) }
        }
        return parentId
    }

    fun build(
        decks: List<Deck>,
        stats: List<CardStat>,
        window: DayWindow,
        sort: LibrarySort,
        lastStudied: Map<String, Long> = emptyMap(),
    ): List<DeckNode> {
        val counts = countsFor(stats, window)
        val parents = parentOf(decks)
        val childrenOf = decks.groupBy { parents[it.id] }
        fun node(deck: Deck, depth: Int, path: List<String>): DeckNode {
            val own = counts[deck.id] ?: DeckCounts()
            val ownPath = path + deck.name
            val children = (childrenOf[deck.id] ?: emptyList()).map { node(it, depth + 1, ownPath) }
            val aggregate = children.fold(own) { acc, child -> acc + child.aggregate }
            val lastActivity = (children.map { it.lastActivity } + (lastStudied[deck.id] ?: deck.updatedAt)).max()
            val subtreeStudied = (children.mapNotNull { it.lastStudied } + listOfNotNull(lastStudied[deck.id])).maxOrNull()
            return DeckNode(
                deck = deck,
                depth = depth,
                path = ownPath,
                children = sortNodes(children, sort),
                own = own,
                aggregate = aggregate,
                descendantCount = children.sumOf { 1 + it.descendantCount },
                lastActivity = lastActivity,
                lastStudied = subtreeStudied,
            )
        }
        val roots = (childrenOf[null] ?: emptyList()).map { node(it, 0, emptyList()) }
        return sortNodes(roots, sort)
    }

    fun sortNodes(nodes: List<DeckNode>, sort: LibrarySort): List<DeckNode> = when (sort) {
        LibrarySort.Name -> nodes.sortedWith { a, b ->
            val byName = collator.compare(a.deck.name, b.deck.name)
            if (byName != 0) byName else a.deck.position.compareTo(b.deck.position)
        }
        LibrarySort.Recent -> nodes.sortedWith(compareByDescending<DeckNode> { it.lastActivity }.thenBy { it.deck.name })
        LibrarySort.Cards -> nodes.sortedWith(compareByDescending<DeckNode> { it.aggregate.total }.thenBy { it.deck.name })
    }

    fun flatten(roots: List<DeckNode>): List<DeckNode> {
        val out = ArrayList<DeckNode>()
        fun walk(node: DeckNode) {
            out.add(node)
            node.children.forEach { walk(it) }
        }
        roots.forEach { walk(it) }
        return out
    }

    fun visible(roots: List<DeckNode>, expanded: Set<String>): List<DeckNode> {
        val out = ArrayList<DeckNode>()
        fun walk(node: DeckNode) {
            out.add(node)
            if (node.id in expanded) node.children.forEach { walk(it) }
        }
        roots.forEach { walk(it) }
        return out
    }

    fun find(roots: List<DeckNode>, id: String): DeckNode? = flatten(roots).firstOrNull { it.id == id }

    fun search(
        roots: List<DeckNode>,
        query: String,
        filter: DeckFilter,
        now: Long,
    ): List<DeckNode> {
        val needle = normalize(query.trim())
        return flatten(roots).filter { node ->
            val matchesQuery = needle.isEmpty() || normalize(node.deck.name).contains(needle)
            matchesQuery && matchesFilter(node, filter, now)
        }.sortedWith { a, b -> collator.compare(a.pathLabel, b.pathLabel) }
    }

    fun matchesFilter(node: DeckNode, filter: DeckFilter, now: Long): Boolean = when (filter) {
        DeckFilter.All -> true
        DeckFilter.Pending -> node.aggregate.pending > 0
        DeckFilter.WithNew -> node.aggregate.newCount > 0
        DeckFilter.Recent -> now - node.deck.createdAt <= RECENT_WINDOW_MS
    }

    fun descendantIds(decks: List<Deck>, id: String): Set<String> {
        val parents = parentOf(decks)
        val childrenOf = decks.groupBy { parents[it.id] }
        val out = LinkedHashSet<String>()
        val stack = ArrayDeque<String>()
        stack.add(id)
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            childrenOf[current]?.forEach { child ->
                if (out.add(child.id)) stack.add(child.id)
            }
        }
        return out
    }

    fun subtreeIds(decks: List<Deck>, id: String): Set<String> = linkedSetOf(id) + descendantIds(decks, id)

    fun ancestors(decks: List<Deck>, id: String): List<Deck> {
        val byId = decks.associateBy { it.id }
        val parents = parentOf(decks)
        val chain = ArrayList<Deck>()
        var cursor: String? = id
        val seen = HashSet<String>()
        while (cursor != null && seen.add(cursor)) {
            val deck = byId[cursor] ?: break
            chain.add(deck)
            cursor = parents[cursor]
        }
        return chain.reversed()
    }

    fun pathLabel(decks: List<Deck>, id: String): String = ancestors(decks, id).joinToString(" / ") { it.name }

    fun canMove(decks: List<Deck>, id: String, newParentId: String?): Boolean {
        if (newParentId == null) return true
        if (newParentId == id) return false
        return newParentId !in descendantIds(decks, id)
    }

    fun normalize(text: String): String =
        java.text.Normalizer.normalize(text.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
}
