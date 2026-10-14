package com.archieapps.oklo.core.fsrs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FsrsVectorsTest {
    @Serializable
    private data class Step(
        val rating: Int,
        val reviewAt: Long,
        val state: String,
        val step: Int?,
        val stability: Double,
        val difficulty: Double,
        val due: Long,
        val lastReview: Long,
    )

    @Serializable
    private data class Scenario(
        val name: String,
        val desiredRetention: Double,
        val learningStepsMinutes: List<Int>,
        val relearningStepsMinutes: List<Int>,
        val maximumIntervalDays: Int,
        val start: Long,
        val steps: List<Step>,
    )

    @Serializable
    private data class Vectors(val generator: String, val scenarios: List<Scenario>)

    private val reader = Json { ignoreUnknownKeys = true }

    private val vectors: Vectors by lazy {
        val raw = javaClass.classLoader!!.getResource("fsrs/py_fsrs_4_1_2_vectors.json")!!.readText()
        reader.decodeFromString(Vectors.serializer(), raw)
    }

    private fun close(expected: Double, actual: Double?, label: String) {
        requireNotNull(actual) { "$label is null" }
        val tolerance = 1e-9 * maxOf(1.0, abs(expected))
        assertTrue("$label expected $expected but was $actual", abs(expected - actual) <= tolerance)
    }

    @Test
    fun `every py-fsrs scenario is reproduced step by step`() {
        assertTrue(vectors.scenarios.size >= 8)
        var checked = 0
        vectors.scenarios.forEach { scenario ->
            val scheduler = FsrsScheduler(
                FsrsParameters(
                    desiredRetention = scenario.desiredRetention,
                    learningStepsMinutes = scenario.learningStepsMinutes,
                    relearningStepsMinutes = scenario.relearningStepsMinutes,
                    maximumIntervalDays = scenario.maximumIntervalDays,
                    enableFuzz = false,
                ),
            )
            var card = FsrsCard(due = scenario.start)
            scenario.steps.forEachIndexed { index, expected ->
                val label = "${scenario.name}#$index"
                card = scheduler.review(card, Rating.of(expected.rating), expected.reviewAt)
                assertEquals("$label state", expected.state, card.state.name.lowercase())
                assertEquals("$label step", expected.step, card.step)
                close(expected.stability, card.stability, "$label stability")
                close(expected.difficulty, card.difficulty, "$label difficulty")
                assertEquals("$label due", expected.due, card.due)
                assertEquals("$label lastReview", expected.lastReview, card.lastReview)
                checked++
            }
        }
        assertTrue(checked >= 150)
    }

    @Test
    fun `preview gives the four ratings without fuzz even when fuzz is enabled`() {
        val scheduler = FsrsScheduler(FsrsParameters(enableFuzz = true)) { 0.99 }
        val now = 1_791_460_800_000L
        val reviewed = FsrsCard(FsrsState.Review, null, 30.0, 5.0, now, now - 30L * FsrsScheduler.DAY)
        val preview = scheduler.preview(reviewed, now)
        assertEquals(4, preview.size)
        val good = preview.getValue(Rating.Good)
        val unfuzzed = FsrsScheduler(FsrsParameters(enableFuzz = false)).review(reviewed, Rating.Good, now)
        assertEquals(unfuzzed.due, good.due)
        assertEquals(FsrsState.Relearning, preview.getValue(Rating.Again).state)
    }

    @Test
    fun `fuzz keeps review intervals inside the py-fsrs fuzz range`() {
        val now = 1_791_460_800_000L
        val reviewed = FsrsCard(FsrsState.Review, null, 30.0, 5.0, now, now - 30L * FsrsScheduler.DAY)
        val base = FsrsScheduler(FsrsParameters(enableFuzz = false)).review(reviewed, Rating.Good, now)
        val baseDays = (base.due - now) / FsrsScheduler.DAY
        val low = FsrsScheduler(FsrsParameters(enableFuzz = true)) { 0.0 }.review(reviewed, Rating.Good, now)
        val high = FsrsScheduler(FsrsParameters(enableFuzz = true)) { 0.999999 }.review(reviewed, Rating.Good, now)
        val lowDays = (low.due - now) / FsrsScheduler.DAY
        val highDays = (high.due - now) / FsrsScheduler.DAY
        assertTrue(lowDays < baseDays)
        assertTrue(highDays > baseDays)
        assertTrue(lowDays >= 2)
    }

    @Test
    fun `learning cards are never fuzzed`() {
        val scheduler = FsrsScheduler(FsrsParameters(enableFuzz = true)) { 0.999 }
        val now = 1_791_460_800_000L
        val next = scheduler.review(FsrsCard(due = now), Rating.Good, now)
        assertEquals(FsrsState.Learning, next.state)
        assertEquals(now + 10 * FsrsScheduler.MINUTE, next.due)
    }
}
