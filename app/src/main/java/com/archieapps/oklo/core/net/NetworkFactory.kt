package com.archieapps.oklo.core.net

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

fun interface TokenProvider {
    fun currentToken(): String?
}

fun interface CodeProvider {
    fun currentCode(): String?
}

class AuthInterceptor(private val tokens: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder().header("Accept", "application/json")
        tokens.currentToken()?.takeIf { it.isNotBlank() }?.let {
            builder.header("Authorization", "Bearer $it")
        }
        return chain.proceed(builder.build())
    }
}

class CodeInterceptor(private val codes: CodeProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.tag(Gated::class.java) == null) {
            return chain.proceed(request)
        }
        val code = codes.currentCode() ?: throw CodeUnavailableException()
        return chain.proceed(request.newBuilder().header("code", code).build())
    }
}

object OkloJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
        coerceInputValues = false
    }
}

class NetworkFactory(
    baseUrl: String,
    tokens: TokenProvider,
    codes: CodeProvider,
    interceptors: List<Interceptor> = emptyList(),
) {
    val json: Json = OkloJson.instance

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor(tokens))
        .addInterceptor(CodeInterceptor(codes))
        .apply { interceptors.forEach { addInterceptor(it) } }
        .build()

    private val gatedClient: OkHttpClient = client.newBuilder()
        .retryOnConnectionFailure(false)
        .readTimeout(150, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS)
        .build()

    private val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(normalizedBaseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val api: OkloApi = retrofit.create(OkloApi::class.java)

    val gatedApi: GatedOkloApi = retrofit.newBuilder()
        .client(gatedClient)
        .build()
        .create(GatedOkloApi::class.java)

    val errors = ApiErrorMapper(json)
}
