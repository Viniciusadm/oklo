package com.archieapps.oklo.ui.screen.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archieapps.oklo.Container
import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.LessonGenerateRequest
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.repo.AiDraftStore
import com.archieapps.oklo.data.repo.DraftBatch
import com.archieapps.oklo.data.repo.DraftItem
import com.archieapps.oklo.data.repo.LessonGenerator
import com.archieapps.oklo.data.repo.LessonJobTracker
import com.archieapps.oklo.data.repo.newId
import com.archieapps.oklo.data.sync.SyncReport
import com.archieapps.oklo.domain.DeckNode
import com.archieapps.oklo.domain.DeckTree
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddSummary(
    val added: Int,
    val skipped: Int,
    val deckId: String,
    val deckPath: String,
    val synced: Boolean,
    val lessonStarted: Boolean = false,
    val lessonMessage: String? = null,
)

data class LessonOption(val checked: Boolean = false, val replacesManualLesson: Boolean = false)

class AiReviewViewModel(
    private val draft: AiDraftStore,
    private val deckExists: suspend (String) -> Boolean,
    private val createCards: suspend (String, List<CardContent>) -> Int,
    private val lessonOriginOf: suspend (String) -> String?,
    private val isSignedIn: suspend () -> Boolean,
    private val requestSync: () -> Unit,
    private val syncNow: suspend () -> SyncReport?,
    private val generator: LessonGenerator,
    private val tracker: LessonJobTracker,
    private val model: suspend () -> String,
    private val isOnline: () -> Boolean,
    decksTree: Flow<List<DeckNode>>,
    var openCodePrompt: (onProvided: () -> Unit) -> Unit = {},
) : ViewModel() {
    constructor(container: Container) : this(
        draft = container.aiDraft,
        deckExists = { container.decks.get(it) != null },
        createCards = { deckId, contents -> container.cards.createMany(deckId, contents).size },
        lessonOriginOf = { container.lessons.get(it)?.origin },
        isSignedIn = { container.sessionStore.load().signedIn },
        requestSync = container.sync::requestSync,
        syncNow = container.sync::syncIfSignedIn,
        generator = container.ai,
        tracker = container.lessonAiFinisher,
        model = { container.settings.current().aiModel.wire },
        isOnline = container.connectivity::isOnline,
        decksTree = combine(container.decks.decks, container.settings.settings) { decks, settings ->
            DeckTree.build(decks, emptyList(), LocalDay.window(System.currentTimeMillis()), settings.librarySort)
        },
    )

    val batch: StateFlow<DraftBatch?> = draft.batch
    val summary = MutableStateFlow<AddSummary?>(null)
    val error = MutableStateFlow<String?>(null)
    val saving = MutableStateFlow(false)
    val lessonOption = MutableStateFlow(LessonOption())

    val roots: StateFlow<List<DeckNode>> = decksTree
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            batch.map { it?.deckId }.filterNotNull().distinctUntilChanged().collect { deckId ->
                val origin = lessonOriginOf(deckId)
                val manual = origin != null && origin != ORIGIN_AI
                lessonOption.value = LessonOption(checked = !manual, replacesManualLesson = manual)
            }
        }
    }

    fun setGenerateLesson(checked: Boolean) {
        lessonOption.update { it.copy(checked = checked) }
    }

    private fun edit(transform: (DraftBatch) -> DraftBatch) {
        draft.batch.update { current -> current?.let(transform) }
        error.value = null
    }

    fun toggle(key: String) = edit { batch ->
        batch.copy(items = batch.items.map { if (it.key == key) it.copy(selected = !it.selected) else it })
    }

    fun selectAll(selected: Boolean) = edit { batch -> batch.copy(items = batch.items.map { it.copy(selected = selected) }) }

    fun remove(key: String) = edit { batch -> batch.copy(items = batch.items.filterNot { it.key == key }) }

    fun duplicate(key: String) = edit { batch ->
        val index = batch.items.indexOfFirst { it.key == key }
        if (index < 0) return@edit batch
        val copy = batch.items[index].copy(key = newId())
        batch.copy(items = batch.items.toMutableList().apply { add(index + 1, copy) })
    }

    fun replace(key: String, content: CardContent): String? {
        content.validationError()?.let { return it }
        edit { batch -> batch.copy(items = batch.items.map { if (it.key == key) it.copy(content = content.normalized()) else it }) }
        return null
    }

    fun changeDeck(deckId: String?) {
        if (deckId == null) return
        edit { it.copy(deckId = deckId) }
    }

    fun addSelected() {
        val current = batch.value ?: return
        if (saving.value) return
        val chosen: List<DraftItem> = current.items.filter { it.selected }
        if (chosen.isEmpty()) {
            error.value = "Selecione pelo menos um card para adicionar."
            return
        }
        val invalid = chosen.indexOfFirst { it.content.validationError() != null }
        if (invalid >= 0) {
            error.value = "O card ${current.items.indexOf(chosen[invalid]) + 1} precisa de ajuste: ${chosen[invalid].content.validationError()}"
            return
        }
        saving.value = true
        val generateLesson = lessonOption.value.checked
        viewModelScope.launch {
            runCatching {
                if (!deckExists(current.deckId)) error("O deck de destino não existe mais. Escolha outro.")
                createCards(current.deckId, chosen.map { it.content })
            }.onSuccess { created ->
                val path = DeckTree.find(roots.value, current.deckId)?.pathLabel ?: ""
                draft.batch.value = null
                draft.form.update { it.copy(text = "") }
                val signedIn = isSignedIn()
                val base = AddSummary(created, current.items.size - chosen.size, current.deckId, path, signedIn)
                if (generateLesson) {
                    val message = startLesson(current.deckId, current.sourceText)
                    summary.value = base.copy(lessonStarted = message == null, lessonMessage = message)
                } else {
                    summary.value = base
                    if (signedIn) requestSync()
                }
            }.onFailure { problem ->
                error.value = problem.message ?: "Não foi possível adicionar os cards."
            }
            saving.value = false
        }
    }

    private suspend fun startLesson(deckId: String, sourceText: String): String? {
        if (!isOnline()) return LESSON_START_FAILED
        when (val report = syncNow()) {
            is SyncReport.Done -> Unit
            null -> return LESSON_NEEDS_SIGN_IN
            is SyncReport.Failed -> return LESSON_START_FAILED
        }
        val request = LessonGenerateRequest(
            deckId = deckId,
            text = sourceText.trim().ifEmpty { null },
            model = model(),
        )
        return when (val result = generator.lessonGenerate(request)) {
            is ApiResult.Success -> {
                tracker.track(deckId, result.data.jobId)
                null
            }
            ApiResult.Failure.CodeMissing -> {
                openCodePrompt { retryLesson(deckId, sourceText) }
                LESSON_START_FAILED
            }
            else -> LESSON_START_FAILED
        }
    }

    private fun retryLesson(deckId: String, sourceText: String) {
        viewModelScope.launch {
            val message = startLesson(deckId, sourceText)
            summary.update { it?.copy(lessonMessage = message, lessonStarted = message == null) }
        }
    }

    fun discard() {
        draft.batch.value = null
    }

    companion object {
        private const val ORIGIN_AI = "ai"
        const val LESSON_START_FAILED = "Os cards foram salvos, mas não foi possível iniciar a aula agora. Você pode gerar a aula depois, no deck."
        const val LESSON_NEEDS_SIGN_IN = "Os cards foram salvos. Entre na conta para gerar a aula depois, no deck."
    }
}
