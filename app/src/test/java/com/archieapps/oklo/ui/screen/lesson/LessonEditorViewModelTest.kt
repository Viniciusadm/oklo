package com.archieapps.oklo.ui.screen.lesson

import com.archieapps.oklo.data.repo.LessonTestFixture
import com.archieapps.ribalta.ir.Diagnostic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LessonEditorViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()
    private var syncRequests = 0

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        syncRequests = 0
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun error(line: Int = 1) = Diagnostic("error", "E001", line, "quebrado")

    private fun viewModel(f: LessonTestFixture, diagnostics: (String) -> List<Diagnostic> = { emptyList() }) =
        LessonEditorViewModel(
            f.lessons,
            { syncRequests++ },
            "deck-1",
            LessonDiagnostics(diagnostics),
            testDispatcher,
            debounceMillis = 0,
        )

    private fun TestScope.fixture() = LessonTestFixture(folder, this)

    @Test
    fun newLessonStartsWithTemplateAndIsNotDirty() = runTest {
        val vm = viewModel(fixture())
        val state = vm.state.value
        assertTrue(state.loaded)
        assertTrue(state.isNew)
        assertEquals(LessonEditorViewModel.TEMPLATE, state.source)
        assertFalse(state.dirty)
        assertTrue(state.canSave)
    }

    @Test
    fun templateHasAnchoredHeadingAndParagraph() {
        val lines = LessonEditorViewModel.TEMPLATE.lines().filter { it.isNotBlank() }
        assertEquals(2, lines.size)
        assertTrue(Regex("""^# .+ \{#[a-z0-9-]+}$""").matches(lines[0]))
        assertEquals("Título da aula", lessonTitle(LessonEditorViewModel.TEMPLATE, ""))
    }

    @Test
    fun existingLessonLoadsItsSource() = runTest {
        val f = fixture()
        f.lessons.saveManual("deck-1", "# Antiga")
        val vm = viewModel(f)
        assertEquals("# Antiga", vm.state.value.source)
        assertFalse(vm.state.value.isNew)
        assertFalse(vm.state.value.dirty)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun editingMarksDirtyAndRevertingClearsIt() = runTest {
        val f = fixture()
        f.lessons.saveManual("deck-1", "# A")
        val vm = viewModel(f)
        vm.onSourceChange("# B")
        assertTrue(vm.state.value.dirty)
        vm.onSourceChange("# A")
        assertFalse(vm.state.value.dirty)
    }

    @Test
    fun saveIsBlockedWhileErrorsExist() = runTest {
        val f = fixture()
        f.lessons.saveManual("deck-1", "# A")
        val vm = viewModel(f) { source -> if (source.contains("bad")) listOf(error()) else emptyList() }
        vm.onSourceChange("bad")
        assertFalse(vm.state.value.canSave)
        vm.save()
        assertEquals(1, f.lessons.get("deck-1")!!.version)
        assertEquals(0, syncRequests)
        assertFalse(vm.state.value.finished)
    }

    @Test
    fun warningsDoNotBlockSave() = runTest {
        val f = fixture()
        f.lessons.saveManual("deck-1", "# A")
        val vm = viewModel(f) { listOf(Diagnostic("warning", "W001", 1, "aviso")) }
        vm.onSourceChange("# B")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun saveIncrementsVersionMarksDirtyAndRequestsSync() = runTest {
        val f = fixture()
        f.lessons.saveManual("deck-1", "# A")
        val vm = viewModel(f)
        vm.onSourceChange("# B")
        vm.save()
        val saved = f.lessons.get("deck-1")!!
        assertEquals("# B", saved.source)
        assertEquals(2, saved.version)
        assertTrue(f.dao.getByDeck("deck-1")!!.dirty)
        assertEquals(1, syncRequests)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun savingTheNewTemplateCreatesVersionOne() = runTest {
        val f = fixture()
        val vm = viewModel(f)
        vm.save()
        assertEquals(1, f.lessons.get("deck-1")!!.version)
        assertEquals(1, syncRequests)
    }

    @Test
    fun oversizedSourceCannotBeSaved() = runTest {
        val f = fixture()
        val vm = viewModel(f)
        vm.onSourceChange("a".repeat(200_001))
        assertTrue(vm.state.value.tooLarge)
        assertFalse(vm.state.value.canSave)
        vm.save()
        assertEquals(null, f.lessons.get("deck-1"))
    }

    @Test
    fun diagnosticsReflectLatestSource() = runTest {
        val f = fixture()
        val vm = viewModel(f) { source -> if (source.contains("bad")) listOf(error(3)) else emptyList() }
        vm.onSourceChange("bad")
        assertEquals(3, vm.state.value.diagnostics.single().line)
        vm.onSourceChange("good")
        assertTrue(vm.state.value.diagnostics.isEmpty())
    }
}
