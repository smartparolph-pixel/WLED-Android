package ca.cgagnier.wlednativeandroid.di

import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.auth.AuthService
import ca.cgagnier.wlednativeandroid.service.auth.FakeAuthService
import ca.cgagnier.wlednativeandroid.service.auth.KeystoreSessionStore
import ca.cgagnier.wlednativeandroid.service.auth.SessionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideSessionStore(preferences: UserPreferencesRepository): SessionStore = KeystoreSessionStore(preferences)

    // TODO(auth): bind the Supabase implementation here once it exists. FakeAuthService is for development only.
    @Provides
    @Singleton
    fun provideAuthService(sessionStore: SessionStore): AuthService = FakeAuthService(sessionStore)
}
