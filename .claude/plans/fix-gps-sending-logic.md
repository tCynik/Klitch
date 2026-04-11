# Fix: GPS Sending Logic — sendPosition вместо setFixedPosition

**Date**: 2026-04-11
**Status**: Ready for implementation

---

## Проблема

`MeshConnectionManagerImpl` отправляет GPS телефона через `setFixedPosition` (Admin `set_fixed_position`) вместо `sendPosition` (POSITION_APP mesh packet).

**Следствия:**
1. `set_fixed_position` отключает внутренний GPS радио и выставляет статическую позицию в конфиге прошивки
2. Радио бродкастит позицию по таймеру `position_broadcast_secs` (дефолт 15–30 мин), а не на каждое обновление с телефона
3. При движении ноды — позиция меняется на карте только раз в 15–30 мин, несмотря на 30-секундные GPS-обновления
4. Если `session_passkey` не совпадает (после перезапуска прошивки) — Admin-сообщение отклоняется без подтверждения

**Правильный подход (оригинальный Meshtastic API):**
- `sendPosition(pos)` → POSITION_APP пакет → прошивка обновляет позицию в БД и бродкастит немедленно (или по short timer при значительном изменении)
- Для радио с внутренним GPS: `remove_fixed_position` один раз при старте, затем `sendPosition` на каждое обновление

---

## Scope

**In scope:**
- Заменить `setFixedPosition` на `sendPosition` в callback'е `locationManager.start()` (Phase 1)
- Добавить `remove_fixed_position` при старте sharing, чтобы сбросить возможное состояние от предыдущих сессий (Phase 1)
- Скорректировать логику при `shouldProvide=false` (Phase 1)

**Out of scope:**
- Добавление UI toggle (уже реализован в NodeSettings)
- Throttling / интервалы (оставить дефолтные 30 сек из `LocationRepositoryImpl`)
- Подтверждение доставки Admin-сообщений

---

## Анализ текущего кода

### `MeshConnectionManagerImpl.kt` (строки 109–139)

```kotlin
// ТЕКУЩАЯ РЕАЛИЗАЦИЯ (неправильная):
if (shouldProvide) {
    locationManager.start(scope) { pos ->
        commandSender.setFixedPosition(myNodeEntity.myNodeNum, Position(lat, lon, altitude))
        // ↑ Admin set_fixed_position — отключает GPS радио, позиция меняется раз в 15-30 мин
    }
} else {
    locationManager.stop()
    commandSender.setFixedPosition(myNodeEntity.myNodeNum, Position(0.0, 0.0, 0))
    // ↑ Admin remove_fixed_position — правильно при отключении
}
```

### `CommandSenderImpl.sendPosition` (строка 175)

```kotlin
override fun sendPosition(pos: org.meshtastic.proto.Position, destNum: Int?, wantResponse: Boolean) {
    val myNum = nodeManager.myNodeNum ?: return
    val idNum = destNum ?: myNum   // null → отправляет на свой nodeNum
    // Отправляет POSITION_APP packet → прошивка бродкастит по своему алгоритму
}
```

### `CommandSenderImpl.setFixedPosition` (строка 222)

```kotlin
override fun setFixedPosition(destNum: Int, pos: Position) {
    sendAdmin(destNum) {
        if (pos != Position(0.0, 0.0, 0)) AdminMessage(set_fixed_position = meshPos)  // выставить фиксированную
        else AdminMessage(remove_fixed_position = true)                                 // сбросить
    }
}
```

---

## Phase 1 — Исправление логики в MeshConnectionManagerImpl

### Изменение

**Файл**: `mesh/src/main/kotlin/ru/tcynik/meshtactics/mesh/data/manager/MeshConnectionManagerImpl.kt`

**Строки 117–135** — заменить тело `onEach { shouldProvide -> ... }`:

```kotlin
.onEach { shouldProvide ->
    if (shouldProvide) {
        // Сбрасываем fixed_position (если было установлено ранее) — чтобы дать прошивке
        // использовать внешнюю позицию через POSITION_APP, а не конфиг-запись
        Logger.i { "PhoneGPS→radio: removing fixed_position, starting sendPosition pipeline" }
        commandSender.setFixedPosition(
            myNodeEntity.myNodeNum,
            Position(0.0, 0.0, 0),  // → remove_fixed_position Admin message
        )
        locationManager.start(scope) { pos ->
            val lat = Position.degD(pos.latitude_i ?: 0)
            val lon = Position.degD(pos.longitude_i ?: 0)
            Logger.i { "PhoneGPS→radio: sendPosition(lat=$lat lon=$lon speed=${pos.ground_speed} track=${pos.ground_track})" }
            commandSender.sendPosition(pos)
        }
    } else {
        locationManager.stop()
        Logger.i { "PhoneGPS→radio: shouldProvide=false, GPS sharing stopped" }
        // Не вызываем remove_fixed_position — оставляем радио в покое,
        // пусть использует собственный GPS если есть
    }
}
```

### Зачем `remove_fixed_position` при старте

- Предыдущие сессии с `setFixedPosition` оставляют `fixed_position=true` в прошивке
- При `fixed_position=true` прошивка T-Beam/T-Echo/других GPS-радио игнорирует `sendPosition` (использует встроенный GPS)
- Радио без встроенного GPS: `remove_fixed_position` безопасен — просто нет эффекта
- Один Admin-вызов при старте sharing — приемлемо

### Зачем убрать `remove_fixed_position` при `shouldProvide=false`

Текущий код при `shouldProvide=false` вызывает `setFixedPosition(0.0, 0.0, 0)` = `remove_fixed_position`.
Это **корректно**, но при переходе к `sendPosition`-логике — нет смысла явно сбрасывать:
- Если у радио есть GPS — пусть использует свой
- Если нет GPS — позиция просто перестанет обновляться (ожидаемое поведение)

---

## Phase 2 — Проверка в logcat

После изменения проверить:

1. **При включении sharing:**
   ```
   PhoneGPS→radio: removing fixed_position, starting sendPosition pipeline
   CHANNEL_DEBUG: sendAdmin to=... payload=AdminMessage(remove_fixed_position=true)
   PhoneGPS→radio: sendPosition(lat=... lon=... speed=... track=...)
   Sending our position/time to=<myNum> ...
   ```

2. **На карте:** позиция ноды должна обновляться сразу после каждого GPS-события телефона (30 сек интервал), а не раз в 15–30 мин

---

## Phase 3 — Field Test

| Сценарий | Ожидаемый результат |
|---|---|
| Радио без GPS (Heltec, RAK, LILYGO без GPS) | Позиция обновляется каждые 30 сек |
| Радио с GPS (T-Beam, T-Echo) | После `remove_fixed_position` — позиция с телефона, 30 сек |
| Выключить sharing → включить | Позиция продолжает обновляться |
| Перезапуск приложения | `remove_fixed_position` вызывается снова при подключении — OK |

---

## Риски

| Риск | Вероятность | Митигация |
|---|---|---|
| `remove_fixed_position` Admin отклонён (session_passkey) | Низкая (при первом коннекте passkey свежий) | Лог предупреждения, функционально нейтрально |
| На прошивках до 2.3 `remove_fixed_position` не поддерживается | Низкая | Деградация до старого поведения — не хуже текущего |
| `sendPosition` на GPS-радио всё ещё игнорируется после `remove_fixed_position` | Средняя | Проверить в field test; если не работает — вернуться к `setFixedPosition` |

---

## Координация

```
Phase 1: MeshConnectionManagerImpl.kt — изменить callback (1 файл, ~15 строк)
Phase 2: Logcat verification
Phase 3: Field test
```

## Change Log

- 2026-04-11: создан
