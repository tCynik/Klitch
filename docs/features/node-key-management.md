# Node PKC Key Management

**Статус**: ✅ Done  
**Дата завершения**: 2026-05-15  
**План**: `docs/archive/node-key-management.md`

---

## Контекст

DM-чат не работал: firmware не расшифровывал входящие пакеты (`decoded=null`).  
Причина — `hasPKC=false` у партнёра в NodeDB → firmware выбирал PSK-путь (channel=0) вместо PKC.

Три возможных кейса:
- **Кейс A** — наш `private_key` отсутствует/сломан → `hasPKC=false` у нас
- **Кейс B** — `hasPKC=false` у партнёра в нашем NodeDB (ключ не дошёл или отклонён)
- **Кейс C** — устаревший `public_key` партнёра → ECDH на неверном ключе

---

## Архитектура

```
domain/mesh/
    model/NodeSecurityModel.kt                — publicKeyHex, hasKey, isMismatch
    usecase/
        ObserveNodeSecurityConfigUseCase.kt   — Flow<NodeSecurityModel?>
        RegeneratePkcKeysUseCase.kt           — set_config(private_key=EMPTY)
        CheckOwnPkcHealthUseCase.kt           — Boolean: ключ отсутствует или ERROR
        RefreshNodePublicKeysUseCase.kt       — requestUserInfo() для всех нод
        RefreshNodePublicKeyUseCase.kt        — requestUserInfo() для одной ноды
        ObserveCallsignChangesUseCase.kt      — Flow<Int>: nodeNum при смене позывного

data/mesh/repository/MeshConfigRepositoryImpl.kt
    — observeSecurityConfig(), isOwnPkcKeyBroken(), refreshKnownNodePublicKeys(),
      refreshNodePublicKey(nodeNum), refreshNodePublicKeys(), regeneratePkcKeys(),
      observeCallsignChanges()

presentation/feature/node/
    NodeSettingsScreen.kt     — PKC-секция с публичным ключом и кнопкой регенерации
    NodeSettingsViewModel.kt  — observes security config + connection status
    NodeSettingsUiState.kt    — publicKeyHex, hasKey, isMismatch, showRegenerateDialog
```

---

## Флоу автоматического восстановления при connect

`UserSettingsViewModel.onConnected()`:
1. `checkOwnPkcHealth()` → сохраняет флаг `needsPkcRegen`
2. `refreshNodePublicKeys()` — первый проход: если ключ партнёра изменился → reject → stored=EMPTY
3. `delay(30_000)` → `refreshNodePublicKeys()` — второй проход: принять новый ключ

При подтверждении sync dialog (`onConfirmChannelSync()`):
- Если `needsPkcRegen` → `regeneratePkcKeys()` перед reboot
- Один reboot применяет и каналы, и новую PKC пару

### Двухшаговое принятие ключа (анти-MITM)

`NodeManagerImpl.handleReceivedUser()`:
```kotlin
val keyMatch = !node.hasPKC || node.user.public_key == p.public_key
val newUser = if (keyMatch) p else p.copy(public_key = ByteString.EMPTY)
```
Если stored key есть И пришёл другой → REJECT (stored=EMPTY). Следующий User → ACCEPT.  
Поэтому двойной `requestUserInfo()` с задержкой: 1й reject → 2й принят.

---

## Callsign-triggered механика

### Мы меняем позывной

`UserSettingsViewModel.onSaveAndReboot()` → `regeneratePkcKeys()` перед reboot.  
После reboot firmware рассылает новый `User(public_key)` → все соседи получают обновлённый ключ.

### Партнёр сменил позывной

`observeCallsignChanges()` в `MainViewModel.init {}`:
```kotlin
observeCallsignChanges(NoParams)
    .onEach { nodeNum ->
        refreshNodePublicKey(nodeNum)
        delay(10_000)
        refreshNodePublicKey(nodeNum)
    }
    .launchIn(viewModelScope)
```

---

## NodeSettingsScreen

Маршрут: `NavGraph → NodeSettingsScreen` (ссылка из настроек ноды).

### Отображение

- `publicKeyHex` → первые 8 + "…" + последние 8 символов, или "—" если `hasKey=false`
- `isMismatch=true` (ERROR_BYTE_STRING, 32 нуля) → красный `WarningCard`: "Ключ повреждён"
- `!hasKey` → `WarningCard`: "Ключ отсутствует"
- Иначе → "PKC-шифрование: ✓ активно"

### Регенерация

Кнопка "Сгенерировать новые ключи PKC" (disabled если нода не подключена) → `AlertDialog` → `onRegenerateConfirm()`:
```kotlin
regeneratePkcKeys()
delay(300)
rebootNode()
```

---

## PKC-статус в ChatScreen

Баннер между строкой поиска и сообщениями для открытого приватного чата:
- `PKC ✓ — зашифровано` (цвет `tertiary`) если `partnerHasPKC = true`
- `PKC ✗ — незашифровано` (цвет `error`) если `partnerHasPKC = false`
- Скрыт (`null`) для каналов

Цепочка: `node.hasPKC` → `PrivateNodeCandidate.partnerHasPKC` → `ChatContactDto` → `ChatContact` → `ChatFilterItem` → `ChatUiState.selectedChatPartnerHasPKC` → `PkcStatusBanner`.

---

## Таблица покрытия сценариев

| Сценарий | Покрыт? | Механизм |
|---|---|---|
| `hasPKC=false` у партнёра в NodeDB | ✅ | Двойной `requestUserInfo()` на connect |
| Наш `private_key` отсутствует + sync нужен | ✅ | `needsPkcRegen` → fold в sync reboot |
| Наш `private_key` отсутствует, sync не нужен | ✅ | NodeSettingsScreen → ручная регенерация |
| Ключ партнёра изменился (регенерировал) | ✅ | Двойной `requestUserInfo()`: reject → accept |
| Ключ партнёра изменился, партнёр оффлайн | ❌ | Починится при следующем его broadcast |
| Наш ключ ERROR_BYTE_STRING | ✅ | `isMismatch` в NodeSettingsScreen + `isOwnPkcKeyBroken()` |
| Мы сменили позывной | ✅ | `regeneratePkcKeys()` fold в `onSaveAndReboot()` |
| Партнёр сменил позывной | ✅ | `observeCallsignChanges()` → двойной `requestUserInfo()` |
