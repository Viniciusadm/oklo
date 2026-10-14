package com.archieapps.oklo.ui.screen.lesson

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.archieapps.oklo.data.prefs.AppPreferences
import com.archieapps.oklo.data.repo.FakeLessonDao
import com.archieapps.oklo.data.repo.LessonRepository
import com.archieapps.oklo.data.repo.LessonValidator
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LessonViewModelTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val dao = FakeLessonDao()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class Fixture(val lessons: LessonRepository, val preferences: AppPreferences)

    private fun fixture(scope: TestScope, valid: Boolean = true): Fixture {
        val store = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { folder.newFile("prefs-${System.nanoTime()}.preferences_pb") }
        val preferences = AppPreferences(store)
        return Fixture(LessonRepository(dao, preferences, LessonValidator { valid }, { 1L }, { "lesson-1" }), preferences)
    }

    private fun viewModel(f: Fixture, mode: LessonMode, target: String? = null, valid: Boolean = true) =
        LessonViewModel(f.lessons, f.preferences, "deck-1", mode, target, LessonValidator { valid })

    @Test
    fun missingLessonYieldsMissing() = runTest {
        val vm = viewModel(fixture(this), LessonMode.Gate)
        assertTrue(vm.ui.value is LessonUi.Missing)
    }

    @Test
    fun gateCompleteIsDisabledUntilEndReached() = runTest {
        val f = fixture(this)
        f.lessons.saveManual("deck-1", "# A")
        val vm = viewModel(f, LessonMode.Gate)
        assertFalse((vm.ui.value as LessonUi.Ready).canComplete)
        vm.onReachedEnd(true)
        assertTrue((vm.ui.value as LessonUi.Ready).canComplete)
        vm.onReachedEnd(false)
        assertFalse((vm.ui.value as LessonUi.Ready).canComplete)
    }

    @Test
    fun gateMarksSeenOnlyOnComplete() = runTest {
        val f = fixture(this)
        f.lessons.saveManual("deck-1", "# A")
        val vm = viewModel(f, LessonMode.Gate)
        vm.onReachedEnd(true)
        assertNull(f.preferences.seenLessonVersionNow("deck-1"))
        assertTrue(f.lessons.needsGate("deck-1"))
        vm.complete()
        assertEquals(1, f.preferences.seenLessonVersionNow("deck-1"))
        assertFalse(f.lessons.needsGate("deck-1"))
    }

    @Test
    fun completeIgnoredWhenNotAllowed() = runTest {
        val f = fixture(this)
        f.lessons.saveManual("deck-1", "# A")
        val vm = viewModel(f, LessonMode.Gate)
        assertFalse(vm.complete())
        assertNull(f.preferences.seenLessonVersionNow("deck-1"))
    }

    @Test
    fun invalidLessonDoesNotBlockGate() = runTest {
        val f = fixture(this, valid = false)
        f.lessons.saveManual("deck-1", "broken")
        val vm = viewModel(f, LessonMode.Gate, valid = false)
        val ready = vm.ui.value as LessonUi.Ready
        assertTrue(ready.invalid)
        assertTrue(ready.canComplete)
        assertTrue(vm.complete())
        assertEquals(1, f.preferences.seenLessonVersionNow("deck-1"))
    }

    @Test
    fun readAndTargetModesNeverComplete() = runTest {
        val f = fixture(this)
        f.lessons.saveManual("deck-1", "# A")
        listOf(LessonMode.Read, LessonMode.Target).forEach { mode ->
            val vm = viewModel(f, mode, target = "anchor:x")
            vm.onReachedEnd(true)
            assertFalse((vm.ui.value as LessonUi.Ready).canComplete)
            assertFalse(vm.complete())
        }
        assertNull(f.preferences.seenLessonVersionNow("deck-1"))
    }

    @Test
    fun targetIsParsedOnlyWhenValid() = runTest {
        val f = fixture(this)
        f.lessons.saveManual("deck-1", "# A")
        assertEquals("anchor:x", (viewModel(f, LessonMode.Target, "anchor:x").ui.value as LessonUi.Ready).target)
        assertNull((viewModel(f, LessonMode.Target, "bogus").ui.value as LessonUi.Ready).target)
        assertNull((viewModel(f, LessonMode.Target, "").ui.value as LessonUi.Ready).target)
    }

    @Test
    fun modeParsing() {
        assertEquals(LessonMode.Gate, LessonMode.of("gate"))
        assertEquals(LessonMode.Read, LessonMode.of("read"))
        assertEquals(LessonMode.Read, LessonMode.of(null))
        assertEquals(LessonMode.Target, LessonMode.of("target"))
    }
}
