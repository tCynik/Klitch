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
| Визуальный язык (цвет, типографика, HUD) | ⏳ Deferred (после отработки userFlow) |
| BLE-узлы на карте (telemetry) | ⬜ Planned |
| Чат | ⏳ In Progress |
| Маркеры / заметки | ✅ Done |
| Настройки ноды | ⬜ Planned |

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

---

## Правила работы

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

| Фича | Документ |
|---|---|
| Geo Marks | `.claude/docs/geo-marks.md` |
| App Structure | `.claude/docs/app-structure.md` |
| Map Start Position | `.claude/docs/map-start-position.md` |
| GPS User Position Marker | `.claude/docs/gps-user-position-marker.md` |
| Phone GPS to Radio | `.claude/docs/phone-gps-to-radio.md` |
| HUD Structure | `.claude/docs/hud-structure.md` |
| Directional Node Markers | `.claude/docs/directional-node-markers.md` |
| GPS Sending Logic & Location Settings | `.claude/docs/fix-gps-sending-logic.md` |
| KMZ/KML Import | `.claude/docs/kmz-kml-import.md` |
| KMZ/KML Rendering | `.claude/docs/kmz-kml-rendering.md` |
| GPS Background Service | `.claude/docs/gps-background-service.md` |
| Universal Orientation | `.claude/docs/universal-orientation.md` |
| Auto-connect on Start | `.claude/docs/auto-connect-on-start.md` |

---

## Активные планы

| План | Файл | Статус |
|---|---|---|
| HUD Node Status Indicator | `.claude/plans/hud-node-status-indicator.md` | In Progress |
| Connection Status HUD Info | `.claude/plans/connection-status-hud-info.md` | Approved |
| Settings Refactor | `.claude/plans/settings-refactor.md` | Approved |
| Geo Nodes Tab | `.claude/plans/geo-nodes-tab.md` | Approved |
| Node Markers on Map | `.claude/plans/node-markers-on-map.md` | Approved |
| Chat Feature | `.claude/plans/chat-feature-plan.md` | In Progress |
