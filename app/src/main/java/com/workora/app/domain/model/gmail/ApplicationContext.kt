package com.workora.app.domain.model.gmail

/** Data prepared by vacancy analysis and consumed as-is by the Gmail draft flow. */
data class CandidateSignature(
    val fullName: String? = null,
    val profession: String? = null,
    val personalEmail: String? = null,
    val phone: String? = null
) {
    fun asText(): String = buildList {
        add("Saludos cordiales,")
        listOf(fullName, profession, personalEmail, phone)
            .mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
            .forEach(::add)
    }.joinToString("\n")
}

data class ApplicationContext(
    val candidate: CandidateSignature,
    val company: String?,
    val role: String?,
    val subject: String,
    val recipient: String?,
    val body: String
)
