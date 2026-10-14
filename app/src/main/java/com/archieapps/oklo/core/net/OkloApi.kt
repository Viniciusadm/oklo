package com.archieapps.oklo.core.net

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

interface OkloApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Envelope<LoginBody>

    @POST("api/flashcards/sync/push")
    suspend fun push(@Body request: PushRequest): Envelope<PushBody>

    @GET("api/flashcards/sync/pull")
    suspend fun pull(
        @Query("since") since: Long,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int? = null,
    ): Envelope<PullBody>

    @GET("api/flashcards/sync/status")
    suspend fun status(): Envelope<SyncStatusBody>

    @GET("api/flashcards/ai/status")
    suspend fun aiStatus(): Envelope<AiStatusBody>

    @GET("api/flashcards/ai/lesson/{jobId}")
    suspend fun lessonJob(@Path("jobId") jobId: String): Envelope<LessonJobBody>
}

interface GatedOkloApi {
    @GET("api/flashcards/sync/snapshot")
    suspend fun snapshot(
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int? = null,
        @Tag gated: Gated = Gated.REQUIRED,
    ): Envelope<PullBody>

    @POST("api/flashcards/ai/draft")
    suspend fun draft(
        @Body request: DraftRequest,
        @Tag gated: Gated = Gated.REQUIRED,
    ): Envelope<DraftBody>

    @POST("api/flashcards/ai/lesson")
    suspend fun lessonGenerate(
        @Body request: LessonGenerateRequest,
        @Tag gated: Gated = Gated.REQUIRED,
    ): Envelope<LessonJobStarted>
}

class Gated private constructor() {
    companion object {
        val REQUIRED = Gated()
    }
}

class CodeUnavailableException : java.io.IOException("Código de acesso indisponível")
