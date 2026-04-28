# Plan: Inactive Contour — Read-Only Chat

**Date**: 2026-04-28
**Status**: Approved

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

### Phase 2 — UI Design

**Goal**: approved visual spec for the inactive state before coding the UI.

**Tasks:**
1. Define the inactive row style: alpha value, optional indicator (lock icon or "неактивен" chip)
2. Design the "Контур неактивен" banner for Chat Tab (replaces `ChatInputBar`)
3. Confirm that unread badge on an inactive contact is still shown (user should see unread count)

**Skill**: `/ui-designer component: inactive channel contact row and inactive chat banner`

**Output**: design tokens and component spec (or explicit "use existing tokens, no new components")

> Token checkpoint: run `/compact` after Phase 2 before starting Phase 3.

### Phase 3 — Implementation

**Goal**: working code, all layers, compiling and behaving correctly on device.

**Order (strict — each step must compile before the next):**

**3.1 Domain**
- `ChatContact`: add `isActive: Boolean = true`

**3.2 Data — DTO**
- `ChatContactDto`: add `isActive: Boolean = true`
- `toDomain()`: pass `isActive`

**3.3 Data — Adapter**
- `MeshToChatAdapter.observeContactsAsFlow()`: inside the CHANNEL branch, after resolving
  `contourId`, add `isActive = contours.find { it.id == contourId }?.isActive ?: false`.
  Inside the PRIVATE branch: `isActive = true`.

**3.4 Presentation — model**
- `ChatFilterItem`: add `isActive: Boolean = true`
- `ChatUiState`: add `isSelectedChatActive: Boolean = true`

**3.5 Presentation — ViewModel**
- `toFilterItem()` mapper: add `isActive = isActive`
- `selectChat()`: after finding the item, set `isSelectedChatActive = item?.isActive ?: true`
  in the `_uiState.update { }` block
- `observeContacts()`: after rebuilding `filterItems`, also update `isSelectedChatActive` for
  the current `selectedChatId` (so it reacts if contour toggles while chat is open)
- `sendMessage()`: add `if (!_uiState.value.isSelectedChatActive) return` as first guard

**3.6 Presentation — Screen**

Filter Tab row (`ChatFilterItemRow` or equivalent):
- Apply `Modifier.alpha(if (item.isActive) 1f else 0.45f)` to the row content, **except the
  unread badge** — it stays at full alpha so the user can see unread count even on inactive contacts

Chat Tab:
- Where `ChatInputBar` is rendered, wrap with:
  ```kotlin
  if (isSelectedChatActive) {
      ChatInputBar(...)
  } else {
      InactiveContourBanner()
  }
  ```
- `InactiveContourBanner`: simple `Surface` row (same height as input bar), centered text
  `"Контур неактивен"`, `MaterialTheme.colorScheme.surfaceVariant` background,
  `MaterialTheme.colorScheme.onSurfaceVariant` text color.

**Skill**: direct coding

**After implementation**: run `/simplify` on all changed files before Phase 5.

### Phase 4 — Testing (deferred)

Unit tests for `MeshToChatAdapter.observeContactsAsFlow()` with inactive contour scenario
can be added in a follow-up. Not blocking for MVP.

### Phase 5 — Integration Review

**Goal**: confirm no Clean Architecture violations.

**Tasks:**
- Verify `isActive` from `Contour` domain model does NOT leak into presentation layer directly
  (only via `ChatFilterItem.isActive` and `ChatUiState.isSelectedChatActive`)
- Verify `MeshToChatAdapter` remains the only file importing `mesh.model.*`

**Skill**: `/architect review: data/chat/adapter/MeshToChatAdapter.kt presentation/feature/chat/`

### Phase 6 — Skill Update Review

- `/architect`: no new patterns introduced — no update needed.
- `/ui-designer`: if Phase 2 produces new tokens (dimmed alpha value, inactive banner spec) — add to Design System. Otherwise note "no changes".
- `/icon-designer`: no new icons — no changes needed.
- `/planner`: no methodology gaps found — no update needed.

### Phase 6b — Project Docs & Memory Update

- Update `CLAUDE.md` status table: Chat row stays ✅ Done (this is an extension, not a new feature)
- Update `.claude/docs/chat.md`: add section "Inactive contour state" describing the behaviour
- Archive this plan: move to `.claude/archive/inactive-contour-readonly-chat.md`
- Memory `project_state.md`: no change needed (Chat feature status unchanged)
- Token log: append `- 2026-04-28: done | tokens: <value>`

### Phase 7 — Commit Preparation

Stage files by name, propose commit message, wait for user confirmation.

Draft commit message:
```
feat(chat): запрет отправки в неактивном контуре, история доступна для чтения
```

## Coordination Map

```
Phase 2: /ui-designer component: inactive channel row + inactive chat banner → [/compact]
Phase 3: [direct coding, 3.1 → 3.6] → /simplify
Phase 5: /architect review: MeshToChatAdapter + chat presentation
Phase 6: [skill update review — check each skill]
Phase 6b: [update chat.md, archive plan, check memory]
Phase 7: [stage by name] → [propose commit] → [wait confirmation] → git commit
```

## Open Questions

~~1. Lock icon~~ — **closed**: no lock icon, text label "неактивен" only.
~~2. Unread badge on inactive contact~~ — **closed**: badge is always shown at full alpha
   (inactive channel can have unread messages and user must see them).
3. **`isActive` reactivity while chat is open**: if a user has the Chat Tab open and then
   someone toggles the contour `isActive` in Settings — the banner should appear/disappear
   live. This is covered by step 3.5 (`observeContacts()` update), but verify timing is correct.

## Change Log

- 2026-04-28: created
