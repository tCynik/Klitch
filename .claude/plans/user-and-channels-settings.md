# Plan: User & Channels Settings (User Tab)

**Date**: 2026-04-20
**Status**: Done

## Goal

Реализовать вкладку **Пользователь** на экране настроек.
Вкладка даёт доступ к двум группам настроек:

1. **Профиль пользователя** — позывной (`displayName`) и короткое имя (`shortName`), реализующие `AppUser` из спека [channels-and-identity.md](../specs/channels-and-identity.md).
2. **Логические каналы** — список настроенных `LogicalChannel`, создание, редактирование, удаление.

Подход: **UI-first**. Сначала domain-модели + fake impl + UI, затем реальное хранилище.

---

## UI: структура вкладки Пользователь

```
UserTabContent
├── Section header: "Профиль"
│   ├── OutlinedTextField: Позывной (displayName)
│   │   shortName — атрибут ноды, не пользователя; настраивается в Node Settings
│   └── Button: Сохранить (активен при наличии несохранённых изменений)
│
└── Section header: "Каналы"
    ├── LazyColumn: список ChannelCard
    │   └── ChannelCard
    │       ├── Название канала
    │       ├── Badge: "MT · слот N" (для MeshtasticBinding)
    │       └── IconButton MoreVert → DropdownMenu
    │           ├── Редактировать → открыть ChannelEditorSheet
    │           └── Удалить → подтверждение AlertDialog
    └── Button: + Добавить канал → открыть ChannelEditorSheet (новый)
```

### ChannelEditorSheet (BottomSheet)

```
├── OutlinedTextField: Название
├── Section: Meshtastic
│   ├── Dropdown: Слот (0–7)
│   └── OutlinedTextField: PSK (base64)
│       └── TrailingIcon: "Сгенерировать" → random PSK
└── Row кнопок: Отмена / Сохранить
```

---

## Архитектура

### Отдельный ViewModel для вкладки

`UserTabContent` получает `UserSettingsViewModel` через `koinViewModel()` — изолировано от `SettingsViewModel`, который управляет другими вкладками.

### Domain модели (новые файлы)

```
domain/channel/model/
├── LogicalChannelId.kt         — @JvmInline value class (String — хэш credentials)
├── ChannelMetadata.kt          — data class (name: String, + поля расширяются)
├── TransportBinding.kt         — sealed interface
├── MeshtasticBinding.kt        — data class : TransportBinding (channelIndex, psk: ByteArray)
└── LogicalChannel.kt           — data class (id, metadata, transports)

domain/user/model/
└── AppUser.kt                  — data class (displayName: String)
                                  shortName — атрибут ноды, не пользователя; живёт в Node Settings
```

### Domain репозитории (интерфейсы)

```
domain/channel/repository/
└── LogicalChannelRepository.kt
        observeChannels(): Flow<List<LogicalChannel>>
        saveChannel(channel: LogicalChannel)
        deleteChannel(id: LogicalChannelId)

domain/user/repository/
└── AppUserRepository.kt
        observeUser(): Flow<AppUser>
        saveUser(user: AppUser)
```

### Use cases

```
domain/channel/usecase/
├── ObserveLogicalChannelsUseCase.kt
├── SaveLogicalChannelUseCase.kt
└── DeleteLogicalChannelUseCase.kt

domain/user/usecase/
├── ObserveAppUserUseCase.kt
└── SaveAppUserUseCase.kt
```

### Presentation модели

```
presentation/feature/settings/models/
├── ChannelItem.kt          — UI-представление канала (id, name, transportLabel)
└── UserProfileDraft.kt     — черновик редактирования (displayName, shortName)
```

### UiState

`UserSettingsUiState` добавляется в отдельный файл:

```kotlin
data class UserSettingsUiState(
    val displayName: String = "",
    val hasUnsavedUserChanges: Boolean = false,
    val channels: List<ChannelItem> = emptyList(),
    val editorSheet: ChannelEditorState? = null,   // null = закрыт
    val deleteConfirmId: LogicalChannelId? = null,
)

data class ChannelEditorState(
    val id: LogicalChannelId?,                     // null = новый канал
    val name: String,
    val slotIndex: Int,
    val pskBase64: String,
)
```

---

## Порядок реализации

### Шаг 1 — Domain модели

Создать файлы из раздела "Domain модели" выше. Чистый Kotlin, без Android-зависимостей.

### Шаг 2 — Repository интерфейсы + Use Cases

Создать интерфейсы и use cases. Пока без impl.

### Шаг 3 — Fake in-memory реализации

```
data/channel/repository/FakeLogicalChannelRepository.kt   — MutableStateFlow<List<LogicalChannel>>
data/user/repository/FakeAppUserRepository.kt             — MutableStateFlow<AppUser>
```

Зарегистрировать в DI (заменяется на реальные impl позже без изменения ViewModel).

### Шаг 4 — UserSettingsViewModel

```
presentation/feature/settings/UserSettingsViewModel.kt
```

Подключить use cases, строить `UserSettingsUiState`. Методы:
- `onDisplayNameChange(String)` → `hasUnsavedUserChanges = true`
- `onSaveUser()`
- `onAddChannelClick()` → открыть пустой `ChannelEditorState`
- `onEditChannelClick(id)` → открыть `ChannelEditorState` с данными канала
- `onDeleteChannelRequest(id)` / `onConfirmDelete()` / `onDismissDelete()`
- `onEditorNameChange(String)` / `onEditorSlotChange(Int)` / `onEditorPskChange(String)`
- `onEditorGeneratePsk()` → `SecureRandom` → base64
- `onEditorSave()` → вычислить `LogicalChannelId` = хэш PSK, вызвать `SaveLogicalChannelUseCase`
- `onEditorDismiss()`

### Шаг 5 — UI: UserTabContent

```
presentation/feature/settings/UserTabContent.kt
```

Реализовать composable согласно структуре из раздела "UI" выше.
Подключить в `SettingsScreen.kt`:
```kotlin
SettingsTab.User -> UserTabContent()
```

### Шаг 6 — Реальные репозитории (хранилище)

После того как UI протестирован с fake impl:

- `AppUserRepository` → **DataStore** (простые строки, нет смысла в SQLDelight)
- `LogicalChannelRepository` → **SQLDelight** (таблица `logical_channel`)

Схема SQLDelight:
```sql
CREATE TABLE logical_channel (
    id              TEXT NOT NULL PRIMARY KEY,  -- хэш credentials
    name            TEXT NOT NULL,
    meshtastic_slot INTEGER,                    -- NULL если нет MT-байндинга
    meshtastic_psk  BLOB                        -- NULL если нет MT-байндинга
);
```

Заменить Fake impl на реальные в DI. ViewModel не меняется.

### Шаг 7 — Валидация PSK

`MeshtasticBinding.psk` должен быть валидным base64, длина 16 или 32 байта (стандарт Meshtastic).
Добавить inline-валидацию в `ChannelEditorSheet`: подсветить поле красным если невалидно, заблокировать кнопку Сохранить.

---

## Definition of Done

- [x] Профиль пользователя сохраняется и восстанавливается после перезапуска
- [x] Список каналов отображается, поддерживает добавление / редактирование / удаление
- [x] PSK генерируется случайно или вводится вручную; невалидный PSK блокирует сохранение
- [x] `LogicalChannelId` — при создании нового канала временно `UUID.randomUUID()` (TODO: хэш PSK)
- [x] DI использует реальные impl (DataStore + SQLDelight)

---

## Открытые вопросы

- Хэш для `LogicalChannelId`: `hash(psk)` или `hash(name + psk)` — решить при реализации Шага 6.
  Для Шага 3–5 можно временно использовать `UUID.randomUUID()`.

## Change Log

- 2026-04-20: создан
