package com.archieapps.oklo.core.sched

import com.archieapps.oklo.core.fsrs.Rating
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class Sm2SchedulerTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDay.of(LocalDate.of(2026, 11, 11), zone)
    private val now = today.start + 6 * HOUR
    private val plain = Sm2Scheduler(Sm2Config(enableFuzz = false), zone)

    private fun newCard(id: String = "c1") = SchedulingCard(id, CardState.New, null, null, null, null, 0, null, null, 0)

    private fun reviewCard(interval: Int, ease: Int = 2500, lastReview: Long = now - interval * DAY) =
        SchedulingCard("c1", CardState.Review, null, null, null, ease, interval, lastReview + interval * DAY, lastReview, 5)

    @Test
    fun `a new card previews like Anki defaults`() {
        val preview = plain.preview(newCard(), now)
        assertEquals(now + MINUTE, preview.getValue(Rating.Again).dueAt)
        assertEquals(now + 330_000, preview.getValue(Rating.Hard).dueAt)
        assertEquals(now + 10 * MINUTE, preview.getValue(Rating.Good).dueAt)
        val easy = preview.getValue(Rating.Easy)
        assertEquals(CardState.Review, easy.state)
        assertEquals(4, easy.intervalDays)
        assertEquals(2500, easy.easeFactor)
        assertEquals(LocalDay.startAfterDays(now, 4, zone), easy.dueAt)
    }

    @Test
    fun `fuzz on the easy graduation stays within three to five days and matches the preview`() {
        val fuzzed = Sm2Scheduler(Sm2Config(), zone)
        val seen = (1..200).map { id ->
            val card = newCard("card-$id")
            val preview = fuzzed.preview(card, now).getValue(Rating.Easy)
            assertEquals(preview, fuzzed.review(card, Rating.Easy, now))
            preview.intervalDays
        }.toSet()
        assertEquals(setOf(3, 4, 5), seen)
    }

    @Test
    fun `good twice graduates with one day`() {
        val first = plain.review(newCard(), Rating.Good, now)
        assertEquals(CardState.Learning, first.state)
        assertEquals(1, first.step)
        val card = newCard().copy(state = first.state, step = first.step, easeFactor = first.easeFactor, dueAt = first.dueAt, lastReviewAt = now, reps = 1)
        val second = plain.review(card, Rating.Good, now + 10 * MINUTE)
        assertEquals(CardState.Review, second.state)
        assertEquals(1, second.intervalDays)
        assertEquals(today.end, second.dueAt)
    }

    @Test
    fun `a one day review card gets two, three and four days`() {
        val preview = plain.preview(reviewCard(1), now)
        assertEquals(2, preview.getValue(Rating.Hard).intervalDays)
        assertEquals(3, preview.getValue(Rating.Good).intervalDays)
        assertEquals(4, preview.getValue(Rating.Easy).intervalDays)
        assertEquals(2350, preview.getValue(Rating.Hard).easeFactor)
        assertEquals(2500, preview.getValue(Rating.Good).easeFactor)
        assertEquals(2650, preview.getValue(Rating.Easy).easeFactor)
    }

    @Test
    fun `review intervals follow ease, hard factor and easy bonus`() {
        val preview = plain.preview(reviewCard(10), now)
        assertEquals(12, preview.getValue(Rating.Hard).intervalDays)
        assertEquals(25, preview.getValue(Rating.Good).intervalDays)
        assertEquals(33, preview.getValue(Rating.Easy).intervalDays)
    }

    @Test
    fun `late reviews add the delay to good and easy`() {
        val preview = plain.preview(reviewCard(10, lastReview = now - 14 * DAY), now)
        assertEquals(12, preview.getValue(Rating.Hard).intervalDays)
        assertEquals(30, preview.getValue(Rating.Good).intervalDays)
        assertEquals(46, preview.getValue(Rating.Easy).intervalDays)
    }

    @Test
    fun `a lapse drops ease, relearns for ten minutes and comes back after one day`() {
        val again = plain.review(reviewCard(20), Rating.Again, now)
        assertEquals(CardState.Relearning, again.state)
        assertEquals(2300, again.easeFactor)
        assertEquals(1, again.intervalDays)
        assertEquals(now + 10 * MINUTE, again.dueAt)
        val relearning = reviewCard(20).copy(state = CardState.Relearning, step = 0, easeFactor = again.easeFactor, intervalDays = again.intervalDays, lastReviewAt = now)
        val good = plain.review(relearning, Rating.Good, now + 10 * MINUTE)
        assertEquals(CardState.Review, good.state)
        assertEquals(1, good.intervalDays)
        assertEquals(2300, good.easeFactor)
    }

    @Test
    fun `ease never drops below 130 percent`() {
        val again = plain.review(reviewCard(5, ease = 1350), Rating.Again, now)
        assertEquals(1300, again.easeFactor)
        val hard = plain.review(reviewCard(5, ease = 1300), Rating.Hard, now)
        assertEquals(1300, hard.easeFactor)
    }

    @Test
    fun `fuzzed review intervals stay ordered`() {
        val fuzzed = Sm2Scheduler(Sm2Config(), zone)
        (1..100).forEach { id ->
            val card = reviewCard(3 + id % 40).copy(id = "card-$id")
            val preview = fuzzed.preview(card, now)
            val hard = preview.getValue(Rating.Hard).intervalDays
            val good = preview.getValue(Rating.Good).intervalDays
            val easy = preview.getValue(Rating.Easy).intervalDays
            assertTrue("$hard < $good < $easy", hard < good && good < easy)
        }
    }

    @Test
    fun `the day rolls over at four in the morning`() {
        val lateNight = today.start + 22 * HOUR
        val afterMidnight = today.start + 23 * HOUR
        assertEquals(0, LocalDay.daysBetween(lateNight, afterMidnight, zone))
        assertEquals(1, LocalDay.daysBetween(lateNight, today.end, zone))
        assertEquals(4, java.time.Instant.ofEpochMilli(today.start).atZone(zone).hour)
    }

    companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
    }
}
