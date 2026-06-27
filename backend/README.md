# SquadRelay Backend

NestJS backend for alliance communication features:
- JWT authentication with alliance roles (R2-R5)
- Access + refresh token sessions
- Realtime chat stream over Socket.IO
- MongoDB persistence for users and messages
- Role-based permissions and moderation controls
- Role-based access; no HTTP rate limits on chat/team messaging (protect at infra if needed)

## Tech stack

- NestJS + TypeScript
- MongoDB (Mongoose)
- JWT (Passport)
- Socket.IO gateway

## Security

- Copy **`.env.example`** → **`.env`** locally only. Never commit `.env` (see repo root [`SECURITY.md`](../SECURITY.md)).
- Use long random `JWT_SECRET` / `JWT_REFRESH_SECRET` in production; set `ALLOWED_ORIGINS` to real origins.
- Passwords are stored as bcrypt hashes; API responses do not include `passwordHash` or refresh tokens.

## Environment variables

Copy `.env.example` to `.env` and fill real values:

```bash
PORT=3000
MONGODB_URI=...
MONGODB_DB_NAME=last_asylum
JWT_SECRET=...
JWT_EXPIRES_IN=7d
JWT_REFRESH_SECRET=...
JWT_REFRESH_EXPIRES_IN=30d

# Sideloaded APK (optional): app calls GET /mobile/android-update. If ANDROID_APK_VERSION_CODE is greater than the installed app’s versionCode and ANDROID_APK_DOWNLOAD_URL is set, the client shows an update prompt and opens the URL in the browser.
ANDROID_APK_VERSION_CODE=2
ANDROID_APK_DOWNLOAD_URL=

# Game patch (optional): app calls GET /mobile/game-patch (JWT). Backend reads the latest release of the
# private repo GITHUB_PATCH_REPO ("owner/repo") — its patches.json index + patched APK asset — using
# GITHUB_TOKEN (fine-grained PAT, that repo only, Contents: read) and returns a short-lived signed
# download URL. The token never reaches the client.
GITHUB_PATCH_REPO=
GITHUB_TOKEN=
```

### Chat image uploads (Cloudflare R2)

Chat image attachments are stored in Cloudflare R2 (S3-compatible). Configure the following env vars:

```bash
R2_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
R2_ACCESS_KEY_ID=...
R2_SECRET_ACCESS_KEY=...
R2_BUCKET=...
R2_REGION=auto
```

## Maintenance scripts (`scripts/`)

Operator-only tools — see [`scripts/README.md`](scripts/README.md). They read **`backend/.env`** locally (never commit secrets).

## Run locally

```bash
npm install
npm run start:dev
```

## API overview

- `POST /auth/register` - register player
- `POST /auth/login` - login player
- `POST /auth/refresh` - rotate token pair by refresh token
- `POST /auth/logout` - close active refresh session (JWT)
- `GET /users/me` - current profile (JWT)
- `GET /users` - list alliance members (R4+)
- `PATCH /users/role` - change member role (R5)
- `PATCH /users/mute` - mute player for N minutes (R4+)
- `GET /chat/messages` - recent alliance messages (JWT)
- `POST /chat/messages` - create message (JWT) and broadcast `message:new` to the alliance room; optional FCM to other alliance members (see `FIREBASE_SERVICE_ACCOUNT_JSON`)
- `POST /users/me/push-token` - register FCM device token (JWT)
- `DELETE /users/me/push-tokens` - clear all FCM tokens for the user (JWT)
- `POST /users/me/presence` - update presence heartbeat (`status` string, JWT)
- `GET /mobile/game-patch` - latest patched game APK info: signed download URL + sha256 (JWT)

## Chat rooms (per player team)

Chat tab rooms:

| Room | Scope | `ChatRoom.allianceId` |
|------|--------|------------------------|
| **Межсерв** | All players, any server and team | `__global__` |
| **#&lt;n&gt;** (active game server) | All players on that server | `srv:<n>` |
| **Альянс** | Members of that player team only | `pt:<teamMongoId>` |
| **Рейд** | Same team; messages feed the combat overlay strip | `pt:<teamMongoId>` |

Users **without** `playerTeamId` see **Межсерв** and their server room. Team rooms are provisioned when a team is created or joined (`TeamsService` → `ChatRoomsService.ensureAllianceChatRoomsForScope`).

## WebSocket overview

Namespace: `/chat`

- `room:join` with `{ roomId }`
- `message:send` with `{ roomId, text, replyToMessageId? }`
- `typing` with `{ roomId }` — server emits `user:typing` `{ roomId, userId, username }` to others in the room
- server emits `message:new`, `message:edited`, `message:reaction`, `message:deleted`
- per-user socket room: `rooms:unread` `{ roomId, unreadCount, lastReadMessageId }` (no `room:join` required)
- room channel: `room:read` after HTTP mark-read
- admin wipe: `chat:history:cleared` then `rooms:unread` zero for all eligible users per room
- raid overlay: `message:new` on `user:{id}` only if not already in `chat:{raidRoomId}`
- connect with `auth.token` (Bearer access token)
