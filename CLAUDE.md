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
| Визуальный язык (цвет, типографика, HUD) | ⏳ Deferred (после отработки userFlow) |
| BLE-узлы на карте (telemetry) | ⬜ Planned |
| Чат | ⬜ Planned |
| Маркеры / заметки | ⬜ Planned |
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

---

## Активные планы

| План | Файл | Статус |
|---|---|---|
| GPS User Position Marker | `.claude/plans/gps-user-position-marker.md` | Done |
| App Structure | `.claude/plans/app-structure.md` | Done |
| Map Start Position | `.claude/plans/map-start-position.md` | Done |
| Phone GPS to Radio | `.claude/plans/phone-gps-to-radio.md` | Done |
