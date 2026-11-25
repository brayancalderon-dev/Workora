package com.workora.app.domain.model

data class Profile(
    val id: Int = 1, // Usually just 1 profile per app instance for this MVP
    val name: String,
    val summary: String,
    val pdfFileName: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)