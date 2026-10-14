package com.archieapps.oklo.data.repo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.archieapps.oklo.data.prefs.AppPreferences
import kotlinx.coroutines.test.TestScope
import org.junit.rules.TemporaryFolder

class LessonTestFixture(folder: TemporaryFolder, scope: TestScope, valid: Boolean = true) {
    val dao = FakeLessonDao()
    val preferences = AppPreferences(
        PreferenceDataStoreFactory.create(scope = scope.backgroundScope) { folder.newFile("prefs-${System.nanoTime()}.preferences_pb") },
    )
    val lessons = LessonRepository(dao, preferences, LessonValidator { valid }, { 1L }, { "lesson-1" })
}
