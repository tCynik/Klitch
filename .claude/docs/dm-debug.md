# DM Debug — текущее состояние

## Проблема

DM (прямые сообщения) не доставляются. Канальные сообщения работают.

---

## Устройства

| | Device A (отправитель) | Device B (получатель) |
|---|---|---|
| nodenum | 2661230224 | 2658563744 |
| nodeId | !9e9f2690 | !9e7676a0 |
| signed int | -1633737072 | -1636403552 |

Оба в одном меше. Общий канал: channel=1 PSK (контур).  
Channel=0 PSK: **асимметрия** — Device A получает пакеты Device B на ch=0 (телеметрия приходит), но Device B **НЕ** расшифровывает пакеты Device A на ch=0 (portnum=null).

---

## Статус PKC ключей

- Device A: `pubKey=32B, privKey=32B` ✓ (из `isOwnPkcKeyBroken` лога)
- Device B: отправляет `respondNodeInfo: myPubKey=32B` ✓

---

## Цепочка симптомов (конечный лог)

На Device A при отправке DM:
```
doSend: to=!9e7676a0 channel=8 contactKey=8!9e7676a0
buildMeshPacket PKC to=2658563744: userPubKey=32B nodePubKey=-1B keyEmpty=false
```

На Device B после DM:
```
fromRadio: portnum=null from=2661230224 to=2658563744 pki=false channel=0 pubkeySize=0B
```

**Диагноз**: Android Device A строит корректный ToRadio (`pki=true, public_key=32B`), но firmware Device A стрипает PKC и отправляет PSP channel=0. Device B не может расшифровать (несовпадение PSK на ch=0).

---

## Причина firmware fallback PSP

**Гипотеза (наиболее вероятная)**: Firmware Device A не имеет stored public key для Device B в своей firmware node DB, ИЛИ имеет несовпадающий ключ. При получении ToRadio с `pki=true`, firmware валидирует `public_key` против хранимого ключа:
- Если совпадает → ECDH, отправляет OTA `pki=true`
- Если не совпадает или отсутствует → fallback PSP channel=0

---

## Key exchange flow (что работает и что нет)

```
Device A                               Device B
requestUserInfo ch=0 ──────────────> [ch=0 PSK mismatch → не расшифровывает]
requestUserInfo ch=1 ──────────────> handleReceivedUser from=A (keyMatch=true, incomingKey=32B) ✓
                                      respondNodeInfo ch=8 (PKC) ──────────> ???
                    <── fromRadio: portnum=POSITION_APP from=B (ch=1) ✓
                    [fromRadio: portnum=NODEINFO_APP pki=true from=B — НЕ ВИДНО]
DM: buildMeshPacket PKC keyEmpty=false
                    → firmware strips PKC → PSP ch=0 OTA
                                       fromRadio: portnum=null pki=false ch=0 ✗
```

**Критический пробел**: Device A **не получает** `fromRadio: portnum=NODEINFO_APP pki=true from=DeviceB`. Это значит либо:
1. `respondNodeInfo PKC` не доходит до firmware Device A (OTA не получен)
2. Firmware Device A получает но тихо дропает (нет stored key для Device B → anti-MITM silent drop)
3. `fromRadio` лог присутствует но пользователь его не видел (нужна проверка)

---

## Применённые фиксы (по порядку)

| # | Файл | Фикс | Статус |
|---|---|---|---|
| 1 | `MeshDataHandlerImpl.kt` | `handleNodeInfo` отвечает на `want_response=true` через `respondNodeInfo` | ✓ Applied |
| 2 | `NodeManagerImpl.kt` | `handleReceivedUser` two-pass: очищает `publicKey=null` при keyMatch=false | ✓ Applied |
| 3 | `MainViewModel.kt` | `refreshNodePublicKeys` при старте (t=2s, t=7s, t=32s) | ✓ Applied |
| 4 | `MeshToChatAdapter.kt` | `buildPrivateCandidates`: при `hasPKC=true` всегда PKC contactKey, игнорирует PSP историю | ✓ Applied (ключевой) |
| 5 | `NodeManagerImpl.kt` | `installNodeInfo`: не сбрасывает channel в 0 если уже есть лучший | ✓ Applied |
| 6 | `CommandSenderImpl.kt` | `requestUserInfo`: при destChannel=0 также пробует первый secondary channel | ✓ Applied |
| 7 | `CommandSenderImpl.kt` | `respondNodeInfo`: использует PKC если оба узла имеют ключи | ✓ Applied |
| 8 | `MainViewModel.kt` | `checkOwnPkcHealth` + auto-regenerate при подключении | ✓ Applied |
| 9 | `MeshConfigRepositoryImpl.kt` | `isOwnPkcKeyBroken`: логирует privKey размер | ✓ Applied |

---

## Нерешённая проблема

Firmware Device A стрипает PKC при отправке DM, несмотря на то что:
- Android правильно строит ToRadio (`pki=true, publicKey=32B`)
- Device A имеет валидные собственные ключи (`pubKey=32B, privKey=32B`)
- Device A имеет ключ Device B в Android NodeDB (`userPubKey=32B, keyEmpty=false`)

**Вывод**: Firmware Device A не принимает ключ Device B через `respondNodeInfo` PKC. Возможно, firmware требует stored key для валидации входящего PKC пакета прежде чем его декриптовать (no TOFU — только если ключ уже в DB).

---

## Следующие шаги (не реализованы)

**Шаг 1 — Диагностика**: Проверить полный лог Device A после reconnect:
- Есть ли `fromRadio: portnum=NODEINFO_APP pki=true from=2658563744`? (Device A расшифровал PKC от Device B?)
- Есть ли `fromRadio: portnum=null pki=true from=2658563744`? (PKC дошёл но не расшифровался?)

**Шаг 2 — Альтернативный path для firmware key**:  
Device B периодически бродкастит NodeInfo на channel=0. Device A firmware **получает** channel=0 от Device B (телеметрия приходит). Если Device B NodeInfo broadcast также идёт на channel=0 — firmware Device A должен уже иметь ключ Device B от этих бродкастов. Нужно проверить timing: возможно ключ есть после первого broadcast (до 15 минут после коннекта).

**Шаг 3 — Forced NodeInfo broadcast**:  
Добавить явный запрос Device B сделать NODEINFO broadcast на channel=0 сразу при подключении. Это обновит firmware DB Device A с ключом Device B без зависимости от PKC handshake.

**Шаг 4 — Проверить `wantConfig` trigger**:  
После `wantConfig` firmware пересылает всю node DB в Android. Возможно это также обновляет внутренние ключи.

**Шаг 5 — Firmware version check**:  
Проверить версию firmware на Device A. PKC DM поддерживается начиная с 2.5.x. Возможны баги в конкретных минорных версиях.

---

## Файлы изменённые в этой ветке

- `mesh/.../CommandSenderImpl.kt` — respondNodeInfo, requestUserInfo multi-channel
- `mesh/.../CommandSender.kt` — интерфейс respondNodeInfo
- `mesh/.../MeshDataHandlerImpl.kt` — handleNodeInfo want_response
- `mesh/.../NodeManagerImpl.kt` — handleReceivedUser, installNodeInfo channel fix
- `mesh/.../MeshMessageProcessorImpl.kt` — PKCDebug fromRadio log
- `mesh/.../MeshDataHandlerImpl.kt` — PKCDebug handleReceivedData log
- `app/.../MainViewModel.kt` — checkOwnPkcHealth, refreshNodePublicKeys, imports
- `app/.../PresentationModule.kt` — DI wiring
- `app/.../MeshConfigRepositoryImpl.kt` — isOwnPkcKeyBroken privKey log
- `app/.../MeshToChatAdapter.kt` — buildPrivateCandidates PKC priority fix

---

## Cleanup после завершения дебага

### Удалить PKCDebug логи

Все `android.util.Log.i("PKCDebug", ...)` — временные диагностические логи. Удалить после фикса:

| Файл | Строка / метод |
|---|---|
| `MeshMessageProcessorImpl.kt` | `handleFromRadio` — лог `"fromRadio: portnum=..."` |
| `MeshMessageProcessorImpl.kt` | закомментированный лог `"DBG fromRadio: PARSE FAIL..."` |
| `CommandSenderImpl.kt` | `buildMeshPacket` — лог `"buildMeshPacket PKC to=..."` |
| `CommandSenderImpl.kt` | `buildMeshPacket` — лог `"buildMeshPacket PSK DM to=..."` |
| `CommandSenderImpl.kt` | `requestUserInfo` — лог `"requestUserInfo: to=..."` |
| `CommandSenderImpl.kt` | `respondNodeInfo` — лог `"respondNodeInfo: to=..."` |
| `NodeManagerImpl.kt` | `handleReceivedUser` — лог `"handleReceivedUser from=..."` |
| `MeshDataHandlerImpl.kt` | `handleReceivedData` — лог `"handleReceivedData: portnum=..."` |
| `MeshDataHandlerImpl.kt` | `handleReceivedData` — лог `"toDataPacket=null → dropped"` |
| `MeshConfigRepositoryImpl.kt` | `refreshKnownNodePublicKeys` — все логи `"PKCDebug"` |
| `MeshConfigRepositoryImpl.kt` | `refreshNodePublicKey` — (если есть лог) |
| `MeshConfigRepositoryImpl.kt` | `isOwnPkcKeyBroken` — лог `"isOwnPkcKeyBroken: pubKey=... privKey=..."` |
| `MeshToChatAdapter.kt` | `doSend` — логи `"doSend: to=..."` и `"after sendData"` |
| `MainViewModel.kt` | `"MainViewModel: own PKC broken → regenerating"` |

### Оставить (функциональные изменения, не debug)

- `MainViewModel.kt` — тройной refresh (t=2s, t=7s, t=32s): нужен для two-pass firmware anti-MITM
- `CommandSenderImpl.kt` — `respondNodeInfo` через PKC когда оба hasPKC: функциональный фикс
- `CommandSenderImpl.kt` — `requestUserInfo` multi-channel: функциональный фикс
- `MeshDataHandlerImpl.kt` — `handleNodeInfo` вызывает `respondNodeInfo` на want_response: функциональный фикс
- `NodeManagerImpl.kt` — `handleReceivedUser` очищает publicKey при keyMatch=false: функциональный фикс
- `NodeManagerImpl.kt` — `installNodeInfo` не сбрасывает channel в 0: функциональный фикс
- `MeshToChatAdapter.kt` — `buildPrivateCandidates` PKC priority: функциональный фикс
- `MainViewModel.kt` — `checkOwnPkcHealth` + `regeneratePkcKeys` при подключении: функциональный фикс
- `MeshConfigRepositoryImpl.kt` — `isOwnPkcKeyBroken` (без лога): функциональный фикс (проверка privKey убрана как потенциально опасная)

---

## Branch

`nodes_and_contours_chat_fix`
