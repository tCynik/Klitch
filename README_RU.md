# Klitch

Офлайн-навигация для активностей на открытом воздухе. Работает автономно как топографическая карта с метками и KMZ-оверлеями. При подключении [Meshtastic](https://meshtastic.org/) BLE-устройства добавляются групповой трекинг, текстовый чат и SOS по меш-радио — без интернета.

---

## Что умеет

**Автономно (без радио):**
- **Топографическая карта** — тайлы OpenTopoMap, полный офлайн
- **Маркер GPS-позиции** — твоё местоположение в реальном времени
- **Гео-метки** — создание и управление точками на карте с именами и координатами
- **KMZ/KML оверлеи** — импорт пользовательских слоёв карты из файлов
- **Follow Me** — карта автоматически следует за твоей позицией
- **Ориентация карты** — режимы «север вверх» и «курс вверх»

**С Meshtastic BLE-устройством:**
- **Автонастройка узла** — нода конфигурируется автоматически при первом подключении, погружение пользователя в технологию Meshtastic не требуется
- **Карта группы** — позиции всех подключённых узлов в реальном времени
- **Текстовый чат** — сообщения по меш-радио группе или конкретному узлу
- **Экстренный SOS** — трансляция GPS-позиции всем узлам по требованию
- **GPS в фоне** — трансляция позиции продолжается при выключенном экране (foreground service)
- **Экран сети** — подключённые узлы, телеметрия, уровень заряда батарей
- **Автоподключение** — переподключается к последнему BLE-узлу при старте

---

## Требования

- Android 7.0+ (API 24)
- Интернет не нужен

**Опционально:** BLE-устройство с прошивкой [Meshtastic](https://meshtastic.org/) (T-Beam, Heltec, RAK WisBlock и другие) — добавляет групповой трекинг, чат и SOS.

---

## Сборка

```bash
git clone https://github.com/tCynik/Klitch.git
cd Klitch
./gradlew assembleDebug
```

Установка APK:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Release-сборка** (без подписи): `./gradlew assembleRelease`

Для подписанных release-сборок скопируй `keystore.properties.example` в `keystore.properties` и заполни данные своего keystore.

---

## Стек технологий

| Компонент | Назначение |
|---|---|
| [MapLibre Native Android](https://github.com/maplibre/maplibre-native) | Рендеринг карты |
| [OpenTopoMap](https://opentopomap.org/) | Топографические тайлы (XYZ, публичный источник) |
| Meshtastic Android (BLE-слой) | Радиосвязь |
| SQLDelight | Локальное хранилище (метки, оверлеи, сообщения) |
| Jetpack Compose | UI |
| Clean Architecture (domain / data / presentation) | Структура приложения |

---

## Структура проекта

```
app/       — Compose UI, ViewModels, NavGraph
mesh/      — интеграция Meshtastic BLE (AIDL, proto, сервис)
shared/    — доменный слой: use cases, репозитории, модели, SQLDelight-схемы
```

---

## Атрибуция

Проект использует код из:

**Meshtastic Android**
Copyright © Meshtastic LLC
Лицензия: GPL-3.0 — https://www.gnu.org/licenses/gpl-3.0.html
Источник: https://github.com/meshtastic/Meshtastic-Android

Модуль `mesh/` содержит `.proto` и `.aidl` файлы, производные от проекта Meshtastic Android.

---

## Лицензия

GPL-3.0 — см. [LICENSE](LICENSE)
