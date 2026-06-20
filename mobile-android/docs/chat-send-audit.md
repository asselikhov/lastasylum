# Chat send / wipe audit (2026-06)

## Fixed in delivery audit pass (2026-06-07)

| Area | Issue | Fix |
|------|-------|-----|
| Dual listener | Overlay `applyOverlayIncomingMessage` bypassed `ChatSocketIngress` → duplicate rows | Unified ingress claim on overlay apply |
| Overlay forward | Hub policy applied before primary defer | Defer check runs first when activity VM is primary |
| Overlay stash | Non-raid early `return` skipped `OverlaySocketMessageStash` | Always `forwardOverlaySocketMessageToViewModel` |
| Dual VM | Fallback + activity VM coexistence disabled defer | Clear fallback on activity bind; defer when resolved VM is primary |
| Send dup | `singleOrNull()` clientMessageId with multi-send | `resolveOutgoingClientMessageId` — guess only when one inflight |
| Backend fanout | Per-user inRoom dedup dropped overlay sockets | Per-socket fanout to `user:{id}` sockets not in `chat:{room}` |
| Gap reconcile | Only 60–120s ObjectId time jumps | Counter-gap + pre-batch known ids; reconnect REST for badged rooms |
| Hub realtime | Primary subscription omitted hub room | Hub added to `realtimeRoomIdsForPrimary` |
| Hub badge | Overlay skipped bump when primary active but hub not subscribed | Bump when primary lacks hub in room set |
| Reconnect | Stale JWT on socket reconnect | `configureReconnectSessionRefresh` + token validity check |
| REST skip | Suppressed refresh while socket disconnected | `socketConnected=false` forces refresh |
| Overlay strip | No REST catch-up after reconnect | `catchUpOverlayRaidStripFromRest` on overlay chat subscribe |
| Rehydrate | Chat pane switch missed session merge | `mergeSessionCacheForSelectedRoom` before rehydrate |
| Reconnect rooms | Partial `room:join` after socket reconnect | One-shot `forceSubscribeAllRoomsOnce` on connect/foreground |
| Fast path | Incoming batch latency on active chat tab | `ChatIncomingSync` uses `Main.immediate` for single-message batches |
| Strip reconnect | Strip only caught up on overlay subscribe | `startOverlayChatConnectionCollector` on socket reconnect |
| Eligible cache | Stale fanout after roster/membership change | `invalidateEligibleUsersCache` on team join/leave + admin membership |
| Dead code | Unused `OverlayChatHistoryPanel`, `shouldOverlayAutoMarkReadSelectedRoom` | Removed |

## Fixed in deep remaining audit (2026-06-20)

| Area | Issue | Fix |
|------|-------|-----|
| Forum outbox P0 | Success never called `markSent` — rows stuck in `sending` | `ForumOutbox.sendOutboxEntry` + `ForumOutboxUiBridge` + stuck-sending recovery |
| Forum mark-read | Network failure silently advanced watermark | `ForumMarkReadCoalescer` parity with chat (Boolean + re-queue) |
| Forum persist | `message:new` → Room only when forum panel open | Service-level persist listener in `CombatOverlayService` |
| Strip fallback | Inbound ingest drop with no REST catch-up | `catchUpOverlayRaidStripFromRest` on drop / retry exhaustion |
| Raid prefetch Q1 | Cold FGS first quick command delayed | `ensureOverlayRaidRoomReadyForSend` at FGS start + game entry |
| Reconnect C3 | Long backoff after airplane mode | `reconnectImmediatelyWithFreshToken` on game entry / app ON_START |
| Forum reconnect | No list/bg catch-up or outbox resume | NavHost + overlay collectors: `syncTopics`, stash drain, `ForumOutboxResumeScheduler` |
| Forum panel R5 | List route skipped mark-read flush on close | Flush registered for all forum routes + `ForumTopicViewModelRegistry.flushAllPendingMarkRead` |
| Team badges | Stale server badge on Team tab | `InboxUnreadReconciler` in `TeamViewModel.refreshSectionBadges` |
| Chat tab pause | Debounced mark-read lost on quick tab switch | `markReadCoalescer.flushAndAwait()` in `onChatTabPausedImpl` |
| FCM registration | Parallel callers, scattered backoff | `PushTokenRegistrationCoordinator` (mutex + dedupe) |
| Telemetry | No FCM receive→notify span | `LatencySpanType.FcmReceiveToNotify` in `SquadRelayFirebaseMessagingService` |

## Remaining risks (low)

- Overlay chat panel intentionally omits mark-all-read and clear-history (main app only).
- `mergeLoadedPageWithExisting(authoritativeEmpty=false)` still keeps local rows when REST returns empty (intentional offline UX).
- Forum pending ids use separate `ForumListMutations` helpers — not unified with `isOptimisticOutgoingMessageId`.
- Optional: add `clearedAt` to `chat:history:cleared` socket payload for offline clients that missed the event.
- Two-device QA pass for full 18+8 scenario matrix remains manual (top 5 automated in `DeepRemainingAuditRegressionTest`).

## Fixed in prior pass

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
| Overlay chat crash | Duplicate LazyColumn key `day:7 июня` when same date label appears twice | `chatTimelineDaySeparatorKey(index, label)` includes timeline index |

## Smoke test checklist

1. Send text in raid room — single row for sender, peers see one message.
2. Send 5 rapid messages — exactly 5 rows, no duplicates.
3. Send overlay quick command — HUD stays visible, no crash.
4. Admin clears all chat — sender and peers see empty history after refresh.
5. Main app + overlay simultaneously — hub message: one list row, badge correct.
6. Receiver in game (overlay): raid strip + panel show all peer messages.
7. Receiver off chat tab in quiet room — badge + full history on open.
8. Reconnect after API sleep — missed messages visible within ~5s.
