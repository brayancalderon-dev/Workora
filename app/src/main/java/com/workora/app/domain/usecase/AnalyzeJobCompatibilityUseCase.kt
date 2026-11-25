package com.workora.app.domain.usecase

import com.workora.app.data.local.entity.HiringRoomJobEntity
import com.workora.app.data.remote.hiringroom.RawHiringRoomJob
import javax.inject.Inject
import javax.inject.Singleton

/** Conservative local, evidence-based comparison. This is not an AI service response. */
@Singleton
class AnalyzeJobCompatibilityUseCase @Inject constructor() {

    fun execute(rawJob: RawHiringRoomJob, userCvSummary: String? = null): HiringRoomJobEntity {
        val cv = normalize(userCvSummary.orEmpty())
        val title = normalize(rawJob.title)
        val functions = normalize(rawJob.description)
        val requirements = normalize(rawJob.requirements)
        val jobEvidence = meaningfulTokens("$title $functions $requirements")
        val cvEvidence = meaningfulTokens(cv)

        val roleTokens = roleEvidence(title)
        val functionTokens = meaningfulTokens(functions)
        val requirementTokens = meaningfulTokens(requirements)
        val roleCoverage = coverage(roleTokens, cvEvidence)
        val functionCoverage = coverage(functionTokens, cvEvidence)
        val requirementCoverage = coverage(requirementTokens, cvEvidence)
        val overallCoverage = coverage(jobEvidence, cvEvidence)
        val explicit = (roleTokens + functionTokens + requirementTokens).intersect(cvEvidence)
            .filter { it.length >= 5 }
            .distinct()

        // A missing CV is never treated as evidence. Generic words are removed before
        // comparison, so incidental mentions cannot create a strong recommendation.
        var score = if (cvEvidence.isEmpty() || jobEvidence.isEmpty()) 0 else
            (roleCoverage * 0.35 + functionCoverage * 0.35 + requirementCoverage * 0.20 + overallCoverage * 0.10)
                .toInt().coerceIn(0, 100)

        val mandatory = Regex("(?i)(requisito obligatorio|indispensable|must have|requisito excluyente|debe contar con)[:\\s]+([^.;\\n]+)")
            .findAll(rawJob.requirements).flatMap { meaningfulTokens(normalize(it.groupValues[2])).asSequence() }.toSet()
        val missingMandatory = mandatory - cvEvidence
        if (mandatory.isNotEmpty() && cvEvidence.isNotEmpty()) {
            val mandatoryCoverage = coverage(mandatory, cvEvidence)
            score = (score * mandatoryCoverage / 100.0).toInt()
        }
        // A role with no demonstrated relationship to the CV cannot receive a high score
        // solely from transferable or general skills.
        if (roleCoverage < 15) score = minOf(score, 35)

        val gaps = buildList {
            if (cvEvidence.isEmpty()) add("No hay texto de CV disponible para respaldar la evaluación")
            if (roleCoverage < 15) add("El CV no evidencia experiencia relacionada con el cargo")
            if (missingMandatory.isNotEmpty()) add("Requisitos obligatorios sin evidencia: ${missingMandatory.joinToString()}")
            if (isEmpty()) add("No se detectaron brechas textuales claras; revisar el contexto profesional")
        }
        val matches = explicit.take(12)
        val fitReason = when {
            cvEvidence.isEmpty() -> "Comparación no disponible: falta texto extraído de la hoja de vida."
            matches.isEmpty() -> "La comparación local no encontró evidencia específica compartida entre el CV y esta vacante."
            else -> "Coincidencias textuales detectadas en el CV y la vacante: ${matches.joinToString()}"
        }
        val conclusion = when {
            cvEvidence.isEmpty() -> "Compatibilidad no disponible hasta contar con texto del CV. No se realizó análisis de IA."
            score >= 75 -> "La estimación local encontró evidencia textual relacionada. No es un análisis de IA; confirma funciones y requisitos antes de postularte."
            score >= 40 -> "La estimación local encontró coincidencias parciales. No es un análisis de IA; revisa las brechas antes de postularte."
            else -> "La evidencia textual local es insuficiente para recomendar la postulación. No se realizó análisis de IA."
        }

        return HiringRoomJobEntity(
            id = rawJob.id,
            title = rawJob.title,
            companyName = rawJob.companyName,
            companyLogoUrl = rawJob.companyLogoUrl,
            location = rawJob.location,
            workMode = rawJob.workMode,
            level = rawJob.level,
            description = rawJob.description,
            requirements = rawJob.requirements,
            applyUrl = rawJob.applyUrl,
            matchScore = score,
            category = "Estimación local",
            isRecommended = score >= 75,
            postedDate = rawJob.postedDate,
            daysAgo = rawJob.daysAgo,
            salary = rawJob.salary,
            aiWhyFit = fitReason,
            aiMatches = matches.joinToString(" | ").ifBlank { "Sin evidencia coincidente" },
            aiGaps = gaps.joinToString(" | "),
            aiConclusion = conclusion,
            techScore = (requirementCoverage).coerceIn(0, 100),
            expScore = roleCoverage.coerceIn(0, 100),
            funcScore = functionCoverage.coerceIn(0, 100)
        )
    }

    private fun coverage(terms: Set<String>, cv: Set<String>): Int =
        if (terms.isEmpty() || cv.isEmpty()) 0 else (terms.intersect(cv).size * 100 / terms.size).coerceIn(0, 100)

    private fun normalize(value: String) = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    private fun meaningfulTokens(value: String): Set<String> = normalize(value)
        .split(Regex("[^a-z0-9+#.]+"))
        .filter { (it.length >= 4 || it in shortTechnicalTerms) && it !in stopWords }
        .map(::canonicalTerm)
        .toSet()

    // Career descriptors are excluded ("analyst" alone is not a specialty), while
    // the domain terms in titles remain evidence. Canonical forms cover common CV/job
    // wording differences in Spanish and English without assuming a profession.
    private fun roleEvidence(value: String): Set<String> = normalize(value)
        .split(Regex("[^a-z0-9+#.]+"))
        .filter { (it.length >= 4 || it in shortTechnicalTerms) && it !in roleDescriptors }
        .map(::canonicalTerm)
        .toSet()

    private fun canonicalTerm(token: String): String = when (token) {
        "dato", "datos", "data" -> "data"
        "analisis", "analysis", "analitica", "analitico", "analytics" -> "analytics"
        "informe", "informes", "reporte", "reportes", "report", "reports", "reporting" -> "reporting"
        "tablero", "tableros", "dashboard", "dashboards" -> "dashboard"
        "automatizacion", "automatizar", "automation", "automating", "automated" -> "automation"
        "consulta", "consultas", "query", "queries" -> "query"
        "procesamiento", "processing", "processed" -> "processing"
        "visualizacion", "visualization", "visualisation" -> "visualization"
        else -> token
    }

    private companion object {
        val shortTechnicalTerms = setOf("sql", "aws", "gcp", "sap", "crm", "erp", "r", "go", "c", "c++", "c#", ".net")
        val roleDescriptors = setOf(
            "analista", "analyst", "ingeniero", "engineer", "desarrollador", "developer", "programador", "programmer",
            "gerente", "manager", "coordinador", "coordinator", "especialista", "specialist", "asistente", "assistant",
            "pasante", "intern", "senior", "junior", "lead", "jefe", "supervisor"
        )
        val stopWords = setOf(
            "para", "como", "conocimientos", "experiencia", "experto", "experta", "manejo", "trabajo", "empresa", "equipo", "perfil",
            "responsable", "responsabilidades", "requisitos", "deseable", "deseables", "habilidad", "habilidades", "capacidad", "capacidades",
            "profesional", "profesionales", "educacion", "formacion", "nivel", "anos", "anios", "cargo", "puesto", "vacante", "buscamos",
            "busca", "debera", "debe", "tener", "contar", "realizar", "gestion", "gestionar", "desarrollo", "desarrollar",
            "analista", "sistemas", "administracion", "tecnologia", "coordinacion", "con", "del", "las", "los", "una", "uno",
            "este", "esta", "desde", "sobre", "entre", "cuando", "donde", "will", "with", "from", "this", "that", "have", "your"
        )
    }
}
