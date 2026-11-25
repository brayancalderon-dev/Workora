package com.workora.app.data.remote.gmail

import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import android.util.Patterns
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.workora.app.domain.repository.GmailAuthorizationResult
import com.workora.app.domain.repository.GmailDraftRepository
import com.workora.app.domain.repository.GmailSessionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleGmailDraftRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gmailApi: GmailApi
) : GmailDraftRepository {
    private val authorizationClient by lazy { Identity.getAuthorizationClient(context) }
    private val prefs by lazy { context.getSharedPreferences("gmail_prefs", Context.MODE_PRIVATE) }

    private val _sessionState = MutableStateFlow(
        GmailSessionState(
            isAuthorized = prefs.getBoolean("gmail_connected", false),
            accountEmail = prefs.getString("gmail_account_email", null)
        )
    )
    override val sessionState: StateFlow<GmailSessionState> = _sessionState.asStateFlow()

    private fun updateSession(isAuthorized: Boolean, email: String? = null) {
        val storedEmail = prefs.getString("gmail_account_email", null)
        val detectedEmail = email?.takeIf { it.isNotBlank() } ?: storedEmail

        prefs.edit()
            .putBoolean("gmail_connected", isAuthorized)
            .apply()

        if (isAuthorized && !detectedEmail.isNullOrBlank()) {
            prefs.edit().putString("gmail_account_email", detectedEmail).apply()
        } else if (!isAuthorized) {
            prefs.edit().remove("gmail_account_email").apply()
        }

        _sessionState.value = GmailSessionState(
            isAuthorized = isAuthorized,
            accountEmail = if (isAuthorized) detectedEmail else null
        )
        Log.d(TAG, "[STATE] Global sessionState updated: isAuthorized=$isAuthorized, email=$detectedEmail")
    }

    override suspend fun authorize(): GmailAuthorizationResult {
        Log.d(TAG, "[AUTH] Google authorization request created")
        Log.d(TAG, "[AUTH] Starting authorization request")
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GMAIL_COMPOSE_SCOPE)))
            .build()
        
        val result = try {
            authorizationClient.authorize(request).await()
        } catch (e: Exception) {
            Log.e(TAG, "[AUTH] Token retrieval failed during authorize()", e)
            if (e is ApiException) {
                Log.e(TAG, "[AUTH] ApiException status code=${e.statusCode}, message=${e.message}", e)
            }
            throw e
        }

        Log.d(TAG, "[AUTH] Authorization request completed")
        if (result.hasResolution()) {
            Log.d(TAG, "[AUTH] Consent required")
            Log.d(TAG, "[AUTH] PendingIntent resolution launched")
            return GmailAuthorizationResult.ConsentRequired(
                result.pendingIntent ?: error("Google no devolvió la solicitud de autorización.")
            )
        }
        val token = extractTokenFromResult(result)
        updateSession(true, null)
        Log.d(TAG, "[AUTH] Token retrieval successful")
        Log.d(TAG, "[AUTH] Token present: ${token.isNotBlank()}")
        return GmailAuthorizationResult.Authorized(token, prefs.getString("gmail_account_email", null))
    }

    override suspend fun completeAuthorization(data: Intent?): String {
        Log.d(TAG, "[AUTH] Token retrieval started in completeAuthorization")
        if (data != null) {
            val intentResult = try {
                authorizationClient.getAuthorizationResultFromIntent(data)
            } catch (e: ApiException) {
                Log.e(TAG, "[AUTH] getAuthorizationResultFromIntent failed with ApiException: statusCode=${e.statusCode}, message=${e.message}", e)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "[AUTH] getAuthorizationResultFromIntent failed with Exception", e)
                null
            }

            if (intentResult != null) {
                val token = runCatching { extractTokenFromResult(intentResult) }.getOrNull()
                if (!token.isNullOrBlank()) {
                    updateSession(true, null)
                    Log.d(TAG, "[AUTH] Token retrieval successful from Intent")
                    Log.d(TAG, "[AUTH] Token present: true")
                    return token
                }
            }
        }

        Log.d(TAG, "[AUTH] Re-querying authorizationClient.authorize() after system consent")
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GMAIL_COMPOSE_SCOPE)))
            .build()
        
        val authResult = try {
            authorizationClient.authorize(request).await()
        } catch (e: ApiException) {
            Log.e(TAG, "[AUTH] re-query authorize() failed with ApiException: statusCode=${e.statusCode}, message=${e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[AUTH] re-query authorize() failed with Exception", e)
            throw e
        }

        val token = extractTokenFromResult(authResult)
        updateSession(true, null)
        Log.d(TAG, "[AUTH] Token retrieval successful")
        Log.d(TAG, "[AUTH] Token present: ${token.isNotBlank()}")
        return token
    }

    private fun extractTokenFromResult(result: com.google.android.gms.auth.api.identity.AuthorizationResult): String {
        val hasScope = result.grantedScopes.any { it.contains("gmail", ignoreCase = true) || it.contains("compose", ignoreCase = true) }
        Log.d(TAG, "[AUTH] Authorization status: Granted scopes count=${result.grantedScopes.size}, hasGmailScope=$hasScope")

        val directToken = result.accessToken
        if (!directToken.isNullOrBlank()) {
            return directToken
        }

        Log.e(TAG, "[AUTH] Token retrieval failed: Direct accessToken in AuthorizationResult was empty")
        throw GmailDraftException("Google no devolvió un token de acceso válido para Gmail. Vuelve a intentar la conexión.")
    }

    override suspend fun revoke(accessToken: String?) {
        Log.d(TAG, "[AUTH] Revoking Gmail authorization")
        if (!accessToken.isNullOrBlank()) {
            runCatching {
                authorizationClient.clearToken(
                    ClearTokenRequest.builder().setToken(accessToken).build()
                ).await()
            }
        }
        runCatching {
            authorizationClient.revokeAccess(
                RevokeAccessRequest.builder()
                    .setScopes(listOf(Scope(GMAIL_COMPOSE_SCOPE)))
                    .build()
            ).await()
        }
        updateSession(false, null)
    }

    override suspend fun createDraft(
        accessToken: String,
        recipient: String?,
        subject: String,
        body: String
    ): String {
        Log.d(TAG, "[DRAFT] Request sent to Gmail API: recipientPresent=${!recipient.isNullOrBlank()}, subjectLength=${subject.length}")
        require(subject.isNotBlank() && body.isNotBlank()) { "El asunto y el cuerpo del borrador son obligatorios." }
        val safeRecipient = recipient?.takeIf { Patterns.EMAIL_ADDRESS.matcher(it).matches() }
        val encodedSubject = Base64.encodeToString(
            subject.replace(Regex("[\\r\\n]+"), " ").toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
        val encodedBody = Base64.encodeToString(body.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
            .chunked(MIME_LINE_LENGTH)
            .joinToString("\r\n")
        val rawMime = buildString {
            if (safeRecipient != null) append("To: $safeRecipient\r\n")
            append("Subject: =?UTF-8?B?$encodedSubject?=\r\n")
            append("MIME-Version: 1.0\r\n")
            append("Content-Type: text/plain; charset=UTF-8\r\n")
            append("Content-Transfer-Encoding: base64\r\n\r\n")
            append(encodedBody)
        }
        val encoded = Base64.encodeToString(
            rawMime.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        
        val response = try {
            gmailApi.createDraft("Bearer $accessToken", GmailDraftRequest(GmailRawMessage(encoded)))
        } catch (e: Exception) {
            Log.e(TAG, "[DRAFT] Draft creation failed due to network exception", e)
            throw e
        }

        Log.d(TAG, "[DRAFT] HTTP status: ${response.code()}")

        if (!response.isSuccessful) {
            val reason = when (response.code()) {
                401, 403 -> {
                    updateSession(false, null)
                    "Google rechazó la autorización (HTTP ${response.code()}). Desconecta y vuelve a conectar Gmail."
                }
                else -> "Gmail no pudo crear el borrador (HTTP ${response.code()}). Inténtalo nuevamente."
            }
            Log.e(TAG, "[DRAFT] Draft creation failed with HTTP code ${response.code()}")
            throw GmailDraftException(reason)
        }
        val draftId = response.body()?.id?.takeIf(String::isNotBlank)
            ?: throw GmailDraftException("Gmail no confirmó la creación del borrador.")
        Log.i(TAG, "[DRAFT] Draft creation successful: draftId present=${draftId.isNotBlank()}")
        return draftId
    }

    private companion object {
        const val TAG = "WORKORA_GMAIL_AUTH"
        const val GMAIL_COMPOSE_SCOPE = "https://www.googleapis.com/auth/gmail.compose"
        const val MIME_LINE_LENGTH = 76
    }
}

class GmailDraftException(message: String) : IllegalStateException(message)
