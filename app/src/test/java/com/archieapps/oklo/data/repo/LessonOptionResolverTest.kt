package com.archieapps.oklo.data.repo

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LessonOptionResolverTest {
    private val origins = mutableMapOf<String, String>()
    private val resolver = LessonOptionResolver { origins[it] }

    @Test
    fun noLessonIsCheckedByDefault() = runTest {
        assertEquals(LessonOption(checked = true, replacesManualLesson = false), resolver.resolve("d1", null))
    }

    @Test
    fun aiLessonIsCheckedByDefault() = runTest {
        origins["d1"] = "ai"
        assertEquals(LessonOption(checked = true, replacesManualLesson = false), resolver.resolve("d1", null))
    }

    @Test
    fun manualLessonIsUncheckedAndWarnsReplacement() = runTest {
        origins["d1"] = "manual"
        assertEquals(LessonOption(checked = false, replacesManualLesson = true), resolver.resolve("d1", null))
    }

    @Test
    fun explicitChoiceWinsButStillWarns() = runTest {
        origins["d1"] = "manual"
        assertEquals(LessonOption(checked = true, replacesManualLesson = true), resolver.resolve("d1", true))
        assertEquals(LessonOption(checked = false, replacesManualLesson = false), resolver.resolve("d2", false))
    }
}
