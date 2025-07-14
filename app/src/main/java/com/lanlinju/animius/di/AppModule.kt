package com.lanlinju.animius.di

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.lanlinju.animius.application.AnimeApplication
import com.lanlinju.animius.data.local.database.AnimeDatabase
import com.lanlinju.animius.data.repository.RoomRepositoryImpl
import com.lanlinju.animius.domain.repository.RoomRepository
import com.lanlinju.animius.util.ANIME_DATABASE
import com.lanlinju.animius.util.createHttpClient
import com.lanlinju.animius.util.preferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun providesAnimeApplication(
        @ApplicationContext app: Context
    ): AnimeApplication {
        return app as AnimeApplication
    }

    @Singleton
    @Provides
    fun providesContext(
        @ApplicationContext app: Context
    ): Context {
        return app
    }

    @Singleton
    @Provides  // The Application binding is available without qualifiers.
    fun providesDatabase(application: Application): AnimeDatabase {
        return Room.databaseBuilder(
            application,
            AnimeDatabase::class.java,
            ANIME_DATABASE,
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Singleton
    @Provides
    fun providesRoomRepository(database: AnimeDatabase): RoomRepository {
        return RoomRepositoryImpl(database)
    }

    @Singleton
    @Provides
    fun providesPreferences(@ApplicationContext context: Context): SharedPreferences {
        return context.preferences
    }

    @Singleton
    @Provides
    fun provideHttpClient(): HttpClient {
        return DefaultHttpClient
    }
}

val DefaultHttpClient = createHttpClient {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            },
        )
    }
}