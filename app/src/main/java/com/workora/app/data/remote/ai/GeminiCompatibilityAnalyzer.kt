package com.workora.app.data.remote.ai

import android.util.Patterns
import com.workora.app.domain.model.gmail.AnalysisQuestion
import com.workora.app.domain.model.gmail.EvidenceItem
import com.workora.app.domain.model.gmail.GeneratedApplicationEmail
import com.workora.app.domain.model.gmail.QuestionAnswer
import com.workora.app.domain.model.gmail.RequirementItem
import com.workora.app.domain.model.gmail.ScoringCriterion
import com.workora.app.domain.model.gmail.VacancyAssessment
import com.workora.app.domain.model.gmail.VacancyDetails
import com.workora.app.domain.repository.AiProvider
import com.workora.app.domain.repository.AiImagePayload
import com.workora.app.domain.repository.CompatibilityAnalyzer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class GeminiCompatibilityAnalyzer @Inject constructor(
    private val aiProvider: AiProvider,
    private val geminiProvider: GeminiApiKeyProvider
) : CompatibilityAnalyzer {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyzeInitial(cvText: String, vacancyText: String, vacancyImage: AiImagePayload?): VacancyAssessment {
        validateInputs(cvText, vacancyText, vacancyImage)
        val rawJson = aiProvider.generateJson(assessmentPrompt(cvText, vacancyText, null), vacancyImage)
        val result = decodeAssessment(rawJson, vacancyText)
        return sanitizeAssessment(result)
    }

    override suspend fun reevaluate(
        cvText: String,
        vacancyText: String,
        initial: VacancyAssessment,
        answers: Map<String, QuestionAnswer>,
        vacancyImage: AiImagePayload?
    ): VacancyAssessment {
        validateInputs(cvText, vacancyText, vacancyImage)
        val answerText = initial.questions.joinToString("\n") { question ->
            val answer = answers[question.id]
            "Pregunta: ${question.question}\nRespuesta declarada por el usuario: ${answer?.selectedOption ?: "Sin selección"}\nDetalle adicional: ${answer?.additionalDetails?.ifBlank { "No proporcionado" } ?: "No proporcionado"}"
        }
        val rawJson = aiProvider.generateJson(assessmentPrompt(cvText, vacancyText, answerText), vacancyImage)
        val result = decodeAssessment(rawJson, vacancyText)
        return sanitizeAssessment(result).copy(questions = emptyList())
    }

    override suspend fun generateApplicationEmail(
        cvText: String,
        vacancyText: String,
        assessment: VacancyAssessment
    ): GeneratedApplicationEmail {
        require(assessment.score >= 75) {
            "El puntaje de compatibilidad no alcanza el umbral del 75% requerido para redactar el correo."
        }
        val companyName = assessment.vacancy.company?.takeIf { it.isNotBlank() && !it.contains("desconocid", ignoreCase = true) }
        val roleName = assessment.vacancy.role?.takeIf { it.isNotBlank() } ?: "Vacante"

        val prompt = """
            Redacta un correo profesional de postulación para una vacante laboral.
            Devuelve ÚNICAMENTE un objeto JSON válido con las claves "subject" y "body", sin bloques Markdown.

            REGLAS STRICTAS DEL ASUNTO ("subject"):
            - El asunto debe usar exactamente el formato: "Postulación – $roleName"
            - Ejemplo de subject: "Postulación – $roleName"
            - PROHIBIDO dejar el asunto vacío o usar un asunto genérico.

            REGLAS STRICTAS DEL CUERPO ("body"):
            1. Saludo personalizado:
               - Si la empresa es conocida ("${companyName ?: "Desconocida"}"), usa exactamente: "Buenas tardes, equipo de ${companyName}:"
               - Si la empresa no se conoce con certeza, usa: "Buenas tardes, equipo de selección:"
            2. Introducción:
               - Expresa brevemente interés en participar en el proceso de selección para el cargo de $roleName.
            3. Perfil profesional:
               - Presenta al candidato como Ingeniero en Sistemas con experiencia en desarrollo de software, mencionando su trayectoria laboral en Produbanco y Banco Pichincha.
            4. Personalización tecnológica:
               - Conecta las tecnologías más relevantes solicitadas en la vacante con la experiencia real del candidato.
            5. Cierre profesional:
               - Finaliza con agradecimiento y consideración.
               - Firma con los marcadores:
                 Saludos cordiales,
                 [Nombre del candidato]
                 [Teléfono del candidato]
                 [Correo del candidato]

            VACANTE FUENTE:
            $vacancyText

            CV FUENTE:
            $cvText

            EVALUACIÓN DE COMPATIBILIDAD:
            ${assessment.justification}
        """.trimIndent()

        val jsonResult = runCatching {
            geminiProvider.generateJson(prompt)
        }.getOrElse {
            // Fallback to Groq if Gemini is temporarily unavailable
            aiProvider.generateJson(prompt)
        }

        val parsed = parseJsonObject(jsonResult)
        val rawSubject = parsed.string("subject").trim().replace(Regex("[\\r\\n]+"), " ")
        val subject = rawSubject.removePrefix("Postulación al cargo de ")
            .removePrefix("Postulación a ")
            .removePrefix("Solicitud de empleo: ")
            .removePrefix("Solicitud para ")
            .trim()
            .ifBlank { roleName }

        val body = parsed.string("body").trim()
        require(subject.isNotBlank() && body.isNotBlank()) {
            "La IA no generó un correo válido. No se creó ningún borrador."
        }
        return GeneratedApplicationEmail(subject, body)
    }

    private fun assessmentPrompt(cvText: String, vacancyText: String, answers: String?): String = """
        Actúa como un Asesor Profesional de Empleo y Especialista en Selección de Talento Humano.
        Tu objetivo es evaluar de forma holística, inteligente, contextual y realista la compatibilidad del candidato para desempeñar el puesto.

        VALIDACIÓN PREVIA OBLIGATORIA DE CONTENIDO LABORAL:
        Antes de calcular cualquier compatibilidad, determina estrictamente si la FUENTE corresponde a una OFERTA/VACANTE LABORAL REAL.
        - Si el texto o imagen es una publicidad general, documento personal, foto casual, meme, conversación o texto sin relación directa con la contratación para un puesto de trabajo:
          Asigna "isJobPosting": false y en "rejectionReason" explica amigablemente por qué no es una oferta de empleo válida.
        - Si es una oferta de empleo real:
          Asigna "isJobPosting": true y "rejectionReason": null.

        EVIDENCIAS Y CONTEXTO REAL DEL CV DEL CANDIDATO:
        - Título Universitario: Ingeniería en Sistemas (Universidad Técnica de Cotopaxi).
        - Experiencia en Sector Financiero / Bancario: Trayectoria comprobada en Produbanco y Banco Pichincha.
        - Desarrollo de Software Backend / Fullstack: C#, .NET, APIs REST, microservicios, aplicaciones empresariales.
        - Datos & BI: SQL Server, Procedimientos Almacenados (Stored Procedures), integración de datos ETL / DTSX, Power BI, Reporting Services, procesamiento de datos.

        REGLAS STRICTAS DE CLASIFICACIÓN DE EVIDENCIAS:
        1. NO MARQUES COMO "NO COMPROBADO" LO QUE CONSTA EN EL CV:
           - Si la vacante requiere Título Universitario o Ingeniería en Sistemas, reconócelo como CUMPLIDO (el CV respalda UTC).
           - Si la vacante requiere experiencia en el Sector Financiero o Bancario, reconócelo como CUMPLIDO (el CV respalda Produbanco y Banco Pichincha).
           - Si la vacante requiere SQL o bases de datos, reconócelo como CUMPLIDO (el CV respalda SQL Server y Stored Procedures).
           - Si la vacante requiere ETL, BI o Reporting, reconócelo como CUMPLIDO o PARCIALMENTE CUMPLIDO (el CV respalda ETL/DTSX, Power BI y Reporting Services).

        2. CLASIFICACIÓN DE ESTADOS (status):
           - 'requirementsMet': Requisitos cumplidos con evidencia directa del CV.
           - 'requirementsPartial': Requisitos cumplidos parcialmente o mediante competencias transferibles/relacionadas.
           - 'unverified': Mención 'No comprobado en el CV' ÚNICAMENTE para requisitos secundarios que NO constan expuestos en el CV. No penalices drásticamente la puntuación por estos puntos.
           - 'gaps': Brechas reales únicamente para requisitos indispensables donde exista evidencia clara de incompatibilidad.

        3. EVALUACIÓN DE VACANTES DE DATA Y BI:
           - SQL Server + Stored Procedures -> Evidencia sólida de bases de datos relacionales y lenguaje SQL.
           - ETL/DTSX -> Evidencia válida de integración y transformación de datos.
           - Power BI + Reporting Services -> Evidencia válida de analítica y reporting.
           - NO inventes especializaciones en R, estadística avanzada o Gobierno de Datos si no están en el CV; clasifícalas como parciales o 'unverified' y genera una pregunta de aclaración si puede cambiar el resultado.

        4. RECOMENDACIÓN REALISTA DE CARRERA:
           - En 'recommendation', brinda una sugerencia directa como asesor profesional. Usa frases como: "Excelente oportunidad - Yo sí postularía.", "Muy buena oportunidad - Vale la pena postular.", "Postulación viable - Destaca X en la entrevista.", "Postulación poco recomendable por brechas en requisitos críticos."

        5. ABSOLUTA COHERENCIA:
           - El puntaje de 'criteria' debe reflejar la evaluación cualitativa.
           - 'confidence' es un número entero de 0 a 100.

        Devuelve SOLO un objeto JSON válido que siga exactamente este esquema de llaves y tipos:
        {
          "isJobPosting": true,
          "rejectionReason": null,
          "cvSufficient": true,
          "vacancySufficient": true,
          "missingInformation": ["cadena"],
          "vacancy": {
            "role": "cargo",
            "company": "empresa",
            "summary": "resumen",
            "responsibilities": ["función"],
            "mandatoryRequirements": ["requisito"],
            "desirableRequirements": ["deseable"],
            "technologies": ["tecnología"],
            "experience": "experiencia",
            "seniority": "nivel",
            "education": "formación",
            "location": "ubicación",
            "modality": "modalidad",
            "salary": null,
            "contactEmail": "correo_detectado_o_null",
            "applicationUrl": null
          },
          "criteria": [
            {"dimension": "mandatory", "score": 90, "applicable": true, "evidence": "justificación"},
            {"dimension": "experience", "score": 80, "applicable": true, "evidence": "justificación"},
            {"dimension": "technical", "score": 85, "applicable": true, "evidence": "justificación"},
            {"dimension": "responsibilities", "score": 85, "applicable": true, "evidence": "justificación"},
            {"dimension": "seniority", "score": 85, "applicable": true, "evidence": "justificación"},
            {"dimension": "transferability", "score": 90, "applicable": true, "evidence": "justificación"},
            {"dimension": "education", "score": 90, "applicable": true, "evidence": "justificación"},
            {"dimension": "desirable", "score": 80, "applicable": true, "evidence": "justificación"}
          ],
          "strengths": [
            {"statement": "fortaleza", "cvEvidence": "evidencia del CV", "evidenceType": "professional_experience"}
          ],
          "requirementsMet": [
            {"requirement": "requisito", "cvEvidence": "evidencia", "status": "Cumplido", "critical": true}
          ],
          "requirementsPartial": [],
          "gaps": [],
          "unverified": [],
          "justification": "justificación clara y detallada de la puntuación",
          "recommendation": "consejo directo del asesor",
          "confidence": 85,
          "questions": []
        }

        CV FUENTE:
        $cvText

        VACANTE FUENTE:
        $vacancyText

        RESPUESTAS (solo en reevaluación):
        ${answers ?: "Ninguna. Evaluación inicial."}
    """.trimIndent()

    private fun decodeAssessment(response: String, vacancySource: String): VacancyAssessment {
        val root = parseJsonObject(response)
        
        val isJobPosting = root.booleanOrDefault("isJobPosting", true)
        val rejectionReason = root.stringOrNull("rejectionReason")
        
        if (!isJobPosting) {
            val msg = rejectionReason?.takeIf { it.isNotBlank() }
                ?: "No se detectó una oferta laboral válida. Sube una imagen o texto que contenga una vacante para poder analizar tu compatibilidad."
            throw IllegalArgumentException(msg)
        }

        val vacancy = root.obj("vacancy")
        
        val criteriaList = root.array("criteria").map { element ->
            val criterion = element.jsonObject
            ScoringCriterion(
                dimension = criterion.string("dimension"),
                score = criterion.int("score").coerceIn(0, 100),
                applicable = criterion.booleanOrDefault("applicable", true),
                evidence = criterion.stringOrNull("evidence") ?: "Evaluación realizada por Asesor de Empleo IA"
            )
        }

        val existingDimensions = criteriaList.map { it.dimension }.toSet()
        val allRequiredDimensions = listOf("mandatory", "experience", "technical", "responsibilities", "seniority", "transferability", "education", "desirable")
        val completeCriteria = criteriaList + allRequiredDimensions.filterNot { it in existingDimensions }.map { dim ->
            ScoringCriterion(dimension = dim, score = 70, applicable = true, evidence = "Evaluado en base al perfil del candidato")
        }

        val aiEmail = vacancy.stringOrNull("contactEmail")?.takeIf { candidate ->
            Patterns.EMAIL_ADDRESS.matcher(candidate).matches()
        }
        val ocrEmail = extractEmailFromText(vacancySource)
        val detectedEmail = aiEmail ?: ocrEmail

        return VacancyAssessment(
            vacancy = VacancyDetails(
                role = vacancy.stringOrNull("role")?.takeIf { it.isNotBlank() && !it.equals("Cargo no especificado", ignoreCase = true) } ?: "Cargo en la vacante",
                company = vacancy.stringOrNull("company"),
                summary = vacancy.stringOrNull("summary"),
                responsibilities = vacancy.strings("responsibilities"),
                mandatoryRequirements = vacancy.strings("mandatoryRequirements"),
                desirableRequirements = vacancy.strings("desirableRequirements"),
                technologies = vacancy.strings("technologies"),
                experience = vacancy.stringOrNull("experience"),
                seniority = vacancy.stringOrNull("seniority"),
                education = vacancy.stringOrNull("education"),
                location = vacancy.stringOrNull("location"),
                modality = vacancy.stringOrNull("modality"),
                salary = vacancy.stringOrNull("salary"),
                contactEmail = detectedEmail,
                applicationUrl = vacancy.stringOrNull("applicationUrl")?.takeIf { url ->
                    url.startsWith("https://") || url.startsWith("http://")
                }
            ),
            cvSufficient = root.booleanOrDefault("cvSufficient", true),
            vacancySufficient = root.booleanOrDefault("vacancySufficient", true),
            missingInformation = root.stringsOrEmpty("missingInformation"),
            criteria = completeCriteria,
            strengths = root.arrayOrEmpty("strengths").map { element ->
                val item = element.jsonObject
                EvidenceItem(
                    statement = item.string("statement").ifBlank { "Competencia demostrada en el perfil" },
                    cvEvidence = item.string("cvEvidence").ifBlank { "Evidencia o tecnología equivalente respaldada" },
                    evidenceType = item.string("evidenceType").ifBlank { "practical_knowledge" }
                )
            },
            requirementsMet = root.requirements("requirementsMet"),
            requirementsPartial = root.requirements("requirementsPartial"),
            gaps = root.requirements("gaps"),
            unverified = root.requirements("unverified"),
            justification = root.string("justification").ifBlank { "Análisis contextual en base a tus competencias profesionales y los requisitos del cargo." },
            recommendation = root.string("recommendation").ifBlank { "Sugerimos revisar las fortalezas destacadas para preparar tu postulación." },
            confidence = root.intOrDefault("confidence", 85).coerceIn(0, 100),
            questions = root.arrayOrEmpty("questions").mapNotNull { element ->
                runCatching {
                    val question = element.jsonObject
                    val opts = question.strings("options")
                    if (opts.size >= 2) {
                        AnalysisQuestion(
                            id = question.string("id").ifBlank { "q_${System.currentTimeMillis()}" },
                            question = question.string("question"),
                            reason = question.string("reason").ifBlank { "Pregunta para verificar competencias relacionadas" },
                            options = opts,
                            allowsFreeText = question.booleanOrDefault("allowsFreeText", true)
                        )
                    } else null
                }.getOrNull()
            }
        )
    }

    private fun extractEmailFromText(sourceText: String): String? {
        if (sourceText.isBlank()) return null
        val matcher = Patterns.EMAIL_ADDRESS.matcher(sourceText)
        val emails = mutableListOf<String>()
        while (matcher.find()) {
            val found = matcher.group().trim().lowercase()
            if (found.contains("@") && !found.endsWith(".png") && !found.endsWith(".jpg") && !found.endsWith(".jpeg") && !found.endsWith(".webp")) {
                emails.add(found)
            }
        }
        if (emails.isEmpty()) return null

        val priorityEmail = emails.firstOrNull { email ->
            email.contains("seleccion") || email.contains("talento") || email.contains("reclutamiento") ||
            email.contains("farmaenlace") || email.contains("empleo") || email.contains("cv") || email.contains("rh") ||
            email.contains("postula") || email.contains("rrhh")
        }
        return priorityEmail ?: emails.first()
    }

    private fun sanitizeAssessment(assessment: VacancyAssessment): VacancyAssessment {
        val sanitizedStrengths = assessment.strengths.map { item ->
            val validTypes = setOf(
                "professional_experience", "personal_project", "practical_knowledge", "theoretical_knowledge",
                "transferable", "inferred", "user_self_report"
            )
            val type = if (item.evidenceType in validTypes) item.evidenceType else "practical_knowledge"
            item.copy(evidenceType = type)
        }
        return assessment.copy(
            cvSufficient = true,
            vacancySufficient = true,
            strengths = sanitizedStrengths
        )
    }

    private fun validateInputs(cvText: String, vacancyText: String, vacancyImage: AiImagePayload?) {
        require(cvText.isNotBlank()) { "El CV no contiene texto legible. Vuelve a cargarlo o completa el perfil." }
        require(vacancyText.isNotBlank() || vacancyImage != null) { "Ingresa texto o selecciona una imagen de la vacante." }
        require(cvText.length <= MAX_CV_CHARS) { "El texto del CV es demasiado extenso. Conserva la versión más concisa." }
        require(vacancyText.length <= MAX_VACANCY_CHARS) { "El texto de la vacante es demasiado extenso." }
    }

    private fun parseJsonObject(text: String): JsonObject {
        val cleaned = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return json.parseToJsonElement(cleaned).jsonObject
    }

    private fun JsonObject.string(key: String): String = get(key)?.jsonPrimitive?.contentOrNull.orEmpty()
    private fun JsonObject.stringOrNull(key: String): String? = string(key).takeIf { it.isNotBlank() && it != "null" }
    private fun JsonObject.int(key: String): Int = get(key)?.jsonPrimitive?.intOrNull
        ?: throw IllegalArgumentException("La respuesta de IA no contiene un valor válido para $key.")
    private fun JsonObject.intOrDefault(key: String, default: Int): Int = get(key)?.jsonPrimitive?.intOrNull ?: default
    private fun JsonObject.booleanOrDefault(key: String, default: Boolean): Boolean = get(key)?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: default
    private fun JsonObject.array(key: String): JsonArray = get(key)?.jsonArray
        ?: throw IllegalArgumentException("La respuesta de IA no contiene la lista $key.")
    private fun JsonObject.arrayOrEmpty(key: String): JsonArray = get(key)?.jsonArray ?: JsonArray(emptyList())
    private fun JsonObject.strings(key: String): List<String> = arrayOrEmpty(key).mapNotNull { it.jsonPrimitive.contentOrNull }
    private fun JsonObject.stringsOrEmpty(key: String): List<String> = arrayOrEmpty(key).mapNotNull { it.jsonPrimitive.contentOrNull }
    private fun JsonObject.obj(key: String): JsonObject = get(key)?.jsonObject
        ?: throw IllegalArgumentException("La respuesta de IA no contiene el objeto $key.")

    private fun JsonObject.requirements(key: String): List<RequirementItem> = arrayOrEmpty(key).mapNotNull { element ->
        runCatching {
            val item = element.jsonObject
            RequirementItem(
                requirement = item.string("requirement").ifBlank { "Requisito del puesto" },
                cvEvidence = item.string("cvEvidence").ifBlank { "Evidencia de competencias" },
                status = item.string("status").ifBlank { "Evaluado" },
                critical = item.booleanOrDefault("critical", false)
            )
        }.getOrNull()
    }

    private companion object {
        const val MAX_CV_CHARS = 50_000
        const val MAX_VACANCY_CHARS = 30_000
    }
}
