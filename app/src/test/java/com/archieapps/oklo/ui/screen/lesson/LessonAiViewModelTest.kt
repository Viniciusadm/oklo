package com.archieapps.oklo.ui.screen.lesson

import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.LessonGenerateRequest
import com.archieapps.oklo.core.net.LessonJobBody
import com.archieapps.oklo.core.net.LessonJobStarted
import com.archieapps.oklo.core.net.OkloJson
import com.archieapps.oklo.core.net.LessonLinkDto
import com.archieapps.oklo.data.repo.LessonGenerator
import com.archieapps.oklo.data.sync.SyncReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
        val statuses = ArrayDeque<ApiResult<LessonJobBody>>()
        var polls = 0

        override suspend fun lessonGenerate(request: LessonGenerateRequest): ApiResult<LessonJobStarted> {
            events += "post"
            requests += request
            return start
        }

        override suspend fun lessonJob(jobId: String): ApiResult<LessonJobBody> {
            polls++
            return statuses.removeFirstOrNull() ?: ApiResult.Success(LessonJobBody("running"))
        }
    }

    private var syncs = 0
    private val finished = mutableListOf<LessonJobBody>()
    private var syncReport: SyncReport? = SyncReport.Done(pushed = 0, pulled = 0, applied = 0, rejected = 0, usn = 1)
    private var codePrompts = 0
    private var retryAfterCode: (() -> Unit)? = null
    private var online = true

    @Before
    fun setUp() {
        syncs = 0
        finished.clear()
        events.clear()
        syncReport = SyncReport.Done(pushed = 0, pulled = 0, applied = 0, rejected = 0, usn = 1)
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
            model = { "pro" },
            isOnline = { online },
            syncNow = {
                events += "sync"
                syncReport
            },
            finish = {
                syncs++
                finished += it
            },
            openCodePrompt = { retry ->
                codePrompts++
                retryAfterCode = retry
            },
        )
    }

    private fun done() = ApiResult.Success(LessonJobBody("done", lessonId = "lesson-1"))

    private fun running() = ApiResult.Success(LessonJobBody("running"))

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
        vm.dispose()
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
        vm.dispose()
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
    fun pollsEveryThreeSecondsUntilDone() = runTest {
        val generator = FakeGenerator()
        generator.statuses += listOf(running(), running(), done())
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertEquals(1, generator.polls)
        assertEquals(LessonAiPhase.Polling, vm.state.value.phase)
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(1, generator.polls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, generator.polls)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(3, generator.polls)
        assertEquals(LessonAiPhase.Done, vm.state.value.phase)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(3, generator.polls)
    }

    @Test
    fun doneTriggersSyncOnce() = runTest {
        val generator = FakeGenerator()
        generator.statuses += listOf(running(), done())
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertEquals(0, syncs)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(1, syncs)
        assertEquals(LessonAiPhase.Done, vm.state.value.phase)
    }

    @Test
    fun failedShowsServerErrorAndRetryRestarts() = runTest {
        val generator = FakeGenerator()
        generator.statuses += ApiResult.Success(LessonJobBody("failed", error = "A IA não conseguiu montar a aula."))
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertEquals(LessonAiPhase.Failed, vm.state.value.phase)
        assertEquals("A IA não conseguiu montar a aula.", vm.state.value.error)
        assertEquals(0, syncs)
        generator.start = ApiResult.Success(LessonJobStarted("job-2"))
        generator.statuses += done()
        vm.retry()
        runCurrent()
        assertEquals(2, generator.requests.size)
        assertEquals(LessonAiPhase.Done, vm.state.value.phase)
        assertEquals(1, syncs)
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
        vm.dispose()
    }

    @Test
    fun noConfirmationWhenDeckHasNoLesson() = runTest {
        val generator = FakeGenerator()
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertEquals(1, generator.requests.size)
        vm.dispose()
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
        generator.statuses += done()
        retryAfterCode?.invoke()
        runCurrent()
        assertEquals(LessonAiPhase.Done, vm.state.value.phase)
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
        assertEquals(0, generator.polls)
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
    fun transientNetworkErrorWhilePollingKeepsPolling() = runTest {
        val generator = FakeGenerator()
        generator.statuses += listOf(ApiResult.Failure.Network, done())
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertEquals(LessonAiPhase.Polling, vm.state.value.phase)
        advanceTimeBy(3_000)
        runCurrent()
        assertEquals(LessonAiPhase.Done, vm.state.value.phase)
    }

    @Test
    fun prepareKeepsOngoingGenerationButResetsFinishedOne() = runTest {
        val generator = FakeGenerator()
        generator.statuses += listOf(running(), done())
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        vm.prepare(hasLesson = false)
        assertEquals(LessonAiPhase.Polling, vm.state.value.phase)
        advanceTimeBy(3_000)
        runCurrent()
        vm.prepare(hasLesson = true)
        assertEquals(LessonAiPhase.Form, vm.state.value.phase)
        assertTrue(vm.state.value.hasLesson)
    }

    @Test
    fun fullSyncFinishesBeforeTheRequestIsSent() = runTest {
        val generator = FakeGenerator()
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertEquals(listOf("sync", "post"), events.take(2))
        vm.dispose()
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
    }

    @Test
    fun doneHandsLinksAndClearFlagToTheFinisher() = runTest {
        val generator = FakeGenerator()
        val body = LessonJobBody(
            "done",
            lessonId = "lesson-1",
            links = listOf(LessonLinkDto("c1", "anchor:fase-clara")),
            clearTargets = true,
        )
        generator.statuses += ApiResult.Success(body)
        val vm = viewModel(generator)
        vm.prepare(hasLesson = false)
        vm.submit()
        runCurrent()
        assertEquals(listOf(body), finished)
        assertEquals(LessonAiPhase.Done, vm.state.value.phase)
    }

    @Test
    fun doneStatusDecodesLinksAndClearTargets() {
        val raw = """{"status":"done","lessonId":"l1","warnings":[],"links":[{"cardId":"c1","target":"anchor:a"}],"clearTargets":true}"""
        val body = OkloJson.instance.decodeFromString(LessonJobBody.serializer(), raw)
        assertEquals(listOf(LessonLinkDto("c1", "anchor:a")), body.links)
        assertTrue(body.clearTargets)
    }
}
