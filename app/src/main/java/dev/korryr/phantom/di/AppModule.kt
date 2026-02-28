package dev.korryr.phantom.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.korryr.phantom.ai.GroqRepository
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)     // Fast failure on unreachable host
            .readTimeout(8, TimeUnit.SECONDS)         // Streaming: chunks arrive over time
            .writeTimeout(3, TimeUnit.SECONDS)
            // Keep connections alive to skip TCP+TLS handshake on repeat requests
            .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }

    @Provides
    @Singleton
    fun provideGroqRepository(client: OkHttpClient): GroqRepository {
        return GroqRepository(client)
    }
}
