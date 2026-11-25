package com.workora.app.domain.model.gmail

data class VacancyDetails(
    val role: String?,
    val company: String?,
    val summary: String?,
    val responsibilities: List<String>,
    val mandatoryRequirements: List<String>,
    val desirableRequirements: List<String>,
    val technologies: List<String>,
    val experience: String?,
    val seniority: String?,
    val education: String?,
    val location: String?,
    val modality: String?,
    val salary: String?,
    val contactEmail: String?,
    val applicationUrl: String?
)

data class ScoringCriterion(
    val dimension: String,
    val score: Int,
    val applicable: Boolean,
    val evidence: String
)

data class EvidenceItem(
    val statement: String,
    val cvEvidence: String,
    val evidenceType: String
)

data class RequirementItem(
    val requirement: String,
    val cvEvidence: String,
    val status: String,
    val critical: Boolean
)

data class AnalysisQuestion(
    val id: String,
    val question: String,
    val reason: String,
    val options: List<String>,
    val allowsFreeText: Boolean
)

data class VacancyAssessment(
    val vacancy: VacancyDetails,
    val cvSufficient: Boolean,
    val vacancySufficient: Boolean,
    val missingInformation: List<String>,
    val criteria: List<ScoringCriterion>,
    val strengths: List<EvidenceItem>,
    val requirementsMet: List<RequirementItem>,
    val requirementsPartial: List<RequirementItem>,
    val gaps: List<RequirementItem>,
    val unverified: List<RequirementItem>,
    val justification: String,
    val recommendation: String,
    val confidence: Int,
    val questions: List<AnalysisQuestion>
) {
    val score: Int
        get() = CompatibilityScoring.score(criteria)

    val level: String
        get() = when {
            score >= 75 -> "Alta"
            score >= 60 -> "Media"
            else -> "Baja"
        }
}

data class QuestionAnswer(
    val selectedOption: String?,
    val additionalDetails: String
)

data class GeneratedApplicationEmail(
    val subject: String,
    val body: String
)

object CompatibilityScoring {
    private val weights = mapOf(
        "mandatory" to 30,
        "experience" to 20,
        "technical" to 15,
        "responsibilities" to 15,
        "seniority" to 10,
        "transferability" to 5,
        "education" to 3,
        "desirable" to 2
    )

    fun score(criteria: List<ScoringCriterion>): Int {
        val applicable = criteria.filter { it.applicable }
        val totalWeight = applicable.sumOf { weights[it.dimension] ?: 0 }
        require(totalWeight > 0) { "No hay evidencia suficiente para calcular una compatibilidad." }
        require(applicable.all { it.score in 0..100 && it.dimension in weights }) {
            "El análisis de compatibilidad contiene criterios inválidos."
        }
        return (applicable.sumOf { it.score * weights.getValue(it.dimension) } / totalWeight.toDouble())
            .toInt().coerceIn(0, 100)
    }

    fun validateCriteria(criteria: List<ScoringCriterion>) {
        require(criteria.map { it.dimension }.toSet() == weights.keys && criteria.size == weights.size) {
            "El análisis no incluyó todos los criterios requeridos."
        }
        require(criteria.all { it.score in 0..100 }) { "Una puntuación de criterio está fuera de rango." }
        require(criteria.all { it.evidence.isNotBlank() }) { "Falta justificar un criterio de compatibilidad." }
        require(criteria.none { it.evidence == "Sin evidencia en el CV" && it.applicable && it.score > 25 }) {
            "Un criterio con alta puntuación no contiene evidencia verificable del CV."
        }
        if (criteria.any { it.applicable }) {
            require(criteria.count { it.applicable } >= 3 &&
                criteria.any { it.dimension == "experience" && it.applicable } &&
                criteria.any { it.dimension == "responsibilities" && it.applicable }
            ) { "La vacante no contiene suficiente información para una evaluación equilibrada." }
        }
        score(criteria)
    }
}
