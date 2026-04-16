# Передача серии гео-точек через Meshtastic

**Статус**: Draft — промежуточные результаты обсуждения, реализация не начата

---

## Концепция

Передавать серию произвольных гео-точек, упакованных в один стандартный Meshtastic Waypoint.
Backward-совместимость с другими Meshtastic-клиентами **не требуется**.
Сложные объекты и длинные маршруты **не планируются**.

---

## Формат пакета

```
Waypoint {
  lat/lon      = якорь = начало локальной системы координат
  name         = название серии (задаёт пользователь)
  description  = "MT1:<base64(payload)>"
}
```

### Структура payload

```
type:   u8      // тип серии: POINTS=0, ROUTE=1, POLYGON=2
count:  u8      // число дополнительных точек (не считая якоря)
points: []      // массив точек в локальной СК
  x: int16      // метры на восток от якоря
  y: int16      // метры на север от якоря
```

---

## Локальная система координат

- Якорь (первая точка) = начало координат (0, 0)
- X = восток, Y = север, единица = 1 метр
- Диапазон int16: ±32 767 м ≈ ±33 км от якоря — достаточно для тактических задач
- Точность: 1 метр

### Конвертация (плоскоземельное приближение, точно до ~50 км)

```kotlin
fun toLocal(anchor: LatLon, point: LatLon): Pair<Int, Int> {
    val metersPerDegLat = 111_320.0
    val metersPerDegLon = 111_320.0 * cos(Math.toRadians(anchor.lat))
    val x = ((point.lon - anchor.lon) * metersPerDegLon).roundToInt()
    val y = ((point.lat - anchor.lat) * metersPerDegLat).roundToInt()
    return x to y
}

fun fromLocal(anchor: LatLon, x: Int, y: Int): LatLon {
    val metersPerDegLat = 111_320.0
    val metersPerDegLon = 111_320.0 * cos(Math.toRadians(anchor.lat))
    return LatLon(
        lat = anchor.lat + y / metersPerDegLat,
        lon = anchor.lon + x / metersPerDegLon
    )
}
```

---

## Ёмкость

| Параметр | Значение |
|---|---|
| Макс. payload LoRa-пакета | ~237 байт |
| Overhead Waypoint (id, lat, lon, expire, name, headers) | ~40 байт |
| Остаток на description | ~195 байт |
| После Base64 overhead (×3/4) | ~145 байт raw |
| Байт на точку (int16 × 2) | 4 байта |
| **Дополнительных точек в пакете** | **~27** |
| **Итого точек включая якорь** | **~28** |

---

## Открытые вопросы

- **Кодировка description**: Base64 (+33% overhead, UTF-8 safe) vs hex (+100% overhead, читаем при дебаге). Вероятно Base64.
- **UI**: как пользователь создаёт и отправляет серию точек — отдельный сценарий, не обсуждался.
- **Приём**: логика сборки и отображения принятой серии на карте — не обсуждалась.
- **Связь с KMZ/KML**: возможна кнопка "поделиться слоем" из уже импортированного оверлея — не обсуждалась.
