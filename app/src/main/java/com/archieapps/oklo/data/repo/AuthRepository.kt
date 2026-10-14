package com.archieapps.oklo.data.repo

import com.archieapps.oklo.core.net.ApiErrorMapper
import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.LoginRequest
import com.archieapps.oklo.core.net.OkloApi
import com.archieapps.oklo.core.session.CodeVault
import com.archieapps.oklo.core.session.SessionStore

class AuthRepository(
    private val api: OkloApi,
    private val errors: ApiErrorMapper,
    private val sessionStore: SessionStore,
    private val vault: CodeVault,
) {
    suspend fun login(email: String, password: String): ApiResult<Unit> {
        val result = errors.call { api.login(LoginRequest(email.trim(), password)) }
        return when (result) {
            is ApiResult.Failure -> result
            is ApiResult.Success -> {
                val body = result.data
                sessionStore.save(body.token, body.user.name, body.user.email, body.user.blocked)
                vault.seedServerBlocked(body.user.blocked)
                ApiResult.Success(Unit)
            }
        }
    }

    suspend fun logout() {
        vault.forget()
        sessionStore.clear()
    }
}
