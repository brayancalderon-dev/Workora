package com.workora.app.presentation.jobs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workora.app.data.local.entity.HiringRoomJobEntity
import com.workora.app.data.remote.hiringroom.HiringRoomCompanySource
import com.workora.app.data.remote.hiringroom.HiringRoomCompany
import com.workora.app.data.remote.hiringroom.HiringRoomWebSearch
import com.workora.app.domain.repository.HiringRoomRepository
import com.workora.app.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

sealed class SearchStatus {
    data object Idle : SearchStatus()
    data class Searching(val stepMessage: String, val progressFraction: Float) : SearchStatus()
    data class Completed(val message: String) : SearchStatus()
    data class Error(val error: String) : SearchStatus()
}

data class HiringRoomStats(
    val totalAnalyzed: Int = 0,
    val recommendedCount: Int = 0,
    val highMatchCount: Int = 0,
    val companiesCount: Int = 0
)

@HiltViewModel
class HiringRoomViewModel @Inject constructor(
    private val repository: HiringRoomRepository,
    private val profileRepository: ProfileRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    sealed interface CompanySearchState {
        data object Idle : CompanySearchState
        data object Searching : CompanySearchState
        data class Found(val company: HiringRoomCompany) : CompanySearchState
        data class Candidate(val company: HiringRoomCompany, val message: String) : CompanySearchState
        data class NotFound(val message: String) : CompanySearchState
        data class ConnectionError(val message: String) : CompanySearchState
        data class SearchError(val message: String) : CompanySearchState
    }

    private val customCompanyPrefs = context.getSharedPreferences("hiringroom_companies", Context.MODE_PRIVATE)
    private fun loadCustomCompanies(): List<HiringRoomCompany> = customCompanyPrefs.getStringSet("companies", emptySet()).orEmpty().mapNotNull { row ->
        val parts = row.split("|", limit = 3)
        if (parts.size != 3) null else HiringRoomCompany(parts[0], parts[1], "Empresa verificada en Hiring Room", "", parts[2], 0, false)
    }
    private val _customCompanies = MutableStateFlow(loadCustomCompanies())
    val customCompanies: StateFlow<List<HiringRoomCompany>> = _customCompanies.asStateFlow()
    private val _companySearchState = MutableStateFlow<CompanySearchState>(CompanySearchState.Idle)
    val companySearchState: StateFlow<CompanySearchState> = _companySearchState.asStateFlow()

    fun searchCompany(name: String) {
        val query = name.trim()
        if (query.isBlank()) { _companySearchState.value = CompanySearchState.NotFound("Escribe el nombre de una empresa para buscar."); return }
        val normalizedQuery = normalizeCompanyName(query)
        val cachedCompany = (HiringRoomCompanySource.verifiedCompanies + _customCompanies.value)
            .firstOrNull { company ->
                val storedName = normalizeCompanyName(company.name)
                normalizedQuery == normalizeCompanyName(company.id) ||
                    normalizedQuery == storedName ||
                    (normalizedQuery.length >= 4 && storedName.length >= 4 &&
                        (storedName.contains(normalizedQuery) || normalizedQuery.contains(storedName)))
            }
        if (cachedCompany != null) {
            _companySearchState.value = CompanySearchState.Found(cachedCompany)
            return
        }
        viewModelScope.launch {
            _companySearchState.value = CompanySearchState.Searching
            try {
                val found = withContext(Dispatchers.IO) { HiringRoomWebSearch.search(query) }
                if (found == null) {
                    _companySearchState.value = CompanySearchState.NotFound("No se encontró evidencia suficiente de un portal de empleos de Hiring Room para '$query'.")
                } else if (!found.verified) {
                    _companySearchState.value = CompanySearchState.Candidate(found.company, found.message)
                } else {
                    saveVerifiedCompany(found.company)
                }
            } catch (e: Exception) {
                _companySearchState.value = if (e is java.io.IOException) {
                    CompanySearchState.ConnectionError("No se pudo conectar con el servicio de búsqueda. La empresa no se marca como inexistente; inténtalo nuevamente.")
                } else {
                    CompanySearchState.SearchError("La búsqueda no pudo procesarse: ${e.message ?: "error inesperado"}")
                }
            }
        }
    }

    fun clearCompanySearch() { _companySearchState.value = CompanySearchState.Idle }

    fun confirmCandidatePortal(company: HiringRoomCompany) {
        saveVerifiedCompany(company)
    }

    private fun saveVerifiedCompany(company: HiringRoomCompany) {
        val updated = (_customCompanies.value.filterNot { it.id == company.id } + company)
        _customCompanies.value = updated
        customCompanyPrefs.edit().putStringSet("companies", updated.map { "${it.id}|${it.name}|${it.hiringRoomUrl}" }.toSet()).apply()
        _companySearchState.value = CompanySearchState.Found(company)
    }

    private fun normalizeCompanyName(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("(?i)\\b(s\\.?a\\.?s?\\.?|s\\.? de r\\.?l\\.? de c\\.?v\\.?|s\\.? de r\\.?l\\.?|sociedad de responsabilidad limitada(?: de capital variable)?|compania limitada|cia\\.? ltda\\.?|ltda\\.?|incorporated|inc\\.?)\\b"), " ")
            .filter { it.isLetterOrDigit() }

    private val prefs = context.getSharedPreferences("hiringroom_subscriptions", Context.MODE_PRIVATE)

    private fun loadSubscribedCompanies(): Set<String> {
        val saved = prefs.getStringSet("subscribed_company_ids", null)
        return saved ?: HiringRoomCompanySource.verifiedCompanies.map { it.id }.toSet()
    }

    private fun saveSubscribedCompanies(set: Set<String>) {
        prefs.edit().putStringSet("subscribed_company_ids", set).apply()
    }

    private val _searchStatus = MutableStateFlow<SearchStatus>(SearchStatus.Idle)
    val searchStatus: StateFlow<SearchStatus> = _searchStatus.asStateFlow()

    private val _isSearchExecuted = MutableStateFlow(false)
    val isSearchExecuted: StateFlow<Boolean> = _isSearchExecuted.asStateFlow()

    private val _selectedSort = MutableStateFlow("Mayor compatibilidad")
    val selectedSort: StateFlow<String> = _selectedSort.asStateFlow()

    private val _selectedDaysFilter = MutableStateFlow(0) // 0 = Todas, 1 = 1 día, 3 = 3 días, 7 = 7 días, 14 = 14 días
    val selectedDaysFilter: StateFlow<Int> = _selectedDaysFilter.asStateFlow()

    private val _subscribedCompanyIds = MutableStateFlow<Set<String>>(loadSubscribedCompanies())
    val subscribedCompanyIds: StateFlow<Set<String>> = _subscribedCompanyIds.asStateFlow()

    private val subscribedCompanyCatalog = combine(_subscribedCompanyIds, _customCompanies) { ids, custom ->
        (HiringRoomCompanySource.verifiedCompanies + custom).distinctBy { it.id }.filter { it.id in ids }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedJobForDetail = MutableStateFlow<HiringRoomJobEntity?>(null)
    val selectedJobForDetail: StateFlow<HiringRoomJobEntity?> = _selectedJobForDetail.asStateFlow()

    val stats: StateFlow<HiringRoomStats> = repository.getActiveJobs()
        .map { jobs ->
            if (jobs.isEmpty()) {
                HiringRoomStats()
            } else {
                HiringRoomStats(
                    totalAnalyzed = jobs.size,
                    recommendedCount = jobs.count { it.matchScore >= 75 },
                    highMatchCount = jobs.count { it.matchScore >= 85 },
                    companiesCount = jobs.map { it.companyName }.distinct().size
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HiringRoomStats())

    val rawJobs: StateFlow<List<HiringRoomJobEntity>> = repository.getActiveJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredJobs: StateFlow<List<HiringRoomJobEntity>> = combine(
        rawJobs,
        _selectedSort,
        _searchQuery,
        _isSearchExecuted,
        subscribedCompanyCatalog
    ) { jobs, sort, query, isSearchedActive, subscribedCompanies ->
        if (!isSearchedActive) {
            emptyList()
        } else {
            val daysLimit = _selectedDaysFilter.value

            var list = jobs.filter { job ->
                val jobCompany = normalizeCompanyName(job.companyName)
                jobCompany.isNotBlank() && subscribedCompanies.any { company ->
                    val companyName = normalizeCompanyName(company.name)
                    jobCompany == companyName || jobCompany.contains(companyName) || companyName.contains(jobCompany)
                }
            }

            // Apply days ago filter
            if (daysLimit > 0) {
                list = list.filter { it.daysAgo <= daysLimit }
            }

            // Apply search query
            if (query.isNotBlank()) {
                val q = query.trim().lowercase()
                list = list.filter {
                    it.title.lowercase().contains(q) ||
                    it.companyName.lowercase().contains(q) ||
                    it.location.lowercase().contains(q) ||
                    it.description.lowercase().contains(q) ||
                    it.requirements.lowercase().contains(q)
                }
            }

            // Apply sorting
            when (sort) {
                "Mayor compatibilidad" -> list.sortedByDescending { it.matchScore }
                "Más recientes" -> list.sortedBy { it.daysAgo }
                "Empresa" -> list.sortedBy { it.companyName }
                else -> list.sortedByDescending { it.matchScore }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun startSearch() {
        viewModelScope.launch {
            try {
                _searchStatus.value = SearchStatus.Searching("🔎 Analizando fuentes de empresas suscritas...", 0.15f)
                delay(600)

                val subscribedCount = _subscribedCompanyIds.value.size
                _searchStatus.value = SearchStatus.Searching("🏢 Extrayendo vacantes de $subscribedCount empresas suscritas...", 0.45f)
                delay(700)

                val userProfile = profileRepository.getProfile().firstOrNull()
                _searchStatus.value = SearchStatus.Searching("🧠 Evaluando compatibilidad con palabras clave del CV...", 0.80f)
                delay(600)

                val companies = (HiringRoomCompanySource.verifiedCompanies + _customCompanies.value)
                    .distinctBy { it.id }
                    .filter { it.id in _subscribedCompanyIds.value }
                val results = repository.performSearch(userProfile?.summary, companies)
                _isSearchExecuted.value = true
                _searchStatus.value = SearchStatus.Searching("📊 Organizando resultados por coincidencia...", 0.95f)
                delay(400)

                val count = results.size
                _searchStatus.value = SearchStatus.Completed(
                    if (count == 0) "No hay una fuente de vacantes reales disponible; no se muestran registros de ejemplo."
                    else "¡Búsqueda finalizada! Se encontraron $count vacantes para tus empresas suscritas."
                )
            } catch (e: Exception) {
                _searchStatus.value = SearchStatus.Error("Error durante la búsqueda: ${e.message}")
            }
        }
    }

    fun setSort(sort: String) {
        _selectedSort.value = sort
    }

    fun setDaysFilter(days: Int) {
        _selectedDaysFilter.value = days
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleCompanySubscription(companyId: String) {
        val current = _subscribedCompanyIds.value.toMutableSet()
        if (current.contains(companyId)) {
            current.remove(companyId)
        } else {
            current.add(companyId)
        }
        _subscribedCompanyIds.value = current
        saveSubscribedCompanies(current)
    }

    fun openJobDetail(job: HiringRoomJobEntity) {
        _selectedJobForDetail.value = job
    }

    fun closeJobDetail() {
        _selectedJobForDetail.value = null
    }

    fun toggleFavorite(job: HiringRoomJobEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(job.id, !job.isFavorite)
        }
    }

    fun dismissJob(job: HiringRoomJobEntity) {
        viewModelScope.launch {
            repository.toggleDismissed(job.id, true)
            if (_selectedJobForDetail.value?.id == job.id) {
                _selectedJobForDetail.value = null
            }
        }
    }
}
