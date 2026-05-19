# Performance baseline (SquadRelay)

Template for before/after comparisons. Fill **After** column when validating a perf PR.

| Metric | How to measure | Before (pre-audit) | After |
|--------|----------------|-------------------|-------|
| Overlay jank in game | Android Studio Profiler → CPU, 5 min raid with panel | TBD | |
| `tickGameGate` duration | Log tag `CombatOverlayService` / Systrace | ~900ms poll when active | |
| Open overlay chat panel | Time around `showOverlayChatTeamPanel` | Full `ChatScreen` bootstrap | |
| Chat burst (20 msgs) | Main thread frames during socket flood | Full timeline rebuild | |
| `GET /chat/rooms` | Server timing / logs | N+1 unread counts | |
| Cold start → first message | Stopwatch, 2 devices | TBD | |

**Build:** `devDebug`  
**Backend:** Render `lastasylum-backend`  
**Devices:** note model + Android version here when measuring.

## Regression checklist

- [ ] Overlay 30 min in game: no sustained jank
- [ ] Burst chat in raid room: scroll stays smooth
- [ ] Open/close overlay chat: no connection flicker
- [ ] Token refresh (force 401): single reconnect in logs
- [ ] Excavation push to off-game ally
- [ ] MIUI/HyperOS if available: game gate hide/show
