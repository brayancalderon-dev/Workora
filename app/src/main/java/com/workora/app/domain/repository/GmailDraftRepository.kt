package com.workora.app.domain.repository

import android.content.Intent
import android.app.PendingIntent
import kotlinx.coroutines.flow.StateFlow

data class GmailSessionState(
    val isAuthorized: Boolean = false,
    val accountEmail: String? = null
)

sealed interface GmailAuthorizationResult {
    data class Authorized(val accessToken: String, val accountEmail: String? = null) : GmailAuthorizationResult
    data class ConsentRequired(val pendingIntent: PendingIntent) : GmailAuthorizationResult
}

interface GmailDraftRepository {
    val sessionState: StateFlow<GmailSessionState>
    suspend fun authorize(): GmailAuthorizationResult
    suspend fun completeAuthorization(data: Intent?): String
    suspend fun revoke(accessToken: String?)
    suspend fun createDraft(accessToken: String, recipient: String?, subject: String, body: String): String
}
