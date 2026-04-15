package ru.tcynik.meshtactics.data.chat.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.tcynik.meshtactics.presentation.feature.chat.model.ChatTab

class ChatPrefsRepository(
    private val dataStore: DataStore<Preferences>,
) {
    fun observeCurrentTab(): Flow<ChatTab> =
        dataStore.data.map { prefs ->
            when (prefs[KEY_CURRENT_TAB]) {
                ChatTab.CHAT.name -> ChatTab.CHAT
                else -> ChatTab.FILTER
            }
        }

    suspend fun setCurrentTab(tab: ChatTab) {
        dataStore.edit { it[KEY_CURRENT_TAB] = tab.name }
    }

    fun observeSelectedChatId(): Flow<String?> =
        dataStore.data.map { it[KEY_SELECTED_CHAT_ID] }

    suspend fun setSelectedChatId(id: String?) {
        dataStore.edit { prefs ->
            if (id != null) prefs[KEY_SELECTED_CHAT_ID] = id
            else prefs.remove(KEY_SELECTED_CHAT_ID)
        }
    }

    fun observeCheckedIds(): Flow<Set<String>> =
        dataStore.data.map { it[KEY_CHECKED_IDS] ?: emptySet() }

    suspend fun setCheckedIds(ids: Set<String>) {
        dataStore.edit { it[KEY_CHECKED_IDS] = ids }
    }

    companion object {
        val KEY_CURRENT_TAB = stringPreferencesKey("chat_current_tab")
        val KEY_SELECTED_CHAT_ID = stringPreferencesKey("chat_selected_id")
        val KEY_CHECKED_IDS = stringSetPreferencesKey("chat_checked_ids")
    }
}
