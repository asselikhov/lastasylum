# Chat send / wipe audit (2026-06)

## Fixed in this pass

| Area | Issue | Fix |
|------|-------|-----|
| Admin wipe | Room/outbox kept messages; empty REST merge resurrected local rows | `ChatHistoryWipe.wipeAllLocalChatData`, `authoritativeEmpty` merge, Room observer guard |
| Admin wipe | Admin device relied on socket only | `AdminViewModel` calls `applyChatHistoryClearedFromServer` after API success |
| Send crash | Socket thread read `messageIdIndex` during confirm | `ChatSocketManager.dispatchMessageOnMain` |
| Send crash | `removePendingOutgoingMessage` / confirm raced with echo | `synchronized(chatMutationLock)` on block, remove, confirm UI commit |
| Send dup | Overlay quick command double confirm | `publishQuickCommandToStrip(suppressSenderStrip=true)` skips VM forward |
| Overlay send | VM swap between prepare and HTTP | `overlayQuickCommandSendViewModel` pinned for one tap |
| Overlay UI | Redundant refresh icon in participants panel | Removed header `Refresh` button; pull-to-refresh kept |
| Overlay HUD | Quick command send → game gate hard dismiss, HUD gone | `overlayQuickCommandSendInFlight`, unified 3s soft-hide grace, force visibility restore |
| Overlay HUD | HUD windows not recreated during UI hold | `ensureOverlay*HudWindow` allows attach during hold |
| Outbox | Duplicate HTTP while row still `pending` | Atomic `tryMarkSending` / `tryClaimForSend` in `sendEnqueuedOutbox` |
| Share coords | Bypass outbox, duplicate strip cards | Routed through `postOptimistic` + `sendOverlayRaidQuickCommandHttp` |
| Forum send | Double-tap race on `sending` flag | `Mutex.withLock` + composer `sendEnabled` |
| Badges | IO/main race on seed vs refresh, stale `prev` | Cancel seed on refresh; merge on main; sync coordinator cache |
| Badges | Hub optimistic floor mutated on IO | Clear floor only on main thread |
| Notifications | Stale badge when HUD hidden during hold | Collector starts on HUD attach; refresh respects hold |

## Remaining risks (low)

- `mergeLoadedPageWithExisting(authoritativeEmpty=false)` still keeps local rows when REST returns empty (intentional offline UX).
- Forum pending ids use separate `ForumListMutations` helpers — not unified with `isOptimisticOutgoingMessageId`.
- Background outbox success does not always update visible chat VM (WorkManager path).
- Optional: add `clearedAt` to `chat:history:cleared` socket payload for offline clients that missed the event.

## Smoke test checklist

1. Send text in raid room — single row for sender, peers see one message.
2. Send overlay quick command — HUD stays visible, no crash.
3. Admin clears all chat — sender and peers see empty history after refresh.
4. Participants online panel — no header refresh button; pull-to-refresh still works.
