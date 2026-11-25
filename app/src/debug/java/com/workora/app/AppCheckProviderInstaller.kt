package com.workora.app

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object AppCheckProviderInstaller {
    fun install(firebaseApp: FirebaseApp) {
        val appCheck = FirebaseAppCheck.getInstance(firebaseApp)
        // Keep the debug provider available for Firebase-backed features in this build.
        // Gemini requests use the direct Gemini API and do not pass through App Check.
        appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    }
}
