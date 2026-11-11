package com.archieapps.oklo.core.sched

import com.archieapps.oklo.core.fsrs.Rating
import com.archieapps.oklo.core.time.LocalDay
import com.archieapps.oklo.data.model.CardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class FsrsCardSchedulerTest {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val today = LocalDay.of(LocalDate.of(2026, 11, 11), zone)
    private val now = today.start + 6 * 3_600_000L
    private val scheduler = FsrsCardScheduler(zone = zone)

    @Test
    fun `review outcomes land on day boundaries, stay ordered and match the preview`() {
        (1..100).forEach { id ->
            val card = SchedulingCard(
                "card-$id", CardState.Review, null, 3.0 + id, 5.0, null, 0,
                now, now - (3 + id % 10) * 86_400_000L, 4,
            )
            val preview = scheduler.preview(card, now)
            val hard = preview.getValue(Rating.Hard)
            val good = preview.getValue(Rating.Good)
            val easy = preview.getValue(Rating.Easy)
            assertTrue(hard.intervalDays < good.intervalDays && good.intervalDays < easy.intervalDays)
            assertEquals(LocalDay.startAfterDays(now, good.intervalDays.toLong(), zone), good.dueAt)
            assertEquals(good, scheduler.review(card, Rating.Good, now))
        }
    }
}
