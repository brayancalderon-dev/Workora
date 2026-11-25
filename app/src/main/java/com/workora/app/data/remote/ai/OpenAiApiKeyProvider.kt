package com.workora.app.data.remote.ai

import android.util.Base64
import android.util.Log
import com.workora.app.BuildConfig
import com.workora.app.domain.repository.AiImagePayload
import com.workora.app.domain.repository.AiProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.random.Random

class OpenAiApiKeyProvider @Inject constructor() : AiProvider {
    override suspend fun generateJson(prompt: String, image: AiImagePayload?): String = generate(prompt, true, image)
    override suspend fun generateText(prompt: String): String = generate(prompt, false, null)

    private suspend fun generate(prompt: String, jsonResponse: Boolean, image: AiImagePayload?): String {
        require(prompt.isNotBlank()) { "No hay contenido para enviar a OpenAI." }
        require(image == null || (image.bytes.isNotEmpty() && image.bytes.size <= MAX_IMAGE_BYTES && image.mimeType in SUPPORTED_MIME_TYPES)) {
            "La imagen de la vacante está vacía, supera 8 MiB o usa un formato no compatible."
        }
        val apiKey = BuildConfig.OPENAI_API_KEY.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalStateException("OpenAI no está configurado. Añade openai.apiKey a local.properties para compilar Debug.")

        return try {
            withTimeout(TOTAL_TIMEOUT_MS) {
                var attempt = 0
                while (true) {
                    attempt++
                    val started = System.nanoTime()
                    val result = try {
                        runInterruptible(Dispatchers.IO) { request(prompt, apiKey, jsonResponse, image) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        val elapsed = elapsedMs(started)
                        Log.w(TAG, "model=$MODEL endpoint=$ENDPOINT_PATH attempt=$attempt elapsedMs=$elapsed type=${error.javaClass.simpleName} networkFailure=true")
                        if (error is SocketTimeoutException || error is IOException) {
                            if (attempt < MAX_ATTEMPTS) {
                                delay(backoff(attempt))
                                continue
                            }
                            throw IllegalStateException("No se pudo conectar con los servidores de OpenAI tras $attempt intentos. Revisa tu conexión a internet.")
                        }
                        throw error
                    }

                    val elapsed = elapsedMs(started)
                    if (result.status !in 200..299) {
                        val apiError = parseApiError(result.body)
                        Log.w(TAG, "model=$MODEL endpoint=$ENDPOINT_PATH attempt=$attempt elapsedMs=$elapsed http=${result.status} code=${apiError.code ?: "unknown"} message=${safeLog(apiError.message)}")

                        if (result.status in RETRYABLE_STATUS && attempt < MAX_ATTEMPTS) {
                            val waitMs = result.retryAfterMs ?: backoff(attempt)
                            if (waitMs < TOTAL_TIMEOUT_MS) {
                                delay(waitMs)
                                continue
                            }
                        }
                        throw IllegalStateException(userMessage(result.status, apiError, attempt))
                    }

                    if (result.body.isBlank()) {
                        Log.e(TAG, "model=$MODEL attempt=$attempt elapsedMs=$elapsed http=${result.status} bodyEmpty=true")
                        if (attempt < MAX_ATTEMPTS) {
                            delay(backoff(attempt))
                            continue
                        }
                        throw IllegalStateException("El servicio de OpenAI devolvió una respuesta vacía. Inténtalo nuevamente.")
                    }

                    try {
                        val response = Json.parseToJsonElement(result.body).jsonObject
                        val choices = response["choices"]?.jsonArray.orEmpty()
                        val choice = choices.firstOrNull()?.jsonObject
                            ?: throw IllegalStateException("OpenAI no devolvió candidatos de respuesta. Inténtalo nuevamente.")

                        val messageObj = choice["message"]?.jsonObject
                        val text = messageObj?.get("content")?.jsonPrimitive?.contentOrNull
                            ?: throw IllegalStateException("OpenAI no devolvió texto utilizable para el análisis.")

                        Log.i(TAG, "model=$MODEL endpoint=$ENDPOINT_PATH attempt=$attempt elapsedMs=$elapsed http=${result.status} responseBytes=${result.body.toByteArray().size} success=true")
                        return@withTimeout text
                    } catch (error: IllegalStateException) {
                        throw error
                    } catch (error: Exception) {
                        Log.e(TAG, "model=$MODEL attempt=$attempt elapsedMs=$elapsed responseJsonError=${error.javaClass.simpleName}")
                        if (attempt < MAX_ATTEMPTS) {
                            delay(backoff(attempt))
                            continue
                        }
                        throw IllegalStateException("La respuesta del servicio de OpenAI no fue válida. Inténtalo nuevamente.")
                    }
                }
                @Suppress("UNREACHABLE_CODE") throw IllegalStateException("No se pudo completar la solicitud con el servicio de OpenAI.")
            }
        } catch (cancelled: CancellationException) {
            if (!currentCoroutineContext().isActive) throw cancelled
            Log.w(TAG, "model=$MODEL endpoint=$ENDPOINT_PATH timeoutMs=$TOTAL_TIMEOUT_MS timeout=true")
            throw IllegalStateException("El análisis con OpenAI tardó demasiado en responder (Timeout). Inténtalo nuevamente.")
        }
    }

    private fun request(prompt: String, apiKey: String, jsonResponse: Boolean, image: AiImagePayload?): HttpResult {
        val userContent: JsonElement = if (image != null) {
            buildJsonArray {
                add(buildJsonObject {
                    put("type", JsonPrimitive("text"))
                    put("text", JsonPrimitive(prompt))
                })
                add(buildJsonObject {
                    put("type", JsonPrimitive("image_url"))
                    put("image_url", buildJsonObject {
                        val base64Data = Base64.encodeToString(image.bytes, Base64.NO_WRAP)
                        put("url", JsonPrimitive("data:${image.mimeType};base64,$base64Data"))
                    })
                })
            }
        } else {
            JsonPrimitive(prompt)
        }

        val requestBody = buildJsonObject {
            put("model", JsonPrimitive(MODEL))
            if (jsonResponse) {
                put("response_format", buildJsonObject {
                    put("type", JsonPrimitive("json_object"))
                })
            }
            put("messages", buildJsonArray {
                add(buildJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", userContent)
                })
            })
        }.toString()

        val connection = (URL(GENERATE_CONTENT_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }

        return try {
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(MAX_RESPONSE_CHARS) }.orEmpty()
            HttpResult(status, body, parseRetryAfter(connection.getHeaderField("Retry-After")))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseApiError(body: String): ApiError = runCatching {
        val error = Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
        ApiError(
            message = error?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty(),
            code = error?.get("code")?.jsonPrimitive?.contentOrNull ?: error?.get("type")?.jsonPrimitive?.contentOrNull
        )
    }.getOrDefault(ApiError("", null))

    private fun safeLog(value: String): String = value
        .replace(Regex("sk-[0-9A-Za-z_-]{20,}"), "[redacted]")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[email]")
        .replace(Regex("(?<!\\d)(?:\\+?\\d[\\d ()-]{7,}\\d)(?!\\d)"), "[number]")
        .replace(Regex("[\\r\\n\\t]"), " ")
        .take(400)

    private fun parseRetryAfter(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        value.toLongOrNull()?.let { seconds ->
            if (seconds < 0) return null
            return if (seconds > Long.MAX_VALUE / 1_000L) Long.MAX_VALUE else seconds * 1_000L
        }
        return runCatching {
            (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0)
        }.getOrNull()
    }

    private fun backoff(attempt: Int): Long = BASE_BACKOFF_MS * (1L shl (attempt - 1)) + Random.nextLong(0, JITTER_MS + 1)
    private fun elapsedMs(started: Long) = (System.nanoTime() - started) / 1_000_000

    private fun userMessage(status: Int, error: ApiError, attempts: Int) = when {
        status == 400 -> "El formato de la solicitud no fue aceptado por OpenAI (HTTP 400)."
        status == 401 || status == 403 -> "Acceso no autorizado a OpenAI (HTTP $status). Verifica la API Key en local.properties."
        status == 429 || error.code == "insufficient_quota" || error.code == "credit_balance_exhausted" ->
            "Se alcanzó el límite de cuota o crédito de la API Key de OpenAI (HTTP 429). Agrega créditos en la plataforma de OpenAI para continuar."
        status == 503 -> "El servicio de OpenAI está temporalmente no disponible (HTTP 503) tras $attempts intentos. Inténtalo más tarde."
        status in RETRYABLE_STATUS -> "El servicio de OpenAI está ocupado (HTTP $status) tras $attempts intentos. Inténtalo de nuevo."
        else -> "OpenAI no pudo completar la solicitud (HTTP $status)."
    }

    private data class HttpResult(val status: Int, val body: String, val retryAfterMs: Long?)
    private data class ApiError(val message: String, val code: String?)

    private companion object {
        const val TAG = "WorkoraOpenAI"
        const val MODEL = "gpt-4o-mini"
        const val ENDPOINT_PATH = "/v1/chat/completions"
        const val GENERATE_CONTENT_URL = "https://api.openai.com$ENDPOINT_PATH"
        const val TOTAL_TIMEOUT_MS = 40_000L
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 25_000
        const val BASE_BACKOFF_MS = 1_000L
        const val JITTER_MS = 250L
        const val MAX_ATTEMPTS = 3
        const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
        const val MAX_RESPONSE_CHARS = 64 * 1024
        val RETRYABLE_STATUS = setOf(408, 429, 500, 502, 503, 504)
        val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")
    }
}