package com.archieapps.oklo.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException
import java.io.IOException

class ApiErrorMapper(private val json: Json) {
    suspend fun <T> call(block: suspend () -> Envelope<T>): ApiResult<T> = try {
        val envelope = block()
        val body = envelope.body
        if (envelope.success && body != null) {
            ApiResult.Success(body, envelope.message, envelope.misc)
        } else {
            fromEnvelope(200, envelope.message, envelope.error.text())
        }
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: CodeUnavailableException) {
        ApiResult.Failure.CodeMissing
    } catch (failure: HttpException) {
        fromHttp(failure)
    } catch (failure: IOException) {
        ApiResult.Failure.Network
    } catch (failure: SerializationException) {
        ApiResult.Failure.Malformed(failure)
    } catch (failure: IllegalArgumentException) {
        ApiResult.Failure.Malformed(failure)
    }

    private fun fromHttp(failure: HttpException): ApiResult.Failure {
        val status = failure.code()
        if (status == 401) return ApiResult.Failure.Unauthorized
        if (status == 429) {
            return ApiResult.Failure.RateLimited(
                failure.response()?.headers()?.get("Retry-After")?.trim()?.toIntOrNull(),
            )
        }
        val raw = runCatching { failure.response()?.errorBody()?.string() }.getOrNull()
        val envelope = raw?.let { decode(it) }
        return fromEnvelope(status, envelope?.message, envelope?.error.text())
    }

    fun fromEnvelope(status: Int, message: String?, error: String?): ApiResult.Failure {
        val text = message.orEmpty()
        return when {
            text.startsWith("Código inválido", ignoreCase = true) -> ApiResult.Failure.CodeRejected
            status == 503 -> ApiResult.Failure.AiNotConfigured(message)
            status == 502 && text.contains("formato", ignoreCase = true) -> ApiResult.Failure.AiInvalidResponse(message)
            status == 502 -> ApiResult.Failure.AiUnreachable(message)
            status == 422 && text.contains("limite", ignoreCase = true) -> ApiResult.Failure.TextTooLong(text)
            status == 400 || status == 422 -> ApiResult.Failure.Validation(
                error?.takeIf { it.isNotBlank() } ?: message?.takeIf { it.isNotBlank() } ?: "Requisição inválida.",
            )
            else -> ApiResult.Failure.Server(status, message?.takeIf { it.isNotBlank() })
        }
    }

    private fun decode(raw: String): Envelope<JsonElement>? =
        runCatching { json.decodeFromString<Envelope<JsonElement>>(raw) }.getOrNull()

    private fun JsonElement?.text(): String? = when (this) {
        null -> null
        is JsonPrimitive -> contentOrNull
        else -> toString()
    }
}
