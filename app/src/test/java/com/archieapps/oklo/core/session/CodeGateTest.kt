package com.archieapps.oklo.core.session

import com.archieapps.oklo.core.net.ApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CodeGateTest {
    private class MutableClock(var instant: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId): Clock = Shifted(this, zone)
        override fun instant(): Instant = instant
    }

    private class Shifted(private val base: MutableClock, private val zoneId: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zoneId
        override fun withZone(zone: ZoneId): Clock = Shifted(base, zone)
        override fun instant(): Instant = base.instant()
    }

    private val clock = MutableClock(Instant.parse("2026-10-08T15:00:00Z"))
    private val persisted = mutableListOf<Int>()
    private val vault = CodeVault(clock)
    private val gate = CodeGate(vault) { persisted += it }

    @Test
    fun `without a code the call is never made`() = runTest {
        var calls = 0
        val result = gate.run { calls++; ApiResult.Success(Unit) }
        assertEquals(ApiResult.Failure.CodeMissing, result)
        assertEquals(0, calls)
    }

    @Test
    fun `malformed codes are refused locally`() {
        assertNotNull(gate.provide("123"))
        assertNotNull(gate.provide("1234 678"))
        assertNull(gate.provide("12345678"))
        assertTrue(gate.hasCode())
    }

    @Test
    fun `a rejected code is dropped and never retried automatically`() = runTest {
        gate.provide("12345678")
        var calls = 0
        val first = gate.run { calls++; ApiResult.Failure.CodeRejected }
        val second = gate.run { calls++; ApiResult.Success(Unit) }
        assertEquals(ApiResult.Failure.CodeRejected, first)
        assertEquals(ApiResult.Failure.CodeMissing, second)
        assertEquals(1, calls)
        assertEquals(listOf(1), persisted)
    }

    @Test
    fun `two local rejections stop the app from sending more codes today`() = runTest {
        gate.provide("12345678")
        gate.run { ApiResult.Failure.CodeRejected }
        gate.provide("87654321")
        gate.run { ApiResult.Failure.CodeRejected }
        assertNotNull(gate.provide("11111111"))
        assertEquals(ApiResult.Failure.CodeHardStopped, gate.run { ApiResult.Success(Unit) })
        clock.instant = Instant.parse("2026-10-09T15:00:00Z")
        assertNull(gate.provide("11111111"))
    }

    @Test
    fun `a code reaching the server heals the budget and lasts only for the server day`() = runTest {
        gate.provide("12345678")
        gate.run { ApiResult.Failure.CodeRejected }
        gate.provide("87654321")
        val ok = gate.run { ApiResult.Failure.AiUnreachable("x") }
        assertTrue(ok is ApiResult.Failure.AiUnreachable)
        assertEquals(StrikeBudget(), vault.budget.value)
        assertEquals(listOf(1, 0), persisted)
        assertTrue(gate.hasCode())
        clock.instant = Instant.parse("2026-10-09T02:59:00Z")
        assertTrue(gate.hasCode())
        clock.instant = Instant.parse("2026-10-09T03:01:00Z")
        assertEquals(ApiResult.Failure.CodeMissing, gate.run { ApiResult.Success(Unit) })
    }
}
