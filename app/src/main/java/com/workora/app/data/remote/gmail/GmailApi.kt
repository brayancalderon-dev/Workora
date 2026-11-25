package com.workora.app.data.remote.gmail

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class GmailDraftRequest(val message: GmailRawMessage)
data class GmailRawMessage(val raw: String)
data class GmailDraftResponse(val id: String?, val message: GmailRawMessage?)

interface GmailApi {
    @POST("gmail/v1/users/me/drafts")
    suspend fun createDraft(
        @Header("Authorization") authorization: String,
        @Body request: GmailDraftRequest
    ): Response<GmailDraftResponse>
}
