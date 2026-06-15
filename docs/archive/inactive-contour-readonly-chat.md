# Plan: Inactive Contour — Read-Only Chat

**Date**: 2026-04-28
**Status**: Done

## Summary

When a channel contact's underlying Contour has `isActive = false`, the chat UI must prevent
sending while still allowing the user to browse the full message history. The contact remains
in the Filter Tab list at its normal position, visually dimmed, with the input bar replaced by
a non-interactive "Контур неактивен" banner in the Chat Tab.

## Scope

**In scope:**
- Propagate `isActive` from `Contour` → `ChatContactDto` → `ChatContact` → `ChatFilterItem`
- Derive `isSelectedChatActive` in `ChatUiState`
- Dim inactive contacts in Filter Tab row (alpha + optional lock indicator)
- Replace Chat Tab input bar with "Контур неактивен" banner when channel is inactive
- Guard `sendMessage()` in ViewModel (double safety)
- `PRIVATE` contacts are always `isActive = true`

**Out of scope:**
- Emergency contour special handling (separate TODO already exists)
- Inactive contacts in the Archive section (archive rows have no send action — no change needed)
- Sorting / grouping changes (inactive contacts stay in their current position)

## Exact Files to Change

| File | Change |
|---|---|
| `domain/chat/model/ChatContact.kt` | add `isActive: Boolean = true` |
| `data/chat/dto/ChatContactDto.kt` | add `isActive: Boolean = true`; pass in `toDomain()` |
| `data/chat/adapter/MeshToChatAdapter.kt` | CHANNEL: `isActive = contour.isActive`; PRIVATE: `isActive = true` |
| `presentation/feature/chat/model/ChatFilterItem.kt` | add `isActive: Boolean = true` |
| `presentation/feature/chat/ChatUiState.kt` | add `isSelectedChatActive: Boolean = true` |
| `presentation/feature/chat/ChatViewModel.kt` | `toFilterItem()` mapper + `selectChat()` + `sendMessage()` guard |
| `presentation/feature/chat/ChatScreen.kt` | dim inactive row; conditional input bar vs banner |

## Phase Plan

### Phase 1 — Architecture (skipped)

Scope is narrow: a single Boolean field propagated through an existing stack.
No new interfaces, use cases, or repositories. Proceed directly to implementation.

### Phase 2 — UI Design ✅

**Output**: alpha = 0.45f для приглушения строки; InactiveContourBanner — Surface(tonalElevation=4dp), height=56dp, centered bodyMedium onSurfaceVariant; unread badge всегда на полной альфе.

### Phase 3 — Implementation ✅

Все файлы реализованы согласно плану.

### Phase 4 — Testing (deferred)

Unit tests для `MeshToChatAdapter.observeContactsAsFlow()` с неактивным контуром — отложены на follow-up.

### Phase 5 — Integration Review ✅

Нарушений не обнаружено. `isActive` не утекает в presentation напрямую. `MeshToChatAdapter` — единственный файл с `mesh.model.*`.

### Phase 6 — Skill Update Review ✅

- `/architect`: не обновлялся — новые паттерны не введены.
- `/ui-designer`: добавлен раздел "Established Token Usage (Inactive / Disabled States)" + `InactiveContourBanner` в Component Library.
- `/icon-designer`: не обновлялся — новые иконки не нужны.
- `/planner`: не обновлялся — методологических пробелов нет.

### Phase 6b — Project Docs & Memory Update ✅

- `CLAUDE.md`: Chat строка остаётся ✅ Done (расширение, не новая фича).
- `.claude/docs/chat.md`: добавлен раздел "Inactive contour state".
- Этот план перемещён в `.claude/archive/`.

### Phase 7 — Commit Preparation ✅

```
feat(chat): запрет отправки в неактивном контуре, история доступна для чтения
```

## Open Questions

~~1. Lock icon~~ — **closed**: no lock icon, text label "неактивен" only.
~~2. Unread badge on inactive contact~~ — **closed**: badge is always shown at full alpha.
~~3. `isActive` reactivity while chat is open~~ — **closed**: `observeContacts()` пересчитывает `isSelectedChatActive` при каждом обновлении списка контуров.

## Change Log

- 2026-04-28: created
- 2026-04-29: done
