package com.workora.app.domain.usecase

import com.workora.app.domain.model.gmail.ApplicationContext
import com.workora.app.domain.model.gmail.VacancyAssessment
import com.workora.app.domain.repository.CompatibilityAnalyzer
import javax.inject.Inject

/** Builds the application payload from the existing assessment and verified CV text. */
class PrepareApplicationContextUseCase @Inject constructor(
    private val analyzer: CompatibilityAnalyzer,
    private val extractCandidateSignature: ExtractCandidateSignatureUseCase
) {
    suspend fun execute(cvText: String, vacancyText: String, assessment: VacancyAssessment): ApplicationContext {
        val generated = analyzer.generateApplicationEmail(cvText, vacancyText, assessment)
        val role = assessment.vacancy.role?.trim()?.takeIf(String::isNotBlank)
        val company = assessment.vacancy.company?.trim()?.takeIf(String::isNotBlank)
        val body = generated.body
            .replace(Regex("(?is)\\n\\s*(saludos cordiales|atentamente|un saludo)[,.:]?.*$"), "")
            .trim()
        val recipient = assessment.vacancy.contactEmail?.trim()?.takeIf {
            EMAIL.matches(it)
        }
        return ApplicationContext(
            candidate = extractCandidateSignature.execute(cvText),
            company = company,
            role = role,
            subject = role?.let { "Postulacion - $it" } ?: "Postulacion",
            recipient = recipient,
            body = body
        )
    }

    private companion object {
        val EMAIL = Regex("(?i)^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$")
    }
}
