# Node PKC Key Management — план реализации

**Статус**: Approved  
**Приоритет**: Medium (блокирует диагностику DM-чата)

---

## Контекст

DM-чат не работает: firmware не расшифровывает входящие пакеты (`decoded=null`).  
Root cause: сломанный PKC key exchange. Без UI управления ключами невозможно диагностировать и восстановить состояние.

Задача: реализовать просмотр и регенерацию PKC key pair ноды.

---

## Что уже есть (не нужно реализовывать)

- `SecurityConfig` **уже сохраняется** в DataStore при connect:  
  `handleDeviceConfig(Config(security=...))` → `LocalConfigDataSource.setLocalConfig()` → `localConfigStore`
- `RadioConfigRepository.localConfigFlow: Flow<LocalConfig>` — содержит `security` поле
- `MeshActionHandlerImpl.handleSetConfig(ByteArray, Int)` → `AdminMessage(set_config = ...)` — работает
- `CommandSender.generatePacketId()`, `rebootNode()` — уже есть

---

## Ограничения протокола

- **Private key никогда не передаётся** в приложение в явном виде (SecurityConfig.private_key приходит с радио, но не должен отображаться)
- Регенерация: отправить `Config(security = SecurityConfig())` с пустым `private_key` → firmware генерирует новую пару → reboot
- Регенерация аннулирует PKC-доверие всех партнёров: им придёт новый User broadcast с новым public_key

---

## Фаза 1 — Domain: ObserveNodeSecurityConfigUseCase

**Файлы:**

### `domain/mesh/model/NodeSecurityModel.kt`
```kotlin
data class NodeSecurityModel(
    val publicKeyHex: String,   // hex-представление public key (32 байта → 64 hex-символа)
    val hasKey: Boolean,        // public_key.size > 0
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

**Принцип**: обнулить `private_key` в SecurityConfig → firmware генерирует новую пару при reboot.

### `MeshConfigRepository` — добавить метод
```kotlin
fun regeneratePkcKeys()
```

### `MeshConfigRepositoryImpl` — реализация
```kotlin
override fun regeneratePkcKeys() {
    val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: return
    val currentSec = meshRouter.configHandler.localConfig.value.security
        ?: Config.SecurityConfig()
    val resetSec = currentSec.copy(private_key = ByteString.EMPTY)
    val payload = Config.ADAPTER.encode(Config(security = resetSec))
    meshRouter.actionHandler.handleSetConfig(payload, myNodeNum)
}
```

> `copy(private_key = ByteString.EMPTY)` сохраняет `admin_key` + `public_key` — firmware заменит только private/public при генерации.

### `domain/mesh/usecase/RegeneratePkcKeysUseCase.kt`
```kotlin
class RegeneratePkcKeysUseCase(private val repository: MeshConfigRepository) {
    operator fun invoke() = repository.regeneratePkcKeys()
}
```

---

## Фаза 3 — UI: NodeSettingsScreen

`NodeSettingsScreen` сейчас — TODO-stub. Реализовать минимальный Security-раздел.

### `NodeSettingsUiState.kt` — расширить
```kotlin
data class NodeSettingsUiState(
    val isLoading: Boolean = false,
    val publicKeyHex: String? = null,   // null = SecurityConfig ещё не загружен
    val hasKey: Boolean = false,
    val showRegenerateDialog: Boolean = false,
    val isNodeConnected: Boolean = false,
)
```

### `NodeSettingsViewModel.kt` — расширить

Внедрить зависимости:
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

Структура:
```
Scaffold (TopAppBar "Настройки ноды", back)
  LazyColumn
    ─── Секция "PKC / Безопасность" ───────────────────────────────
    ListItem: "Публичный ключ"
      trailing: монограмм ключа (первые 8 + "…" + последние 8 hex-символов)
               или "—" если hasKey == false
    ListItem статус: "PKC-шифрование: ✓ активно" / "✗ ключ отсутствует"
    Divider
    Button "Сгенерировать новые ключи PKC"
      enabled = isNodeConnected
      onClick = onRegenerateClick()
```

AlertDialog при `showRegenerateDialog == true`:
```
Заголовок: "Сбросить PKC-ключи?"
Текст: "Устройство сгенерирует новую пару ключей и перезагрузится.
        Контакты потеряют доверие к старому ключу."
Кнопки: [Отмена] [Подтвердить]
```

---

## Фаза 4 — Показ ключа контакта в ChatScreen

**Минимальный вариант**: в `MeshToChatAdapter.buildPrivateCandidates()` уже используется `node.hasPKC`. Добавить в `ChatContact` поле `partnerHasPKC: Boolean` и показывать в шапке приватного чата:

```
[PKC ✓] / [PKC ✗ — сообщения незашифрованы]
```

Это помогает диагностировать: видно, есть ли ключ у партнёра в локальном NodeDB.

**Файлы для изменения:**
- `domain/chat/model/ChatContact.kt` — добавить `partnerHasPKC: Boolean`
- `MeshToChatAdapter.buildPrivateCandidates()` — пробросить `node.hasPKC`
- `ChatTab.kt` / `ChatScreen.kt` — показать PKC-статус в header открытого чата

---

## DI

`NodeSettingsViewModel` — добавить в Koin-модуль (где он объявлен):
- `ObserveNodeSecurityConfigUseCase`
- `RegeneratePkcKeysUseCase`

В `MeshConfigModule` (или аналогичном) добавить `single { ObserveNodeSecurityConfigUseCase(get()) }` и `single { RegeneratePkcKeysUseCase(get()) }`.

---

## Последовательность реализации

1. `NodeSecurityModel` data class
2. `MeshConfigRepository` — добавить 2 метода в интерфейс
3. `MeshConfigRepositoryImpl` — реализовать оба метода
4. `ObserveNodeSecurityConfigUseCase` + `RegeneratePkcKeysUseCase`
5. `NodeSettingsUiState` — расширить поля
6. `NodeSettingsViewModel` — внедрить use cases, добавить логику
7. `NodeSettingsScreen` — реализовать UI
8. DI — зарегистрировать новые use cases
9. Фаза 4 (ChatContact PKC-статус) — опционально, только если диагностика не помогла

---

## Что НЕ входит в план

- `KeyVerificationAdmin` — криптографический протокол верификации, требует отдельного экрана и bidirectional admin exchange. Defer.
- Чтение SecurityConfig через `get_config_request` (on-demand refresh) — не нужно, config приходит при connect и кешируется.
- Показ private key в UI — категорически нет.
