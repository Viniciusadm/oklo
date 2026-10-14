package com.archieapps.oklo.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppPreferencesTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun preferences(scope: TestScope): AppPreferences {
        val store = PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { folder.newFile("prefs-${System.nanoTime()}.preferences_pb") }
        return AppPreferences(store)
    }

    @Test
    fun `seen lesson version is empty until marked and is kept per deck`() = runTest(UnconfinedTestDispatcher()) {
        val preferences = preferences(this)

        assertNull(preferences.seenLessonVersion("d1").first())
        assertNull(preferences.seenLessonVersionNow("d1"))

        preferences.markLessonSeen("d1", 3)
        preferences.markLessonSeen("d2", 1)

        assertEquals(3, preferences.seenLessonVersion("d1").first())
        assertEquals(3, preferences.seenLessonVersionNow("d1"))
        assertEquals(1, preferences.seenLessonVersionNow("d2"))
        assertNull(preferences.seenLessonVersionNow("d3"))
    }

    @Test
    fun `marking a newer version replaces the seen one`() = runTest(UnconfinedTestDispatcher()) {
        val preferences = preferences(this)

        preferences.markLessonSeen("d1", 1)
        preferences.markLessonSeen("d1", 2)

        assertEquals(2, preferences.seenLessonVersionNow("d1"))
    }
}
