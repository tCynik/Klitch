# Channel & Settings Working Contract

**Date**: 2026-05-13  
**Status**: Active

## Purpose

This document defines a single source of truth for:
- when app settings are written locally vs written to node
- when user confirmation is required
- when node reboot is required
- how sync-required state is surfaced to user

It consolidates behavior from:
- `channel-sync-validation.md`
- `logical-channels-management.md`
- `user-settings-write-mechanics.md`

## Core Principles

1. **No silent disruptive writes**
   - Writes that require node reboot must be explicit and user-confirmed.
2. **Safe connected behavior**
   - Connected state prefers deterministic sync rules over implicit partial updates.
3. **Emergency contour is special**
   - Emergency is fixed to slot 0, has separate active flag storage, and has geo protection side effects.
4. **App-level and node-level geo protection are both required**
   - Blocking only at one layer is not sufficient.

## Entities and Ownership

- **Contour**: logical channel model (`name + psk + isActive + transport`)
- **Emergency contour (`DefaultContour`)**:
  - not stored in DB
  - `isActive` stored in DataStore (`emergency_is_active`)
  - pinned to node slot 0 (`LongFast`, `AQ==`)
- **Basic contour (`DefaultActiveContour`)**:
  - seeded DB row on first run
  - active by default
- **App user display name**:
  - local source: `AppUserRepository`
  - node source: `deviceConfig.longName`

## Sync Validation Contract

`CheckNodeSyncUseCase` returns:
- `InSync`
- `NeedsSync`

Node is considered `NeedsSync` if at least one condition is true:
1. slot 0 hash is not Emergency hash
2. any active non-emergency contour has no enabled matching slot (`index != 0`) with `positionPrecision > 0`
3. app display name is not blank and differs from node long name

Special case:
- if node channels list is empty, result is `InSync` (skip until data arrives)

Inactive contours are not part of validation.

## Sync Apply Contract

On explicit user confirmation (`SyncContoursOnConnectUseCase` path):
1. ensure Emergency on slot 0 (write only if mismatch)
2. ensure each active non-emergency contour exists on node
   - reuse already synced slot when possible
   - otherwise write to free slot
   - if no free slot: stop contour writes and surface no-free-slot outcome
3. ensure owner name on node equals app display name (when non-blank and changed)
4. caller triggers `rebootNode()`

## Silent Provisioning Contract

`NodeProvisioningUseCase` runs automatically on connect from main flow:
- writes channels only
- does **not** write owner name
- does **not** trigger reboot
- does not perform precision-only rewrites

Rationale: avoid silent reboot without user intent.

## Write Mechanics Matrix

| Setting | When applied | Reboot required | Leave dialog |
|---|---|---|---|
| GPS broadcast toggle | Immediately on toggle (if connected, writes to node now) | No | No |
| Callsign (connected) | On leave-confirm save OR sync-confirm dialog | Yes | Yes for leave flow, no for sync dialog |
| Callsign (disconnected) | Local save on leave | No | No |

Rule for new settings on User Settings screen:
- always classify as one of: `immediate`, `deferred+reboot`, `local-only`
- document that class in docs

## Leave Flow Contract (User Settings)

When user navigates back:
- no unsaved changes -> exit immediately
- unsaved + disconnected -> save local and exit
- unsaved + connected -> show leave dialog:
  - **Save** -> `writeOwner + saveAppUser + rebootNode + exit`
  - **Reset** -> restore local display name from repository and exit

Implementation rule:
- `navigateBack` flow is single source for actual back navigation
- `BackHandler` intercepts system back only while unsaved changes exist

## Sync Dialog Contract

Sync dialog may appear outside settings (for example on connect or contour activation path) when `NeedsSync` is detected.

On confirm:
- apply sync writes (channels + owner if needed)
- reboot node
- in MeshTest flow, set reboot UI state immediately before reboot call

On dismiss:
- set in-memory sync-required flag to true
- do not auto-clear until successful sync flow or explicit clear path

## MeshTest Status Bar Contract During Reboot

When reboot is initiated from MeshTest sync confirmation:
- status bar must switch immediately to reboot state (no transient return to connected label)
- status text format: `{nodeName} - Перезагрузка...`
- if node name is not available yet, fallback text is `Перезагрузка...`
- reboot state remains active until reboot cycle completion is observed:
  - first non-connected status (`Disconnected`/`Scanning`/`Connecting`/`Error`)
  - then `Connected` again

## HUD Contract for Connected State

Radio info slot priority:
1. `syncRequired == true` -> show "требуется синхронизация" (red)
2. no channel on node -> show "Настройте канал" (red)
3. connected label enabled -> show paired short name (green)
4. otherwise empty

This guarantees sync risk visibility has top priority over cosmetic connection labels.

## Geo Protection Contract

When Emergency is active:
- **App-level**: outbound geo packets are blocked; inbound geo tied to inactive channels is dropped
- **Node-level**: node position broadcast is disabled (`position_broadcast_secs = UInt.MAX_VALUE`)

When Emergency is inactive and GPS broadcast is enabled:
- node position broadcast is enabled in ready mode (`position_broadcast_secs = 60`, `position_precision = 13`)

Additional safety:
- disable path also sets `position_broadcast_smart_enabled = false`

## Operational Edge Cases

1. **Unknown incoming slot**
   - drop packet with warning log, no user-facing error
2. **No free node slots**
   - write path stops for remaining contours
   - warning/event path should inform user
3. **Disconnected while editing callsign**
   - local save only, no reboot
4. **Empty node channels right after connect**
   - sync check deferred by returning `InSync` until channels arrive

## Invariants

- Emergency contour is never editable/deletable in UI.
- Slot 0 identity is reserved for Emergency.
- Inactive contours do not participate in send/receive and are not sync-validated.
- Owner write requiring reboot is never executed silently from auto-provisioning.

## Implementation Checklist (for future changes)

When introducing any new channel/settings behavior, verify all points:
- update this contract and feature-specific doc
- define write class (`immediate` / `deferred+reboot` / `local-only`)
- confirm connected/disconnected behavior parity
- confirm sync dialog behavior (confirm/dismiss)
- confirm HUD priority is still correct
- add or update tests in domain + viewmodel layers

