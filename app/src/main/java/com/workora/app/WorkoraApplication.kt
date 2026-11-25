package com.workora.app

import android.app.Application
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WorkoraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // FirebaseInitProvider creates the default app before Application.onCreate.
        // Install the variant-specific App Check provider before any app feature uses Firebase.
        AppCheckProviderInstaller.install(FirebaseApp.getInstance())
    }
}
