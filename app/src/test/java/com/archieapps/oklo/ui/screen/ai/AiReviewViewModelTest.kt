package com.archieapps.oklo.ui.screen.ai

import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.LessonGenerateRequest
import com.archieapps.oklo.core.net.LessonJobBody
import com.archieapps.oklo.core.net.LessonJobStarted
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardType
import com.archieapps.oklo.data.repo.AiDraftStore
import com.archieapps.oklo.data.repo.DraftBatch
import com.archieapps.oklo.data.repo.DraftItem
import com.archieapps.oklo.data.repo.LessonGenerator
import com.archieapps.oklo.data.repo.LessonJobState
import com.archieapps.oklo.data.repo.LessonJobTracker
import com.archieapps.oklo.data.sync.SyncReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiReviewViewModelTest {
    private val events = mutableListOf<String>()
    private val draft = AiDraftStore()
    private var lessonOrigin: String? = null
    private var signedIn = true
    private var online = true
    private var syncReport: SyncReport? = SyncReport.Done(pushed = 0, pulled = 0, applied = 0, rejected = 0, usn = 1)
    private var start: ApiResult<LessonJobStarted> = ApiResult.Success(LessonJobStarted("job-1"))
    private val requests = mutableListOf<LessonGenerateRequest>()
    private val tracked = mutableListOf<Pair<String, String>>()
    private val savedContents = mutableListOf<List<CardContent>>()
    private var codePrompts = 0
    private var retryAfterCode: (() -> Unit)? = null

    private var generatorThrows = false
    private var syncThrows = false

    private val generator = object : LessonGenerator {
        override suspend fun lessonGenerate(request: LessonGenerateRequest): ApiResult<LessonJobStarted> {
            if (generatorThrows) error("boom")
            events += "post"
            requests += request
            return start
        }

        override suspend fun lessonJob(jobId: String): ApiResult<LessonJobBody> = error("the review never polls")
    }

    private val tracker = object : LessonJobTracker {
        override suspend fun track(deckId: String, jobId: String) {
            events += "track"
            tracked += deckId to jobId
        }

        override fun jobState(deckId: String): Flow<LessonJobState?> = flowOf(null)

        override fun clear(deckId: String) = Unit
    }

    @Before
    fun setUp() {
        events.clear()
        requests.clear()
        tracked.clear()
        savedContents.clear()
        lessonOrigin = null
        signedIn = true
        online = true
        codePrompts = 0
        retryAfterCode = null
        generatorThrows = false
        syncThrows = false
        syncReport = SyncReport.Done(pushed = 0, pulled = 0, applied = 0, rejected = 0, usn = 1)
        start = ApiResult.Success(LessonJobStarted("job-1"))
        draft.batch.value = DraftBatch(
            deckId = "deck-1",
            items = listOf(DraftItem("k1", CardContent(CardType.Basic, "Frente", "Verso"))),
            discarded = 0,
            warnings = emptyList(),
            model = null,
            sourceText = "Texto de origem",
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): AiReviewViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return AiReviewViewModel(
            draft = draft,
            deckExists = { true },
            createCards = { _, contents ->
                events += "save"
                savedContents += contents
                contents.size
            },
            lessonOriginOf = { lessonOrigin },
            isSignedIn = { signedIn },
            requestSync = { events += "requestSync" },
            syncNow = {
                if (syncThrows) error("boom")
                events += "sync"
                syncReport
            },
            generator = generator,
            tracker = tracker,
            model = { "pro" },
            isOnline = { online },
            decksTree = flowOf(emptyList()),
            openCodePrompt = { retry ->
                codePrompts++
                retryAfterCode = retry
            },
        )
    }

    @Test
    fun defaultsToCheckedWhenDeckHasNoLesson() = runTest {
        lessonOrigin = null
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.lessonOption.value.checked)
        assertFalse(vm.lessonOption.value.replacesManualLesson)
    }

    @Test
    fun defaultsToCheckedWhenLessonIsAi() = runTest {
        lessonOrigin = "ai"
        val vm = viewModel()
        runCurrent()
        assertTrue(vm.lessonOption.value.checked)
        assertFalse(vm.lessonOption.value.replacesManualLesson)
    }

    @Test
    fun defaultsToUncheckedWhenLessonIsManual() = runTest {
        lessonOrigin = "manual"
        val vm = viewModel()
        runCurrent()
        assertFalse(vm.lessonOption.value.checked)
        assertTrue(vm.lessonOption.value.replacesManualLesson)
    }

    @Test
    fun userChoiceIsKeptUntilTheDeckChanges() = runTest {
        lessonOrigin = null
        val vm = viewModel()
        runCurrent()
        vm.setGenerateLesson(false)
        assertFalse(vm.lessonOption.value.checked)
        runCurrent()
        assertFalse(vm.lessonOption.value.checked)
        lessonOrigin = "manual"
        vm.changeDeck("deck-2")
        runCurrent()
        assertFalse(vm.lessonOption.value.checked)
        assertTrue(vm.lessonOption.value.replacesManualLesson)
        lessonOrigin = "ai"
        vm.changeDeck("deck-3")
        runCurrent()
        assertTrue(vm.lessonOption.value.checked)
    }

    @Test
    fun savingWithOptionSavesThenSyncsThenGeneratesAndTracks() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.addSelected()
        runCurrent()
        assertEquals(listOf("save", "sync", "post", "track"), events)
        assertEquals(listOf(LessonGenerateRequest("deck-1", "Texto de origem", "pro")), requests)
        assertEquals(listOf("deck-1" to "job-1"), tracked)
        assertNotNull(vm.summary.value)
        assertNull(vm.summary.value?.lessonMessage)
        assertTrue(vm.summary.value?.lessonStarted == true)
    }

    @Test
    fun savingWithoutOptionMakesNoLessonCall() = runTest {
        val vm = viewModel()
        runCurrent()
        vm.setGenerateLesson(false)
        vm.addSelected()
        runCurrent()
        assertEquals(listOf("save", "requestSync"), events)
        assertTrue(requests.isEmpty())
        assertTrue(tracked.isEmpty())
        assertFalse(vm.summary.value?.lessonStarted == true)
    }

    @Test
    fun startFailureKeepsCardsAndShowsMessage() = runTest {
        start = ApiResult.Failure.Network
        val vm = viewModel()
        runCurrent()
        vm.addSelected()
        runCurrent()
        assertEquals(1, savedContents.size)
        assertTrue(tracked.isEmpty())
        assertEquals(1, vm.summary.value?.added)
        assertEquals(AiReviewViewModel.LESSON_START_FAILED, vm.summary.value?.lessonMessage)
        assertNull(vm.error.value)
    }

    @Test
    fun syncFailureKeepsCardsAndSkipsLessonCall() = runTest {
        syncReport = SyncReport.Failed(ApiResult.Failure.Network, SyncReport.Stage.Push)
        val vm = viewModel()
        runCurrent()
        vm.addSelected()
        runCurrent()
        assertTrue(requests.isEmpty())
        assertEquals(AiReviewViewModel.LESSON_START_FAILED, vm.summary.value?.lessonMessage)
    }

    @Test
    fun missingCodeOpensPromptAndRetryStartsLesson() = runTest {
        start = ApiResult.Failure.CodeMissing
        val vm = viewModel()
        runCurrent()
        vm.addSelected()
        runCurrent()
        assertEquals(1, codePrompts)
        start = ApiResult.Success(LessonJobStarted("job-2"))
        retryAfterCode?.invoke()
        runCurrent()
        assertEquals(listOf("deck-1" to "job-2"), tracked)
        assertNull(vm.summary.value?.lessonMessage)
        assertTrue(vm.summary.value?.lessonStarted == true)
    }

    @Test
    fun startFailureStillRequestsBackupSync() = runTest {
        start = ApiResult.Failure.Network
        val vm = viewModel()
        runCurrent()
        vm.addSelected()
        runCurrent()
        assertEquals(listOf("save", "sync", "post", "requestSync"), events)
    }

    @Test
    fun syncFailureStillRequestsBackupSync() = runTest {
        syncReport = SyncReport.Failed(ApiResult.Failure.Network, SyncReport.Stage.Push)
        val vm = viewModel()
        runCurrent()
        vm.addSelected()
        runCurrent()
        assertEquals(listOf("save", "sync", "requestSync"), events)
    }

    @Test
    fun offlineSkipsLessonButStillBacksUp() = runTest {
        online = false
        val vm = viewModel()
        runCurrent()
        vm.addSelected()
        runCurrent()
        assertEquals(listOf("save", "requestSync"), events)
        assertEquals(AiReviewViewModel.LESSON_START_FAILED, vm.summary.value?.lessonMessage)
    }

    @Test
    fun notSignedInShowsSignInMessageWithoutSync() = runTest {
        signedIn = false
        syncReport = null
        val vm = viewModel()
        runCurrent()
        vm.addSelected()
        runCurrent()
        assertEquals(AiReviewViewModel.LESSON_NEEDS_SIGN_IN, vm.summary.value?.lessonMessage)
        assertFalse(events.contains("requestSync"))
    }

    @Test
    fun throwingGeneratorDoesNotLeaveSavingStuck() = runTest {
        generatorThrows = true
        val vm = viewModel()
        runCurrent()
        vm.addSelected()
        runCurrent()
        assertFalse(vm.saving.value)
        assertEquals(AiReviewViewModel.LESSON_START_FAILED, vm.summary.value?.lessonMessage)
        assertEquals(1, savedContents.size)
        assertTrue(events.contains("requestSync"))
    }

    @Test
    fun throwingSyncDoesNotLeaveSavingStuck() = runTest {
        syncThrows = true
        val vm = viewModel()
        runCurrent()
        vm.addSelected()
        runCurrent()
        assertFalse(vm.saving.value)
        assertEquals(AiReviewViewModel.LESSON_START_FAILED, vm.summary.value?.lessonMessage)
    }
}
