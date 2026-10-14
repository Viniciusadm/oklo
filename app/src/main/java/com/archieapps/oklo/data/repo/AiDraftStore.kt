package com.archieapps.oklo.data.repo

import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardType
import kotlinx.coroutines.flow.MutableStateFlow

enum class DraftDifficulty(val wire: String, val label: String) {
    Auto("auto", "Automática"),
    Basic("basic", "Básica"),
    Intermediate("intermediate", "Intermediária"),
    Advanced("advanced", "Avançada"),
}

data class AiForm(
    val deckId: String? = null,
    val text: String = "",
    val maxCards: Int? = null,
    val difficulty: DraftDifficulty = DraftDifficulty.Auto,
    val types: Set<CardType> = emptySet(),
)

data class DraftItem(
    val key: String,
    val content: CardContent,
    val selected: Boolean = true,
)

data class DraftBatch(
    val deckId: String,
    val items: List<DraftItem>,
    val discarded: Int,
    val warnings: List<String>,
    val model: String?,
)

class AiDraftStore {
    val form = MutableStateFlow(AiForm())
    val batch = MutableStateFlow<DraftBatch?>(null)

    fun reset() {
        form.value = AiForm(deckId = form.value.deckId)
        batch.value = null
    }
}
