package com.workora.app.data.remote.hiringroom

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.concurrent.atomic.AtomicInteger
import com.workora.app.core.utils.HtmlTextDecoder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

/** Reads only vacancy permalinks exposed by each subscribed Hiring Room portal. */
object HiringRoomVacancySource {
    private const val MAX_VACANCIES_PER_COMPANY = 12

    suspend fun fetch(companies: List<HiringRoomCompany>): List<RawHiringRoomJob> = supervisorScope {
        val reachablePortals = AtomicInteger(0)
        val results = companies.distinctBy { it.id }.map { company ->
            async {
                runCatching { fetchCompany(company) }.onSuccess { reachablePortals.incrementAndGet() }.getOrDefault(emptyList())
            }
        }.awaitAll()
        if (companies.isNotEmpty() && reachablePortals.get() == 0) {
            throw IOException("No se pudo conectar con los portales de Hiring Room suscritos.")
        }
        results.flatten().distinctBy { it.id }
    }

    private fun fetchCompany(company: HiringRoomCompany): List<RawHiringRoomJob> {
        val listing = request(company.hiringRoomUrl)
        val baseUrl = URL(company.hiringRoomUrl)
        val links = Regex("(?is)<a\\b[^>]*href\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>(.*?)</a>")
            .findAll(listing)
            .mapNotNull { match ->
                val href = HtmlTextDecoder.decode(match.groupValues[1]).replace("\\/", "/")
                val absolute = runCatching { URL(baseUrl, href) }.getOrNull() ?: return@mapNotNull null
                if (!absolute.host.endsWith(".hiringroom.com", ignoreCase = true) ||
                    !absolute.path.contains("get_vacancy", ignoreCase = true)) return@mapNotNull null
                val anchorText = plainText(match.groupValues[2])
                absolute.toString() to anchorText
            }
            .distinctBy { it.first.substringBefore('?') }
            .take(MAX_VACANCIES_PER_COMPANY)

        return links.mapNotNull { (applyUrl, cardText) ->
            val detail = runCatching { request(applyUrl) }.getOrNull() ?: return@mapNotNull null
            parseVacancy(company, applyUrl, cardText, detail)
        }.toList()
    }

    private fun parseVacancy(
        company: HiringRoomCompany,
        applyUrl: String,
        cardText: String,
        html: String
    ): RawHiringRoomJob? {
        val text = plainText(html)
        val title = firstGroup(html, Regex("(?is)<h1\\b[^>]*>(.*?)</h1>"))
            ?.let(::plainText)
            ?.replace(Regex("(?i)^nuevas oportunidades de empleo:\\s*"), "")
            ?.replace(Regex("(?i)\\s+en\\s+${Regex.escape(company.name)}\\s*$"), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: cardText.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val header = text.substringAfter(title, "").take(600)

        val description = section(text, "Descripción del puesto", listOf("Requisitos", "Beneficios", "Nosotros"))
            .ifBlank { section(text, "Misión:", listOf("Funciones:", "Requisitos", "Beneficios")) }
            .ifBlank { cardText }
        val requirements = section(text, "Requisitos", listOf("Beneficios", "Detalles", "Nosotros"))
            .ifBlank { "Requisitos no especificados en la página de la vacante." }
        val age = extractAge(header)
        val id = URL(applyUrl).path.substringAfterLast('/').takeIf { it.isNotBlank() } ?: return null
        val location = Regex("(?im)^([^\\n,]{2,50},\\s*[^\\n,]{2,50},\\s*[^\\n]{2,50})$")
            .find(header)?.groupValues?.get(1)?.trim()
            ?: "Ubicación no especificada"
        val workMode = when {
            header.contains("híbrido", ignoreCase = true) || header.contains("hibrido", ignoreCase = true) -> "Híbrido"
            header.contains("remoto", ignoreCase = true) -> "Remoto"
            header.contains("presencial", ignoreCase = true) -> "Presencial"
            else -> "No especificado"
        }
        val level = Regex("(?i)\\b(Semi[ -]?Senior|Junior|Senior|Lead|Gerencia|Dirección)\\b")
            .find(header)?.groupValues?.get(1) ?: "No especificado"

        return RawHiringRoomJob(
            id = id,
            companyId = company.id,
            companyName = company.name,
            companyLogoUrl = company.logoUrl,
            title = title,
            location = location,
            workMode = workMode,
            level = level,
            description = description,
            requirements = requirements,
            applyUrl = applyUrl,
            postedDate = age.first,
            daysAgo = age.second
        )
    }

    private fun extractAge(text: String): Pair<String, Int> {
        val age = Regex("(?i)hace\\s+(\\d+)\\s+(horas?|d[ií]as?|semanas?|mes(?:es)?)").find(text)
        if (age != null) {
            val count = age.groupValues[1].toIntOrNull() ?: return "Fecha no disponible" to 3650
            val unit = normalize(age.groupValues[2])
            val days = when {
                unit.startsWith("hora") -> 0
                unit.startsWith("dia") -> count
                unit.startsWith("semana") -> count * 7
                else -> count * 30
            }
            return age.value to days
        }
        if (text.contains("nuevo", ignoreCase = true)) return "Nuevo" to 0
        return "Fecha no disponible" to 3650
    }

    private fun section(text: String, heading: String, stops: List<String>): String {
        val start = text.indexOf(heading, ignoreCase = true)
        if (start < 0) return ""
        val contentStart = start + heading.length
        val end = stops.mapNotNull { stop -> text.indexOf(stop, contentStart, ignoreCase = true).takeIf { it >= 0 } }
            .minOrNull() ?: text.length
        return text.substring(contentStart, end).trim().take(12_000)
    }

    private fun firstGroup(value: String, regex: Regex): String? = regex.find(value)?.groupValues?.getOrNull(1)

    private fun plainText(value: String): String = HtmlTextDecoder.decode(
        value.replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)</(p|div|li|h[1-6]|section|br|article|tr)>"), "\n")
            .replace(Regex("<[^>]+>"), " ")
    ).replace(Regex("[\\t\\r ]+"), " ").replace(Regex(" *\\n *"), "\n").trim()

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")

    private fun request(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 4500
        connection.readTimeout = 4500
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Workora vacancy reader")
        return try {
            if (connection.responseCode !in 200..399) throw IOException("HTTP ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
