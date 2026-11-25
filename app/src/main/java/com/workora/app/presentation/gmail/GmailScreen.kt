package com.workora.app.presentation.gmail

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import com.workora.app.core.navigation.AppRoute
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.workora.app.core.theme.PrimaryBlue
import com.workora.app.data.local.entity.VacancyEntity
import com.workora.app.domain.model.gmail.AnalysisQuestion
import com.workora.app.domain.model.gmail.RequirementItem
import com.workora.app.domain.model.gmail.VacancyAssessment
import com.workora.app.presentation.gmail.viewmodel.GmailAnalysisStage
import com.workora.app.presentation.gmail.viewmodel.GmailViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GmailScreen(
    navController: NavController,
    gmailViewModel: GmailViewModel = hiltViewModel()
) {
    var showHelpDialog by remember { mutableStateOf(false) }
    var jobDescription by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var ocrError by remember { mutableStateOf<String?>(null) }
    var isExtracting by remember { mutableStateOf(false) }
    var showImageDialog by remember { mutableStateOf<String?>(null) }
    var selectedHistoryItem by remember { mutableStateOf<VacancyEntity?>(null) }
    var imageScale by remember(showImageDialog) { mutableStateOf(1f) }
    var imageOffset by remember(showImageDialog) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }

    val history by gmailViewModel.history.collectAsStateWithLifecycle()
    val uiState by gmailViewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedImageUri = it
            ocrError = null
            gmailViewModel.invalidateAnalysis()
            scope.launch {
                isExtracting = true
                val extractedText = gmailViewModel.extractTextFromImage(context, it)
                if (extractedText.isNullOrBlank()) {
                    jobDescription = ""
                    ocrError = "No se pudo extraer texto suficiente. Puedes ingresar o corregir el texto manualmente."
                } else {
                    jobDescription = extractedText
                }
                isExtracting = false
            }
        }
    }

    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        gmailViewModel.completeGmailAuthorization(result.resultCode, result.data)
    }

    LaunchedEffect(uiState.pendingAuthorization) {
        uiState.pendingAuthorization?.let { pendingIntent ->
            runCatching {
                authorizationLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
            }.onFailure {
                gmailViewModel.completeGmailAuthorization(Activity.RESULT_CANCELED, null)
            }
        }
    }

    LaunchedEffect(uiState.draftId) {
        uiState.draftId?.let { _ ->
            runCatching {
                val launchIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                } else {
                    val emailIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_APP_EMAIL)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(emailIntent)
                }
            }
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("¿Cómo funciona?", color = PrimaryBlue) },
            text = {
                Text(
                    "Ingresa el texto de una vacante o extráelo de una imagen con OCR. " +
                        "Al pulsar Analizar compatibilidad, el CV y la vacante pueden procesarse mediante Gemini. " +
                        "La evaluación y las preguntas se generan con IA; las respuestas se tratan como declaraciones del usuario, no como experiencia documentada. " +
                        "Un borrador solo se crea si la evaluación final alcanza el 75% y tú presionas el botón. Workora nunca envía el correo ni adjunta el CV."
                )
            },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) { Text("Entendido", color = PrimaryBlue) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Asistente de Postulación", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate(AppRoute.GmailSettings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración de Gmail", tint = Color.White)
                    }
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Ayuda", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { innerPadding ->
        Column(
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Vacante", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            
            if (selectedImageUri != null) {
                // MODO IMAGEN EXCLUSIVO
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Captura de la vacante",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp)
                                .background(Color(0xFFE9EDF2))
                                .clickable { showImageDialog = selectedImageUri.toString() }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { imagePickerLauncher.launch("image/*") },
                                enabled = !isExtracting && !uiState.isCreatingDraft,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xFFE8F0FE),
                                    contentColor = PrimaryBlue
                                ),
                                border = BorderStroke(1.dp, PrimaryBlue)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Cambiar imagen", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                            }
                            TextButton(
                                onClick = {
                                    selectedImageUri = null
                                    jobDescription = ""
                                    ocrError = null
                                    gmailViewModel.invalidateAnalysis()
                                },
                                enabled = !isExtracting && !uiState.isCreatingDraft
                            ) {
                                Text("Usar texto manual", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (isExtracting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Extrayendo texto de la imagen con OCR…", color = Color.Gray, fontSize = 12.sp)
                }
                if (ocrError != null) InlineMessage(ocrError.orEmpty(), isError = true)
            } else {
                // MODO TEXTO MANUAL EXCLUSIVO
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = !isExtracting && !uiState.isCreatingDraft,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFE8F0FE),
                        contentColor = PrimaryBlue
                    ),
                    border = BorderStroke(1.5.dp, PrimaryBlue)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Cargar imagen de la vacante (OCR)", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                OutlinedTextField(
                    value = jobDescription,
                    onValueChange = { newText ->
                        if (newText != jobDescription) gmailViewModel.invalidateAnalysis()
                        jobDescription = newText
                        ocrError = null
                    },
                    label = { Text("Texto de la vacante") },
                    placeholder = { Text("Pega o escribe aquí la descripción de la vacante...") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    enabled = !isExtracting && !uiState.isCreatingDraft,
                    minLines = 4,
                    maxLines = 4
                )
            }

            Text(
                "Aviso: el CV y el texto de la vacante pueden procesarse mediante Gemini.",
                color = Color.Gray,
                fontSize = 11.sp,
                lineHeight = 14.sp
            )
            Button(
                onClick = { gmailViewModel.analyzeInitial(jobDescription, selectedImageUri?.toString()) },
                enabled = (jobDescription.isNotBlank() || selectedImageUri != null) && !isExtracting &&
                    uiState.stage != GmailAnalysisStage.ANALYZING_INITIAL && uiState.stage != GmailAnalysisStage.REEVALUATING,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.stage == GmailAnalysisStage.ANALYZING_INITIAL) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Analizando vacante y CV…")
                } else Text("Analizar compatibilidad")
            }

            if (uiState.error != null) InlineMessage(uiState.error.orEmpty(), isError = true)
            if (uiState.notice != null) InlineMessage(uiState.notice.orEmpty(), isError = false)
            if (uiState.gmailAuthorized) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "✓ Gmail conectado" + if (!uiState.connectedAccountEmail.isNullOrBlank()) " · ${uiState.connectedAccountEmail}" else "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            // Display results based on activeAssessment (finalAssessment or initialAssessment)
            val activeAssessment = uiState.finalAssessment ?: uiState.initialAssessment
            if (activeAssessment != null) {
                val isFinal = uiState.finalAssessment != null || activeAssessment.score >= 75
                Text(
                    if (isFinal) "Resultado de compatibilidad" else "Compatibilidad inicial",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                AssessmentCard(activeAssessment, finalResult = isFinal)

                if (activeAssessment.score >= 75) {
                    if (!activeAssessment.cvSufficient || !activeAssessment.vacancySufficient || activeAssessment.vacancy.role.isNullOrBlank()) {
                        InlineMessage("Falta información confiable para redactar una postulación. Completa el CV o la vacante y vuelve a analizar.", isError = true)
                    } else {
                        Text("El 75% es el umbral requerido para habilitar el borrador de correo.", color = Color.Gray, fontSize = 12.sp)
                        if (!uiState.gmailAuthorized) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Gmail no conectado",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "Conecta tu cuenta Google para poder generar borradores de postulación directamente en Gmail.",
                                        fontSize = 12.sp,
                                        color = Color.DarkGray
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Button(
                                        onClick = { navController.navigate(AppRoute.GmailSettings) },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = Color.White),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Configurar Gmail", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Text("Cuenta Google autorizada para esta sesión.", color = Color(0xFF287A45), fontSize = 12.sp)
                            Button(
                                onClick = gmailViewModel::createDraft,
                                enabled = !uiState.isCreatingDraft && uiState.draftId == null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (uiState.isCreatingDraft) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Creando borrador…")
                                } else Text("Crear borrador en Gmail")
                            }
                            if (activeAssessment.vacancy.contactEmail.isNullOrBlank()) {
                                Text("No se detectó correo de contacto; puedes agregar el destinatario manualmente en Gmail.", color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                        if (uiState.draftId != null) {
                            InlineMessage("Borrador confirmado por Gmail. ID: ${uiState.draftId}. Ábrelo, revísalo y adjunta tu CV manualmente.", isError = false)
                            if (uiState.draftRecipientMissing) Text("No se detectó destinatario; agrégalo manualmente en Gmail.", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                } else if (activeAssessment.score in 60..74) {
                    if (uiState.finalAssessment != null) {
                        InlineMessage("El resultado final es del ${activeAssessment.score}%, menor al 75% mínimo requerido para crear un borrador de correo.", isError = false)
                    } else if (activeAssessment.questions.isNotEmpty()) {
                        QuestionForm(
                            questions = activeAssessment.questions,
                            answers = uiState.answers,
                            onAnswer = gmailViewModel::setAnswer
                        )
                        val answered = activeAssessment.questions.count { !uiState.answers[it.id]?.selectedOption.isNullOrBlank() }
                        Text("Progreso: $answered de ${activeAssessment.questions.size} preguntas", fontSize = 12.sp)
                        LinearProgressIndicator(
                            progress = answered.toFloat() / activeAssessment.questions.size,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = gmailViewModel::reevaluate,
                            enabled = answered == activeAssessment.questions.size && uiState.stage != GmailAnalysisStage.REEVALUATING,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (uiState.stage == GmailAnalysisStage.REEVALUATING) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Reevaluando…")
                            } else Text("Reevaluar con mis respuestas")
                        }
                    } else {
                        InlineMessage("Compatibilidad del ${activeAssessment.score}%. Se recomienda revisar las brechas antes de postular.", isError = false)
                    }
                } else {
                    InlineMessage("La compatibilidad inicial es del ${activeAssessment.score}%, menor al 60%. No se habilitará la reevaluación ni la creación de un borrador.", isError = false)
                }
            }

            HorizontalDivider()
            Text("Historial anterior", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
            if (history.isEmpty()) {
                Text("Todavía no hay registros anteriores.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(history, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedHistoryItem = item },
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (item.imageUri != null) {
                                    AsyncImage(
                                        model = Uri.parse(item.imageUri),
                                        contentDescription = "Ampliar imagen de ${item.jobTitle}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(width = 76.dp, height = 68.dp).background(Color(0xFFE9EDF2))
                                            .clickable { showImageDialog = item.imageUri }
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(item.jobTitle, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 2)
                                    item.companyName?.let { Text(it, color = Color.Gray, fontSize = 11.sp, maxLines = 1) }
                                    Text(formatHistoryDate(item.timestamp), color = Color.Gray, fontSize = 10.sp)
                                    Text(item.analysisStatus, color = Color.Gray, fontSize = 10.sp, maxLines = 1)
                                }
                                Text(
                                    "${item.matchScore}%",
                                    color = if (item.matchScore >= 75) Color(0xFF287A45) else if (item.matchScore >= 60) Color(0xFF9A6700) else Color(0xFFB3261E),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                IconButton(onClick = { gmailViewModel.deleteVacancy(item.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar registro", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (showImageDialog != null) {
            Dialog(
                onDismissRequest = { showImageDialog = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = Uri.parse(showImageDialog),
                        contentDescription = "Imagen completa de la vacante; pellizca para ampliar",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.9f)
                            .pointerInput(showImageDialog) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    imageScale = (imageScale * zoom).coerceIn(1f, 5f)
                                    imageOffset = if (imageScale == 1f) androidx.compose.ui.geometry.Offset.Zero else imageOffset + pan
                                }
                            }
                            .graphicsLayer {
                                scaleX = imageScale
                                scaleY = imageScale
                                translationX = imageOffset.x
                                translationY = imageOffset.y
                            }
                    )
                    IconButton(onClick = { showImageDialog = null }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar imagen", tint = Color.White)
                    }
                }
            }
        }
        selectedHistoryItem?.let { item ->
            AlertDialog(
                onDismissRequest = { selectedHistoryItem = null },
                title = { Text(item.jobTitle, color = PrimaryBlue) },
                text = {
                    Column(
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item.companyName?.let { Text(it, fontWeight = FontWeight.Medium) }
                        Text("${item.matchScore}% de compatibilidad · ${item.analysisStatus}", fontWeight = FontWeight.Bold)
                        Text(formatHistoryDate(item.timestamp), color = Color.Gray, fontSize = 12.sp)
                        Text(item.analysisSummary)
                        if (item.pros.isNotBlank()) Text("Fortalezas: ${item.pros}", fontSize = 12.sp)
                        if (item.contras.isNotBlank()) Text("Brechas: ${item.contras}", fontSize = 12.sp)
                        item.imageUri?.let { image ->
                            TextButton(onClick = { showImageDialog = image; selectedHistoryItem = null }) {
                                Text("Ampliar imagen de la vacante")
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { selectedHistoryItem = null }) { Text("Cerrar") } }
            )
        }
    }
}

private fun formatHistoryDate(timestamp: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))

@Composable
private fun QuestionForm(
    questions: List<AnalysisQuestion>,
    answers: Map<String, com.workora.app.domain.model.gmail.QuestionAnswer>,
    onAnswer: (String, com.workora.app.domain.model.gmail.QuestionAnswer) -> Unit
) {
    Text("Preguntas para aclarar el perfil", fontSize = 15.sp, fontWeight = FontWeight.Bold)
    questions.forEach { question ->
        val answer = answers[question.id]
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(question.question, fontWeight = FontWeight.SemiBold)
                Text(question.reason, color = Color.Gray, fontSize = 11.sp)
                question.options.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                        onAnswer(question.id, (answer ?: com.workora.app.domain.model.gmail.QuestionAnswer(null, "")).copy(selectedOption = option))
                    }) {
                        RadioButton(
                            selected = answer?.selectedOption == option,
                            onClick = {
                                onAnswer(question.id, (answer ?: com.workora.app.domain.model.gmail.QuestionAnswer(null, "")).copy(selectedOption = option))
                            }
                        )
                        Text(option, fontSize = 12.sp)
                    }
                }
                if (question.allowsFreeText) {
                    OutlinedTextField(
                        value = answer?.additionalDetails.orEmpty(),
                        onValueChange = { text ->
                            onAnswer(question.id, (answer ?: com.workora.app.domain.model.gmail.QuestionAnswer(null, "")).copy(additionalDetails = text))
                        },
                        label = { Text("Detalle opcional") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            }
        }
    }
}

@Composable
private fun AssessmentCard(assessment: VacancyAssessment, finalResult: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(assessment.vacancy.role ?: "Cargo no identificado", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            assessment.vacancy.company?.let { Text(it, color = Color.Gray, fontSize = 12.sp) }
            Text(
                "${if (finalResult) "Compatibilidad final" else "Compatibilidad inicial"}: ${assessment.score}% · ${assessment.level}",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (assessment.score >= 75) Color(0xFF287A45) else if (assessment.score >= 60) Color(0xFF9A6700) else Color(0xFFB3261E)
            )
            Text("Estimación según la evidencia disponible · confianza ${assessment.confidence}%", color = Color.Gray, fontSize = 11.sp)
            Text(
                when {
                    !finalResult && assessment.score < 60 -> "Regla del flujo: no se generan preguntas ni se habilita un borrador."
                    !finalResult -> "Regla del flujo: responde las preguntas para continuar; aún no se habilita un borrador."
                    assessment.score < 75 -> "Regla del flujo: el resultado final no alcanza el 75%; no se habilita un borrador."
                    else -> "Regla del flujo: supera el umbral interno; el borrador solo se crea si lo solicitas."
                },
                fontSize = 13.sp,
                color = Color.Gray
            )
            Text(assessment.justification, fontSize = 12.sp)
            Text("Recomendación: ${assessment.recommendation}", fontWeight = FontWeight.Medium, fontSize = 12.sp)
            assessment.vacancy.summary?.let { Text(it, fontSize = 12.sp) }
            EvidenceSection("Responsabilidades identificadas", assessment.vacancy.responsibilities)
            EvidenceSection("Tecnologías identificadas", assessment.vacancy.technologies)
            EvidenceSection("Requisitos obligatorios", assessment.vacancy.mandatoryRequirements)
            EvidenceSection("Requisitos deseables", assessment.vacancy.desirableRequirements)
            EvidenceSection("Fortalezas", assessment.strengths.map { "${it.statement} — CV: ${it.cvEvidence} (${it.evidenceType})" })
            EvidenceSection("Requisitos cumplidos", assessment.requirementsMet.map(::requirementLabel))
            EvidenceSection("Cumplimiento parcial", assessment.requirementsPartial.map(::requirementLabel))
            EvidenceSection("Brechas", assessment.gaps.map(::requirementLabel))
            EvidenceSection("No comprobado", assessment.unverified.map(::requirementLabel))
            if (assessment.missingInformation.isNotEmpty()) {
                EvidenceSection("Información por completar", assessment.missingInformation)
            }
            assessment.vacancy.experience?.let { Text("Experiencia solicitada: $it", fontSize = 12.sp) }
            assessment.vacancy.seniority?.let { Text("Nivel: $it", fontSize = 12.sp) }
            assessment.vacancy.education?.let { Text("Formación: $it", fontSize = 12.sp) }
            assessment.vacancy.location?.let { Text("Ubicación: $it", fontSize = 12.sp) }
            assessment.vacancy.modality?.let { Text("Modalidad: $it", fontSize = 12.sp) }
            assessment.vacancy.salary?.let { Text("Salario indicado: $it", fontSize = 12.sp) }
            if (assessment.vacancy.contactEmail.isNullOrBlank()) Text("Correo de contacto: no encontrado.", color = Color.Gray, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EvidenceSection(title: String, items: List<String>) {
    if (items.isNotEmpty()) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        items.forEach { Text("• $it", fontSize = 11.sp) }
    }
}

private fun requirementLabel(item: RequirementItem): String = buildString {
    append(item.requirement)
    if (item.critical) append(" (crítico)")
    append(" — ${item.status}")
    if (item.cvEvidence.isNotBlank()) append("; CV: ${item.cvEvidence}")
}

@Composable
private fun InlineMessage(message: String, isError: Boolean) {
    val color = if (isError) MaterialTheme.colorScheme.error else Color(0xFF315B40)
    Text(message, color = color, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
}
