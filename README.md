# Klitch

[Документация на русском](README_RU.md)

Offline Android navigation app for outdoor activities. Works standalone as a topo map with markers and KMZ overlays. Optionally connects to a [Meshtastic](https://meshtastic.org/) BLE device to add group tracking, text chat, and SOS over mesh radio — no internet required.

---

## What it does

**Standalone (no radio required):**
- **Topographic map** — OpenTopoMap tiles, full offline use
- **GPS position marker** — your live position on the map
- **Geo marks** — drop and manage map annotations with names and coordinates
- **KMZ/KML overlays** — import custom map overlays from files
- **Follow Me** — map auto-centers on your moving position
- **Map orientation** — compass-up and heading-up modes

**With Meshtastic BLE device:**
- **Zero-config setup** — node is configured automatically on first connect, no Meshtastic expertise required
- **Live group map** — real-time positions of all connected nodes
- **Text chat** — mesh radio messaging to the group or individual nodes
- **Emergency SOS** — broadcast GPS position to all nodes on demand
- **Background GPS** — position broadcasts continue with screen off (foreground service)
- **Network screen** — view connected nodes, telemetry, battery levels
- **Auto-connect** — reconnects to last BLE node on app start

---

## Requirements

- Android 7.0+ (API 24)
- No internet required

**Optional:** [Meshtastic](https://meshtastic.org/)-compatible BLE device (T-Beam, Heltec, RAK WisBlock, etc.) with Meshtastic firmware — enables group tracking, chat, and SOS.

---

## Build

```bash
git clone https://github.com/tCynik/Klitch.git
cd Klitch
./gradlew assembleDebug
```

Install the APK:
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Release build** (without signing): `./gradlew assembleRelease`

To sign release builds, copy `keystore.properties.example` to `keystore.properties` and fill in your keystore credentials.

---

## Tech stack

| Component | What |
|---|---|
| [MapLibre Native Android](https://github.com/maplibre/maplibre-native) | Map rendering |
| [OpenTopoMap](https://opentopomap.org/) | Topographic tile source (XYZ, public) |
| Meshtastic Android (BLE layer) | Radio connectivity |
| SQLDelight | Local storage (markers, overlays, messages) |
| Jetpack Compose | UI |
| Clean Architecture (domain / data / presentation) | App structure |

---

## Project structure

```
app/       — Compose UI, ViewModels, NavGraph
mesh/      — Meshtastic BLE integration (AIDL, proto, service)
shared/    — domain layer: use cases, repositories, models, SQLDelight schemas
```

---

## Attribution

This project incorporates code from:

**Meshtastic Android**
Copyright © Meshtastic LLC
License: GPL-3.0 — https://www.gnu.org/licenses/gpl-3.0.html
Source: https://github.com/meshtastic/Meshtastic-Android

The `mesh/` module contains `.proto` and `.aidl` files derived from the Meshtastic Android project.

---

## License

GPL-3.0 — see [LICENSE](LICENSE)
