# MeshTactics — контекст для Claude

## Спецификация проекта

Полное техническое задание и продуктовая документация в Obsidian:
`C:\Users\tcynik\Documents\Obsidian Vault\Проекты\MeshMap`

Структурированный product brief для Claude (деривирован из Obsidian):
`.claude/specs/product-brief.md`

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
- При начале дебага любой фичи — всегда проверять `.claude/debug/<feature-slug>.md` на наличие истории фиксов
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

Технические справочники по протоколу: `.claude/knowledge/meshtastic/`

| Файл | Содержание |
|---|---|
| `meshtastic-node-model.md` | Структура `Node`: идентификаторы, `User`, `Position`, `isOnline`, `DeviceMetrics` |
| `meshtastic-contacts-channels.md` | `contactKey` формат, адресация `^all`/`!nodeId`, каналы 0–7, отправка сообщения |
| `meshtastic-packets.md` | `MeshPacket`, `DataPacket`, `PortNum` enum, Position-поля, Waypoint, Admin-команды |

---

## Документация

Живая документация по реализованным фичам: `.claude/docs/`
Архив завершённых планов: `.claude/archive/`
Дебаг-история по фичам: `.claude/debug/`

| Фича | Документ |
|---|---|
| Geo Marks | `.claude/docs/geo-marks.md` |
| Geo Marks List | `.claude/docs/geo-marks-list.md` |
| App Structure | `.claude/docs/app-structure.md` |
| Map Start Position | `.claude/docs/map-start-position.md` |
| GPS User Position Marker | `.claude/docs/gps-user-position-marker.md` |
| Phone GPS to Radio | `.claude/docs/phone-gps-to-radio.md` |
| HUD Structure | `.claude/docs/hud-structure.md` |
| Directional Node Markers | `.claude/docs/directional-node-markers.md` |
| MapLibre Layer Architecture + Fonts | `.claude/docs/map-layer.md` |
| Location Sharing Config | `.claude/docs/location-sharing-config.md` |
| GPS Pipeline Debug (незавершён) | `.claude/docs/gps-pipeline-debug.md` |
| KMZ/KML Import | `.claude/docs/kmz-kml-import.md` |
| KMZ/KML Rendering | `.claude/docs/kmz-kml-rendering.md` |
| GPS Background Service | `.claude/docs/gps-background-service.md` |
| Universal Orientation | `.claude/docs/universal-orientation.md` |
| Auto-connect on Start | `.claude/docs/auto-connect-on-start.md` |
| Chat | `.claude/docs/chat.md` |
| Contours (единый источник) | `.claude/docs/contours.md` |
| Emergency SOS | `.claude/docs/emergency-sos.md` |
| Channel Sync Validation | `.claude/docs/channel-sync-validation.md` |
| User Settings Write Mechanics | `.claude/docs/user-settings-write-mechanics.md` |
| Tile Cache Settings | `.claude/docs/tile-cache-settings.md` |
| Geo Marks Display Settings | `.claude/docs/geo-marks.md` |
| Autonomous Beacon (concept) | `.claude/docs/autonomous-beacon.md` |
| Node PKC Key Management | `.claude/docs/node-key-management.md` |
| App Logger | `.claude/docs/logger.md` |
| HUD Menu Drawer | `.claude/docs/hud-menu-drawer.md` |
| Map Orientation Binding | `.claude/docs/map-orientation.md` |
| Screen Orientation Lock | `.claude/docs/screen-orientation-lock.md` |
| Callsign (позывной как сущность) | `.claude/docs/callsign.md` |
| Callsign Gate on Connect (реализация) | `.claude/docs/callsign-gate-on-connect.md` |
| Network Screen | `.claude/docs/network-screen.md` |
| positionChannel & Slot Discovery | `.claude/docs/position-channel-slot-discovery.md` |
| GPS Position Staleness Pipeline | `.claude/docs/gps-position-staleness.md` |

---

## Активные планы

| План | Файл | Статус |
|---|---|---|
| HUD Node Status Indicator | `.claude/archive/hud-node-status-indicator.md` | Done |
| Connection Status HUD Info | `.claude/archive/connection-status-hud-info.md` | Done |
| Map Orientation Binding | `.claude/archive/map-orientation.md` | Done |
| Course-Up Mode | `.claude/archive/course-up.md` | Done |
| Settings Screens Split | `.claude/archive/settings-screens-split.md` | Done |
| Node PKC Key Management | `.claude/archive/node-key-management.md` | Done |
| Chat Feature | `.claude/plans/chat-feature-plan.md` | Done |
| Node Provisioning | `.claude/archive/node-provisioning.md` | Done |
| Contour Concept | `.claude/archive/contour-concept.md` | Archived |
| User & Channels Settings | `.claude/archive/user-and-channels-settings.md` | Archived |
| Logical Channels Management | `.claude/archive/logical-channels-management.md` | Archived |
| Emergency SOS | `.claude/archive/emergency-sos.md` | Archived |
| Implementation Roadmap | `.claude/archive/implementation-roadmap.md` | Archived |
| Channel Sync Validation | `.claude/archive/channel-sync-validation.md` | Archived |
| User Settings Write Mechanics | `.claude/archive/user-settings-write-mechanics.md` | Archived |
| Geo Nodes Tab | `.claude/archive/geo-nodes-tab.md` | Archived |
| Node Markers on Map | `.claude/archive/node-markers-on-map.md` | Archived |
| Settings Refactor | `.claude/archive/settings-refactor.md` | Archived |
| Tile Cache Settings | `.claude/archive/tile-cache-settings.md` | Archived |
| App Logger | `.claude/archive/logger.md` | Archived |
| HUD Portrait Refactor | `.claude/archive/hud-portrait-refactor.md` | Archived |
| HUD Menu Drawer | `.claude/archive/hud-menu-drawer.md` | Archived |
| Follow Me | `.claude/archive/follow-me.md` | Archived |
| Geo Marks Bottom Sheet | `.claude/archive/geo-marks-sheet.md` | Done |
| Geo Marks Display Settings | `.claude/archive/geo-marks-display-settings.md` | Done |
| Geo Marks List | `.claude/archive/geo-marks-list.md` | Done |
| Geo Marks UI Completion | `.claude/archive/geo-marks-ui-completion.md` | Done |
| Screen Orientation Lock | `.claude/archive/screen-orientation-lock.md` | Done |
