# Klitch — контекст для Claude

## Спецификация проекта

Полное техническое задание и продуктовая документация в Obsidian:
`C:\Users\tcynik\Documents\Obsidian Vault\Проекты\MeshMap`

Структурированный product brief для Claude (деривирован из Obsidian):
`docs/specs/product-brief.md`

---

## Текущее состояние проекта

**Этап**: MVP — proof of concept пройден, реализация ключевых фич

**Статус фич:**

| Фича | Статус |
|---|---|
| Scaffold Clean Architecture + пакетная структура | ✅ Done |
| Карта (MapLibre + OpenTopoMap) | ✅ Done |
| Стартовая позиция карты из последних координат | ✅ Done |
| GPS-маркер позиции пользователя | ✅ Done |
| Отправка GPS телефона на радиоузел (setFixedPosition) | ✅ Done |
| GPS в фоне (GpsService foreground service) | ✅ Done |
| Импорт KMZ/KML оверлеев (SAF + SQLDelight) | ✅ Done |
| Рендеринг KMZ/KML оверлеев на карте | ✅ Done |
| Автоподключение к последнему узлу при старте | ✅ Done |
| Callsign gate on connect (позывной обязателен) | ✅ Done |
| Визуальный язык (цвет, типографика, HUD) | ⏳ Deferred (после отработки userFlow) |
| BLE-узлы на карте (telemetry) | ⬜ Planned |
| Чат | ✅ Done |
| Маркеры / заметки | ✅ Done |
| Список гео-меток | ✅ Done |
| Контуры (Primary + SOS + Exclusive + UI) | ✅ Done |
| Emergency SOS (кнопка SOS, трансляция геопозиции) | ✅ Done |
| Настройки кэша тайлов (OkHttp duration modes) | ✅ Done |
| Настройки экрана (размер маркеров + геоточек, имена точек) | ✅ Done |
| Блокировка ориентации экрана | ✅ Done |
| Настройки ноды | ⬜ Planned |
| Инжектируемый Logger (MT/<Feature>) | ✅ Done |
| HUD Menu Drawer (выдвижная шторка навигации) | ✅ Done |
| Follow Me (слежение за позицией пользователя) | ✅ Done |
| Привязка ориентации карты (компас + heading-up) | ✅ Done |
| Статус соединения в HUD (connection label + батарея ноды) | ✅ Done |
| Кнопки масштаба на карте | ✅ Done |
| Экран «Сеть» (BLE + телеметрия) | ✅ Done |
| Настройки сети (конфиг ноды + location) | ✅ Done |
| MainViewModel рефакторинг (5 VM + HudStateMapper) | ✅ Done |
| Lifecycle GpsService (условный старт/стоп по событиям) | ✅ Done |

---

## Skills

Все скиллы находятся в `.claude/commands/`:

| Скилл | Назначение |
|---|---|
| `/planner` | Декомпозиция фич и задач на фазы, координация скиллов |
| `/architect` | Архитектурный дизайн, проверка Clean Architecture |
| `/ui-designer` | Дизайн-система: цвета, типографика, компоненты, UX-паттерны |
| `/icon-designer` | Иконки в стиле MeshIconButton (делегировано из `/ui-designer`) |
| `/tester` | Шаблоны тестов: FlowUseCase/Turbine, ViewModel/MockK, SQLDelight integration |
| `/iterate` | Дебаг и итерация по готовым фичам: диагностика багов, скоупные расширения, ревью состояния фичи |
| `/debug-log` | Лог дебага по фиче: сохранение и просмотр истории попыток фиксов (`save:` / `show:`) |

---

## Правила работы

### Дебаг
- При начале дебага любой фичи — всегда проверять `docs/debug/<feature-slug>.md` на наличие истории фиксов
- Если история существует — прочитать и сообщить о повторяющихся паттернах до начала диагностики

### Язык общения
- Запрос на английском → ответ на английском
- Запрос на любом другом языке → ответ на русском
- Украинский язык **запрещён** полностью и при любых обстоятельствах

### Коммиты
- Сообщения коммитов пишутся **только на русском языке**
- Строка `Co-Authored-By: Claude ...` **не добавляется** ни при каких обстоятельствах

### Структура файлов
- Data-классы (модели, DTO) должны находиться в поддиректории `models/` внутри соответствующего пакета
- Composable-файлы и data-классы **не смешиваются** в одной директории
- Каждый data-класс — в **отдельном файле** (один класс = один файл)
- Пример: `presentation/feature/main/osd/models/HudButtonSlotItem.kt`

---

## База знаний Meshtastic

Технические справочники по протоколу: `docs/knowledge/meshtastic/`

| Файл | Содержание |
|---|---|
| `meshtastic-node-model.md` | Структура `Node`: идентификаторы, `User`, `Position`, `isOnline`, `DeviceMetrics` |
| `meshtastic-contacts-channels.md` | `contactKey` формат, адресация `^all`/`!nodeId`, каналы 0–7, отправка сообщения |
| `meshtastic-packets.md` | `MeshPacket`, `DataPacket`, `PortNum` enum, Position-поля, Waypoint, Admin-команды |

---

## Документация

Живая документация по реализованным фичам: `docs/features/`
Архив завершённых планов: `docs/archive/`
Дебаг-история по фичам: `docs/debug/`

| Фича | Документ |
|---|---|
| Geo Marks | `docs/features/geo-marks.md` |
| Geo Marks List | `docs/features/geo-marks-list.md` |
| App Structure | `docs/features/app-structure.md` |
| Map Start Position | `docs/features/map-start-position.md` |
| GPS User Position Marker | `docs/features/gps-user-position-marker.md` |
| Phone GPS to Radio | `docs/features/phone-gps-to-radio.md` |
| HUD Structure | `docs/features/hud-structure.md` |
| Directional Node Markers | `docs/features/directional-node-markers.md` |
| MapLibre Layer Architecture + Fonts | `docs/features/map-layer.md` |
| Location Sharing Config | `docs/features/location-sharing-config.md` |
| GPS Pipeline Debug (незавершён) | `docs/debug/gps-pipeline-debug.md` |
| KMZ/KML Import | `docs/features/kmz-kml-import.md` |
| KMZ/KML Rendering | `docs/features/kmz-kml-rendering.md` |
| GPS Background Service | `docs/features/gps-background-service.md` |
| Universal Orientation | `docs/features/universal-orientation.md` |
| Auto-connect on Start | `docs/features/auto-connect-on-start.md` |
| Chat | `docs/features/chat.md` |
| Contours (единый источник) | `docs/features/contours.md` |
| Packet Channel Attribution | `docs/features/packet-channel-attribution.md` |
| Emergency SOS | `docs/features/emergency-sos.md` |
| Channel Sync Validation | `docs/features/channel-sync-validation.md` |
| User Settings Write Mechanics | `docs/features/user-settings-write-mechanics.md` |
| Tile Cache Settings | `docs/features/tile-cache-settings.md` |
| Geo Marks Display Settings | `docs/features/geo-marks.md` |
| Autonomous Beacon (concept) | `docs/features/autonomous-beacon.md` |
| Node PKC Key Management | `docs/features/node-key-management.md` |
| App Logger | `docs/features/logger.md` |
| HUD Menu Drawer | `docs/features/hud-menu-drawer.md` |
| Map Orientation Binding | `docs/features/map-orientation.md` |
| Screen Orientation Lock | `docs/features/screen-orientation-lock.md` |
| Callsign (позывной как сущность) | `docs/features/callsign.md` |
| Callsign Gate on Connect (реализация) | `docs/features/callsign-gate-on-connect.md` |
| Network Screen | `docs/features/network-screen.md` |
| Filter-at-Input (замена Slot Discovery) | `docs/features/position-channel-slot-discovery.md` |
| GPS Position Staleness Pipeline | `docs/features/gps-position-staleness.md` |
| Background Position Pipeline (BleBackgroundPolicy) | `docs/features/background-position-pipeline.md` |
| Main Screen ViewModel Architecture | `docs/features/main-viewmodel-architecture.md` |
| Foreground Service Lifecycle | `docs/features/foreground-service-lifecycle.md` |
| Node Provisioning Auto-Config | `docs/features/node-provisioning-autoconfig.md` |

---

## Активные планы

| План | Файл | Статус |
|---|---|---|
| HUD Node Status Indicator | `docs/archive/hud-node-status-indicator.md` | Done |
| Connection Status HUD Info | `docs/archive/connection-status-hud-info.md` | Done |
| Map Orientation Binding | `docs/archive/map-orientation.md` | Done |
| Course-Up Mode | `docs/archive/course-up.md` | Done |
| Settings Screens Split | `docs/archive/settings-screens-split.md` | Done |
| Node PKC Key Management | `docs/archive/node-key-management.md` | Done |
| Chat Feature | `docs/archive/chat-feature-plan.md` | Done |
| Node Provisioning | `docs/archive/node-provisioning.md` | Done |
| Contour Concept | `docs/archive/contour-concept.md` | Archived |
| User & Channels Settings | `docs/archive/user-and-channels-settings.md` | Archived |
| Logical Channels Management | `docs/archive/logical-channels-management.md` | Archived |
| Emergency SOS | `docs/archive/emergency-sos.md` | Archived |
| Implementation Roadmap | `docs/archive/implementation-roadmap.md` | Archived |
| Channel Sync Validation | `docs/archive/channel-sync-validation.md` | Archived |
| User Settings Write Mechanics | `docs/archive/user-settings-write-mechanics.md` | Archived |
| Geo Nodes Tab | `docs/archive/geo-nodes-tab.md` | Archived |
| Node Markers on Map | `docs/archive/node-markers-on-map.md` | Archived |
| Settings Refactor | `docs/archive/settings-refactor.md` | Archived |
| Tile Cache Settings | `docs/archive/tile-cache-settings.md` | Archived |
| App Logger | `docs/archive/logger.md` | Archived |
| HUD Portrait Refactor | `docs/archive/hud-portrait-refactor.md` | Archived |
| HUD Menu Drawer | `docs/archive/hud-menu-drawer.md` | Archived |
| Follow Me | `docs/archive/follow-me.md` | Archived |
| Geo Marks Bottom Sheet | `docs/archive/geo-marks-sheet.md` | Done |
| Geo Marks Display Settings | `docs/archive/geo-marks-display-settings.md` | Done |
| Geo Marks List | `docs/archive/geo-marks-list.md` | Done |
| Geo Marks UI Completion | `docs/archive/geo-marks-ui-completion.md` | Done |
| Screen Orientation Lock | `docs/archive/screen-orientation-lock.md` | Done |
| Background Position Pipeline | `docs/archive/background-position-pipeline.md` | Done |
| Packet Channel Attribution | `docs/archive/packet-channel-attribution.md` | Archived |
| Node GPS Position Source | `docs/plans/node-gps-position-source.md` | Planned |
| Node GPS Battery Savings | `docs/plans/node-gps-battery-savings.md` | Planned |
| Foreground Service Lifecycle | `docs/archive/foreground-service-lifecycle.md` | Done |
| Contours Redesign (Primary + SOS + Isolation) | `docs/archive/contours-redesign.md` | Done |
| MainViewModel Split | `docs/archive/mainviewmodel-split.md` | Done |
| English Localization | `docs/archive/english-localization.md` | Done |
