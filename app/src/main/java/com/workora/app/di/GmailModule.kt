package com.workora.app.di

import com.workora.app.data.remote.ai.GroqApiKeyProvider
import com.workora.app.data.remote.ai.GeminiCompatibilityAnalyzer
import com.workora.app.data.remote.gmail.GmailApi
import com.workora.app.data.remote.gmail.GoogleGmailDraftRepository
import com.workora.app.domain.repository.AiProvider
import com.workora.app.domain.repository.CompatibilityAnalyzer
import com.workora.app.domain.repository.GmailDraftRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GmailBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAiProvider(provider: GroqApiKeyProvider): AiProvider

    @Binds
    @Singleton
    abstract fun bindCompatibilityAnalyzer(analyzer: GeminiCompatibilityAnalyzer): CompatibilityAnalyzer

    @Binds
    @Singleton
    abstract fun bindGmailDraftRepository(repository: GoogleGmailDraftRepository): GmailDraftRepository
}

@Module
@InstallIn(SingletonComponent::class)
object GmailNetworkModule {
    @Provides
    @Singleton
    fun provideGmailApi(): GmailApi = Retrofit.Builder()
        .baseUrl("https://gmail.googleapis.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GmailApi::class.java)
}
