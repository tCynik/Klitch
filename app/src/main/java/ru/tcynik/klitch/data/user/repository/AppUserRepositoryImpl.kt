package ru.tcynik.klitch.data.user.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.klitch.domain.user.model.AppUser
import ru.tcynik.klitch.domain.user.repository.AppUserRepository

class AppUserRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
) : AppUserRepository {

    override fun observeUser(): Flow<AppUser> =
        dataStore.data.map { prefs ->
            AppUser(displayName = prefs[KEY_DISPLAY_NAME] ?: "")
        }

    override suspend fun saveUser(user: AppUser) {
        dataStore.edit { it[KEY_DISPLAY_NAME] = user.displayName }
    }

    companion object {
        val KEY_DISPLAY_NAME = stringPreferencesKey("app_user_display_name")
    }
}
