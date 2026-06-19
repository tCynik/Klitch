package ru.tcynik.klitch.presentation.feature.chat

import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import ru.tcynik.klitch.data.chat.prefs.ChatPrefsRepository
import ru.tcynik.klitch.domain.channel.repository.ContourSyncStateRepository
import ru.tcynik.klitch.domain.chat.usecase.ClearChatHistoryUseCase
import ru.tcynik.klitch.domain.chat.usecase.MarkChatAsReadUseCase
import ru.tcynik.klitch.domain.chat.usecase.ObserveChatContactsUseCase
import ru.tcynik.klitch.domain.chat.usecase.ObserveChatMessagesUseCase
import ru.tcynik.klitch.domain.chat.usecase.SendChatMessageParams
import ru.tcynik.klitch.domain.chat.usecase.SendChatMessageUseCase
import ru.tcynik.klitch.domain.chat.usecase.ToggleChatArchivedUseCase
import ru.tcynik.klitch.domain.chat.usecase.ToggleChatFavoriteUseCase
import ru.tcynik.klitch.domain.chat.usecase.ToggleChatPinnedUseCase
import ru.tcynik.klitch.presentation.feature.chat.model.ChatTab

/** Covers Phase 4 (node-gps-position-source plan) sync-required gating: chat must stop sending while syncRequired=true. */
class ChatViewModelSyncGateTest {

    private val observeContactsUseCase: ObserveChatContactsUseCase = mockk()
    private val observeMessagesUseCase: ObserveChatMessagesUseCase = mockk()
    private val sendMessageUseCase: SendChatMessageUseCase = mockk(relaxed = true)
    private val toggleFavoriteUseCase: ToggleChatFavoriteUseCase = mockk(relaxed = true)
    private val toggleArchivedUseCase: ToggleChatArchivedUseCase = mockk(relaxed = true)
    private val togglePinnedUseCase: ToggleChatPinnedUseCase = mockk(relaxed = true)
    private val clearHistoryUseCase: ClearChatHistoryUseCase = mockk(relaxed = true)
    private val markAsReadUseCase: MarkChatAsReadUseCase = mockk(relaxed = true)
    private val chatPrefs: ChatPrefsRepository = mockk(relaxed = true)
    private val syncStateRepository: ContourSyncStateRepository = mockk()

    private val syncRequiredFlow = MutableStateFlow(false)
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { observeContactsUseCase.invoke(any()) } returns flowOf(emptyList())
        every { observeMessagesUseCase.invoke(any()) } returns flowOf(emptyList())
        every { chatPrefs.observeCheckedIds() } returns flowOf(emptySet())
        every { chatPrefs.observeCurrentTab() } returns flowOf(ChatTab.FILTER)
        every { chatPrefs.observeSelectedChatId() } returns flowOf(null)
        every { syncStateRepository.syncRequired } returns syncRequiredFlow

        viewModel = ChatViewModel(
            observeContactsUseCase = observeContactsUseCase,
            observeMessagesUseCase = observeMessagesUseCase,
            sendMessageUseCase = sendMessageUseCase,
            toggleFavoriteUseCase = toggleFavoriteUseCase,
            toggleArchivedUseCase = toggleArchivedUseCase,
            togglePinnedUseCase = togglePinnedUseCase,
            clearHistoryUseCase = clearHistoryUseCase,
            markAsReadUseCase = markAsReadUseCase,
            chatPrefs = chatPrefs,
            syncStateRepository = syncStateRepository,
        )
        viewModel.selectChat("contact1")
        viewModel.updateInputText("hello")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `syncRequired true — sendMessage is dropped`() = runTest(testDispatcher) {
        syncRequiredFlow.value = true

        viewModel.sendMessage()

        coVerify(exactly = 0) { sendMessageUseCase.invoke(any()) }
    }

    @Test
    fun `syncRequired false — sendMessage is delivered`() = runTest(testDispatcher) {
        viewModel.sendMessage()

        coVerify(exactly = 1) {
            sendMessageUseCase.invoke(SendChatMessageParams(text = "hello", contactId = "contact1"))
        }
    }

    @Test
    fun `syncRequired toggling true clears the input gate but keeps inputText untouched`() = runTest(testDispatcher) {
        syncRequiredFlow.value = true
        viewModel.sendMessage()

        assertEquals("hello", viewModel.uiState.value.inputText)
    }
}
