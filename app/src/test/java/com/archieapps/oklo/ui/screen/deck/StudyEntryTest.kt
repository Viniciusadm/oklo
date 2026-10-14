package com.archieapps.oklo.ui.screen.deck

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.archieapps.oklo.data.prefs.AppPreferences
import com.archieapps.oklo.data.repo.FakeLessonDao
import com.archieapps.oklo.data.repo.LessonRepository
import com.archieapps.oklo.data.repo.LessonValidator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StudyEntryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun setup(scope: kotlinx.coroutines.test.TestScope, valid: Boolean = true): Pair<LessonRepository, AppPreferences> {
        val store = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { folder.newFile("prefs-${System.nanoTime()}.preferences_pb") }
        val preferences = AppPreferences(store)
        return LessonRepository(FakeLessonDao(), preferences, LessonValidator { valid }, { 1L }, { "l-1" }) to preferences
    }

    @Test
    fun studiesDirectlyWithoutLesson() = runTest {
        val (lessons, _) = setup(this)
        assertEquals("study?deckId=d", StudyEntry(lessons).route("d"))
    }

    @Test
    fun goesThroughGateWhenLessonUnseen() = runTest {
        val (lessons, _) = setup(this)
        lessons.saveManual("d", "# A")
        assertEquals("lesson/d?mode=gate", StudyEntry(lessons).route("d"))
    }

    @Test
    fun studiesDirectlyOnceSeen() = runTest {
        val (lessons, preferences) = setup(this)
        val lesson = lessons.saveManual("d", "# A")
        preferences.markLessonSeen("d", lesson.version)
        assertEquals("study?deckId=d", StudyEntry(lessons).route("d"))
    }

    @Test
    fun invalidLessonNeverBlocksStudy() = runTest {
        val (lessons, _) = setup(this, valid = false)
        lessons.saveManual("d", "broken")
        assertEquals("study?deckId=d", StudyEntry(lessons).route("d"))
    }
}
