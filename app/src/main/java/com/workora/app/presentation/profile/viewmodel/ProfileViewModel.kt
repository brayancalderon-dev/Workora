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
            repository.saveProfile(currentProfile.copy(
                pdfFileName = savedFileName,
                summary = extractedText,
                lastUpdated = System.currentTimeMillis()
            ))
        }
    }
}
