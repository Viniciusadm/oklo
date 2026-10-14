package com.archieapps.oklo.core.net

import com.archieapps.oklo.core.session.CodeGate
import com.archieapps.oklo.core.session.CodeVault
import com.archieapps.oklo.data.repo.AiRepository
import com.archieapps.oklo.data.sync.InMemorySyncStore
import com.archieapps.oklo.data.sync.RetrofitSyncRemote
import com.archieapps.oklo.data.sync.SyncEngine
import com.archieapps.oklo.data.sync.SyncReport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ApiContractTest {
    private data class Fixture(
        val name: String,
        val status: Int,
        val headers: Map<String, String>,
        val response: JsonElement,
        val requestBody: JsonElement?,
    )

    private val json = OkloJson.instance

    private val directory: File by lazy {
        File(javaClass.classLoader!!.getResource("contract")!!.toURI())
    }

    private fun load(name: String): Fixture {
        val raw = Json.parseToJsonElement(File(directory, name).readText()).jsonObject
        if (name.endsWith(".probe.json")) {
            val status = Regex("_(\\d{3})_").find(name)!!.groupValues[1].toInt()
            return Fixture(name, status, emptyMap(), raw, null)
        }
        val headers = (raw["headers"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
        val requestBody = (raw["request"] as? JsonObject)?.get("body")?.takeUnless { it is JsonNull }
        return Fixture(name, raw.getValue("status").jsonPrimitive.int, headers, raw.getValue("response"), requestBody)
    }

    private class Recorder {
        val requests = mutableListOf<Request>()
        val bodies = mutableListOf<String>()
    }

    private fun factory(vararg fixtures: Fixture, recorder: Recorder = Recorder(), vault: CodeVault = CodeVault()): NetworkFactory {
        val queue = ArrayDeque(fixtures.toList())
        val responder = Interceptor { chain ->
            val request = chain.request()
            recorder.requests += request
            recorder.bodies += request.body?.let { body -> Buffer().also { body.writeTo(it) }.readUtf8() }.orEmpty()
            val fixture = queue.removeFirst()
            val builder = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(fixture.status)
                .message("fixture")
                .body(fixture.response.toString().toResponseBody("application/json".toMediaType()))
            fixture.headers.forEach { (key, value) -> builder.header(key, value) }
            builder.build()
        }
        return NetworkFactory("http://contract.test/", { "token-123" }, vault, listOf(responder))
    }

    private fun assertSubset(expected: JsonElement, actual: JsonElement, path: String = "$") {
        when (expected) {
            is JsonObject -> {
                assertTrue("$path should be an object", actual is JsonObject)
                expected.forEach { (key, value) ->
                    val other = (actual as JsonObject)[key]
                    assertNotNull("$path.$key is missing in what the app sends", other)
                    assertSubset(value, other!!, "$path.$key")
                }
            }
            is JsonArray -> {
                assertTrue("$path should be an array", actual is JsonArray)
                assertEquals("$path size", expected.size, (actual as JsonArray).size)
                expected.forEachIndexed { index, item -> assertSubset(item, actual[index], "$path[$index]") }
            }
            is JsonNull -> assertEquals("$path", JsonNull, actual)
            is JsonPrimitive -> {
                if (actual is JsonPrimitive && actual !is JsonNull && !expected.isString && !actual.isString && expected.content.toDoubleOrNull() != null) {
                    assertEquals("$path", expected.content.toDouble(), actual.content.toDouble(), 0.0)
                } else {
                    assertEquals("$path", expected, actual)
                }
            }
        }
    }

    @Test
    fun `every captured fixture is exercised by this test`() {
        val covered = setOf(
            "ai_draft_200.json", "ai_draft_200_partial.probe.json", "ai_draft_400_empty_text.json",
            "ai_draft_422_text_too_long.json", "ai_draft_429.json", "ai_draft_502_invalid_format.probe.json",
            "ai_draft_502_unreachable.probe.json", "ai_draft_503_not_configured.json", "ai_status_200.json",
            "sync_pull_200_empty.json", "sync_pull_200_page1.json", "sync_pull_200_page2.json", "sync_pull_200_page3.json",
            "sync_pull_400_invalid_cursor.json", "sync_pull_400_validation.json", "sync_push_200.json",
            "sync_push_200_skipped.json", "sync_push_400_too_many_rows.json", "sync_push_400_validation.json",
            "sync_snapshot_200.json", "sync_snapshot_400_missing_code.json", "sync_status_200.json",
        )
        assertEquals(covered, directory.list()!!.filter { it.endsWith(".json") }.toSet())
    }

    @Test
    fun `push request round-trips through the app DTOs and the response decodes`() = runTest {
        val fixture = load("sync_push_200.json")
        val recorder = Recorder()
        val network = factory(fixture, recorder = recorder)
        val request = json.decodeFromJsonElement(PushRequest.serializer(), fixture.requestBody!!)
        val result = network.errors.call { network.api.push(request) }

        assertSubset(fixture.requestBody, Json.parseToJsonElement(recorder.bodies.single()))
        val body = (result as ApiResult.Success).data
        assertEquals(1L, body.usn)
        assertEquals(AppliedCounts(2, 3, 1, 1), body.applied)
        assertEquals("Múltipla escolha precisa de exatamente uma alternativa correta.", body.rejected.single().reason)
        assertNull(recorder.requests.single().header("code"))
        assertEquals("Bearer token-123", recorder.requests.single().header("Authorization"))
    }

    @Test
    fun `skipped rows decode with their entity`() = runTest {
        val fixture = load("sync_push_200_skipped.json")
        val network = factory(fixture)
        val request = json.decodeFromJsonElement(PushRequest.serializer(), fixture.requestBody!!)
        val body = (network.errors.call { network.api.push(request) } as ApiResult.Success).data
        assertEquals(listOf("deck", "review"), body.skipped.map { it.entity })
    }

    @Test
    fun `push errors map to validation messages`() = runTest {
        val tooMany = load("sync_push_400_too_many_rows.json")
        val invalid = load("sync_push_400_validation.json")
        val network = factory(tooMany, invalid)
        val empty = PushRequest("x", ChangeSet())
        assertEquals(ApiResult.Failure.Validation("Envie no máximo 500 linhas por vez."), network.errors.call { network.api.push(empty) })
        assertEquals(ApiResult.Failure.Validation("The device id field is required."), network.errors.call { network.api.push(empty) })
    }

    @Test
    fun `the engine applies the three captured pull pages`() = runTest {
        val pages = listOf("sync_pull_200_page1.json", "sync_pull_200_page2.json", "sync_pull_200_page3.json").map(::load)
        val recorder = Recorder()
        val network = factory(*pages.toTypedArray(), recorder = recorder)
        val store = InMemorySyncStore()
        val engine = SyncEngine(store, RetrofitSyncRemote(network.api, network.gatedApi, network.errors, null))

        val report = engine.sync()

        assertTrue(report is SyncReport.Done)
        val expected = pages.map { json.decodeFromJsonElement(PullBody.serializer(), it.response.jsonObject.getValue("body")) }
        assertEquals(expected.sumOf { it.changes.decks.size }, store.decks.size)
        assertEquals(expected.sumOf { it.changes.cards.size }, store.cards.size)
        assertEquals(expected.sumOf { it.changes.reviews.size }, store.reviews.size)
        assertNotNull(store.settings)
        assertEquals(expected.last().usn, store.usn)
        assertNotNull(expected.first().nextCursor)
        assertNull(expected.last().nextCursor)
        val cursors = recorder.requests.map { it.url.queryParameter("cursor") }
        assertEquals(listOf(null, expected[0].nextCursor, expected[1].nextCursor), cursors)
        assertTrue(recorder.requests.all { it.url.queryParameter("since") == "0" && it.header("code") == null })
    }

    @Test
    fun `empty pull and pull errors`() = runTest {
        val network = factory(load("sync_pull_200_empty.json"), load("sync_pull_400_invalid_cursor.json"), load("sync_pull_400_validation.json"))
        val empty = (network.errors.call { network.api.pull(1, null) } as ApiResult.Success).data
        assertTrue(empty.changes.isEmpty())
        assertNull(empty.nextCursor)
        assertEquals(ApiResult.Failure.Validation("Cursor inválido."), network.errors.call { network.api.pull(0, "bogus") })
        assertEquals(ApiResult.Failure.Validation("The since field is required."), network.errors.call { network.api.pull(0, null) })
    }

    @Test
    fun `snapshot is gated, has no tombstones and a refused code maps to code rejected`() = runTest {
        val recorder = Recorder()
        val vault = CodeVault().apply { store("40201040") }
        val network = factory(load("sync_snapshot_200.json"), load("sync_snapshot_400_missing_code.json"), recorder = recorder, vault = vault)
        val body = (network.errors.call { network.gatedApi.snapshot(null) } as ApiResult.Success).data
        assertTrue(body.changes.decks.isNotEmpty())
        assertTrue(body.changes.decks.all { it.deletedAt == null } && body.changes.cards.all { it.deletedAt == null })
        assertEquals("40201040", recorder.requests.first().header("code"))
        assertEquals(ApiResult.Failure.CodeRejected, network.errors.call { network.gatedApi.snapshot(null) })
    }

    @Test
    fun `a gated call without a typed code never reaches the network`() = runTest {
        val recorder = Recorder()
        val network = factory(load("sync_snapshot_200.json"), recorder = recorder)
        assertEquals(ApiResult.Failure.CodeMissing, network.errors.call { network.gatedApi.snapshot(null) })
        assertTrue(recorder.requests.isEmpty())
    }

    @Test
    fun `status endpoints decode`() = runTest {
        val network = factory(load("sync_status_200.json"), load("ai_status_200.json"))
        val status = (network.errors.call { network.api.status() } as ApiResult.Success).data
        assertEquals(ServerCounts(2, 2, 1), status.counts)
        assertEquals("2026-10-08 20:21:04", status.lastSyncAt)
        val ai = (network.errors.call { network.api.aiStatus() } as ApiResult.Success).data
        assertFalse(ai.configured)
        assertEquals(setOf("flash", "pro"), ai.models.keys)
    }

    private fun aiRepository(vararg fixtures: Fixture, recorder: Recorder = Recorder()): AiRepository {
        val vault = CodeVault().apply { store("40201040") }
        val network = factory(*fixtures, recorder = recorder, vault = vault)
        return AiRepository(network.api, network.gatedApi, network.errors, CodeGate(vault) { }, network.json)
    }

    @Test
    fun `draft success sends the exact request shape and reads misc`() = runTest {
        val fixture = load("ai_draft_200.json")
        val recorder = Recorder()
        val repository = aiRepository(fixture, recorder = recorder)
        val request = json.decodeFromJsonElement(DraftRequest.serializer(), fixture.requestBody!!)
        val result = (repository.draft(request) as ApiResult.Success).data

        assertEquals(fixture.requestBody, Json.parseToJsonElement(recorder.bodies.single()))
        assertEquals("40201040", recorder.requests.single().header("code"))
        assertEquals(listOf("qa", "true_false", "multiple_choice", "basic"), result.cards.map { it.type })
        assertEquals(false, result.cards[1].answer)
        assertEquals(1, result.cards[2].choices!!.count { it.correct })
        val misc = fixture.response.jsonObject.getValue("misc").jsonObject
        assertEquals(misc.getValue("discarded").jsonPrimitive.int, result.misc.discarded)
        assertEquals(misc.getValue("model").jsonPrimitive.content, result.misc.model)
        assertEquals((misc.getValue("warnings") as JsonArray).size, result.misc.warnings.size)
    }

    @Test
    fun `partial success keeps the warnings`() = runTest {
        val result = (aiRepository(load("ai_draft_200_partial.probe.json")).draft(DraftRequest(text = "x")) as ApiResult.Success).data
        assertEquals(5, result.misc.discarded)
        assertEquals(5, result.misc.warnings.size)
        assertEquals(4, result.cards.size)
    }

    @Test
    fun `every draft failure is distinguished`() = runTest {
        val request = DraftRequest(text = "x")
        assertEquals(ApiResult.Failure.Validation("The text field is required."), aiRepository(load("ai_draft_400_empty_text.json")).draft(request))
        val tooLong = aiRepository(load("ai_draft_422_text_too_long.json")).draft(request)
        assertTrue(tooLong is ApiResult.Failure.TextTooLong && tooLong.detail.startsWith("O texto tem 12001 caracteres"))
        assertEquals(ApiResult.Failure.RateLimited(50), aiRepository(load("ai_draft_429.json")).draft(request))
        assertTrue(aiRepository(load("ai_draft_502_invalid_format.probe.json")).draft(request) is ApiResult.Failure.AiInvalidResponse)
        assertTrue(aiRepository(load("ai_draft_502_unreachable.probe.json")).draft(request) is ApiResult.Failure.AiUnreachable)
        assertTrue(aiRepository(load("ai_draft_503_not_configured.json")).draft(request) is ApiResult.Failure.AiNotConfigured)
    }
}
