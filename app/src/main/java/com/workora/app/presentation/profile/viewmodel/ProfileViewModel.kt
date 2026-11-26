package com.workora.app.presentation.profile.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workora.app.core.utils.FileHelper
import com.workora.app.domain.model.Profile
import com.workora.app.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: ProfileRepository
) : ViewModel() {
    val profile: StateFlow<Profile?> = repository.getProfile().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Display terms actually extracted from the selected CV, never from its filename.
    val indexedSkills: StateFlow<List<String>> = profile.map { p ->
        p?.summary.orEmpty().split(Regex("[^\\p{L}\\p{N}+#.]+"))
            .map { it.trim() }.filter { it.length >= 4 }.distinctBy { it.lowercase() }.take(30)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun uploadPdf(context: Context, uri: Uri) {
        viewModelScope.launch {
            val savedFileName = withContext(Dispatchers.IO) {
                FileHelper.copyUriToInternalStorage(context, uri)
            } ?: return@launch
            val extractedText = withContext(Dispatchers.IO) {
                FileHelper.extractPdfText(context, savedFileName)
            }.trim()
            val currentProfile = profile.value ?: Profile(name = "Usuario", summary = "")
            val detectedName = if (currentProfile.name == "Usuario" || currentProfile.name.isBlank()) {
                extractNameFromCv(extractedText) ?: "Usuario"
            } else {
                currentProfile.name
            }
            repository.saveProfile(currentProfile.copy(
                name = detectedName,
                pdfFileName = savedFileName,
                summary = extractedText,
                lastUpdated = System.currentTimeMillis()
            ))
        }
    }

    private fun extractNameFromCv(cvText: String): String? {
        val lines = cvText.lines().map { it.trim() }.filter { it.isNotBlank() }.take(8)
        for (line in lines) {
            if (!line.contains("@") && !line.contains("http") && !line.contains("CV") &&
                !line.contains("Curriculum", ignoreCase = true) && !line.contains("Hoja de Vida", ignoreCase = true) &&
                !line.contains("Perfil", ignoreCase = true) && !line.contains("Experiencia", ignoreCase = true) &&
                line.matches(Regex("^[A-ZÁÉÍÓÚÑa-záréiouseñ\\s.'-]{3,50}$"))
            ) {
                val words = line.split(Regex("\\s+"))
                if (words.size in 2..4) return line
            }
        }
        return null
    }
}
