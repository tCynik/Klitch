package ru.tcynik.meshtactics.presentation.feature.chat

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import ru.tcynik.meshtactics.R
import ru.tcynik.meshtactics.domain.chat.model.ChatMessageModel
import ru.tcynik.meshtactics.presentation.feature.chat.model.ChatFilterItem
import ru.tcynik.meshtactics.presentation.feature.chat.model.ChatTab
import ru.tcynik.meshtactics.presentation.feature.chat.model.ChatType
import java.text.SimpleDateFormat
import java.util.*

private fun findItemById(id: String?, items: List<ChatFilterItem>): ChatFilterItem? {
    if (id == null) return null
    for (item in items) {
        if (item.id == id) return item
        val found = findItemById(id, item.children)
        if (found != null) return found
    }
    return null
}

private fun collectUnreadAll(items: List<ChatFilterItem>): Int {
    var total = 0
    fun traverse(list: List<ChatFilterItem>) {
        list.forEach { item ->
            total += item.unreadCount
            if (item.isArchiveSection) traverse(item.children)
        }
    }
    traverse(items)
    return total
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = koinViewModel(),
) {
    val view = LocalView.current
    val pagerState = rememberPagerState(
        initialPage = uiState.currentTab.ordinal,
        pageCount = { 2 }
    )

    // Синхронизация pager state с ViewModel
    LaunchedEffect(pagerState.currentPage) {
        val tab = if (pagerState.currentPage == 0) ChatTab.FILTER else ChatTab.CHAT
        if (uiState.currentTab != tab) {
            viewModel.switchTab(tab)
        }
    }

    LaunchedEffect(uiState.currentTab) {
        if (pagerState.currentPage != uiState.currentTab.ordinal) {
            pagerState.animateScrollToPage(uiState.currentTab.ordinal)
        }
        // Скрываем клавиатуру при переходе на вкладку "Фильтр"
        if (uiState.currentTab == ChatTab.FILTER) {
            val imm = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.chat_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back_description)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            TabRow(selectedTabIndex = uiState.currentTab.ordinal) {
                Tab(
                    selected = uiState.currentTab == ChatTab.FILTER,
                    onClick = { viewModel.switchTab(ChatTab.FILTER) },
                    text = { Text(stringResource(R.string.chat_tab_filter)) },
                )
                Tab(
                    selected = uiState.currentTab == ChatTab.CHAT,
                    onClick = { viewModel.switchTab(ChatTab.CHAT) },
                    text = {
                        val totalUnread = if (uiState.selectedChatId != null) {
                            findItemById(uiState.selectedChatId, uiState.filterItems)?.unreadCount ?: 0
                        } else {
                            collectUnreadAll(uiState.filterItems)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = uiState.chatTabTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (totalUnread > 0) {
                                Badge(
                                    modifier = Modifier.padding(start = 4.dp)
                                ) { Text(totalUnread.toString()) }
                            }
                        }
                    },
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> FilterTabContent(
                        filterItems = uiState.filterItems,
                        allSelected = uiState.filterItems.filter { !it.isArchiveSection }.isNotEmpty() &&
                                uiState.filterItems.filter { !it.isArchiveSection }.all { it.isChecked },
                        onToggleSelectAll = {
                            val nonArchive = uiState.filterItems.filter { !it.isArchiveSection }
                            if (nonArchive.all { it.isChecked }) {
                                viewModel.deselectAllItems()
                            } else {
                                viewModel.selectAllItems()
                            }
                        },
                        onToggleItem = { viewModel.toggleFilterItem(it) },
                        onSelectFavorite = { viewModel.selectFavoriteItems() },
                        onToggleArchiveSection = { viewModel.toggleArchiveSection() },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onTogglePinned = { viewModel.togglePinned(it) },
                        onMarkAsRead = { viewModel.markAsRead(it) },
                        onMoveToArchive = { viewModel.moveToArchive(it) },
                        onMoveFromArchive = { viewModel.moveFromArchive(it) },
                        onClearChat = { viewModel.clearChat(it) },
                        onChatClick = { viewModel.selectChat(it) }
                    )

                    1 -> ChatTabContent(
                        messages = uiState.messages,
                        searchQuery = uiState.searchQuery,
                        inputText = uiState.inputText,
                        onSearchChanged = { viewModel.updateSearchQuery(it) },
                        onInputChanged = { viewModel.updateInputText(it) },
                        onSend = { viewModel.sendMessage() }
                    )
                }
            }
        }
    }
}

// ==================== ВКЛАДКА ФИЛЬТР ====================

@Composable
private fun FilterTabContent(
    filterItems: List<ChatFilterItem>,
    allSelected: Boolean,
    onToggleSelectAll: () -> Unit,
    onToggleItem: (String) -> Unit,
    onSelectFavorite: () -> Unit,
    onToggleArchiveSection: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTogglePinned: (String) -> Unit,
    onMarkAsRead: (String) -> Unit,
    onMoveToArchive: (String) -> Unit,
    onMoveFromArchive: (String) -> Unit,
    onClearChat: (String) -> Unit,
    onChatClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Кнопки фильтрации
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterButton(
                stringResource(if (allSelected) R.string.chat_deselect_all else R.string.chat_select_all),
                onClick = onToggleSelectAll
            )
            FilterButton(stringResource(R.string.chat_select_favorite), onClick = onSelectFavorite)
        }

        // Список чатов
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(filterItems, key = { it.id }) { item ->
                if (item.isArchiveSection && item.children.isEmpty()) return@items
                if (item.isArchiveSection) {
                    // Секция «Архив»
                    ArchiveSectionItem(
                        item = item,
                        onToggleExpand = onToggleArchiveSection,
                        onToggleFavorite = { onToggleFavorite(it) },
                        onTogglePinned = { onTogglePinned(it) },
                        onMarkAsRead = { onMarkAsRead(it) },
                        onMoveToArchive = { onMoveToArchive(it) },
                        onClearChat = { onClearChat(it) },
                        onChatClick = { onChatClick(it) },
                        onMoveFromArchive = onMoveFromArchive
                    )
                } else {
                    // Обычный айтем
                    ChatFilterItemRow(
                        item = item,
                        onToggleCheck = { onToggleItem(item.id) },
                        onToggleFavorite = { onToggleFavorite(item.id) },
                        onTogglePinned = { onTogglePinned(item.id) },
                        onMarkAsRead = { onMarkAsRead(item.id) },
                        onMoveToArchive = { onMoveToArchive(item.id) },
                        onClearChat = { onClearChat(item.id) },
                        onChatClick = { onChatClick(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.FilterButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ==================== СЕКЦИЯ АРХИВА ====================

@Composable
private fun ArchiveSectionItem(
    item: ChatFilterItem,
    onToggleExpand: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onTogglePinned: (String) -> Unit,
    onMarkAsRead: (String) -> Unit,
    onMoveToArchive: (String) -> Unit,
    onMoveFromArchive: (String) -> Unit,
    onClearChat: (String) -> Unit,
    onChatClick: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Заголовок секции
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Плюс / минус
            Text(
                text = if (item.isExpanded) "−" else "+",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp, end = 4.dp)
            )
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Дочерние элементы (если раскрыто)
        if (item.isExpanded) {
            Column(
                modifier = Modifier.padding(start = 24.dp)
            ) {
                item.children.forEach { child ->
                    ArchiveItemRow(
                        item = child,
                        onToggleFavorite = { onToggleFavorite(child.id) },
                        onTogglePinned = { onTogglePinned(child.id) },
                        onMarkAsRead = { onMarkAsRead(child.id) },
                        onMoveToArchive = { onMoveToArchive(child.id) },
                        onMoveFromArchive = { onMoveFromArchive(child.id) },
                        onClearChat = { onClearChat(child.id) },
                        onChatClick = { onChatClick(child.id) }
                    )
                }
            }
        }
    }
}

// ==================== АЙТЕМ АРХИВА (без чекбокса) ====================

@Composable
private fun ArchiveItemRow(
    item: ChatFilterItem,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onMarkAsRead: () -> Unit,
    onMoveToArchive: () -> Unit,
    onMoveFromArchive: () -> Unit,
    onClearChat: () -> Unit,
    onChatClick: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChatClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Жирная точка избранного
        if (item.isFavorite) {
            Text(
                text = "●",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 2.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }

        // Индикатор типа
        if (item.isPinned) {
            Text("📌 ", fontSize = 12.sp)
        }

        // Название и превью
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = item.name,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.lastMessagePreview.isNotEmpty()) {
                Text(
                    text = item.lastMessagePreview,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Непрочитанные
        if (item.unreadCount > 0) {
            Badge(modifier = Modifier.padding(end = 4.dp)) {
                Text(item.unreadCount.toString())
            }
        }

        // Время
        Text(
            text = formatTime(item.lastMessageTime),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 2.dp)
        )

        // Троеточие меню
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_menu_read)) },
                    onClick = {
                        onMarkAsRead()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_menu_unarchive)) },
                    onClick = {
                        onMoveFromArchive()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_menu_clear)) },
                    onClick = {
                        showMenu = false
                        showClearDialog = true
                    }
                )
            }
        }
    }

    // Диалог подтверждения очистки
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.chat_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearChat()
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.chat_clear_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            }
        )
    }
}

// ==================== АЙТЕМ ФИЛЬТРА ====================

@Composable
private fun ChatFilterItemRow(
    item: ChatFilterItem,
    onToggleCheck: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onMarkAsRead: () -> Unit,
    onMoveToArchive: () -> Unit,
    onClearChat: () -> Unit,
    onChatClick: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChatClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Чекбокс
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { onToggleCheck() }
        )

        // Жирная точка избранного
        if (item.isFavorite) {
            Text(
                text = "●",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 2.dp)
            )
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }

        // Индикатор типа
        if (item.isPinned) {
            Text("📌 ", fontSize = 12.sp)
        }

        // Название и превью
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = item.name,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (item.lastMessagePreview.isNotEmpty()) {
                Text(
                    text = item.lastMessagePreview,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Непрочитанные
        if (item.unreadCount > 0) {
            Badge(
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Text(item.unreadCount.toString())
            }
        }

        // Время
        Text(
            text = formatTime(item.lastMessageTime),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 2.dp)
        )

        // Троеточие меню
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = null)
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (item.isPinned) R.string.chat_menu_unpin else R.string.chat_menu_pin)) },
                    onClick = {
                        onTogglePinned()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(if (item.isFavorite) R.string.chat_menu_unfavorite else R.string.chat_menu_favorite)) },
                    onClick = {
                        onToggleFavorite()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_menu_read)) },
                    onClick = {
                        onMarkAsRead()
                        showMenu = false
                    }
                )
                if (!item.isArchiveSection) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.chat_menu_archive)) },
                        onClick = {
                            onMoveToArchive()
                            showMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_menu_clear)) },
                    onClick = {
                        showMenu = false
                        showClearDialog = true
                    }
                )
            }
        }
    }

    // Диалог подтверждения очистки
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.chat_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    onClearChat()
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.chat_clear_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.chat_cancel))
                }
            }
        )
    }
}

// ==================== ВКЛАДКА ЧАТ ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTabContent(
    messages: List<ChatMessageModel>,
    searchQuery: String,
    inputText: String,
    onSearchChanged: (String) -> Unit,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        // Строка поиска
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            placeholder = { Text(stringResource(R.string.chat_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            )
        )

        // Список сообщений
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                ChatMessageBubble(message = message)
            }
        }

        // Строка ввода (снизу)
        ChatInputBar(
            inputText = inputText,
            onInputChanged = onInputChanged,
            onSend = onSend
        )

        // Прокрутка вниз при открытии чата (мгновенно)
        var isFirstScroll by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            if (messages.isNotEmpty()) {
                listState.scrollToItem(messages.lastIndex)
            }
        }
        // Прокрутка вниз при новых сообщениях (с анимацией)
        LaunchedEffect(messages.size) {
            if (!isFirstScroll && messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }
            isFirstScroll = false
        }
        // Прокрутка вниз при открытии клавиатуры
        val density = LocalDensity.current
        val imeBottom = WindowInsets.ime.getBottom(density)
        LaunchedEffect(imeBottom) {
            if (imeBottom > 0 && messages.isNotEmpty()) {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }
}

// ==================== ПУЗЫРЬ СООБЩЕНИЯ ====================

@Composable
private fun ChatMessageBubble(
    message: ChatMessageModel,
) {
    val isMe = message.isFromMe

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isMe) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(10.dp)
        ) {
            // Имя отправителя
            Text(
                text = message.senderCallsign,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            // Текст сообщения
            Text(
                text = message.text,
                fontSize = 14.sp,
                color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Время
            Text(
                text = formatTime(message.sentAt),
                fontSize = 10.sp,
                color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

// ==================== СТРОКА ВВОДА ====================

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChanged: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_input_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { onSend() }
                ),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onSend,
                enabled = inputText.trim().isNotEmpty(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send)
                )
            }
        }
    }
}

// ==================== УТИЛИТЫ ====================

private fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
