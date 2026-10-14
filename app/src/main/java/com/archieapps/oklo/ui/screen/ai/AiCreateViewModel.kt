package com.archieapps.oklo.ui.screen.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.Container
import com.archieapps.oklo.core.net.AiStatusBody
import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.DraftRequest
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardType
import com.archieapps.oklo.data.repo.AiForm
import com.archieapps.oklo.data.repo.AiRepository
import com.archieapps.oklo.data.repo.DraftBatch
import com.archieapps.oklo.data.repo.DraftDifficulty
import com.archieapps.oklo.data.repo.DraftItem
import com.archieapps.oklo.data.repo.LessonOption
import com.archieapps.oklo.data.repo.LessonOptionResolver
import com.archieapps.oklo.data.repo.newId
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.domain.DeckTree
import com.archieapps.oklo.ui.common.DeckOperations
import com.archieapps.oklo.ui.components.CodePromptController
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AiErrorKind { Validation, TooLong, NotConfigured, Unreachable, InvalidResponse, RateLimited, Network, Unauthorized, Code, Other }

data class AiError(val kind: AiErrorKind, val message: String) {
    val retryable: Boolean
        get() = kind == AiErrorKind.Unreachable || kind == AiErrorKind.InvalidResponse || kind == AiErrorKind.Network || kind == AiErrorKind.RateLimited
}

data class Readiness(
    val signedIn: Boolean,
    val online: Boolean,
    val status: AiStatusBody?,
    val statusChecked: Boolean,
)

class AiCreateViewModel(private val container: Container, initialDeckId: String?) : ViewModel() {
    val form: StateFlow<AiForm> = container.aiDraft.form
    val pendingBatch: StateFlow<DraftBatch?> = container.aiDraft.batch
    val generating = MutableStateFlow(false)
    val error = MutableStateFlow<AiError?>(null)
    val notice = MutableStateFlow<String?>(null)
    val readiness = MutableStateFlow<Readiness?>(null)
    val codePrompt = CodePromptController(container.codeGate)
    val operations = DeckOperations(container, viewModelScope, onCreated = { deck -> chooseDeck(deck.id) })
    private val _navigateToReview = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToReview: SharedFlow<Unit> = _navigateToReview
    private var job: Job? = null

    val roots: StateFlow<List<DeckNode>?> = combine(container.decks.decks, container.settings.settings) { decks, settings ->
        DeckTree.build(decks, emptyList(), LocalDay.window(System.currentTimeMillis()), settings.librarySort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val lessonOptions = LessonOptionResolver { container.lessons.get(it)?.origin }

    val lessonOption: StateFlow<LessonOption> = container.aiDraft.form
        .map { it.deckId to it.generateLesson }
        .distinctUntilChanged()
        .map { (deckId, choice) -> if (deckId == null) LessonOption(checked = choice ?: true) else lessonOptions.resolve(deckId, choice) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LessonOption(checked = true))

    val model: StateFlow<String> = container.settings.settings.map { it.aiModel.label }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    init {
        if (initialDeckId != null) {
            container.aiDraft.form.update { it.copy(deckId = initialDeckId) }
        }
        viewModelScope.launch {
            container.sessionStore.session.map { it.signedIn }.distinctUntilChanged().collect { refreshReadiness() }
        }
    }

    fun refreshReadiness() {
        viewModelScope.launch {
            val signedIn = container.sessionStore.load().signedIn
            val online = container.connectivity.isOnline()
            readiness.value = Readiness(signedIn, online, null, statusChecked = false)
            if (signedIn && online) {
                val status = (container.ai.status() as? ApiResult.Success)?.data
                readiness.value = Readiness(signedIn, online, status, statusChecked = true)
            }
        }
    }

    fun setText(text: String) {
        container.aiDraft.form.update { it.copy(text = text) }
        if (error.value?.kind == AiErrorKind.Validation || error.value?.kind == AiErrorKind.TooLong) error.value = null
    }

    fun chooseDeck(id: String?) = container.aiDraft.form.update { it.copy(deckId = id, generateLesson = null) }

    fun setGenerateLesson(checked: Boolean) = container.aiDraft.form.update { it.copy(generateLesson = checked) }

    fun setMaxCards(value: Int?) = container.aiDraft.form.update { it.copy(maxCards = value) }

    fun setDifficulty(value: DraftDifficulty) = container.aiDraft.form.update { it.copy(difficulty = value) }

    fun toggleType(type: CardType) = container.aiDraft.form.update {
        it.copy(types = if (type in it.types) it.types - type else it.types + type)
    }

    fun clearTypes() = container.aiDraft.form.update { it.copy(types = emptySet()) }

    fun generate() {
        if (generating.value) return
        val current = form.value
        val deckId = current.deckId
        val text = current.text
        when {
            deckId == null -> {
                error.value = AiError(AiErrorKind.Validation, "Escolha o deck de destino.")
                return
            }
            text.isBlank() -> {
                error.value = AiError(AiErrorKind.Validation, "Cole ou digite um texto para gerar cards.")
                return
            }
            text.length > AiRepository.MAX_TEXT -> {
                error.value = AiError(AiErrorKind.TooLong, tooLongMessage(text.length))
                return
            }
        }
        if (!container.connectivity.isOnline()) {
            error.value = AiError(AiErrorKind.Network, "Sem conexão com a internet. A geração com IA precisa de rede.")
            return
        }
        val path = roots.value?.let { DeckTree.find(it, deckId!!)?.pathLabel }
        error.value = null
        notice.value = null
        generating.value = true
        job = viewModelScope.launch {
            val model = container.settings.current().aiModel.wire
            val result = container.ai.draft(
                DraftRequest(
                    text = text,
                    deckPath = path?.take(500),
                    maxCards = current.maxCards,
                    difficulty = current.difficulty.wire,
                    types = current.types.map { it.wire },
                    model = model,
                ),
            )
            generating.value = false
            when (result) {
                is ApiResult.Success -> {
                    val items = result.data.cards.map { draft ->
                        DraftItem(
                            key = newId(),
                            content = CardContent(
                                type = CardType.of(draft.type),
                                front = draft.front,
                                back = draft.back,
                                explanation = draft.explanation,
                                answer = draft.answer,
                                choices = draft.choices,
                            ),
                        )
                    }
                    container.aiDraft.batch.value = DraftBatch(
                        deckId = deckId!!,
                        items = items,
                        discarded = result.data.misc.discarded,
                        warnings = result.data.misc.warnings,
                        model = result.data.misc.model,
                        sourceText = text,
                        generateLesson = lessonOption.value.checked,
                    )
                    _navigateToReview.tryEmit(Unit)
                }

                ApiResult.Failure.CodeMissing -> codePrompt.open { generate() }
                is ApiResult.Failure -> error.value = map(result)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        generating.value = false
        notice.value = "Geração cancelada. Seu texto foi mantido."
    }

    private fun tooLongMessage(length: Int): String =
        "O texto tem $length caracteres; o limite é ${AiRepository.MAX_TEXT}. Divida o texto em partes menores e gere uma de cada vez."

    private fun map(failure: ApiResult.Failure): AiError = when (failure) {
        is ApiResult.Failure.AiNotConfigured -> AiError(AiErrorKind.NotConfigured, "A IA não está configurada no servidor. Você ainda pode adicionar cards manualmente.")
        is ApiResult.Failure.AiUnreachable -> AiError(AiErrorKind.Unreachable, "Falha de conexão entre o servidor e a IA. Nada foi gerado; tente de novo em instantes.")
        is ApiResult.Failure.AiInvalidResponse -> AiError(AiErrorKind.InvalidResponse, "A IA respondeu em um formato inválido e nenhum card pôde ser aproveitado. Tente de novo.")
        is ApiResult.Failure.TextTooLong -> AiError(AiErrorKind.TooLong, failure.detail)
        is ApiResult.Failure.RateLimited -> AiError(AiErrorKind.RateLimited, failure.userMessage)
        ApiResult.Failure.Network -> AiError(AiErrorKind.Network, "Sem conexão com o servidor. Verifique a internet e tente de novo.")
        ApiResult.Failure.Unauthorized -> AiError(AiErrorKind.Unauthorized, "Sua sessão expirou. Entre de novo para usar a IA.")
        ApiResult.Failure.CodeRejected -> AiError(AiErrorKind.Code, "Código recusado. Confira o código do dia antes de tentar de novo.")
        ApiResult.Failure.CodeHardStopped -> AiError(AiErrorKind.Code, failure.userMessage)
        is ApiResult.Failure.Validation -> AiError(AiErrorKind.Validation, failure.detail)
        else -> AiError(AiErrorKind.Other, failure.userMessage)
    }
}
