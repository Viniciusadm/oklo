package com.archieapps.oklo.data.sync

import com.archieapps.oklo.core.net.ApiErrorMapper
import com.archieapps.oklo.core.net.ApiResult
import com.archieapps.oklo.core.net.GatedOkloApi
import com.archieapps.oklo.core.net.OkloApi
import com.archieapps.oklo.core.net.PullBody
import com.archieapps.oklo.core.net.PushBody
import com.archieapps.oklo.core.net.PushRequest
import com.archieapps.oklo.core.session.CodeGate

class RetrofitSyncRemote(
    private val api: OkloApi,
    private val gatedApi: GatedOkloApi,
    private val errors: ApiErrorMapper,
    private val codeGate: CodeGate?,
) : SyncRemote {
    override suspend fun push(request: PushRequest): ApiResult<PushBody> = errors.call { api.push(request) }

    override suspend fun pull(since: Long, cursor: String?): ApiResult<PullBody> =
        errors.call { api.pull(since, cursor) }

    override suspend fun snapshot(cursor: String?): ApiResult<PullBody> {
        val gate = codeGate ?: return errors.call { gatedApi.snapshot(cursor) }
        return gate.run { errors.call { gatedApi.snapshot(cursor) } }
    }
}
