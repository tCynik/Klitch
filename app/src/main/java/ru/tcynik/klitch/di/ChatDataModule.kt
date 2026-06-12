package ru.tcynik.klitch.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import ru.tcynik.klitch.data.chat.adapter.MeshToChatAdapter
import ru.tcynik.klitch.data.chat.prefs.ChatPrefsRepository
import ru.tcynik.klitch.data.chat.repository.ChatMessageRepositoryImpl
import ru.tcynik.klitch.data.chat.repository.ChatRepositoryImpl
import ru.tcynik.klitch.domain.chat.repository.ChatMessageRepository
import ru.tcynik.klitch.domain.chat.repository.ChatRepository
import ru.tcynik.klitch.domain.chat.usecase.ClearChatHistoryUseCase
import ru.tcynik.klitch.domain.chat.usecase.IngestReceivedChatMessagesUseCase
import ru.tcynik.klitch.domain.chat.usecase.MarkChatAsReadUseCase
import ru.tcynik.klitch.domain.chat.usecase.ObserveChatContactsUseCase
import ru.tcynik.klitch.domain.chat.usecase.ObserveChatMessagesUseCase
import ru.tcynik.klitch.domain.chat.usecase.ObserveTotalUnreadChatCountUseCase
import ru.tcynik.klitch.domain.chat.usecase.SendChatMessageUseCase
import ru.tcynik.klitch.domain.chat.usecase.ToggleChatArchivedUseCase
import ru.tcynik.klitch.domain.chat.usecase.ToggleChatFavoriteUseCase
import ru.tcynik.klitch.domain.chat.usecase.SyncEmergencyMuteUseCase
import ru.tcynik.klitch.domain.chat.usecase.ToggleChatPinnedUseCase

val chatDataModule = module {

    // DataStore
    single<DataStore<Preferences>>(named("ChatDataStore")) {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { androidContext().preferencesDataStoreFile("chat_ds") },
        )
    }

    // Prefs
    single { ChatPrefsRepository(get(named("ChatDataStore"))) }

    // Adapter (только он импортирует mesh.model.*)
    single {
        MeshToChatAdapter(
            packetRepository    = get(),
            nodeRepository      = get(),
            commandSender       = get(),
            channelRepository   = get(),
            channelSlotResolver = get(),
        )
    }

    // Message repository (SQLDelight)
    single<ChatMessageRepository> { ChatMessageRepositoryImpl(queries = get()) }

    // Chat repository
    single<ChatRepository> {
        ChatRepositoryImpl(
            adapter = get(),
            chatMessageRepository = get(),
        )
    }

    // Use Cases
    single { ObserveChatContactsUseCase(get()) }
    single { ObserveChatMessagesUseCase(get()) }
    single { SendChatMessageUseCase(get()) }
    single { ToggleChatFavoriteUseCase(get()) }
    single { ToggleChatArchivedUseCase(get()) }
    single { ToggleChatPinnedUseCase(get()) }
    single { ClearChatHistoryUseCase(get()) }
    single { MarkChatAsReadUseCase(get()) }
    single { ObserveTotalUnreadChatCountUseCase(get()) }
    single { SyncEmergencyMuteUseCase(get()) }
    single {
        IngestReceivedChatMessagesUseCase(
            adapter = get(),
            channelRepository = get(),
            chatMessageRepository = get(),
            channelSlotResolver = get(),
            resolveContourFromSlot = get(),
            applyDeliveryPolicy = get(),
            logger = get(),
        )
    }
}
