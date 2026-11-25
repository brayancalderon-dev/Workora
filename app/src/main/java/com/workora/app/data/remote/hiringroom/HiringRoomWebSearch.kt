package com.workora.app.data.remote.hiringroom

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.URLDecoder

/** Finds and checks public Hiring Room portals using evidence returned by DuckDuckGo. */
object HiringRoomWebSearch {
    data class Result(val company: HiringRoomCompany, val verified: Boolean, val message: String)
    private val LEGAL_SUFFIX_PATTERN = Regex(
        "(?i)\\b(s\\.?a\\.?s?\\.?|s\\.? de r\\.?l\\.? de c\\.?v\\.?|s\\.? de r\\.?l\\.?|" +
            "sociedad de responsabilidad limitada(?: de capital variable)?|compania limitada|cia\\.? ltda\\.?|ltda\\.?|" +
            "limited|incorporated|inc\\.?)\\b"
    )

    fun search(companyName: String): Result? {
        val normalized = normalize(companyName)
        require(normalized.length >= 2) { "Escribe un nombre de empresa válido." }
        val legalNameStripped = removeLegalSuffixes(companyName)
        val variants = listOf(companyName.trim(), legalNameStripped).distinct().filter { it.length >= 2 }
        val queries = variants.flatMap { variant ->
            listOf(
                "$variant Hiring Room careers portal employment",
                "$variant portal de empleo candidatos",
                "site:hiringroom.com \"$variant\""
            )
        }.distinct().take(6)
        var successfulSearches = 0
        val resultPages = queries.mapNotNull { query ->
            runCatching {
                request("https://html.duckduckgo.com/html/?q=${URLEncoder.encode(query, "UTF-8")}")
                    .also { successfulSearches++ }
            }.getOrNull()
        }
        if (successfulSearches == 0) throw IOException("No se pudo acceder al servicio de búsqueda web.")

        var links = resultPages.flatMap(::extractLinks).distinct().take(5)
        if (links.none { link ->
                val host = runCatching { URL(link).host.lowercase() }.getOrDefault("")
                nameSimilarity(normalized, host.removeSuffix(".hiringroom.com").filter(Char::isLetterOrDigit)) >= 0.65
            }) {
            val googlePages = queries.take(2).mapNotNull { query ->
                runCatching {
                    request("https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}")
                }.getOrNull()
            }
            links = (links + googlePages.flatMap(::extractLinks)).distinct().take(5)
        }
        var portalCandidateCount = 0
        var accessiblePortalCount = 0
        val ranked = links.mapNotNull { link ->
            val url = runCatching { URL(link) }.getOrNull() ?: return@mapNotNull null
            val host = url.host.lowercase()
            if (!host.endsWith(".hiringroom.com") ||
                url.path.contains("get_vacancy", ignoreCase = true)) return@mapNotNull null
            portalCandidateCount++
            val slug = host.removeSuffix(".hiringroom.com").filter(Char::isLetterOrDigit)
            val slugScore = nameSimilarity(normalized, slug)
            val response = runCatching { requestPage(url.toString()) }.getOrNull() ?: return@mapNotNull null
            accessiblePortalCount++
            val finalUrl = runCatching { URL(response.finalUrl) }.getOrNull() ?: return@mapNotNull null
            if (!finalUrl.host.endsWith(".hiringroom.com", ignoreCase = true)) return@mapNotNull null
            val page = response.body
            val pageText = normalize(page.replace(Regex("<[^>]*>"), " "))
            val bodyEvidence = pageText.contains(normalized)
            val hiringRoomEvidence = page.contains("hiringroom", ignoreCase = true) &&
                listOf("empleo", "vacante", "jobs", "candidato", "perfil", "preguntas frecuentes", "portal")
                    .any { page.contains(it, ignoreCase = true) }
            if (!hiringRoomEvidence) return@mapNotNull null
            Triple(response.finalUrl.substringBefore('?').trimEnd('/'), slugScore, bodyEvidence)
        }.filter { (_, slugScore, bodyEvidence) -> slugScore >= 0.90 || bodyEvidence }
            .sortedByDescending { (candidate, slugScore, bodyEvidence) -> slugScore + if (bodyEvidence) 0.5 else 0.0 }

        if (portalCandidateCount > 0 && accessiblePortalCount == 0) {
            throw IOException("Se encontraron enlaces a portales, pero no fue posible abrirlos.")
        }

        val best = ranked.firstOrNull() ?: return null
        val finalHost = URL(best.first).host.lowercase()
        val hostId = finalHost.removeSuffix(".hiringroom.com")
        val id = if (hostId in setOf("jobs", "careers", "portal")) {
            val pathId = URL(best.first).path.split('/').filter { it.isNotBlank() && it !in setOf("jobs", "careers", "portal") }
                .joinToString("_")
            if (pathId.isNotBlank()) "${hostId}_$pathId" else "${hostId}_${normalize(companyName)}"
        } else hostId
        val verified = best.third || best.second >= 0.98
        return Result(
            company = HiringRoomCompany(
                id = id,
                name = companyName.trim(),
                sector = "Empresa verificada en Hiring Room",
                logoUrl = "",
                hiringRoomUrl = best.first,
                vacanciesCount = 0,
                isDefaultSubscribed = false
            ),
            verified = verified,
            message = if (verified) {
                "Portal corporativo de Hiring Room accesible y relacionado con '$companyName'."
            } else {
                "Se encontró un portal accesible con nombre de dominio parecido, pero la identidad necesita validación manual."
            }
        )
    }

    private fun extractLinks(html: String): List<String> {
        val hrefs = Regex("(?i)href\\s*=\\s*[\"']([^\"']+)[\"']").findAll(html)
            .map { it.groupValues[1].replace("&amp;", "&") }.toList()
        return hrefs.mapNotNull { href ->
            val decodedHref = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
            val direct = if (decodedHref.startsWith("//")) "https:$decodedHref" else decodedHref
            val encodedTarget = when {
                direct.contains("uddg=") -> Regex("[?&]uddg=([^&]+)").find(direct)?.groupValues?.get(1)
                direct.startsWith("/url?") || direct.contains("google.com/url?") -> Regex("[?&]q=([^&]+)").find(direct)?.groupValues?.get(1)
                else -> null
            }
            val candidate = encodedTarget?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() } ?: direct
            candidate?.takeIf { runCatching { URL(it).host.endsWith(".hiringroom.com", true) }.getOrDefault(false) }
        }
    }

    private fun nameSimilarity(name: String, evidence: String): Double {
        if (name.isBlank() || evidence.isBlank()) return 0.0
        if (evidence.contains(name) || name.contains(evidence)) return 1.0
        val longest = maxOf(name.length, evidence.length)
        if (longest == 0) return 0.0
        val previous = IntArray(evidence.length + 1) { it }
        for (i in name.indices) {
            var diagonal = previous[0]
            previous[0] = i + 1
            for (j in evidence.indices) {
                val above = previous[j + 1]
                previous[j + 1] = minOf(previous[j + 1] + 1, previous[j] + 1, diagonal + if (name[i] == evidence[j]) 0 else 1)
                diagonal = above
            }
        }
        return 1.0 - previous[evidence.length].toDouble() / longest
    }

    private fun normalize(value: String): String = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .filter(Char::isLetterOrDigit)

    private fun removeLegalSuffixes(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(LEGAL_SUFFIX_PATTERN, " ")
            .replace(Regex("[^\\p{L}\\p{N} ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun request(address: String): String {
        return requestPage(address).body
    }

    private data class PageResponse(val finalUrl: String, val body: String)

    private fun requestPage(address: String): PageResponse {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Workora careers search")
        return try {
            if (connection.responseCode !in 200..399) throw IOException("HTTP ${connection.responseCode}")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            PageResponse(connection.url.toString(), body)
        } finally {
            connection.disconnect()
        }
    }
}
