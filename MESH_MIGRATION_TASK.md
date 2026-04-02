# Задача: Миграция слоя взаимодействия с нодами из Meshtastic-Android

**Дата составления:** 2026-03-30
**Источник:** `C:\Users\LocAdmin\AndroidStudioProjects\Meshtastic-Android`
**Целевой проект:** `C:\Users\LocAdmin\AndroidStudioProjects\MyMesh1`

---

## Контекст и цель

### Что есть сейчас в MyMesh1

Проект MyMesh1 содержит два модуля:
- **`:app`** — UI (Jetpack Compose, Navigation, ViewModels)
- **`:shared`** — KMP domain/data layer

Слой взаимодействия с mesh-сетью реализован в виде **заглушек**:
- `MeshApiService` обращается к несуществующему URL `https://api.mymesh.example.com/v1`
- `connectToNode()` / `disconnectFromNode()` — пустые тела
- `NodeModel` содержит только 6 полей: id, name, address, rssi, isConnected, lastSeen
- BLE-слоя нет совсем

### Что нужно сделать

Перенести из Meshtastic-Android полноценную реализацию слоя взаимодействия с нодами:
- BLE-соединение с Meshtastic-радиомодулем
- Приём/отправка протобуф-пакетов через BLE-характеристики
- Парсинг и маппинг входящих пакетов (NodeInfo, Position, Telemetry, Messages и др.)
- Управление in-memory и persistent БД нод
- Репозитории для подписки на данные нод и отправки сообщений
- Android Foreground Service для фоновой работы соединения

**UI-слой на данном этапе не реализуется.** По результату в приложении должны быть:
1. `NodeRepository` — Flow со всеми нодами сети (позиция, метрики, SNR, lastHeard и т.д.)
2. `ServiceRepository` — Flow состояния соединения (Disconnected / Connecting / Connected)
3. `RadioController` — методы для отправки сообщений, запроса позиции, конфигурации

### Ключевые принципы

> **Код должен быть максимально близким к оригиналу Meshtastic-Android.**
> В предыдущем аналогичном проекте попытки адаптации и переписывания привели к
> труднообнаруживаемым ошибкам, которые невозможно было отладить.
> Поэтому: копируем как есть, меняем только объявления пакетов.

Допустимые изменения при копировании:
1. Переименование пакетов: `org.meshtastic.*` → `ru.tcynik.mymesh1.mesh.*`
2. Настройка `build.gradle.kts` и `libs.versions.toml`
3. Точки интеграции с существующим `app` и `shared` (DI, Application class)

**Не допускается:** рефакторинг, упрощение, переписывание логики, замена паттернов.

### Важный факт о шифровании

Шифрование/дешифрование трафика mesh-сети **выполняется на стороне firmware устройства**,
а не в Android-приложении. Приложение управляет только ключами (PSK) через конфигурацию канала.
Kotlin-код шифрования в оригинале отсутствует — копировать нечего.

---

## Новая структура проекта

```
MyMesh1/
├── app/              (существующий — UI, без изменений)
├── shared/           (существующий — KMP domain, минимальные обновления)
├── mesh/             (НОВЫЙ Android-модуль — весь mesh-стек)
│   ├── src/main/proto/meshtastic/   ← .proto файлы
│   └── src/main/kotlin/ru/tcynik/mymesh1/mesh/
│       ├── model/       ← domain-модели нод, пакетов, каналов
│       ├── repository/  ← интерфейсы репозиториев
│       ├── database/    ← Room DB (entities, DAOs)
│       ├── datastore/   ← DataStore (ключи каналов, конфиг)
│       ├── ble/         ← BLE-сканирование и соединение (Kable)
│       ├── data/
│       │   ├── manager/     ← обработчики пакетов, менеджер нод
│       │   └── repository/  ← реализации репозиториев
│       ├── service/     ← Android Service, оркестратор, RadioController
│       └── di/          ← Koin-модуль для всего mesh-слоя
├── build.gradle.kts  (добавить Wire plugin)
├── settings.gradle.kts (добавить `:mesh`)
└── gradle/libs.versions.toml (добавить новые зависимости)
```

---

## Этапы выполнения

### Этап 1 — Подготовка модуля `:mesh`

#### 1.1. `settings.gradle.kts`
Добавить строку:
```kotlin
include(":mesh")
```

#### 1.2. `gradle/libs.versions.toml` — добавить версии
```toml
[versions]
wire            = "5.3.1"
kable           = "0.36.0"
room            = "2.7.1"
datastore       = "1.1.4"
atomicfu        = "0.27.0"
kermit          = "2.0.5"
workmanager     = "2.10.1"

[libraries]
# Wire (protobuf)
wire-runtime            = { group = "com.squareup.wire", name = "wire-runtime",     version.ref = "wire" }
wire-grpc-client        = { group = "com.squareup.wire", name = "wire-grpc-client", version.ref = "wire" }

# Kable (BLE)
kable-core              = { group = "com.juul.kable", name = "core", version.ref = "kable" }

# Room
room-runtime            = { group = "androidx.room", name = "room-runtime",  version.ref = "room" }
room-ktx                = { group = "androidx.room", name = "room-ktx",      version.ref = "room" }
room-compiler           = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

# DataStore
datastore-preferences   = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }

# AtomicFU
atomicfu                = { group = "org.jetbrains.kotlinx", name = "atomicfu", version.ref = "atomicfu" }

# Kermit (logging — используется в оригинале)
kermit                  = { group = "co.touchlab", name = "kermit", version.ref = "kermit" }

# WorkManager
work-runtime            = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workmanager" }

[plugins]
wire                    = { id = "com.squareup.wire", version.ref = "wire" }
kotlin-atomicfu         = { id = "org.jetbrains.kotlinx.atomicfu", version.ref = "atomicfu" }
```

#### 1.3. Корневой `build.gradle.kts` — добавить плагины
```kotlin
alias(libs.plugins.wire)           apply false
alias(libs.plugins.kotlin.atomicfu) apply false
```

#### 1.4. `mesh/build.gradle.kts` — создать новый файл
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.wire)
    alias(libs.plugins.kotlin.atomicfu)
}

android {
    namespace = "ru.tcynik.mymesh1.mesh"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}

wire {
    sourcePath {
        srcDir("src/main/proto")
    }
    kotlin {
        javaInterop = true
    }
}

dependencies {
    // Wire protobuf runtime
    implementation(libs.wire.runtime)

    // Kable BLE
    implementation(libs.kable.core)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // AtomicFU
    implementation(libs.atomicfu)

    // Kermit logging
    implementation(libs.kermit)

    // WorkManager
    implementation(libs.work.runtime)

    // Coroutines
    implementation(libs.coroutines.android)

    // Serialization
    implementation(libs.serialization.json)

    // Immutable collections
    implementation(libs.immutable.collections)

    // Koin
    implementation(libs.koin.android)

    // AndroidX
    implementation(libs.androidx.core.ktx)

    // Lifecycle
    implementation(libs.lifecycle.viewmodel)
}
```

#### 1.5. `mesh/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest>
    <!-- BLE -->
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN"
        android:usesPermissionFlags="neverForLocation" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

    <!-- Foreground Service -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />

    <!-- WorkManager -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application>
        <service
            android:name=".service.MeshService"
            android:enabled="true"
            android:exported="false"
            android:foregroundServiceType="connectedDevice|location" />
    </application>
</manifest>
```

---

### Этап 2 — Protocol Buffers

**Источник:** `Meshtastic-Android/core/proto/src/main/proto/meshtastic/`
**Назначение:** `mesh/src/main/proto/meshtastic/`

Копировать все `.proto` файлы без изменений:

| Файл | Содержимое |
|------|-----------|
| `mesh.proto` | MeshPacket, FromRadio, ToRadio, Position, User, RouteDiscovery, Routing, Data |
| `channel.proto` | Channel, ChannelSettings (PSK, название, роль) |
| `config.proto` | DeviceConfig, LoRaConfig, BluetoothConfig, SecurityConfig и др. |
| `admin.proto` | AdminMessage, ConfigType enum, команды управления |
| `portnums.proto` | PortNum enum: TEXT_MESSAGE_APP=1, NODEINFO_APP=4, TELEMETRY_APP=67 и др. |
| `telemetry.proto` | DeviceMetrics, EnvironmentMetrics, PowerMetrics |
| `apponly.proto` | ChannelSet |
| `clientonly.proto` | DeviceUIConfig, ClientNotification |
| `deviceonly.proto` | LocalConfig, OEMStore |
| `module_config.proto` | ModuleConfig (MQTT, Serial, Telemetry и др.) |
| `connection_status.proto` | DeviceConnectionStatus |
| `storeforward.proto` | StoreAndForward |
| `mqtt.proto` | ServiceEnvelope, MqttClientProxyMessage |
| `paxcount.proto` | Paxcount |
| `remote_hardware.proto` | HardwareMessage |
| `rtttl.proto` | RemoteMusicAction |
| `xmodem.proto` | XModem |
| `interdevice.proto` | InterDevice |
| `device_ui.proto` | DeviceUIConfig |
| `localonly.proto` | LocalConfig, LocalModuleConfig |

После копирования Wire автоматически сгенерирует Kotlin-классы в `build/generated/source/wire/`.

---

### Этап 3 — Domain модели

**Источник:** `core/model/src/commonMain/kotlin/org/meshtastic/core/model/`
**Назначение:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/model/`

При копировании каждого файла: изменить строку `package org.meshtastic.core.model`
на `package ru.tcynik.mymesh1.mesh.model`, обновить все import-ы в том же файле.

#### Основные модели

| Исходный файл | Назначение |
|--------------|-----------|
| `Node.kt` | Основная domain-модель ноды. Содержит: num, user, position, snr, rssi, lastHeard, deviceMetrics, channel, isFavorite, isIgnored, publicKey, notes и вычисляемые свойства: isOnline, distance(), bearing(), batteryStr, validPosition |
| `NodeInfo.kt` | MeshUser, Position, DeviceMetrics, EnvironmentMetrics, NodeInfo — маппинг из protobuf |
| `MyNodeInfo.kt` | Информация о собственном устройстве (myNodeNum, firmwareVersion и др.) |
| `Channel.kt` | Channel, ChannelSettings — конфигурация канала с PSK |
| `Message.kt` | Модель входящего/исходящего сообщения |
| `DataPacket.kt` | Обёртка над сырым пакетом данных |
| `ConnectionState.kt` | Sealed class: Disconnected, Connecting, Connected, DeviceSleep |
| `RadioController.kt` | Interface RadioController — все методы управления радиомодулем |
| `MeshLog.kt` | MeshLog — запись в лог пакетов |
| `MeshActivity.kt` | MeshActivity — активность в сети |
| `NeighborInfo.kt` | NeighborInfo — информация о соседях |
| `Contact.kt` | Contact — модель контакта |
| `DeviceHardware.kt` | DeviceHardware — типы аппаратного обеспечения |
| `DeviceVersion.kt` | DeviceVersion — версия прошивки с разбором |
| `TelemetryType.kt` | TelemetryType enum |
| `InterfaceId.kt` | InterfaceId — тип транспорта (BLE, TCP, Serial) |
| `RouteDiscovery.kt` | RouteDiscovery — маршрут traceroute |
| `service/ServiceAction.kt` | ServiceAction — действия из UI в сервис |
| `service/TracerouteResponse.kt` | TracerouteResponse |

#### Утилиты

**Источник:** `core/model/src/commonMain/kotlin/org/meshtastic/core/model/util/`
**Назначение:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/model/util/`

| Файл | Назначение |
|------|-----------|
| `MeshDataMapper.kt` | **Ключевой файл.** Маппинг protobuf-объектов → domain-модели |
| `ChannelSet.kt` | Утилиты для ChannelSet (encode/decode URL каналов) |
| `LocationUtils.kt` | Конвертация координат (int32 ↔ double) |
| `DateTimeUtils.kt` | Форматирование времени |
| `Extensions.kt` | Расширения для базовых типов |
| `WireExtensions.kt` | Расширения для Wire protobuf классов |
| `ByteStringExtensions.kt` | Работа с ByteString |
| `NodeIdLookup.kt` | Lookup нод по ID |
| `TimeUtils.kt`, `TimeConstants.kt` | Константы времени (online threshold = 2 часа) |
| `UnitConversions.kt` | Конвертация единиц (метры, мили, футы) |
| `DistanceExtensions.kt` | Расстояние между нодами |
| `DebugUtils.kt` | Утилиты отладки |
| `UriUtils.kt` | Работа с URI каналов |
| `SfppHasher.kt` | Хэширование для Store & Forward |
| `SharedContact.kt` | Шаринг контактов |

---

### Этап 4 — Интерфейсы репозиториев

**Источник:** `core/repository/src/commonMain/kotlin/org/meshtastic/core/repository/`
**Назначение:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/repository/`

Эти интерфейсы — контракты между сервисным слоем и UI. Копировать точно.

| Файл | Ключевые методы / свойства |
|------|--------------------------|
| `RadioInterfaceService.kt` | `connectionState: StateFlow`, `receivedData: SharedFlow<ByteArray>`, `sendToRadio(bytes)`, `connect()`, `handleFromRadio(bytes)` |
| `ServiceRepository.kt` | `connectionState: StateFlow<ConnectionState>`, `meshPacketFlow: SharedFlow<MeshPacket>`, `serviceAction: Channel<ServiceAction>`, set/clear методы |
| `NodeRepository.kt` | `nodeDBbyNum: StateFlow<Map<Int, Node>>`, `myNodeInfo: Flow<MyNodeInfo?>`, `myId: StateFlow<String?>`, `getNodes()`, `upsert()`, `updateFavorite()`, `deleteNode()`, `clearNodeDB()` |
| `NodeManager.kt` | `loadCachedNodeDB()`, `getMyNodeInfo()`, `getMyId()`, `handleReceivedUser()`, `updateNode()`, `removeByNodenum()` |
| `MeshRouter.kt` | `dataHandler`, `configHandler`, `configFlowManager`, `actionHandler`, `start(scope)` |
| `PacketHandler.kt` | `sendToRadio(ToRadio)`, `sendToRadio(MeshPacket)`, `handleQueueStatus()`, `start()` |
| `CommandSender.kt` | `sendPacket()`, `setOwner()`, `setChannel()`, `setConfig()`, `requestPosition()`, `requestTelemetry()`, `reboot()`, `factoryReset()` |
| `MeshConnectionManager.kt` | `start(scope)`, connection state management |
| `MeshConfigHandler.kt` | `handleConfig()`, `handleChannel()`, `handleModuleConfig()` |
| `MeshDataHandler.kt` | `handleReceivedData()` — парсинг по portNum |
| `MeshMessageProcessor.kt` | `processReceivedPacket()` |
| `MeshActionHandler.kt` | `handleSend()`, `handleSetConfig()`, `handleSetChannel()`, `handleRequestPosition()` и др. |
| `NeighborInfoHandler.kt` | `handleNeighborInfo()` |
| `TracerouteHandler.kt` | `handleTraceroute()` |
| `AppWidgetUpdater.kt` | `updateWidgets()` (stub можно оставить пустым) |
| `LocationRepository.kt` | `locationFlow: Flow<Location>` |
| `MqttManager.kt` | MQTT proxy интерфейс |
| `MeshLogRepository.kt` | `insertLog()`, `getLogs()` |
| `MessageFilter.kt` | Фильтрация сообщений |

---

### Этап 5 — База данных (Room)

**Источник:** `core/database/src/`
**Назначение:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/database/`

> **Важно:** Версию базы данных сбрасываем до **1**. Тянуть 37 миграций из оригинала нет смысла —
> это новая установка. Нужно только убедиться, что финальная схема соответствует оригиналу.

#### Главный класс базы

`MeshtasticDatabase.kt` → копировать, изменить:
- `package` и `import`
- `version = 1` (вместо 37)
- Убрать все `AutoMigration` аннотации
- Namespace пакетов entities и DAO

#### Entities (таблицы)

| Файл | Таблица | Ключевые поля |
|------|---------|--------------|
| `NodeEntity.kt` | `nodes` | num (PK), userId, longName, shortName, hwModel, latitude, longitude, altitude, snr, rssi, lastHeard, batteryLevel, role, publicKey, isFavorite, isIgnored, notes и др. |
| `MyNodeEntity.kt` | `my_node_info` | myNodeNum, firmwareVersion, messageTimeoutMsec, minAppVersion |
| `Packet.kt` | `packets` | uuid, myNodeNum, portNum, contact, time, read, data, status, snr, errorCode |
| `ContactSettings.kt` | `contact_settings` | contact, muteUntil, ignoredByFilter |
| `MeshLog.kt` | `mesh_log` | uuid, fromNum, portNum, data, received_date, gateway_id |
| `MetadataEntity.kt` | `metadata` | num (FK→nodes), firmwareVersion, hasWifi, hasBluetooth, role, isManaged |
| `QuickChatAction.kt` | `quick_chat` | id, name, value, mode, position |

#### DAOs

| Файл | Ключевые методы |
|------|----------------|
| `NodeInfoDao.kt` | `upsert()` (с защитой от имперсонации через publicKey), `getNodes()`, `deleteNode()`, `clearNodeInfo()`, `setFavorite()`, `setIgnored()`, `updateNotes()` |
| `PacketDao.kt` | `insert()`, `getContactPackets()`, `deleteAll()` |
| `MeshLogDao.kt` | `insertLog()`, `getLogs()`, `deleteOlderThan()` |
| `QuickChatActionDao.kt` | CRUD для быстрых сообщений |

> **NodeInfoDao.upsert()** содержит специальную логику защиты от имперсонации:
> если нода с тем же userId уже существует с другим publicKey — это потенциальная атака,
> новая запись отклоняется. Копировать **точно**, это критически важная безопасность.

#### TypeConverters

`Converters.kt` — конвертеры для ByteString, Instant, List и других типов Room не поддерживает нативно.

---

### Этап 6 — DataStore (конфигурация и ключи)

**Источник:** `core/datastore/src/commonMain/kotlin/org/meshtastic/core/datastore/`
**Назначение:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/datastore/`

| Файл | Хранит |
|------|--------|
| `ChannelSetDataSource.kt` | **ChannelSet с PSK-ключами** (0 байт = нет шифрования, 16 = AES128, 32 = AES256). Единственный байт `0x01` означает стандартный ключ Meshtastic |
| `LocalConfigDataSource.kt` | Конфигурация устройства: LoRa, BT, Security, GPS |
| `ModuleConfigDataSource.kt` | Конфигурация модулей: MQTT, Store&Forward, Telemetry, CannedMessages |
| `LocalStatsDataSource.kt` | Локальная статистика нод |
| `RecentAddressesDataSource.kt` | История адресов (последние 3 IP/BLE-адреса) |
| `BootloaderWarningDataSource.kt` | Отображённые предупреждения о bootloader |

---

### Этап 7 — BLE-слой

**Источник A:** `core/ble/src/`
**Источник B:** `core/network/src/`
**Назначение:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/ble/`

#### Из `core/ble/`

| Файл | Назначение |
|------|-----------|
| `MeshtasticBleConstants.kt` | **Критический файл.** BLE UUIDs: SERVICE_UUID, TORADIO, FROMRADIO, FROMNUM, LOGRADIO, FROMRADIOSYNC, OTA характеристики |
| `MeshtasticRadioProfile.kt` | Интерфейс профиля радио |
| `KableMeshtasticRadioProfile.kt` | **Реализация с fallback-логикой:** сначала пробует FROMRADIOSYNC (новые прошивки), при ошибке переключается на старые FROMNUM/FROMRADIO |
| `BleScanner.kt` | Интерфейс сканирования |
| `KableBleScanner.kt` | Реализация сканирования через Kable |
| `BleDevice.kt` | Интерфейс BLE-устройства |
| `KableBleDevice.kt` | Kable-реализация устройства |
| `DirectBleDevice.kt` | Прямое BLE-устройство |
| `BleConnection.kt` | Интерфейс соединения |
| `KableBleConnection.kt` | Kable-реализация соединения (connect, disconnect, write TORADIO, read FROMRADIO) |
| `BleConnectionFactory.kt` | Интерфейс фабрики |
| `KableBleConnectionFactory.kt` | Kable-фабрика |
| `BleConnectionState.kt` | Enum состояний: Disconnected, Connecting, Connected |
| `BleRetry.kt` | Логика retry при ошибках соединения |
| `ActiveBleConnection.kt` | Глобальный трекер активного соединения (решает проблему несоответствия инстансов) |
| `BluetoothRepository.kt` | Интерфейс состояния Bluetooth |
| `AndroidBluetoothRepository.kt` | Android-реализация (BluetoothManager, bonding) |
| `BleServiceExtensions.kt` | Extension-функции |
| `di/CoreBleModule.kt` | Koin DI для BLE модуля |

#### Из `core/network/radio/`

| Файл | Назначение |
|------|-----------|
| `BleRadioInterface.kt` | **Центральный класс BLE.** Связывает BLE-соединение с RadioInterfaceService. Управляет: подключением, повторными попытками, MTU, bonding, чтением FROMRADIO в цикле |
| `StreamFrameCodec.kt` | **Кодек фреймирования.** Реализует Meshtastic-протокол: START1(0x94) + START2(0xc3) + size_msb + size_lsb + payload. Без этого пакеты не читаются через TCP/Serial |
| `StreamInterface.kt` | Базовый класс для потоковых интерфейсов (TCP, Serial) |
| `TCPInterface.kt` | TCP/WiFi соединение с Meshtastic-устройством |
| `MockInterface.kt` | Мок для тестов |
| `NopInterface.kt` | No-op заглушка |

#### Из `core/network/`

| Файл | Назначение |
|------|-----------|
| `NetworkRepository.kt` | Интерфейс сетевого репозитория |
| `NetworkRepositoryImpl.kt` | Реализация (выбор транспорта: BLE/TCP) |
| `ServiceDiscovery.kt` | mDNS/network discovery Meshtastic-устройств |

---

### Этап 8 — Менеджеры данных

**Источник:** `core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/`
**Назначение:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/data/manager/`

Это **бизнес-логика обработки пакетов**. Копировать точно.

| Файл | Назначение |
|------|-----------|
| `NodeManagerImpl.kt` | In-memory БД нод через AtomicFU maps. `loadCachedNodeDB()` при старте, `handleReceivedUser()` при получении NodeInfo, `updateNode()` при изменениях. Автоматически сохраняет в Room и рассылает broadcasts |
| `FromRadioPacketHandlerImpl.kt` | **Диспетчер входящих пакетов.** Разбирает FromRadio по типу (myInfo, nodeInfo, packet, config, channel, metadata, clientNotification) и роутит в специализированные обработчики |
| `PacketHandlerImpl.kt` | Очередь отправки пакетов с таймаутом 5 сек. CompletableDeferred для отслеживания ответов. Thread-safe через Mutex. Логирует в MeshLog |
| `MeshConnectionManagerImpl.kt` | Lifecycle соединения. **Двухфазный handshake:** Stage 1 (CONFIG_NONCE, только конфигурация) → Stage 2 (NODE_INFO_NONCE, данные нод). Обнаруживает зависание handshake и восстанавливается |
| `MeshConfigFlowManagerImpl.kt` | Оркестрирует процесс конфигурационного handshake. Собирает myNodeInfo, metadata, nodeInfo до получения configComplete |
| `MeshDataHandlerImpl.kt` | Обрабатывает пакеты с данными по portNum: TEXT_MESSAGE→сохранение, POSITION→обновление ноды, NODEINFO→обновление пользователя, TELEMETRY→метрики |
| `MeshMessageProcessorImpl.kt` | Парсит расшифрованный payload пакета, определяет portNum, вызывает dataHandler |
| `MeshRouterImpl.kt` | Центральный роутер: связывает все обработчики и запускает их |
| `CommandSenderImpl.kt` | Формирует ToRadio-пакеты и отправляет через PacketHandler |
| `MeshActionHandlerImpl.kt` | Обрабатывает ServiceAction из UI: sendMessage, requestPosition, removeNode, setConfig, setChannel, reboot, factoryReset и др. (~20 действий) |
| `NeighborInfoHandlerImpl.kt` | Обрабатывает NEIGHBORINFO_APP пакеты |
| `TracerouteHandlerImpl.kt` | Обрабатывает TRACEROUTE_APP ответы |
| `StoreForwardPacketHandlerImpl.kt` | Store & Forward |
| `MqttManagerImpl.kt` | MQTT proxy через устройство |
| `MessageFilterImpl.kt` | Фильтрация входящих сообщений |
| `HistoryManagerImpl.kt` | История сообщений |

---

### Этап 9 — Реализации репозиториев

**Источник:** `core/data/src/commonMain/kotlin/org/meshtastic/core/data/repository/`
**Назначение:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/data/repository/`

| Файл | Ключевые возможности |
|------|---------------------|
| `NodeRepositoryImpl.kt` | **Центральный репозиторий нод.** `nodeDBbyNum: StateFlow<Map<Int,Node>>` — реактивный словарь всей mesh-сети. Синхронизирует данные из Room с in-memory состоянием. Обновляет `ourNodeInfo` и `myId`. Методы: upsert, delete, updateFavorite, updateNotes, clearNodeDB, installConfig |
| `PacketRepositoryImpl.kt` | Хранит пакеты сообщений в Room. `getContactPackets(contact): Flow<List<Packet>>` для чата |
| `RadioConfigRepositoryImpl.kt` | Хранит и предоставляет Flow конфигурации LoRa, каналов, модулей |
| `MeshLogRepositoryImpl.kt` | Лог всех входящих/исходящих пакетов |
| `QuickChatActionRepositoryImpl.kt` | CRUD для быстрых сообщений |
| `TracerouteSnapshotRepositoryImpl.kt` | Снимки traceroute маршрутов |

---

### Этап 10 — Сервисный слой

**Источник:** `core/service/src/`
**Назначение:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/service/`

#### Общий (platform-agnostic)

| Файл | Назначение |
|------|-----------|
| `SharedRadioInterfaceService.kt` | Мультиплатформенный оркестратор интерфейса. Управляет device address, connection state flows, heartbeat. Делегирует в конкретный RadioTransport |
| `MeshServiceOrchestrator.kt` | **Главный оркестратор запуска.** `start()`: создаёт notification channels → запускает handlers → подключает radioInterfaceService → wire-ит receivedData flow → wire-ит serviceAction flow → loadCachedNodeDB. `stop()`: отменяет все корутины |
| `ServiceRepositoryImpl.kt` | Реализует ServiceRepository. StateFlow для connectionState, clientNotification, errorMessage. SharedFlow для meshPacketFlow (extraBufferCapacity=64). Channel для serviceAction |
| `DirectRadioControllerImpl.kt` | RadioController для in-process использования (Android single-process). Делегирует напрямую в handlers без AIDL |
| `ServiceBroadcasts.kt` | Broadcast-уведомления об изменениях нод и состояния |

#### Android-specific

| Файл | Назначение |
|------|-----------|
| `MeshService.kt` | **Android Foreground Service.** Lifecycle: onCreate (Koin inject + orchestrator.start), onStartCommand, onDestroy (orchestrator.stop). Foreground type: CONNECTED_DEVICE + LOCATION (API 29+). Управляет уведомлением |
| `MeshServiceClient.kt` | **Lifecycle-aware клиент для Activity.** DefaultLifecycleObserver: onStart→bindService, onStop→unbindService. Использует BIND_AUTO_CREATE|BIND_ABOVE_CLIENT. Обновляет serviceRepository при onServiceConnected |
| `AndroidRadioControllerImpl.kt` | RadioController через AIDL (для multi-process конфигурации) |
| `AndroidLocationService.kt` | Foreground location service |
| `AndroidMeshLocationManager.kt` | Управление GPS и отправкой позиции |
| `AndroidMeshWorkerManager.kt` | WorkManager integration |
| `MeshServiceNotificationsImpl.kt` | Уведомление foreground service |
| `AndroidNotificationManager.kt` | Управление системными уведомлениями |
| `workers/SendMessageWorker.kt` | WorkManager worker для надёжной отправки |
| `workers/MeshLogCleanupWorker.kt` | Плановая очистка старых логов |
| `workers/ServiceKeepAliveWorker.kt` | Поддержание сервиса живым |
| `datasource/NodeInfoReadDataSource.kt` | Источник чтения нод (адаптер Room→StateFlow) |
| `datasource/NodeInfoWriteDataSource.kt` | Источник записи нод (адаптер StateFlow→Room) |
| `datasource/SwitchingNodeInfoReadDataSource.kt` | Переключаемый источник чтения |
| `datasource/SwitchingNodeInfoWriteDataSource.kt` | Переключаемый источник записи |

---

### Этап 11 — Dependency Injection (Koin)

**Создать:** `mesh/src/main/kotlin/ru/tcynik/mymesh1/mesh/di/MeshModule.kt`

По аналогии с оригинальными DI-модулями объединить всё в один:

```kotlin
val meshModule = module {
    // Database
    single { MeshtasticDatabase.getDatabase(androidContext()) }
    single { get<MeshtasticDatabase>().nodeInfoDao() }
    single { get<MeshtasticDatabase>().packetDao() }
    single { get<MeshtasticDatabase>().meshLogDao() }

    // DataStore
    single { ChannelSetDataSource(androidContext()) }
    single { LocalConfigDataSource(androidContext()) }
    single { ModuleConfigDataSource(androidContext()) }

    // BLE
    single { KableBleScanner() }
    single { KableBleConnectionFactory() }
    single<BluetoothRepository> { AndroidBluetoothRepository(androidContext()) }
    single<NetworkRepository> { NetworkRepositoryImpl(get(), get()) }

    // Service
    single<ServiceRepository> { ServiceRepositoryImpl() }
    single<RadioInterfaceService> { SharedRadioInterfaceService(get()) }

    // Repositories
    single<NodeRepository> { NodeRepositoryImpl(get(), get(), get()) }
    single<PacketRepository> { PacketRepositoryImpl(get()) }
    single<RadioConfigRepository> { RadioConfigRepositoryImpl(get(), get()) }
    single<MeshLogRepository> { MeshLogRepositoryImpl(get()) }

    // Managers
    single<NodeManager> { NodeManagerImpl(get(), get(), get()) }
    single<PacketHandler> { PacketHandlerImpl(get(), get(), get(), get()) }
    single<CommandSender> { CommandSenderImpl(get()) }
    single<MeshConnectionManager> { MeshConnectionManagerImpl(/*...*/) }
    single<MeshConfigFlowManager> { MeshConfigFlowManagerImpl(/*...*/) }
    single<MeshDataHandler> { MeshDataHandlerImpl(/*...*/) }
    single<MeshMessageProcessor> { MeshMessageProcessorImpl(get(), get()) }
    single<MeshRouter> { MeshRouterImpl(/*...*/) }
    single<MeshActionHandler> { MeshActionHandlerImpl(/*...*/) }
    single<FromRadioPacketHandler> { FromRadioPacketHandlerImpl(/*...*/) }

    // Orchestrator
    single { MeshServiceOrchestrator(/*...*/) }
    single<RadioController> { DirectRadioControllerImpl(/*...*/) }
}
```

**Обновить `MyMeshApplication.kt`:**
```kotlin
modules(commonModule, androidModule, presentationModule, meshModule)
```

---

### Этап 12 — Интеграция с существующим кодом

#### 12.1 Обновить `app/build.gradle.kts`
Добавить зависимость:
```kotlin
implementation(project(":mesh"))
```

#### 12.2 Обновить `shared/build.gradle.kts`
Добавить зависимость:
```kotlin
implementation(project(":mesh"))
```

#### 12.3 Обновить `shared/.../domain/repository/NodeRepository.kt`
Заменить существующий интерфейс-заглушку на использование интерфейса из `:mesh`.
Либо удалить старый интерфейс и везде ссылаться на `ru.tcynik.mymesh1.mesh.repository.NodeRepository`.

#### 12.4 Обновить `shared/.../domain/model/NodeModel.kt`
Удалить. Заменить все использования `NodeModel` на `ru.tcynik.mymesh1.mesh.model.Node`.

#### 12.5 Обновить `shared/.../domain/usecase/node/GetNodesUseCase.kt`
Обновить тип возвращаемых данных: `Flow<List<Node>>` вместо `Flow<List<NodeModel>>`.

#### 12.6 Удалить заглушки из `shared`
- `data/remote/api/MeshApiService.kt` — удалить
- `data/remote/dto/NodeDto.kt` — удалить
- `data/remote/mapper/NodeDtoMapper.kt` — удалить
- `data/repository/NodeRepositoryImpl.kt` — удалить (заменён реализацией из `:mesh`)

#### 12.7 Обновить `app/.../presentation/feature/nodes/NodesViewModel.kt`
Обновить зависимость: использовать `NodeRepository` из `:mesh`.
Тип данных `NodesUiState` обновить для `Node` вместо `NodeModel`.

#### 12.8 Обновить `app/.../MainActivity.kt`
Добавить запуск `MeshService`:
```kotlin
// В onCreate():
MeshServiceClient(lifecycle, serviceRepository).bind(this)
```
Добавить запрос BLE-разрешений при старте.

---

## Порядок выполнения и зависимости

```
[1] Создание модуля mesh (build.gradle, settings, manifest, libs.versions)
     ↓
[2] Protocol Buffers (копирование .proto, Wire генерация)
     ↓
[3] Domain модели (Node, NodeInfo, Channel, Message, утилиты)
     ↓
[4] Интерфейсы репозиториев (RadioInterfaceService, NodeRepository, и др.)
     ↓
    [5] Room Database ──┐
    [6] DataStore ──────┤→ [7] BLE слой → [8] Data менеджеры → [9] Репозитории
                        ↓
                   [10] Сервисный слой
                        ↓
                   [11] DI (Koin MeshModule)
                        ↓
                   [12] Интеграция (app + shared)
```

---

## Критические точки внимания

### BLE UUIDs (Этап 7)
Должны совпадать **побайтово** с константами в устройстве:
```
SERVICE_UUID         = 6ba1b218-15a8-461f-9fa8-5dcae273eafd
TORADIO              = f75c76d2-129e-4dad-a1dd-7866124401e7
FROMRADIO            = 2c55e69e-4993-11ed-b878-0242ac120002
FROMNUM              = ed9da18c-a800-4f66-a670-aa7547e34453
FROMRADIOSYNC        = 888a50c3-982d-45db-9963-c7923769165d
```

### Двухфазный handshake (Этап 8: MeshConnectionManagerImpl + MeshConfigFlowManagerImpl)
При подключении устройство должно пройти:
1. **Stage 1:** отправить `WANT_CONFIG` с config nonce → получить config пакеты
2. **Stage 2:** отправить `WANT_CONFIG` с node_info nonce → получить nodeInfo пакеты → получить `configComplete`

Нарушение последовательности → устройство не начнёт слать данные.

### Framing (Этап 7: StreamFrameCodec)
TCP/Serial соединения требуют обёртки каждого пакета:
`[0x94][0xc3][size_msb][size_lsb][payload bytes...]`
Без этого устройство не распознаёт пакеты.

### Защита от имперсонации (Этап 5: NodeInfoDao)
В `NodeInfoDao.upsert()` есть проверка: если нода с тем же `userId` имеет другой `publicKey` — запись отклоняется. Это protection against node impersonation. Копировать **точно без изменений**.

### NodeInfo vs Node (Этап 3)
- `NodeInfo` — legacy модель, ближе к protobuf структуре
- `Node` — domain модель с вычисляемыми свойствами (isOnline, distance, colors и т.д.)
Маппинг между ними выполняется в `MeshDataMapper.kt`.

---

## Финальный результат

После выполнения всех этапов приложение предоставляет:

| Точка доступа | Тип | Содержимое |
|--------------|-----|-----------|
| `NodeRepository.nodeDBbyNum` | `StateFlow<Map<Int, Node>>` | Все ноды сети: имя, позиция, SNR, RSSI, метрики, lastHeard, isFavorite |
| `NodeRepository.myId` | `StateFlow<String?>` | ID собственного устройства |
| `NodeRepository.myNodeInfo` | `Flow<MyNodeInfo?>` | Информация о собственном устройстве |
| `ServiceRepository.connectionState` | `StateFlow<ConnectionState>` | Текущее состояние: Disconnected/Connecting/Connected |
| `ServiceRepository.meshPacketFlow` | `SharedFlow<MeshPacket>` | Поток всех входящих пакетов |
| `PacketRepository.getContactPackets(id)` | `Flow<List<Packet>>` | История сообщений с нодой |
| `RadioController.sendMessage(...)` | suspend fun | Отправка текстового сообщения |
| `RadioController.requestPosition(nodeNum)` | suspend fun | Запрос позиции у ноды |
| `ServiceRepository.serviceAction` | `Channel<ServiceAction>` | Канал для команд из UI в сервис |

---

## Что НЕ входит в задачу

- UI-экраны списка нод, деталей ноды, чата
- Метрики и графики телеметрии
- Карта с нодами
- Traceroute UI
- Настройки каналов через UI
- iOS-поддержка
- TCP/WiFi интерфейс (реализуется, но опционально — можно оставить NopInterface)
- MQTT proxy (реализуется структурно, но тестирование не обязательно)
