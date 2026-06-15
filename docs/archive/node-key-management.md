# Node PKC Key Management — план реализации

**Статус**: Done  
**Приоритет**: Medium (блокирует диагностику DM-чата)

| Фаза | Статус |
|---|---|
| Фаза 0 — Автоматическое восстановление ключей при connect | ✅ Done |
| Фаза 1 — Domain: ObserveNodeSecurityConfigUseCase | ✅ Done |
| Фаза 2 — Domain: RegeneratePkcKeysUseCase | ✅ Done (реализован в рамках Фазы 0) |
| Фаза 3 — UI: NodeSettingsScreen | ⬜ Next |
| Фаза 4 — PKC-статус контакта в ChatScreen | ✅ Done |

---

## Контекст

DM-чат не работает: firmware не расшифровывает входящие пакеты (`decoded=null`).

Диагностические логи показали:
```
DBG doSend: to=!9e9f2690 channel=0 contactKey=0!9e9f2690
DBG fromRadio: portnum=null from=... pki=false
```

`channel=0` означает: `buildPrivateCandidates` выбрал PSK-путь, не PKC. Причина — `hasPKC=false` у партнёра в нашем NodeDB. Пакет уходит как PSK ch.0, а если PSK не совпадает — `decoded=null` на приёме.

Три независимых кейса:
- **Кейс A** — наш `private_key` отсутствует/сломан → `hasPKC=false` у нас → DM через PSK
- **Кейс B** — `hasPKC=false` у партнёра в нашем NodeDB (их ключ не дошёл или был отклонён) → `buildPrivateCandidates` выбирает `"0!nodeId"` → DM через PSK ch.0
- **Кейс C** — наш NodeDB содержит **устаревший** `public_key` партнёра → ECDH на неверном ключе → decrypt fail

Кейс B — наиболее вероятный в текущей диагностике. PSK-мисматч на ch.0 = партнёр не имеет нашего ключа и мы не имеем его.

---

## Что уже есть (не нужно реализовывать)

- `SecurityConfig` уже сохраняется в DataStore при connect:  
  `handleDeviceConfig(Config(security=...))` → `LocalConfigDataSource.setLocalConfig()` → кеш
- `RadioConfigRepository.localConfigFlow: Flow<LocalConfig>` — содержит `security` поле
- `CommandSender.requestUserInfo(destNum: Int)` — двусторонний обмен: отправляем свой `User`, получаем ответный `User` партнёра → NodeDB обновляется автоматически
- `nodeRepository.nodeDBbyNum: StateFlow<Map<Int, Node>>` — все известные ноды
- `MeshActionHandlerImpl.handleSetConfig(ByteArray, Int)` → `AdminMessage(set_config = ...)` — работает
- `UserSettingsViewModel.onConnected()` — вызывается при каждом reconnect, точка интеграции

---

## Ограничения протокола

- `private_key` никогда не отображается в UI
- Регенерация: `SecurityConfig(private_key = EMPTY)` → firmware генерирует новую пару → reboot
- После регенерации firmware делает `User` broadcast → все соседи получают новый `public_key` автоматически
- `requestUserInfo()`: двусторонний — отправляем свой `User`, получаем ответный `User` партнёра

### Двухшаговое принятие ключа (анти-MITM механизм)

`NodeManagerImpl.handleReceivedUser()` содержит защиту:
```kotlin
val keyMatch = !node.hasPKC || node.user.public_key == p.public_key
val newUser = if (keyMatch) p else p.copy(public_key = ByteString.EMPTY)
```

Если нода уже имеет stored key И пришёл **другой** key → **REJECT**: сохраняется `EMPTY`.  
После REJECT: `hasPKC = false` → следующий User от той же ноды → `!hasPKC = true` → **ACCEPT**.

**Вывод**: обновление стороннего ключа требует двух прохождений:
1. Первый `requestUserInfo` → если ключ изменился → reject → stored = EMPTY
2. Второй `requestUserInfo` (или очередной firmware broadcast) → принят ✓

Если ключ не изменился (тот же) → одного прохождения достаточно.

### Где хранится привязка ключа к ноде

Room `nodes` table, PK = `nodeNum`:
- `user` (BLOB) — `User.public_key` — из NodeInfo broadcast
- `public_key` (отдельный столбец) — приоритетный, для верифицированных ключей

`Node.hasPKC = (publicKey ?: user.public_key).size > 0` — проверяет оба источника.  
Получатель PKC DM **не нуждается в NodeDB** для расшифровки: `MeshPacket.public_key` отправителя приходит прямо в пакете. Если `decoded=null` на получателе → его `private_key` сломан.

---

## Фаза 0 — Автоматическое восстановление ключей при connect

**Приоритет: High.** Не требует UI. Решает оба кейса без участия пользователя.

### 0a. `MeshConfigRepository` — 2 новых метода

```kotlin
// Проверить: наш public_key отсутствует или ERROR (32 нуля)
fun isOwnPkcKeyBroken(): Boolean

// Запросить свежий User от всех известных нод (обновляет их public_key в NodeDB)
fun refreshKnownNodePublicKeys()
```

### 0b. `MeshConfigRepositoryImpl` — реализация

```kotlin
private val ERROR_PUBLIC_KEY = ByteArray(32) { 0 }.toByteString()

override fun isOwnPkcKeyBroken(): Boolean {
    val sec = meshRouter.configHandler.localConfig.value.security ?: return true
    return sec.public_key.size == 0 || sec.public_key == ERROR_PUBLIC_KEY
}

override fun refreshKnownNodePublicKeys() {
    val myNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
    val targets = nodeRepository.nodeDBbyNum.value.values
        .filter { it.num != myNum && it.lastHeard > 0 }
    targets.forEach { node -> commandSender.requestUserInfo(node.num) }
}
```

> **Двухшаговое принятие**: если у партнёра изменился ключ, первый `requestUserInfo` → reject → stored=EMPTY.  
> Второй вызов принимает новый ключ. `onConnected()` вызывает метод дважды с задержкой (см. 0d).  
> Если ключ не изменился — первого прохода достаточно.

### 0c. Новые use cases

```
domain/mesh/usecase/
    CheckOwnPkcHealthUseCase.kt    → repository.isOwnPkcKeyBroken(): Boolean
    RefreshNodePublicKeysUseCase.kt → repository.refreshKnownNodePublicKeys()
```

```kotlin
class CheckOwnPkcHealthUseCase(private val repository: MeshConfigRepository) {
    operator fun invoke(): Boolean = repository.isOwnPkcKeyBroken()
}

class RefreshNodePublicKeysUseCase(private val repository: MeshConfigRepository) {
    operator fun invoke() = repository.refreshKnownNodePublicKeys()
}
```

### 0d. Интеграция в `UserSettingsViewModel`

Добавить в конструктор:
- `CheckOwnPkcHealthUseCase`
- `RefreshNodePublicKeysUseCase`
- `RegeneratePkcKeysUseCase` ← из Фазы 2, нужен здесь тоже

Добавить приватное поле:
```kotlin
private var needsPkcRegen: Boolean = false
```

Расширить `onConnected()`:
```kotlin
private fun onConnected(contours: List<Contour>) {
    viewModelScope.launch {
        // существующая логика GPS broadcast...
        val emergencyActive = contours.find { it.isEmergency }?.isActive ?: false
        val broadcastEnabled = observeGpsBroadcastEnabled().first()
        if (emergencyActive || !broadcastEnabled) disableNodePositionBroadcast()
        else enableNodePositionBroadcastReady()

        // NEW: проверить собственный ключ
        needsPkcRegen = checkOwnPkcHealth()

        // NEW: первый проход — если ключ партнёра изменился: reject → stored=EMPTY
        refreshNodePublicKeys()
        // NEW: второй проход через 30с — принять новый ключ после reject
        // (если ключ не изменился, второй проход идемпотентен)
        delay(30_000)
        refreshNodePublicKeys()
    }
}
```

### 0e. Fold в channel sync reboot

В `onConfirmChannelSync()` — добавить регенерацию перед reboot:

```kotlin
fun onConfirmChannelSync() {
    _uiState.update { it.copy(showSyncDialog = false) }
    viewModelScope.launch {
        if (needsPkcRegen) {
            regeneratePkcKeys()   // отправить set_config до reboot
            needsPkcRegen = false
        }
        syncContoursOnConnect()
        rebootStateRepository.setRebooting(true)
        rebootNode()              // один reboot: и каналы, и новая PKC пара
        syncStateRepository.clear()
    }
}
```

**Что происходит после reboot:**
1. Firmware применяет новые channel settings
2. Firmware генерирует новую PKC пару (если `private_key` был пуст)
3. Firmware делает NodeInfo broadcast с `public_key=newKey`
4. Все соседи обновляют NodeDB → DM работает

### 0f. Если ключ сломан, но sync не нужен

В этом случае `onConfirmChannelSync()` не вызывается. Пользователь должен вручную триггернуть регенерацию через NodeSettingsScreen (Фаза 3). Баннер о сломанном ключе — в NodeSettingsScreen (показывается через `hasKey = false` в `NodeSettingsUiState`).

---

### 0g. Callsign-triggered key management

**Принцип**: смена позывного = смена оператора/конфигурации = практически гарантированная смена ключей.

#### Кейс 1 — Мы меняем свой позывной

В `UserSettingsViewModel.onSaveAndReboot()` — добавить `regeneratePkcKeys()` перед reboot:

```kotlin
fun onSaveAndReboot() {
    _uiState.update { it.copy(showLeaveDialog = false) }
    viewModelScope.launch {
        val shortName = withTimeoutOrNull(5_000) {
            observeDeviceConfig(NoParams).first { it != null }
        }?.shortName ?: ""
        writeOwner(_uiState.value.displayName, shortName)
        regeneratePkcKeys()          // NEW: новый позывной = новая крипто-идентичность
        saveAppUser(AppUser(displayName = _uiState.value.displayName))
        _uiState.update { it.copy(hasUnsavedUserChanges = false) }
        rebootStateRepository.setRebooting(true)
        rebootNode()                 // один reboot: и позывной, и новая PKC пара
        _navigateBack.tryEmit(Unit)
    }
}
```

Reboot уже есть → fold бесплатный. После reboot firmware рассылает новый `User(public_key=newKey)` → все соседи автоматически получают обновлённый ключ.

#### Кейс 2 — Известная нода сменила позывной

Смена позывного = сигнал что ключи устарели. Нужно пересмотреть привязку ключа: запросить свежий `User` дважды (двухшаговое принятие нового ключа).

**Новый метод `MeshConfigRepository`:**
```kotlin
fun refreshNodePublicKey(nodeNum: Int)
```

**`MeshConfigRepositoryImpl`:**
```kotlin
override fun refreshNodePublicKey(nodeNum: Int) {
    commandSender.requestUserInfo(nodeNum)
}
```

**Новый use case `ObserveCallsignChangesUseCase`:**
```kotlin
class ObserveCallsignChangesUseCase(
    private val repository: MeshConfigRepository,
) : FlowUseCase<NoParams, Int>() {   // эмитит nodeNum при смене позывного
    override fun invoke(params: NoParams): Flow<Int> =
        repository.observeCallsignChanges()
}
```

**`MeshConfigRepository` — добавить метод:**
```kotlin
fun observeCallsignChanges(): Flow<Int>
```

**`MeshConfigRepositoryImpl` — реализация:**
```kotlin
override fun observeCallsignChanges(): Flow<Int> = channelFlow {
    var prevNames = emptyMap<Int, String>()
    nodeRepository.nodeDBbyNum.collect { db ->
        val currNames = db.mapValues { (_, node) -> node.user.long_name }
        currNames.forEach { (num, name) ->
            val oldName = prevNames[num]
            // только если имя было известно и реально изменилось
            if (oldName != null && oldName.isNotEmpty() && oldName != name) {
                send(num)
            }
        }
        prevNames = currNames
    }
}
```

**Интеграция в `MainViewModel`** (уже имеет множество `launchIn(viewModelScope)`):

Добавить в `init {}`:
```kotlin
observeCallsignChanges(NoParams)
    .onEach { nodeNum ->
        // первый запрос: если ключ изменился → reject → stored=EMPTY
        refreshNodePublicKey(nodeNum)
        delay(10_000)
        // второй запрос: принять новый ключ
        refreshNodePublicKey(nodeNum)
    }
    .launchIn(viewModelScope)
```

Задержка 10с между запросами: партнёр онлайн и отвечает быстро — достаточно.

**Новые зависимости MainViewModel:**
- `ObserveCallsignChangesUseCase`
- `RefreshNodePublicKeyUseCase` (обёртка над `refreshNodePublicKey(nodeNum)`)

```kotlin
class RefreshNodePublicKeyUseCase(private val repository: MeshConfigRepository) {
    operator fun invoke(nodeNum: Int) = repository.refreshNodePublicKey(nodeNum)
}
```

---

## Фаза 1 — Domain: ObserveNodeSecurityConfigUseCase

Чтение SecurityConfig для отображения в UI.

### `domain/mesh/model/NodeSecurityModel.kt`
```kotlin
data class NodeSecurityModel(
    val publicKeyHex: String,   // hex public key (32 байта → 64 символа)
    val hasKey: Boolean,        // public_key.size > 0
    val isMismatch: Boolean,    // public_key == ERROR_BYTE_STRING (32 нуля)
)
```

### `MeshConfigRepository` — добавить метод
```kotlin
fun observeSecurityConfig(): Flow<NodeSecurityModel?>
```

### `MeshConfigRepositoryImpl` — реализация
```kotlin
override fun observeSecurityConfig(): Flow<NodeSecurityModel?> =
    meshRouter.configHandler.localConfig.map { localConfig ->
        val sec = localConfig.security ?: return@map null
        NodeSecurityModel(
            publicKeyHex = sec.public_key.hex(),
            hasKey = sec.public_key.size > 0,
            isMismatch = sec.public_key == ERROR_PUBLIC_KEY,
        )
    }
```

### `domain/mesh/usecase/ObserveNodeSecurityConfigUseCase.kt`
```kotlin
class ObserveNodeSecurityConfigUseCase(
    private val repository: MeshConfigRepository,
) : FlowUseCase<NoParams, NodeSecurityModel?>() {
    override fun invoke(params: NoParams) = repository.observeSecurityConfig()
}
```

---

## Фаза 2 — Domain: RegeneratePkcKeysUseCase

### `MeshConfigRepository` — добавить метод
```kotlin
fun regeneratePkcKeys()
```

### `MeshConfigRepositoryImpl` — реализация
```kotlin
private val ERROR_PUBLIC_KEY = ByteArray(32) { 0 }.toByteString()   // объявлен в 0b

override fun regeneratePkcKeys() {
    val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
    val currentSec = meshRouter.configHandler.localConfig.value.security
        ?: Config.SecurityConfig()
    val resetSec = currentSec.copy(private_key = ByteString.EMPTY)
    val payload = Config.ADAPTER.encode(Config(security = resetSec))
    meshRouter.actionHandler.handleSetConfig(payload, myNodeNum)
}
```

> `copy(private_key = EMPTY)` сохраняет `admin_key` — firmware заменит только private/public.

### `domain/mesh/usecase/RegeneratePkcKeysUseCase.kt`
```kotlin
class RegeneratePkcKeysUseCase(private val repository: MeshConfigRepository) {
    operator fun invoke() = repository.regeneratePkcKeys()
}
```

---

## Фаза 3 — UI: NodeSettingsScreen

### `NodeSettingsUiState.kt` — расширить
```kotlin
data class NodeSettingsUiState(
    val isLoading: Boolean = false,
    val publicKeyHex: String? = null,   // null = ещё не загружен
    val hasKey: Boolean = false,
    val isMismatch: Boolean = false,    // ERROR_BYTE_STRING → показать предупреждение
    val showRegenerateDialog: Boolean = false,
    val isNodeConnected: Boolean = false,
)
```

### `NodeSettingsViewModel.kt` — расширить

Добавить в конструктор:
- `ObserveNodeSecurityConfigUseCase`
- `RegeneratePkcKeysUseCase`
- `RebootNodeUseCase`
- `ObserveConnectionStatusUseCase`

```kotlin
init {
    observeNodeSecurityConfig(NoParams)
        .onEach { model ->
            _uiState.update {
                it.copy(
                    publicKeyHex = model?.publicKeyHex,
                    hasKey = model?.hasKey ?: false,
                    isMismatch = model?.isMismatch ?: false,
                )
            }
        }.launchIn(viewModelScope)

    observeConnectionStatus(NoParams)
        .onEach { status ->
            _uiState.update { it.copy(isNodeConnected = status is MeshConnectionStatus.Connected) }
        }.launchIn(viewModelScope)
}

fun onRegenerateClick() {
    _uiState.update { it.copy(showRegenerateDialog = true) }
}

fun onRegenerateConfirm() {
    _uiState.update { it.copy(showRegenerateDialog = false) }
    regeneratePkcKeys()
    viewModelScope.launch {
        delay(300)   // дать время sendAdmin уйти в радио
        rebootNode()
    }
}

fun onRegenerateDismiss() {
    _uiState.update { it.copy(showRegenerateDialog = false) }
}
```

### `NodeSettingsScreen.kt` — реализовать

```
Scaffold (TopAppBar "Настройки ноды", back)
  LazyColumn
    ─── Секция "PKC / Безопасность" ──────────────────────────────
    ListItem: "Публичный ключ"
      trailing: первые 8 + "…" + последние 8 hex символов
               или "—" если hasKey == false
    если isMismatch:
      WarningCard: "Ключ повреждён — PKC DM не работает. Сгенерируйте новые ключи."
    иначе если !hasKey:
      WarningCard: "Ключ отсутствует — PKC DM недоступен."
    иначе:
      InfoRow: "PKC-шифрование: ✓ активно"
    Divider
    Button "Сгенерировать новые ключи PKC"
      enabled = isNodeConnected
      onClick = onRegenerateClick()
```

AlertDialog при `showRegenerateDialog`:
```
Заголовок: "Сбросить PKC-ключи?"
Текст: "Устройство сгенерирует новую пару ключей и перезагрузится.
        Контакты потеряют доверие к старому ключу — ключ обновится автоматически при reconnect."
Кнопки: [Отмена] [Подтвердить]
```

---

## Фаза 4 — PKC-статус контакта в ChatScreen

Добавить поле `partnerHasPKC: Boolean` в `ChatContact` для диагностики в UI.

**Файлы:**
- `domain/chat/model/ChatContact.kt` — добавить `partnerHasPKC: Boolean`
- `MeshToChatAdapter.buildPrivateCandidates()` — пробросить `node.hasPKC`
- `ChatTab.kt` / `ChatScreen.kt` — badge в шапке открытого PRIVATE чата:  
  `[PKC ✓]` / `[PKC ✗ — незашифровано]`

---

## DI — все новые use cases

Добавить в `MeshConfigModule` (или аналогичном):
```kotlin
single { ObserveNodeSecurityConfigUseCase(get()) }
single { RegeneratePkcKeysUseCase(get()) }
single { CheckOwnPkcHealthUseCase(get()) }
single { RefreshNodePublicKeysUseCase(get()) }
```

`UserSettingsViewModel` — добавить 3 новых параметра: `CheckOwnPkcHealthUseCase`, `RefreshNodePublicKeysUseCase`, `RegeneratePkcKeysUseCase`.  
`NodeSettingsViewModel` — добавить: `ObserveNodeSecurityConfigUseCase`, `RegeneratePkcKeysUseCase`, `RebootNodeUseCase`, `ObserveConnectionStatusUseCase`.

---

## Последовательность реализации

| # | Что | Где | Фаза | Статус |
|---|---|---|---|---|
| 1 | `ERROR_PUBLIC_KEY` + `isOwnPkcKeyBroken()` + `refreshKnownNodePublicKeys()` + `refreshNodePublicKey(nodeNum)` + `regeneratePkcKeys()` + `observeCallsignChanges()` в интерфейс и impl | `MeshConfigRepository` / `MeshConfigRepositoryImpl` | 0+2 | ✅ |
| 2 | `CheckOwnPkcHealthUseCase`, `RefreshNodePublicKeysUseCase`, `RefreshNodePublicKeyUseCase`, `RegeneratePkcKeysUseCase`, `ObserveCallsignChangesUseCase` | `domain/mesh/usecase/` | 0+2 | ✅ |
| 3 | `onConnected()` + `onConfirmChannelSync()` + `onSaveAndReboot()` расширить | `UserSettingsViewModel` | 0g кейс 1 | ✅ |
| 4 | `observeCallsignChanges` observer добавить в `init {}` | `MainViewModel` | 0g кейс 2 | ✅ |
| 5 | `NodeSecurityModel` data class + `observeSecurityConfig()` | `domain/mesh/model/` + repo | 1 | ✅ |
| 6 | `ObserveNodeSecurityConfigUseCase` | `domain/mesh/usecase/` | 1 | ✅ |
| 7 | `NodeSettingsUiState` расширить | `presentation/feature/node/` | 3 | ✅ |
| 8 | `NodeSettingsViewModel` расширить | `presentation/feature/node/` | 3 | ✅ |
| 9 | `NodeSettingsScreen` реализовать | `presentation/feature/node/` | 3 | ✅ |
| 10 | DI зарегистрировать всё новое | `di/` | — | ✅ |
| 11 | `ChatContact.partnerHasPKC` + adapter + UI | chat пакеты | 4 | ✅ |
| 12 | Обновить `user-settings-write-mechanics.md`, `channel-sync-validation.md`, `chat.md` | `.claude/docs/` | docs | ⬜ |
| 13 | Создать `node-key-management.md` + добавить строку в `CLAUDE.md` | `.claude/docs/` + `CLAUDE.md` | docs | ⬜ |

---

## Покрытие сценариев

| Сценарий | Покрыт? | Механизм |
|---|---|---|
| `hasPKC=false` у партнёра в NodeDB → DM через PSK → мисматч | ✅ | Фаза 0d: двойной `requestUserInfo()` → партнёр отдаёт свой User с public_key → `hasPKC=true` → следующий DM через PKC |
| Наш private_key отсутствует → DM через PSK | ✅ | Фаза 0e: fold regen в sync reboot |
| Наш private_key отсутствует, sync не нужен | ⚠️ | Фаза 3: вручную через NodeSettingsScreen |
| Ключ партнёра изменился (регенерировал) | ✅ | Двойной `requestUserInfo()`: 1й reject → EMPTY, 2й принят |
| Ключ партнёра изменился, контакт **оффлайн** | ❌ | Починится при следующем его broadcast онлайн |
| Наш ключ ERROR_BYTE_STRING (сломан) | ✅ | `isMismatch` в UI + `isOwnPkcKeyBroken()` → regen |
| Устаревший ключ партнёра, ключ НЕ изменился | ✅ | `requestUserInfo()` идемпотентен: те же данные → один проход достаточен |
| Мы сменили позывной → старый ключ у партнёров | ✅ | Фаза 0g кейс 1: `regeneratePkcKeys()` fold в `onSaveAndReboot()` reboot → новый `User` broadcast |
| Партнёр сменил позывной → его ключ устарел | ✅ | Фаза 0g кейс 2: `observeCallsignChanges()` → двойной `requestUserInfo()` с задержкой 10с |

---

## Обновление документации

После реализации обновить следующие документы:

### 1. `user-settings-write-mechanics.md` — обновить

**Таблица write mechanics** — добавить строку:
```
| PKC key pair | При сохранении позывного (если connected) | Да (fold в reboot позывного) | — |
```

**Leave-Dialog Flow** — схема `"Сохранить" → writeOwner + saveAppUser + rebootNode` меняется на:
```
"Сохранить" → writeOwner + regeneratePkcKeys + saveAppUser + rebootNode → выход
```

**Sync Dialog Path** — добавить абзац:
> При подтверждении sync dialog: если `isOwnPkcKeyBroken()` — перед reboot отправляется `set_config(SecurityConfig(private_key=EMPTY))`. Один reboot применяет и каналы, и новую PKC пару.

### 2. `channel-sync-validation.md` — обновить

Добавить в описание `onConfirmChannelSync()`:
> Если при подключении был обнаружен сломанный PKC ключ (`isOwnPkcKeyBroken=true`), перед reboot отправляется `regeneratePkcKeys()`. Один reboot применяет каналы + новые ключи одновременно.

### 3. `chat.md` — обновить секцию "Диагностика DM"

Заменить TODO-секцию:
- Снять `[ ]` с пункта про проверку key exchange на нодах
- Добавить раздел "Автоматическое восстановление PKC":
  > При каждом подключении приложение вызывает `requestUserInfo()` для всех известных нод дважды с задержкой 30с. Это обновляет NodeDB с актуальными public key — устраняет кейс `hasPKC=false` без участия пользователя.
  >
  > При смене позывного на ноде — `regeneratePkcKeys()` автоматически добавляется в reboot-флоу.
  >
  > При обнаружении смены позывного у партнёра — запускается двойной `requestUserInfo()` с задержкой 10с.

### 4. `node-key-management.md` — создать новый документ

Создать `.claude/docs/node-key-management.md` после завершения реализации со следующими разделами:
- Архитектура (слои, use cases, репозиторий)
- Флоу автоматического восстановления при connect
- Callsign-triggered механика
- NodeSettingsScreen — что показывает и какие действия
- PKC-статус в ChatScreen
- Таблица покрытия сценариев (из плана → в doc)

### 5. `CLAUDE.md` — добавить строку в таблицу документации

```
| Node PKC Key Management | `.claude/docs/node-key-management.md` |
```

---

## Что НЕ входит в план

- `KeyVerificationAdmin` — bidirectional crypto verification. Defer.
- `get_config_request` on-demand refresh — не нужен, config кешируется при connect.
- Показ `private_key` в UI — категорически нет.
- Автоматический reboot при сломанном ключе без sync — чтобы не ломать ожидания пользователя. Только ручной trigger через UI.
