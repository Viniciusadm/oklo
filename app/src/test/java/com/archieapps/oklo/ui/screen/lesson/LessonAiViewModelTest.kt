package com.archieapps.oklo.ui.screen.lesson

import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.LessonGenerateRequest
import com.archieapps.oklo.core.net.LessonJobBody
import com.archieapps.oklo.core.net.LessonJobStarted
import com.archieapps.oklo.core.net.LessonLinkDto
import com.archieapps.oklo.core.net.OkloJson
import com.archieapps.oklo.data.repo.LessonGenerator
import com.archieapps.oklo.data.repo.LessonJobState
import com.archieapps.oklo.data.repo.LessonJobTracker
import com.archieapps.oklo.data.sync.SyncReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LessonAiViewModelTest {
    private val events = mutableListOf<String>()

    private inner class FakeGenerator : LessonGenerator {
        val requests = mutableListOf<LessonGenerateRequest>()
        var start: ApiResult<LessonJobStarted> = ApiResult.Success(LessonJobStarted("job-1"))

        override suspend fun lessonGenerate(request: LessonGenerateRequest): ApiResult<LessonJobStarted> {
            events += "post"
            requests += request
            return start
        }

        override suspend fun lessonJob(jobId: String): ApiResult<LessonJobBody> = error("the sheet never polls")
    }

    private inner class FakeTracker : LessonJobTracker {
        val states = MutableStateFlow<Map<String, LessonJobState>>(emptyMap())
        val tracked = mutableListOf<Pair<String, String>>()

        override suspend fun track(deckId: String, jobId: String) {
            events += "track"
            tracked += deckId to jobId
            emit(deckId, LessonJobState.Polling)
        }

        override fun jobState(deckId: String): Flow<LessonJobState?> = states.map { it[deckId] }

        override fun clear(deckId: String) {
            if (states.value[deckId] != LessonJobState.Polling) states.value = states.value - deckId
        }

        fun emit(deckId: String, state: LessonJobState) {
            states.value = states.value + (deckId to state)
        }
    }

    private var codePrompts = 0
    private var retryAfterCode: (() -> Unit)? = null
    private var online = true
    private var syncReport: SyncReport? = SyncReport.Done(pushed = 0, pulled = 0, applied = 0, rejected = 0, usn = 1)
    private val tracker = FakeTracker()

    @Before
    fun setUp() {
        events.clear()
        codePrompts = 0
        retryAfterCode = null
        online = true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(generator: FakeGenerator): LessonAiViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return LessonAiViewModel(
            deckId = "deck-1",
            generator = generator,
            tracker = tracker,
            model = { "pro" },
            isOnline = { online },
            syncNow = {
                events += "sync"
                syncReport
            },
            openCodePrompt = { retry ->
                codePrompts++
                retryAfterCode = retry
            },
        )
    }

    @Test
    fun requestBodyCarriesDeckTextAndModel() = runTest {
        val generator = FakeGenerator()
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.setText("Notas de apoio")
        vm.submit()
        runCurrent()
        assertEquals(listOf(LessonGenerateRequest("deck-1", "Notas de apoio", "pro")), generator.requests)
        val encoded = OkloJson.instance.encodeToString(LessonGenerateRequest.serializer(), generator.requests.single())
        assertEquals("""{"deckId":"deck-1","text":"Notas de apoio","model":"pro"}""", encoded)
    }

    @Test
    fun blankTextIsSentAsNull() = runTest {
        val generator = FakeGenerator()
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.setText("   ")
        vm.submit()
        runCurrent()
        assertNull(generator.requests.single().text)
    }

    @Test
    fun textOverLimitIsRejectedWithoutRequest() = runTest {
        val generator = FakeGenerator()
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.setText("a".repeat(12001))
        vm.submit()
        runCurrent()
        assertTrue(generator.requests.isEmpty())
        assertTrue(vm.state.value.error != null)
    }

    @Test
    fun fullSyncFinishesBeforeTheRequestAndTheJobIsHandedToTheTracker() = runTest {
        val generator = FakeGenerator()
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertEquals(listOf("sync", "post", "track"), events)
        assertEquals(listOf("deck-1" to "job-1"), tracker.tracked)
        assertEquals(LessonAiPhase.Polling, vm.state.value.phase)
    }

    @Test
    fun phaseMirrorsTheTrackerEvenWhenTheJobWasStartedElsewhere() = runTest {
        val vm = viewModel(FakeGenerator())
        runCurrent()
        tracker.emit("deck-1", LessonJobState.Polling)
        runCurrent()
        assertEquals(LessonAiPhase.Polling, vm.state.value.phase)
        assertTrue(vm.state.value.busy)
        tracker.emit("deck-1", LessonJobState.Done(listOf("Aviso")))
        runCurrent()
        assertEquals(LessonAiPhase.Done, vm.state.value.phase)
        assertEquals(listOf("Aviso"), vm.state.value.warnings)
        tracker.emit("deck-1", LessonJobState.Failed("A IA falhou."))
        runCurrent()
        assertEquals(LessonAiPhase.Failed, vm.state.value.phase)
        assertEquals("A IA falhou.", vm.state.value.error)
        tracker.emit("deck-1", LessonJobState.Delayed)
        runCurrent()
        assertEquals(LessonAiPhase.Delayed, vm.state.value.phase)
    }

    @Test
    fun failedSyncBeforeSendingShowsAClearMessageAndSkipsTheRequest() = runTest {
        syncReport = SyncReport.Failed(ApiResult.Failure.Network, SyncReport.Stage.Push)
        val generator = FakeGenerator()
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertTrue(generator.requests.isEmpty())
        assertEquals(LessonAiPhase.Form, vm.state.value.phase)
        assertEquals(LessonAiViewModel.SYNC_FAILED, vm.state.value.error)
    }

    @Test
    fun notSignedInSyncShowsAClearMessage() = runTest {
        syncReport = null
        val generator = FakeGenerator()
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertTrue(generator.requests.isEmpty())
        assertEquals(ApiResult.Failure.NotSignedIn.userMessage, vm.state.value.error)
    }

    @Test
    fun deckMissingOnServerShowsAClearMessageInsteadOfTheRawValidation() = runTest {
        val generator = FakeGenerator()
        generator.start = ApiResult.Failure.Validation("The selected deck id is invalid.")
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertEquals(LessonAiPhase.Form, vm.state.value.phase)
        assertEquals(LessonAiViewModel.DECK_NOT_ON_SERVER, vm.state.value.error)
        assertFalse(vm.state.value.error!!.contains("invalid"))
        assertTrue(tracker.tracked.isEmpty())
    }

    @Test
    fun retryAfterFailureStartsANewJob() = runTest {
        val generator = FakeGenerator()
        val vm = viewModel(generator)
        tracker.emit("deck-1", LessonJobState.Failed("A IA falhou."))
        runCurrent()
        generator.start = ApiResult.Success(LessonJobStarted("job-2"))
        vm.retry()
        runCurrent()
        assertEquals(listOf("deck-1" to "job-2"), tracker.tracked)
        assertEquals(LessonAiPhase.Polling, vm.state.value.phase)
    }

    @Test
    fun replacingExistingLessonRequiresConfirmation() = runTest {
        val generator = FakeGenerator()
        val vm = viewModel(generator)
        vm.prepare(hasLesson = true)
        vm.submit()
        runCurrent()
        assertTrue(generator.requests.isEmpty())
        assertEquals(LessonAiPhase.ConfirmReplace, vm.state.value.phase)
        vm.cancelReplace()
        assertEquals(LessonAiPhase.Form, vm.state.value.phase)
        vm.submit()
        vm.confirmReplace()
        runCurrent()
        assertEquals(1, generator.requests.size)
        assertEquals(LessonAiPhase.Polling, vm.state.value.phase)
    }

    @Test
    fun missingCodeOpensPromptAndResumesAfterwards() = runTest {
        val generator = FakeGenerator()
        generator.start = ApiResult.Failure.CodeMissing
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertEquals(1, codePrompts)
        generator.start = ApiResult.Success(LessonJobStarted("job-1"))
        retryAfterCode?.invoke()
        runCurrent()
        assertEquals(listOf("deck-1" to "job-1"), tracker.tracked)
    }

    @Test
    fun networkErrorWhileSendingKeepsFormAndAllowsRetry() = runTest {
        val generator = FakeGenerator()
        generator.start = ApiResult.Failure.Network
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.setText("texto")
        vm.submit()
        runCurrent()
        assertEquals(LessonAiPhase.Form, vm.state.value.phase)
        assertEquals("texto", vm.state.value.text)
        assertEquals(ApiResult.Failure.Network.userMessage, vm.state.value.error)
    }

    @Test
    fun offlineBlocksSending() = runTest {
        online = false
        val generator = FakeGenerator()
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertTrue(generator.requests.isEmpty())
        assertTrue(vm.state.value.error != null)
    }

    @Test
    fun prepareKeepsOngoingGenerationButResetsAndClearsAFinishedOne() = runTest {
        val vm = viewModel(FakeGenerator())
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        vm.prepare(hasLesson = false)
        assertEquals(LessonAiPhase.Polling, vm.state.value.phase)
        tracker.emit("deck-1", LessonJobState.Done(emptyList()))
        runCurrent()
        vm.prepare(hasLesson = true)
        runCurrent()
        assertEquals(LessonAiPhase.Form, vm.state.value.phase)
        assertTrue(vm.state.value.hasLesson)
        assertTrue(tracker.states.value.isEmpty())
    }

    @Test
    fun doneStatusDecodesVersionLinksAndClearTargets() {
        val raw = """{"status":"done","lessonId":"l1","lessonVersion":4,"warnings":[],"links":[{"cardId":"c1","target":"anchor:a"}],"clearTargets":true}"""
        val body = OkloJson.instance.decodeFromString(LessonJobBody.serializer(), raw)
        assertEquals(4, body.lessonVersion)
        assertEquals(listOf(LessonLinkDto("c1", "anchor:a")), body.links)
        assertTrue(body.clearTargets)
    }
}
