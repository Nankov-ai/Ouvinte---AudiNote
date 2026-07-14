package com.ouvinte.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.ouvinte.app.data.local.AppDatabase
import com.ouvinte.app.data.local.dao.ProjectDao
import com.ouvinte.app.data.local.dao.SessionDao
import com.ouvinte.app.BuildConfig
import com.ouvinte.app.data.remote.GeminiAuthInterceptor
import com.ouvinte.app.data.remote.api.GeminiFilesApi
import com.ouvinte.app.data.remote.api.GeminiGenerateApi
import com.ouvinte.app.data.remote.api.GoogleSearchApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().setLenient().create()

    // Cliente para Google Search — timeouts curtos são suficientes
    @Provides
    @Singleton
    @Named("search")
    fun provideSearchOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

    @Provides
    @Singleton
    fun provideGeminiAuthInterceptor(): GeminiAuthInterceptor =
        GeminiAuthInterceptor(BuildConfig.GEMINI_API_KEY)

    // Cliente para Gemini — timeouts longos para upload de áudio e transcrição de 2h
    @Provides
    @Singleton
    @Named("gemini")
    fun provideGeminiOkHttpClient(authInterceptor: GeminiAuthInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .addInterceptor(authInterceptor)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

    @Provides
    @Singleton
    @Named("search")
    fun provideSearchRetrofit(@Named("search") okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @Named("gemini")
    fun provideGeminiRetrofit(@Named("gemini") okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideGoogleSearchApi(@Named("search") retrofit: Retrofit): GoogleSearchApi =
        retrofit.create(GoogleSearchApi::class.java)

    @Provides
    @Singleton
    fun provideGeminiFilesApi(@Named("gemini") retrofit: Retrofit): GeminiFilesApi =
        retrofit.create(GeminiFilesApi::class.java)

    @Provides
    @Singleton
    fun provideGeminiGenerateApi(@Named("gemini") retrofit: Retrofit): GeminiGenerateApi =
        retrofit.create(GeminiGenerateApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    @Singleton
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context
}
