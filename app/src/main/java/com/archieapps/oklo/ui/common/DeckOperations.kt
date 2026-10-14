package com.archieapps.oklo.ui.common

import com.archieapps.oklo.Container
import com.archieapps.oklo.data.model.Deck
import com.archieapps.oklo.data.repo.DeckNameException
import com.archieapps.oklo.data.repo.DeleteImpact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class DeckOperations(
    private val container: Container,
    private val scope: CoroutineScope,
    private val onCreated: (Deck) -> Unit = {},
    private val onMoved: (String?) -> Unit = {},
) {
    val message = MutableStateFlow<String?>(null)

    fun validateName(name: String): String? = try {
        container.decks.validateName(name)
        null
    } catch (problem: DeckNameException) {
        problem.message
    }

    fun create(name: String, parentId: String?) {
        scope.launch {
            runCatching { container.decks.create(name, parentId) }
                .onSuccess {
                    onCreated(it)
                    message.value = "Deck “${it.name}” criado."
                }
                .onFailure { message.value = it.message ?: "Não foi possível criar o deck." }
        }
    }

    fun rename(id: String, name: String) {
        scope.launch {
            runCatching { container.decks.rename(id, name) }
                .onSuccess { message.value = "Nome atualizado." }
                .onFailure { message.value = it.message ?: "Não foi possível renomear." }
        }
    }

    fun move(id: String, parentId: String?) {
        scope.launch {
            val moved = container.decks.move(id, parentId)
            message.value = if (moved) "Deck movido." else "Não é possível mover um deck para dentro dele mesmo."
            if (moved) onMoved(parentId)
        }
    }

    fun duplicate(id: String) {
        scope.launch {
            val copy = container.decks.duplicate(id)
            message.value = if (copy != null) "Cópia “${copy.name}” criada, com os cards como novos." else "Não foi possível duplicar."
        }
    }

    suspend fun impact(id: String): DeleteImpact = container.decks.impact(id)

    fun delete(id: String, name: String, after: () -> Unit = {}) {
        scope.launch {
            container.decks.delete(id)
            message.value = "Deck “$name” excluído."
            after()
        }
    }

    fun consumeMessage() {
        message.value = null
    }
}
