package com.workora.app.domain.repository

import com.workora.app.domain.model.gmail.AnalysisQuestion
import com.workora.app.domain.model.gmail.GeneratedApplicationEmail
import com.workora.app.domain.model.gmail.QuestionAnswer
import com.workora.app.domain.model.gmail.VacancyAssessment

interface CompatibilityAnalyzer {
    suspend fun analyzeInitial(cvText: String, vacancyText: String, vacancyImage: AiImagePayload? = null): VacancyAssessment
    suspend fun reevaluate(
        cvText: String,
        vacancyText: String,
        initial: VacancyAssessment,
        answers: Map<String, QuestionAnswer>,
        vacancyImage: AiImagePayload? = null
    ): VacancyAssessment
    suspend fun generateApplicationEmail(
        cvText: String,
        vacancyText: String,
        assessment: VacancyAssessment
    ): GeneratedApplicationEmail
}

interface AiProvider {
    suspend fun generateJson(prompt: String, image: AiImagePayload? = null): String
    suspend fun generateText(prompt: String): String
}

data class AiImagePayload(val mimeType: String, val bytes: ByteArray)
