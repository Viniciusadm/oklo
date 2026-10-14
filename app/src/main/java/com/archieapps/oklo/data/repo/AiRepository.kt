package com.archieapps.oklo.data.repo

import com.archieapps.oklo.core.net.AiStatusBody
import com.archieapps.oklo.core.net.ApiErrorMapper
import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.DraftCardDto
import com.archieapps.oklo.core.net.DraftMisc
import com.archieapps.oklo.core.net.DraftRequest
import com.archieapps.oklo.core.net.GatedOkloApi
import com.archieapps.oklo.core.net.LessonGenerateRequest
import com.archieapps.oklo.core.net.LessonJobBody
import com.archieapps.oklo.core.net.LessonJobStarted
import com.archieapps.oklo.core.net.OkloApi
import com.archieapps.oklo.core.session.CodeGate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

data class DraftResult(
    val cards: List<DraftCardDto>,
    val misc: DraftMisc,
)

interface LessonGenerator {
    suspend fun lessonGenerate(request: LessonGenerateRequest): ApiResult<LessonJobStarted>

    suspend fun lessonJob(jobId: String): ApiResult<LessonJobBody>
}

class AiRepository(
    private val api: OkloApi,
    private val gatedApi: GatedOkloApi,
    private val errors: ApiErrorMapper,
    private val codeGate: CodeGate,
    private val json: Json,
) : LessonGenerator {
    suspend fun status(): ApiResult<AiStatusBody> = errors.call { api.aiStatus() }

    suspend fun draft(request: DraftRequest): ApiResult<DraftResult> {
        val result = codeGate.run { errors.call { gatedApi.draft(request) } }
        return when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val misc = (result.misc as? JsonObject)?.let {
                    runCatching { json.decodeFromJsonElement(DraftMisc.serializer(), it) }.getOrNull()
                } ?: DraftMisc()
                ApiResult.Success(DraftResult(result.data.cards, misc), result.message, result.misc)
            }
        }
    }

    override suspend fun lessonGenerate(request: LessonGenerateRequest): ApiResult<LessonJobStarted> =
        codeGate.run { errors.call { gatedApi.lessonGenerate(request) } }

    override suspend fun lessonJob(jobId: String): ApiResult<LessonJobBody> = errors.call { api.lessonJob(jobId) }

    companion object {
        const val MAX_TEXT = 12000
        const val MAX_CARDS = 40
    }
}
