package com.workora.app.presentation.dashboard.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.workora.app.domain.model.Profile
import com.workora.app.domain.repository.GmailDraftRepository
import com.workora.app.domain.repository.GmailSessionState
import com.workora.app.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    profileRepository: ProfileRepository,
    gmailDraftRepository: GmailDraftRepository
) : ViewModel() {

    val profile: StateFlow<Profile?> = profileRepository.getProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val gmailSession: StateFlow<GmailSessionState> = gmailDraftRepository.sessionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GmailSessionState()
        )
}