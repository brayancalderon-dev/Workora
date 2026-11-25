package com.workora.app.presentation.gmail.viewmodel

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.workora.app.core.utils.FileHelper
import com.workora.app.data.local.dao.VacancyDao
import com.workora.app.data.local.entity.VacancyEntity
import com.workora.app.domain.model.gmail.QuestionAnswer
import com.workora.app.domain.model.gmail.VacancyAssessment
import com.workora.app.domain.repository.CompatibilityAnalyzer
import com.workora.app.domain.repository.AiImagePayload
import com.workora.app.domain.repository.GmailAuthorizationResult
import com.workora.app.domain.repository.GmailDraftRepository
import com.workora.app.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject

enum class GmailAnalysisStage {
    IDLE,
    ANALYZING_INITIAL,
    INITIAL_LOW,
    QUESTIONS,
    REEVALUATING,
    FINAL_RESULT
}

data class GmailUiState(
    val stage: GmailAnalysisStage = GmailAnalysisStage.IDLE,
    val initialAssessment: VacancyAssessment? = null,
    val finalAssessment: VacancyAssessment? = null,
    val answers: Map<String, QuestionAnswer> = emptyMap(),
    val isAuthorizing: Boolean = false,
    val pendingAuthorization: PendingIntent? = null,
    val gmailAuthorized: Boolean = false,
    val connectedAccountEmail: String? = null,
    val isCreatingDraft: Boolean = false,
    val draftId: String? = null,
    val draftRecipientMissing: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val diagnosticsLog: String? = null
)

@HiltViewModel
class GmailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vacancyDao: VacancyDao,
    private val profileRepository: ProfileRepository,
    private val compatibilityAnalyzer: CompatibilityAnalyzer,
    private val gmailDraftRepository: GmailDraftRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val history: StateFlow<List<VacancyEntity>> = vacancyDao.getAllVacancies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val prefs = context.getSharedPreferences("gmail_prefs", Context.MODE_PRIVATE)

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow(GmailUiState(
        draftId = savedStateHandle[KEY_LAST_DRAFT_ID],
        gmailAuthorized = prefs.getBoolean("gmail_connected", false)
    ))
    val uiState: StateFlow<GmailUiState> = _uiState

    private var gmailAccessToken: String? = null

    init {
        checkExistingGmailAuthorization()
    }

    fun checkExistingGmailAuthorization() {
        val wasConnected = prefs.getBoolean("gmail_connected", false)
        if (wasConnected || _uiState.value.gmailAuthorized) {
            viewModelScope.launch {
                try {
                    when (val result = gmailDraftRepository.authorize()) {
                        is GmailAuthorizationResult.Authorized -> {
                            gmailAccessToken = result.accessToken
                            prefs.edit().putBoolean("gmail_connected", true).apply()
                            _uiState.value = _uiState.value.copy(gmailAuthorized = true)
                        }
                        is GmailAuthorizationResult.ConsentRequired -> {
                            // Only reset if silent check explicitly needs new consent
                            if (!wasConnected) {
                                _uiState.value = _uiState.value.copy(gmailAuthorized = false)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Retain saved connected state on network blip so button stays enabled
                    _uiState.value = _uiState.value.copy(gmailAuthorized = wasConnected)
                }
            }
        }
    }

    private var evaluatedCvText: String? = null
    private var evaluatedCvHash: String? = null
    private var currentVacancyText: String? = null
    private var currentVacancyImage: AiImagePayload? = null
    private var analysisKey: String? = null
    private var currentHistoryId: Int? = null
    private var requestGeneration = 0

    fun invalidateAnalysis() {
        requestGeneration++
        currentVacancyText = null
        currentVacancyImage = null
        analysisKey = null
        currentHistoryId = null
        evaluatedCvText = null
        evaluatedCvHash = null
        _uiState.value = _uiState.value.copy(
            stage = GmailAnalysisStage.IDLE,
            initialAssessment = null,
            finalAssessment = null,
            answers = emptyMap(),
            draftId = null,
            draftRecipientMissing = false,
            error = null,
            notice = null
        )
    }

    fun analyzeInitial(vacancyText: String, imageUri: String?) {
        if (_uiState.value.stage == GmailAnalysisStage.ANALYZING_INITIAL ||
            _uiState.value.stage == GmailAnalysisStage.REEVALUATING
        ) return
        if (vacancyText.isBlank() && imageUri.isNullOrBlank()) {
            _uiState.value = _uiState.value.copy(error = "Ingresa texto o selecciona una imagen de la vacante antes de analizarla.")
            return
        }
        val priorAssessment = _uiState.value.initialAssessment
        val priorKey = analysisKey
        val generation = ++requestGeneration
        _uiState.value = _uiState.value.copy(stage = GmailAnalysisStage.ANALYZING_INITIAL, error = null, notice = null)
        viewModelScope.launch {
            try {
                val profile = profileRepository.getProfile().first()
                    ?: error("No hay un CV registrado. Carga tu hoja de vida en Perfil.")
                if (profile.pdfFileName.isNullOrBlank()) error("No hay un CV seleccionado. Carga tu hoja de vida en Perfil.")
                
                var cvText = profile.summary.trim()
                if (cvText.isBlank() || cvText.equals("Mi resumen profesional", ignoreCase = true)) {
                    val reExtracted = withContext(Dispatchers.IO) {
                        FileHelper.extractPdfText(context, profile.pdfFileName)
                    }.trim()
                    if (reExtracted.isNotBlank()) {
                        cvText = reExtracted
                        profileRepository.saveProfile(profile.copy(summary = reExtracted))
                    }
                }

                if (cvText.isBlank() || cvText.equals("Mi resumen profesional", ignoreCase = true)) {
                    error("No se pudo extraer texto suficiente de tu Hoja de Vida. Vuelve a cargar tu CV en PDF en la sección Mi Perfil.")
                }

                val cvHash = sha256(cvText)
                val key = sha256("$cvHash\n${vacancyText.trim()}\n${imageUri.orEmpty()}")
                if (priorKey == key && priorAssessment != null) {
                    _uiState.value = _uiState.value.copy(
                        stage = stageForInitial(priorAssessment),
                        initialAssessment = priorAssessment,
                        finalAssessment = null
                    )
                    return@launch
                }
                var imagePayload: AiImagePayload? = null
                var imageNotice: String? = null
                if (!imageUri.isNullOrBlank()) {
                    try {
                        imagePayload = withContext(Dispatchers.IO) { readVacancyImage(imageUri) }
                    } catch (imageError: Exception) {
                        if (imageError is CancellationException) throw imageError
                        if (vacancyText.isBlank()) throw IllegalArgumentException("No se pudo preparar la imagen para Gemini: ${imageError.message.orEmpty()}")
                        imageNotice = "No se pudo enviar la imagen a Gemini; el análisis continuará con el texto disponible."
                    }
                }
                val assessment = compatibilityAnalyzer.analyzeInitial(cvText, vacancyText.trim(), imagePayload)
                if (generation != requestGeneration) return@launch
                val latestProfile = profileRepository.getProfile().first()
                if (latestProfile?.pdfFileName.isNullOrBlank() || sha256(latestProfile?.summary?.trim().orEmpty()) != cvHash) {
                    error("El CV cambió durante el análisis. Inicia nuevamente con la versión actual.")
                }
                evaluatedCvText = cvText
                evaluatedCvHash = cvHash
                currentVacancyText = vacancyText.trim()
                currentVacancyImage = imagePayload
                analysisKey = key
                var historyNotice: String? = null
                try {
                    val storedImageUri = imageUri?.let(::copyHistoryImage)
                    currentHistoryId = vacancyDao.insertVacancy(assessment.toHistoryEntity(storedImageUri)).toInt()
                } catch (storageError: Exception) {
                    historyNotice = "El análisis terminó, pero no se pudo guardar en el historial: ${storageError.message.orEmpty()}"
                }
                val nextStage = stageForInitial(assessment)
                _uiState.value = _uiState.value.copy(
                    stage = nextStage,
                    initialAssessment = assessment,
                    finalAssessment = if (nextStage == GmailAnalysisStage.FINAL_RESULT) assessment else null,
                    answers = emptyMap(),
                    error = null,
                    notice = listOfNotNull(imageNotice, historyNotice).joinToString(" ").ifBlank { null }
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation != requestGeneration) return@launch
                _uiState.value = _uiState.value.copy(
                    stage = GmailAnalysisStage.IDLE,
                    error = formatUserFriendlyError(error)
                )
            }
        }
    }

    fun setAnswer(questionId: String, answer: QuestionAnswer) {
        val assessment = _uiState.value.initialAssessment ?: return
        if (assessment.questions.none { it.id == questionId }) return
        _uiState.value = _uiState.value.copy(
            answers = _uiState.value.answers + (questionId to answer),
            error = null
        )
    }

    fun reevaluate() {
        val state = _uiState.value
        val initial = state.initialAssessment ?: return
        val cvText = evaluatedCvText ?: return
        val vacancyText = currentVacancyText ?: return
        if (initial.score < INITIAL_QUESTION_THRESHOLD || initial.questions.isEmpty()) return
        if (initial.questions.any { state.answers[it.id]?.selectedOption.isNullOrBlank() }) {
            _uiState.value = state.copy(error = "Responde cada pregunta para completar la reevaluación.")
            return
        }
        val generation = ++requestGeneration
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(stage = GmailAnalysisStage.REEVALUATING, error = null)
            try {
                val currentProfile = profileRepository.getProfile().first()
                val currentCv = currentProfile?.summary?.trim().orEmpty()
                if (currentProfile?.pdfFileName.isNullOrBlank() || sha256(currentCv) != evaluatedCvHash) {
                    invalidateAnalysis()
                    _uiState.value = _uiState.value.copy(error = "El CV cambió desde el análisis inicial. Inicia nuevamente para evaluar la versión vigente.")
                    return@launch
                }
                val result = compatibilityAnalyzer.reevaluate(cvText, vacancyText, initial, state.answers, currentVacancyImage)
                if (generation != requestGeneration) return@launch
                val latestProfile = profileRepository.getProfile().first()
                if (latestProfile?.pdfFileName.isNullOrBlank() || sha256(latestProfile?.summary?.trim().orEmpty()) != evaluatedCvHash) {
                    invalidateAnalysis()
                    _uiState.value = _uiState.value.copy(error = "El CV cambió durante la reevaluación. Inicia nuevamente con la versión actual.")
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    stage = GmailAnalysisStage.FINAL_RESULT,
                    finalAssessment = result,
                    error = null
                )
                currentHistoryId?.let { historyId ->
                    vacancyDao.updateAnalysis(
                        id = historyId,
                        title = result.vacancy.role?.takeIf(String::isNotBlank) ?: "Vacante analizada",
                        company = result.vacancy.company,
                        score = result.score,
                        pros = result.strengths.joinToString("\n") { it.statement },
                        contras = result.gaps.joinToString("\n") { it.requirement },
                        summary = result.vacancy.summary ?: result.justification,
                        status = "Reevaluada"
                    )
                }
            } catch (error: Exception) {
                if (generation != requestGeneration) return@launch
                _uiState.value = _uiState.value.copy(
                    stage = GmailAnalysisStage.QUESTIONS,
                    error = error.message ?: "No se pudo reevaluar la vacante. Puedes intentarlo nuevamente."
                )
            }
        }
    }

    fun authorizeGmail() {
        Log.d(TAG, "[AUTH] Gmail button clicked")
        Log.d(TAG, "[AUTH] Current Gmail connection state: authorized=${_uiState.value.gmailAuthorized}, tokenPresent=${!gmailAccessToken.isNullOrBlank()}")
        if (_uiState.value.isAuthorizing || _uiState.value.isCreatingDraft) return
        if (_uiState.value.gmailAuthorized && !gmailAccessToken.isNullOrBlank()) {
            Log.d(TAG, "[AUTH] Already authorized in session. Invoking createDraft directly.")
            createDraft()
            return
        }
        viewModelScope.launch {
            Log.d(TAG, "[STATE] Gmail authorization state before update: ${_uiState.value.gmailAuthorized}")
            _uiState.value = _uiState.value.copy(isAuthorizing = true, error = null, pendingAuthorization = null)
            Log.d(TAG, "[STATE] StateFlow updated: isAuthorizing=true")
            try {
                Log.d(TAG, "[AUTH] Starting authorization request")
                when (val result = gmailDraftRepository.authorize()) {
                    is GmailAuthorizationResult.Authorized -> {
                        Log.d(TAG, "[AUTH] Token retrieval successful (Silent Authorized)")
                        gmailAccessToken = result.accessToken
                        prefs.edit().putBoolean("gmail_connected", true).apply()
                        Log.d(TAG, "[AUTH] Token present: true")
                        _uiState.value = _uiState.value.copy(
                            isAuthorizing = false,
                            gmailAuthorized = true,
                            notice = "Cuenta Google autorizada. Creando borrador en Gmail…"
                        )
                        Log.d(TAG, "[STATE] Gmail authorization state after update: true")
                        Log.d(TAG, "[STATE] StateFlow updated")
                        createDraft()
                    }
                    is GmailAuthorizationResult.ConsentRequired -> {
                        Log.d(TAG, "[AUTH] Consent required")
                        Log.d(TAG, "[AUTH] PendingIntent resolution launched")
                        _uiState.value = _uiState.value.copy(
                            isAuthorizing = false,
                            pendingAuthorization = result.pendingIntent
                        )
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "[AUTH] Token retrieval failed", error)
                _uiState.value = _uiState.value.copy(
                    isAuthorizing = false,
                    error = error.message ?: "No se pudo iniciar la autorización de Gmail."
                )
            }
        }
    }

    fun changeGmailAccount() {
        Log.d(TAG, "[CHANGE_ACCOUNT] Change account button clicked. Re-launching authorization consent...")
        if (_uiState.value.isAuthorizing || _uiState.value.isCreatingDraft) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAuthorizing = true, error = null, pendingAuthorization = null)
            try {
                gmailAccessToken = null
                when (val result = gmailDraftRepository.authorize()) {
                    is GmailAuthorizationResult.Authorized -> {
                        gmailAccessToken = result.accessToken
                        prefs.edit().putBoolean("gmail_connected", true).apply()
                        _uiState.value = _uiState.value.copy(
                            isAuthorizing = false,
                            gmailAuthorized = true,
                            notice = "Cuenta Google actualizada exitosamente."
                        )
                    }
                    is GmailAuthorizationResult.ConsentRequired -> {
                        _uiState.value = _uiState.value.copy(
                            isAuthorizing = false,
                            pendingAuthorization = result.pendingIntent
                        )
                    }
                }
            } catch (error: Exception) {
                Log.e(TAG, "[CHANGE_ACCOUNT] Failed to change account", error)
                _uiState.value = _uiState.value.copy(
                    isAuthorizing = false,
                    error = error.message ?: "No se pudo cambiar la cuenta de Google."
                )
            }
        }
    }

    fun completeGmailAuthorization(resultCode: Int, data: Intent?) {
        Log.d(TAG, "[AUTH] Activity result received")
        Log.d(TAG, "[AUTH] Result code: $resultCode (RESULT_OK=${Activity.RESULT_OK}, RESULT_CANCELED=${Activity.RESULT_CANCELED})")
        Log.d(TAG, "[AUTH] Intent is null: ${data == null}")
        Log.d(TAG, "[AUTH] Intent data present: ${data != null}")
        Log.d(TAG, "[AUTH] Intent extras keys: ${data?.extras?.keySet()?.joinToString() ?: "none"}")
        Log.d(TAG, "[AUTH] Authorization flow returned")

        val statusObj = data?.let { intent ->
            runCatching {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("status", com.google.android.gms.common.api.Status::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra("status") as? com.google.android.gms.common.api.Status
                }
            }.getOrNull()
        }

        if (statusObj != null) {
            Log.e(TAG, "[STATUS] Google Identity Status extra found: statusCode=${statusObj.statusCode}, statusMessage=${statusObj.statusMessage}")
        }

        if (resultCode == Activity.RESULT_CANCELED && data == null) {
            Log.w(TAG, "[AUTH] Authorization flow cancelled by user (data is null)")
            val diagReport = buildDiagnosticsReport("Conectar Gmail", "ACTIVITY_RESULT", "CANCELADO", resultCode, data, statusObj, null)
            _uiState.value = _uiState.value.copy(pendingAuthorization = null, error = "La autorización de Gmail se canceló.", diagnosticsLog = diagReport)
            return
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "[AUTH] Token retrieval started")
                val token = gmailDraftRepository.completeAuthorization(data)
                gmailAccessToken = token
                prefs.edit().putBoolean("gmail_connected", true).apply()
                Log.d(TAG, "[AUTH] Token retrieval successful")
                Log.d(TAG, "[AUTH] Token present: true")
                Log.d(TAG, "[STATE] Gmail authorization state before update: ${_uiState.value.gmailAuthorized}")
                _uiState.value = _uiState.value.copy(
                    pendingAuthorization = null,
                    gmailAuthorized = true,
                    error = null,
                    notice = "Cuenta Google autorizada. Creando borrador en Gmail…",
                    diagnosticsLog = null
                )
                Log.d(TAG, "[STATE] Gmail authorization state after update: true")
                Log.d(TAG, "[STATE] StateFlow updated")
                createDraft()
            } catch (error: Exception) {
                Log.e(TAG, "[AUTH] Token retrieval failed", error)
                val diagReport = buildDiagnosticsReport("Conectar Gmail", "TOKEN_RETRIEVAL", "ERROR", resultCode, data, statusObj, error)
                val userMsg = when {
                    error is com.google.android.gms.common.api.ApiException && error.statusCode == 10 ->
                        "Error de configuración Google OAuth (DEVELOPER_ERROR 10). Registra la huella SHA-1 de Debug en Google Cloud Console."
                    error is com.google.android.gms.common.api.ApiException && error.statusCode == 12500 ->
                        "Error de inicio de sesión Google (SIGN_IN_FAILED 12500). Añade la cuenta a Usuarios de prueba en Google Cloud Console."
                    else -> error.message ?: "No se pudo completar la autorización de Gmail."
                }
                _uiState.value = _uiState.value.copy(
                    pendingAuthorization = null,
                    gmailAuthorized = false,
                    error = userMsg,
                    diagnosticsLog = diagReport
                )
            }
        }
    }

    private fun buildDiagnosticsReport(
        operation: String,
        stageName: String,
        resultType: String,
        resultCode: Int?,
        data: Intent?,
        statusObj: com.google.android.gms.common.api.Status?,
        exception: Throwable?
    ): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        
        val apiEx = exception as? com.google.android.gms.common.api.ApiException
        val statusCode = statusObj?.statusCode ?: apiEx?.statusCode
        val statusMsg = statusObj?.statusMessage ?: apiEx?.message ?: exception?.message ?: "N/A"

        return buildString {
            append("--------------------------------------------------\n")
            append("WORKORA GMAIL DIAGNOSTICS\n")
            append("Fecha: $timestamp\n\n")
            append("Operación: $operation\n")
            append("Etapa: $stageName\n")
            append("Resultado: $resultType\n\n")
            append("Authorization iniciado: Sí\n")
            append("Consentimiento solicitado: Sí\n")
            append("PendingIntent recibido: ${uiState.value.pendingAuthorization != null || resultCode != null}\n")
            append("Activity result recibido: ${resultCode != null}\n")
            append("Result code: ${resultCode ?: "N/A"} (RESULT_OK=-1, RESULT_CANCELED=0)\n")
            append("Intent recibido: ${data != null}\n")
            append("Intent extras: ${data?.extras?.keySet()?.joinToString() ?: "ninguno"}\n\n")
            append("Google Identity Status:\n")
            append("Status Code: ${statusCode ?: "N/A"}\n")
            append("Status Message: $statusMsg\n\n")
            append("Token recibido: ${if (!gmailAccessToken.isNullOrBlank()) "Sí" else "No"}\n")
            append("Scope requerido: https://www.googleapis.com/auth/gmail.compose\n\n")
            append("Excepción: ${exception?.javaClass?.simpleName ?: "Ninguna"}\n")
            append("Detalle técnico: ${safeLog(exception?.message ?: statusMsg)}\n\n")
            append("Estado Gmail: ${if (_uiState.value.gmailAuthorized) "Conectado" else "Desconectado"}\n")
            append("--------------------------------------------------")
        }
    }

    private fun safeLog(value: String): String = value
        .replace(Regex("sk-[0-9A-Za-z_-]{20,}"), "[redacted]")
        .replace(Regex("gsk_[0-9A-Za-z_-]{20,}"), "[redacted]")
        .replace(Regex("AIza[0-9A-Za-z_-]{20,}"), "[redacted]")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[email]")
        .replace(Regex("(?<!\\d)(?:\\+?\\d[\\d ()-]{7,}\\d)(?!\\d)"), "[number]")
        .replace(Regex("[\\r\\n\\t]"), " ")
        .take(400)

    fun disconnectGmail() {
        viewModelScope.launch {
            val token = gmailAccessToken
            try {
                gmailDraftRepository.revoke(token)
                gmailAccessToken = null
                prefs.edit().putBoolean("gmail_connected", false).apply()
                _uiState.value = _uiState.value.copy(
                    gmailAuthorized = false,
                    error = null,
                    notice = "Se revocó el acceso de Workora a Gmail."
                )
            } catch (error: Exception) {
                prefs.edit().putBoolean("gmail_connected", false).apply()
                _uiState.value = _uiState.value.copy(
                    gmailAuthorized = false,
                    error = error.message ?: "No se pudo revocar el acceso a Gmail."
                )
            }
        }
    }

    fun createDraft() {
        Log.d(TAG, "[DRAFT] Create draft requested")
        val state = _uiState.value
        val assessment = state.finalAssessment ?: state.initialAssessment ?: return
        val cvText = evaluatedCvText ?: return
        val vacancyText = currentVacancyText ?: return
        if (assessment.score < DRAFT_THRESHOLD) {
            Log.w(TAG, "[DRAFT] Create draft skipped: assessment score ${assessment.score} < threshold $DRAFT_THRESHOLD")
            return
        }

        if (state.isCreatingDraft) {
            Log.w(TAG, "[DRAFT] Create draft skipped: already creating draft")
            return
        }
        val generation = requestGeneration

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingDraft = true, error = null, notice = null)
            try {
                var token = gmailAccessToken
                if (token.isNullOrBlank()) {
                    Log.d(TAG, "[DRAFT] token is null/blank, attempting silent authorize()")
                    when (val authResult = gmailDraftRepository.authorize()) {
                        is GmailAuthorizationResult.Authorized -> {
                            token = authResult.accessToken
                            gmailAccessToken = token
                            prefs.edit().putBoolean("gmail_connected", true).apply()
                            Log.d(TAG, "[DRAFT] Silent authorize succeeded in createDraft")
                            _uiState.value = _uiState.value.copy(gmailAuthorized = true)
                        }
                        is GmailAuthorizationResult.ConsentRequired -> {
                            Log.d(TAG, "[DRAFT] Silent authorize required consent, setting pendingAuthorization")
                            _uiState.value = _uiState.value.copy(
                                isCreatingDraft = false,
                                pendingAuthorization = authResult.pendingIntent
                            )
                            return@launch
                        }
                    }
                }

                Log.d(TAG, "[DRAFT] Gmail authorized: ${!token.isNullOrBlank()}")
                val key = analysisKey ?: return@launch

                if (state.draftId != null && savedStateHandle.get<String>(KEY_LAST_DRAFT_KEY) == key) {
                    Log.d(TAG, "[DRAFT] Draft already created for key $key (ID: ${state.draftId})")
                    _uiState.value = _uiState.value.copy(
                        isCreatingDraft = false,
                        notice = "El borrador ya fue creado en Gmail (ID: ${state.draftId}). Ábrelo en Gmail para revisarlo."
                    )
                    return@launch
                }

                val currentProfile = profileRepository.getProfile().first()
                val currentCv = currentProfile?.summary?.trim().orEmpty()
                if (currentProfile?.pdfFileName.isNullOrBlank() || sha256(currentCv) != evaluatedCvHash) {
                    Log.w(TAG, "[DRAFT] Create draft aborted: CV changed since evaluation")
                    _uiState.value = _uiState.value.copy(
                        isCreatingDraft = false,
                        error = "El CV cambió desde la evaluación. Repite el análisis antes de crear un borrador."
                    )
                    return@launch
                }

                Log.d(TAG, "[DRAFT] Generating application email content...")
                val email = compatibilityAnalyzer.generateApplicationEmail(cvText, vacancyText, assessment)
                if (generation != requestGeneration) {
                    Log.w(TAG, "[DRAFT] Create draft aborted: vacancy changed during generation")
                    _uiState.value = _uiState.value.copy(isCreatingDraft = false, error = "La vacante cambió; no se creó ningún borrador.")
                    return@launch
                }

                // Personal data is sourced only from the saved profile name and CV text. Never let AI invent it.
                val candidateName = currentProfile?.name?.trim()?.takeIf {
                    it.isNotBlank() && !it.equals("Usuario", ignoreCase = true)
                }
                val candidatePhone = Regex("(?<!\\d)(?:\\+?593|0)9\\d{8}(?!\\d)").find(cvText)?.value
                val candidateEmail = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").find(cvText)?.value

                Log.d(TAG, "[CV] Signature data validated: name=${!candidateName.isNullOrBlank()}, phone=${!candidatePhone.isNullOrBlank()}, email=${!candidateEmail.isNullOrBlank()}")

                val signatureBuilder = StringBuilder("\n\nSaludos cordiales,\n\n")
                listOf(candidateName, candidatePhone, candidateEmail)
                    .filterNot { it.isNullOrBlank() }
                    .forEach { signatureBuilder.append(it).append("\n") }

                // Remove any AI-generated signature/contact section. Do not replace fabricated data with guessed values.
                val cleanBody = email.body
                    .substringBefore("Saludos cordiales,")
                    .substringBefore("Atentamente,")
                    .substringBefore("Un saludo,")
                    .trim()
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()

                val finalBody = "$cleanBody\n\n${signatureBuilder.toString().trim()}".trim()

                val recipient = assessment.vacancy.contactEmail
                Log.d(TAG, "[DRAFT] Request sent to Gmail API")
                val draftId = gmailDraftRepository.createDraft(token, recipient, email.subject, finalBody)
                Log.d(TAG, "[DRAFT] Draft creation successful: draftId present=${draftId.isNotBlank()}")

                savedStateHandle[KEY_LAST_DRAFT_KEY] = key
                savedStateHandle[KEY_LAST_DRAFT_ID] = draftId

                _uiState.value = _uiState.value.copy(
                    isCreatingDraft = false,
                    draftId = draftId,
                    draftRecipientMissing = recipient.isNullOrBlank(),
                    notice = "Gmail confirmó la creación del borrador (ID: $draftId). Ábrelo en tu cuenta de Gmail para revisarlo y adjuntar tu CV."
                )
            } catch (error: Exception) {
                Log.e(TAG, "[DRAFT] Draft creation failed", error)
                if (generation != requestGeneration) return@launch
                val message = error.message ?: "No se pudo crear el borrador."
                val authorizationFailed = message.contains("autorización", ignoreCase = true) ||
                        message.contains("HTTP 401", ignoreCase = true) ||
                        message.contains("HTTP 403", ignoreCase = true)
                
                if (authorizationFailed) {
                    gmailAccessToken = null
                    prefs.edit().putBoolean("gmail_connected", false).apply()
                }

                _uiState.value = _uiState.value.copy(
                    isCreatingDraft = false,
                    gmailAuthorized = if (authorizationFailed) false else _uiState.value.gmailAuthorized,
                    error = message
                )
            }
        }
    }

    fun deleteVacancy(id: Int) {
        viewModelScope.launch { vacancyDao.deleteVacancy(id) }
    }

    private fun readVacancyImage(uriString: String): AiImagePayload {
        val uri = Uri.parse(uriString)
        val mimeType = context.contentResolver.getType(uri)?.lowercase()
            ?: throw IllegalArgumentException("No se pudo identificar el formato de la imagen.")
        require(mimeType in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")) {
            "El formato de imagen no es compatible con Gemini."
        }
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No se pudo abrir la imagen seleccionada.")
        val output = ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_GEMINI_IMAGE_BYTES) { "La imagen supera el límite de 8 MiB para el análisis Gemini." }
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        require(bytes.isNotEmpty()) { "La imagen seleccionada está vacía." }
        return AiImagePayload(mimeType, bytes)
    }

    private fun copyHistoryImage(uriString: String): String? {
        val sourceUri = Uri.parse(uriString)
        val folder = File(context.filesDir, "vacancy_history").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
        val mimeType = context.contentResolver.getType(sourceUri).orEmpty()
        val extension = when (mimeType.substringAfter('/', "").lowercase()) {
            "jpeg", "jpg" -> "jpg"
            "png" -> "png"
            "webp" -> "webp"
            else -> "img"
        }
        val temporary = File.createTempFile("vacancy-", ".tmp", folder)
        try {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return null
            DigestInputStream(input, digest).use { digestInput ->
                temporary.outputStream().use { output -> digestInput.copyTo(output) }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val saved = File(folder, "$hash.$extension")
            if (saved.exists()) temporary.delete() else if (!temporary.renameTo(saved)) {
                temporary.copyTo(saved, overwrite = true)
                temporary.delete()
            }
            return Uri.fromFile(saved).toString()
        } finally {
            temporary.delete()
        }
    }

    private fun VacancyAssessment.toHistoryEntity(imageUri: String?) = VacancyEntity(
        jobTitle = vacancy.role?.takeIf(String::isNotBlank) ?: "Vacante analizada",
        matchScore = score,
        pros = strengths.joinToString("\n") { it.statement },
        contras = gaps.joinToString("\n") { it.requirement },
        imageUri = imageUri,
        extractedEmail = vacancy.contactEmail,
        companyName = vacancy.company,
        analysisSummary = vacancy.summary ?: justification,
        analysisStatus = if (score < INITIAL_QUESTION_THRESHOLD) "Compatibilidad inicial" else "Pendiente de reevaluación"
    )

    suspend fun extractTextFromImage(context: Context, uri: Uri): String? {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image).await().text
        } catch (_: Exception) {
            null
        } finally {
            recognizer.close()
        }
    }

    private fun stageForInitial(assessment: VacancyAssessment): GmailAnalysisStage = when {
        assessment.score < INITIAL_QUESTION_THRESHOLD -> GmailAnalysisStage.INITIAL_LOW
        assessment.score >= DRAFT_THRESHOLD || assessment.questions.isEmpty() -> GmailAnalysisStage.FINAL_RESULT
        else -> GmailAnalysisStage.QUESTIONS
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun formatUserFriendlyError(error: Throwable): String {
        val msg = error.message.orEmpty()
        return when {
            msg.contains("503") || msg.contains("no disponible", ignoreCase = true) ->
                "El servicio de IA está ocupado o no disponible temporalmente (HTTP 503). Inténtalo de nuevo en unos instantes."
            msg.contains("429") || msg.contains("cuota", ignoreCase = true) ->
                "Se alcanzó el límite de solicitudes a la IA (HTTP 429). Espera unos segundos e inténtalo de nuevo."
            msg.contains("Timeout", ignoreCase = true) || msg.contains("tardó demasiado", ignoreCase = true) ->
                "El análisis tardó demasiado en responder (Timeout). Vuelve a intentarlo."
            msg.contains("conectar", ignoreCase = true) || msg.contains("red", ignoreCase = true) ->
                "No se pudo conectar con el servicio de IA. Verifica tu conexión a internet."
            msg.isNotBlank() -> msg
            else -> "No se pudo realizar el análisis. Inténtalo nuevamente."
        }
    }

    private companion object {
        const val TAG = "WORKORA_GMAIL_AUTH"
        const val MAX_GEMINI_IMAGE_BYTES = 8 * 1024 * 1024
        const val INITIAL_QUESTION_THRESHOLD = 60
        const val DRAFT_THRESHOLD = 75
        const val KEY_LAST_DRAFT_ID = "gmail_last_draft_id"
        const val KEY_LAST_DRAFT_KEY = "gmail_last_draft_key"
        const val KEY_CREATED_DRAFT_KEYS = "gmail_created_draft_keys"
        const val MAX_TRACKED_DRAFT_KEYS = 50
    }
}
