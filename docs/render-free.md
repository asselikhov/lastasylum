# Render Free tier — SquadRelay backend

Single NestJS instance on Render **Free** (~512 MB RAM, sleeps after inactivity). Design for one process; horizontal scale is a separate phase after upgrading the plan.

## Environment

| Variable | Purpose |
|----------|---------|
| `PORT` | Set by Render (usually `10000`) |
| `JWT_SECRET` | Auth (required) |
| `MONGODB_URI` | Atlas M0 (external) |
| `ALLOWED_ORIGINS` | CORS for Android / web |
| R2 / storage vars | File uploads (see `storage` module) |

## Operational limits (implemented)

- **Admin lists**: paginated `skip` / `limit` (default 50, max 200); no full `userModel.find({})`.
- **Forum topics**: `.limit(100)` per team; no per-topic `updateOne` on GET.
- **Uploads**: global concurrency queue (2 slots); large APK only where product allows; avoid parallel 120 MB buffers.
- **MongoDB**: indexes on `playerTeamId`, `gameIdentities.serverNumber`, `gameIdentities.playerTeamId`.

## Cold start

Free services spin down after ~15 minutes without traffic. First request after sleep can take **10–30 s**.

**Mitigation (recommended):** external uptime ping every **5–10 minutes** against `GET /` health (not a substitute for upgrading, but reduces user-facing cold starts).

## Monitoring

- Render dashboard: memory and CPU.
- Application log filter: requests slower than **2 s** (see `main.ts`).
- No Redis / multi-replica on Free — in-memory Socket.IO limits are correct for one instance.

## When to upgrade

Move to Starter+ (or multiple instances) before:

- `@socket.io/redis-adapter` and Redis
- Distributed `@nestjs/throttler` storage
- Load-balanced replicas (`REPLICAS > 1`)

Until then, keep pagination, caps, and upload serialization as the primary scalability tools.
