# MeshTactics — Product Brief

> Derived from Obsidian: `C:\Users\tcynik\Documents\Obsidian Vault\Проекты\MeshMap`
> Keep this file in sync with Obsidian when product decisions change.

---

## What It Is

MeshTactics is an Android navigation and mutual tracking app for outdoor group activities.
Users see a shared tactical map with real-time positions of all group members, communicate via
text, and place markers — all over a local mesh radio network (Meshtastic BLE), with no internet
required in the field.

---

## Target Audience

- Airsoft and tactical games
- Tourism and hiking groups
- Active outdoor recreation
- Potential commercial use (B2B for coordinated field teams)

---

## Core User Scenarios

1. **See the map** — open the app, see your position and group members on a topo map
2. **Track the group** — follow live positions of all connected Meshtastic nodes
3. **Communicate** — send text messages to the group via mesh radio
4. **Mark points** — drop map annotations (waypoints, notes, zones)
5. **Navigate** — use map tools: zoom, pan, cursor mode, track recording

---

## Map & Geo Sources

| Source | Stage |
|---|---|
| OpenTopoMap (XYZ tiles) | MVP |
| Custom KMZ overlays | Beta 1.0 |
| Soviet military topo (гены штаб) | Beta 1.0 |
| Satellite imagery | Beta 1.0 |
| Overlay layers (grids, markers) | MVP |
| Dynamic user markers (live positions) | MVP |

---

## Transport / Connectivity

| Transport | Stage | Notes |
|---|---|---|
| Meshtastic BLE | MVP | Primary; no internet required |
| Wi-Fi (own server) | Post-MVP | Same domain interfaces |
| Encrypted groups | Post-MVP | Closed groups, access control |

---

## Screen Design Principles

- **Landscape-only** orientation (locked at manifest level)
- **Full-screen map** — the entire viewport is the map
- **Left column** (modes/tools): follow-user, map tools, track recording, cursor mode
- **Right column** (settings/access): main menu, app settings, map source, object filters, group menu, map annotations, chat
- **Button style**: icon-only, transparent background (`MeshIconButton`)
- **Visual approach**: practical and minimal
- **Day/Night mode**: switchable (exact implementation TBD)

---

## MVP Scope (current)

**In MVP:**
- MapLibre map with OpenTopoMap tiles
- User's own GPS position as marker on map
- Meshtastic BLE connection and node discovery
- Group members' positions as markers (from Meshtastic telemetry)
- Basic text chat via Meshtastic
- Map markers / annotations (local SQLDelight storage)
- Node status and settings screen
- Single hardcoded XYZ tile source (no switcher)

**Explicitly NOT in MVP:**
- Tile source switcher UI
- Tile caching / offline map downloads
- KMZ / MBTiles import
- Wi-Fi transport
- Encrypted closed groups
- Custom animated user position icon (TODO left in code)
- Visual Language definition (deferred to after core userFlow is working)

---

## Key Technical Constraints

- **No internet dependency** in core field use case (Meshtastic is local BLE mesh)
- **Min SDK 24** — broad device support for field hardware
- **KMP-ready architecture** but KMP migration deferred to post-Beta-1.0
- **MapLibre** (not Google Maps, not Mapbox) — open license, custom tile URLs, no API key
- **MeshTest screen** stays until all Meshtastic capabilities migrated to proper feature screens
