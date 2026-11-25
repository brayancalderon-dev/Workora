package com.workora.app.data.remote.hiringroom

data class HiringRoomCompany(
    val id: String,
    val name: String,
    val sector: String,
    val logoUrl: String,
    val hiringRoomUrl: String,
    val vacanciesCount: Int,
    val isDefaultSubscribed: Boolean = true
)

data class RawHiringRoomJob(
    val id: String,
    val companyId: String,
    val companyName: String,
    val companyLogoUrl: String,
    val title: String,
    val location: String,
    val workMode: String,
    val level: String,
    val description: String,
    val requirements: String,
    val applyUrl: String,
    val postedDate: String,
    val daysAgo: Int,
    val salary: String? = null
)

object HiringRoomCompanySource {
    // Keep these identifiers stable: they are the keys already persisted in subscriptions.
    val verifiedCompanies = listOf(
        HiringRoomCompany("bolivariano", "Banco Bolivariano", "Banca & Fintech", "https://images.unsplash.com/photo-1541354329998-f4d9a9f9297f?w=300&q=80", "https://bancobolivariano.hiringroom.com/jobs", 0),
        HiringRoomCompany("multicines", "Multicines", "Entretenimiento & Servicios", "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=300&q=80", "https://multicines.hiringroom.com/jobs", 0),
        HiringRoomCompany("lafabril", "La Fabril", "Consumo Masivo & Industria", "https://images.unsplash.com/photo-1581091226825-a6a2a5aee158?w=300&q=80", "https://lafabril.hiringroom.com/jobs", 0),
        HiringRoomCompany("bbva", "BBVA", "Banca & Servicios Financieros", "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?w=300&q=80", "https://bbva.hiringroom.com/jobs", 0)
    )
}
