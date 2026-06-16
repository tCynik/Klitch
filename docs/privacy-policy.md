# Privacy Policy / Политика конфиденциальности — Klitch

---

## English

**Effective date:** 2026-06-16
**Developer:** tCynik

### What Klitch does

Klitch is an offline navigation app for outdoor activities. It displays your GPS position on a topographic map and, when connected to a Meshtastic Bluetooth device, shares your position with nearby group members over a local radio mesh network.

### Data we collect and how we use it

**Location (GPS)**
Klitch accesses your device's precise GPS location to show your position on the map and, when a Meshtastic device is connected, to broadcast it over the local Bluetooth mesh radio to other group members. Location data is processed **entirely on-device and over the local radio network**. It is never sent to any remote server, cloud service, or third party.

The app uses background location to continue broadcasting your GPS position while the screen is off (foreground service with a persistent notification). You can stop this at any time by disconnecting the Meshtastic device or stopping the service from the notification.

**Bluetooth**
Klitch uses Bluetooth to discover and connect to Meshtastic-compatible BLE devices. Bluetooth data (device name, connection state) is used solely to establish and maintain the radio link. No Bluetooth identifiers are stored persistently or transmitted over the internet.

**Internet (map tiles only)**
The app requests map tiles from [OpenTopoMap](https://opentopomap.org/) (tile.opentopomap.org) to display the topographic map. This is a standard HTTP request for public geographic data — **no personal data, location, or user identifiers are included in these requests**. Tile loading can be disabled by using the app without an internet connection; cached tiles remain functional offline.

**Local storage**
Geo marks, imported map overlays (KMZ/KML), and chat messages are stored **locally on your device** using an on-device database (SQLDelight). This data never leaves your device unless you explicitly export or share it.

**Notifications**
A persistent notification is shown while the background GPS service is running. No notification data is sent to external services.

### Data we do NOT collect

- No user accounts or registration
- No analytics or usage tracking
- No crash reporting sent to external servers
- No advertising
- No data sold or shared with third parties

### Data transmission over radio

When a Meshtastic device is connected, the following data is transmitted **over the local radio mesh network** to other devices in the same group:

- Your GPS position (while the GPS service is active)
- Text messages you send in the chat
- Geo marks you share explicitly

This transmission happens over short-range Bluetooth and LoRa radio — **not over the internet**. Data reaches only devices physically within radio range that are part of the same Meshtastic network. No copy of this data is sent to any server.

Meshtastic firmware supports AES-256 channel encryption. Users can configure an encrypted channel on their device to protect all data transmitted over the radio link.

### Local storage and retention

Geo marks, imported overlays (KMZ/KML), received chat messages, and app settings are stored **locally on your device**. This data never leaves your device through the internet. You can delete it at any time by clearing the app's data in Android system settings or uninstalling the app.

### Children's privacy

Klitch does not knowingly collect any data from children under 13.

### Changes to this policy

If this policy changes, the updated version will be published at the same URL with a new effective date.

### Contact

Questions: mechaniksir84@gmail.com

---

## Русский

**Дата вступления в силу:** 16 июня 2026 г.
**Разработчик:** tCynik

### Что делает Klitch

Klitch — офлайн-приложение навигации для активностей на открытом воздухе. Показывает вашу GPS-позицию на топографической карте и, при подключении Meshtastic Bluetooth-устройства, передаёт вашу позицию участникам группы по локальной радиосети (меш).

### Какие данные собираются и для чего

**Местоположение (GPS)**
Klitch использует точное GPS-местоположение устройства для отображения вашей позиции на карте. При подключении Meshtastic-устройства позиция также транслируется другим участникам группы по локальной радиосети Bluetooth. Данные о местоположении обрабатываются **исключительно на устройстве и в локальной радиосети** — они никогда не отправляются на удалённые серверы, в облако или третьим лицам.

Приложение использует фоновое местоположение для продолжения трансляции GPS-позиции при выключенном экране (foreground-сервис с постоянным уведомлением). Вы можете остановить это в любой момент, отключив Meshtastic-устройство или остановив сервис через уведомление.

**Bluetooth**
Klitch использует Bluetooth для поиска и подключения к совместимым BLE-устройствам Meshtastic. Данные Bluetooth (имя устройства, состояние соединения) используются исключительно для установки и поддержания радиосвязи. Идентификаторы Bluetooth не сохраняются постоянно и не передаются через интернет.

**Интернет (только тайлы карты)**
Приложение загружает тайлы карты с [OpenTopoMap](https://opentopomap.org/) (tile.opentopomap.org) для отображения топографической карты. Это стандартный HTTP-запрос публичных географических данных — **никакие личные данные, местоположение или идентификаторы пользователя в эти запросы не включаются**. При использовании без интернета загрузка тайлов не выполняется; ранее кэшированные тайлы доступны офлайн.

**Локальное хранилище**
Гео-метки, импортированные оверлеи карты (KMZ/KML) и сообщения чата хранятся **локально на вашем устройстве** в базе данных на устройстве (SQLDelight). Эти данные не покидают устройство, если вы явно не экспортируете или не передаёте их.

**Уведомления**
Постоянное уведомление отображается во время работы фонового GPS-сервиса. Данные уведомлений не передаются внешним сервисам.

### Данные, которые мы НЕ собираем

- Без аккаунтов и регистрации
- Без аналитики и отслеживания действий
- Без отправки отчётов об ошибках на внешние серверы
- Без рекламы
- Данные не продаются и не передаются третьим лицам

### Передача данных по радио

При подключённом Meshtastic-устройстве следующие данные передаются **по локальной радиосети** другим устройствам группы:

- Ваше GPS-местоположение (пока активен GPS-сервис)
- Текстовые сообщения, отправленные в чате
- Явно переданные гео-метки

Передача происходит по Bluetooth и LoRa-радио ближнего действия — **не через интернет**. Данные достигают только устройств, физически находящихся в зоне радиосвязи и входящих в ту же сеть Meshtastic. Никакая копия этих данных не отправляется на сервер.

Прошивка Meshtastic поддерживает шифрование канала (AES-256). Пользователь может настроить шифрованный канал на своём устройстве для защиты всех данных, передаваемых по радио.

### Локальное хранение данных

Гео-метки, импортированные оверлеи (KMZ/KML), полученные сообщения чата и настройки приложения хранятся **локально на вашем устройстве**. Эти данные не покидают устройство через интернет. Вы можете удалить их в любое время, очистив данные приложения в настройках Android или удалив приложение.

### Конфиденциальность детей

Klitch намеренно не собирает данные детей до 13 лет.

### Изменения политики

При изменении политики обновлённая версия будет опубликована по тому же адресу с новой датой вступления в силу.

### Контакт

Вопросы: mechaniksir84@gmail.com
