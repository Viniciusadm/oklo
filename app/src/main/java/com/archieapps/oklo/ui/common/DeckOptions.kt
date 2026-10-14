package com.archieapps.oklo.ui.common

import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.domain.DeckTree
import com.archieapps.oklo.ui.components.DeckOption

fun deckOptions(
    roots: List<DeckNode>,
    disabledSubtreeOf: String? = null,
    includeRoot: Boolean = false,
): List<DeckOption> {
    val disabled = disabledSubtreeOf?.let { id ->
        DeckTree.find(roots, id)?.let { node -> DeckTree.flatten(listOf(node)).map { it.id }.toSet() }
    }.orEmpty()
    val items = DeckTree.flatten(roots).map { node ->
        DeckOption(node.id, node.deck.name, node.depth, enabled = node.id !in disabled)
    }
    return if (includeRoot) listOf(DeckOption(null, "Nenhum (nível principal)", 0)) + items else items
}
