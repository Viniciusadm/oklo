package com.archieapps.oklo.core.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrikeBudgetTest {
    @Test
    fun `a fresh budget can attempt and shows five attempts`() {
        val budget = StrikeBudget()

        assertTrue(budget.canAttempt)
        assertEquals(5, budget.remainingShown)
    }

    @Test
    fun `two local strikes hard stop before the server can reach five`() {
        val first = StrikeBudget().afterRejection(4)
        assertTrue(first.canAttempt)

        val second = first.afterRejection(3)
        assertTrue(second.hardStopped)
        assertEquals(2, second.serverBlocked)
        assertFalse(second.permanentlyBlocked)
    }

    @Test
    fun `a server that already burned three attempts refuses to try`() {
        val budget = StrikeBudget().withServerBlocked(3)

        assertTrue(budget.hardStopped)
        assertEquals(2, budget.remainingShown)
    }

    @Test
    fun `the reported attempts left wins over the local counter`() {
        val budget = StrikeBudget().afterRejection(1)

        assertEquals(3, budget.serverBlocked)
        assertTrue(budget.hardStopped)
    }

    @Test
    fun `a rejection with no parsed number still counts`() {
        val budget = StrikeBudget().afterRejection(null)

        assertEquals(1, budget.serverBlocked)
        assertEquals(1, budget.localStrikes)
        assertTrue(budget.canAttempt)
    }

    @Test
    fun `success heals both counters`() {
        val healed = StrikeBudget(2, 1).afterSuccess()

        assertEquals(0, healed.serverBlocked)
        assertEquals(0, healed.localStrikes)
        assertTrue(healed.canAttempt)
    }

    @Test
    fun `five server strikes is permanent`() {
        val budget = StrikeBudget().withServerBlocked(5)

        assertTrue(budget.permanentlyBlocked)
        assertTrue(budget.hardStopped)
        assertEquals(0, budget.remainingShown)
    }
}
