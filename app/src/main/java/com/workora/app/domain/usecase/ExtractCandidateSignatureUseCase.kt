package com.workora.app.domain.usecase

import com.workora.app.domain.model.gmail.CandidateSignature
import javax.inject.Inject

/** Extracts contact details only from OCR text supplied by the user's own CV. */
class ExtractCandidateSignatureUseCase @Inject constructor() {
    fun execute(cvText: String): CandidateSignature {
        val lines = cvText.lines().map(String::trim).filter(String::isNotBlank).take(12)
        val email = EMAIL.find(cvText)?.value
        val phone = PHONE.find(cvText)?.value
        val name = lines.firstOrNull { line ->
            val words = line.split(Regex("\\s+")).filter(String::isNotBlank)
            line.length in 5..60 && words.size in 2..5 && line.count(Char::isLetter) >= 8 &&
                line.none(Char::isDigit) && !line.contains('@') && !PHONE.containsMatchIn(line) &&
                !line.contains(Regex("(?i)\\b(cv|curriculum|hoja de vida|perfil|experiencia|educacion|contacto|ingeniero|ingeniera|analista|desarrollador)\\b"))
        }
        val profession = Regex("(?im)^(?:profesi(?:on|\\u00f3n)|t(?:itulo|\\u00edtulo) profesional|profesional en|cargo|ocupaci(?:on|\\u00f3n))\\s*[:\\-]?\\s*(.+)$")
            .find(cvText)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)
        return CandidateSignature(name, profession, email, phone)
    }

    private companion object {
        val EMAIL = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
        val PHONE = Regex("(?<!\\d)(?:\\+?\\d{1,3}[ .-]?)?(?:\\(?\\d{2,3}\\)?[ .-]?)?\\d{3}[ .-]?\\d{4}(?!\\d)")
    }
}
