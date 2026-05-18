# Backend maintenance scripts

Operator-only Node scripts. They connect to MongoDB using **`backend/.env`** on your machine (never commit `.env`).

Shared helper: [`load-env.mjs`](load-env.mjs).

## Prerequisites

```bash
cd backend
cp .env.example .env
# fill MONGODB_URI, JWT_*, and any SCRIPT_* vars below
```

Run from `backend/`:

```bash
node scripts/<script>.mjs
```

## Scripts

| Script | Purpose | Extra `.env` keys |
|--------|---------|-------------------|
| [`migrate-chat-rooms.mjs`](migrate-chat-rooms.mjs) | Create default «Общий» room per alliance; backfill `message.roomId` | — |
| [`normalize-legacy-users.mjs`](normalize-legacy-users.mjs) | Set `membershipStatus: active` for legacy users; optional admin role fix | `SCRIPT_ADMIN_USERNAME` (optional) |
| [`consolidate-obzhory.mjs`](consolidate-obzhory.mjs) | One-off team/chat consolidation | `SCRIPT_TEAM_TAG`, `SCRIPT_ALLIANCE_SCOPE`, `SCRIPT_MEMBER_USERNAMES` |

## Safety

- Take a MongoDB backup before destructive or mass updates.
- Run only against databases you own.
- Do not put real player names or production credentials in tracked source — use `SCRIPT_*` in `.env`.
