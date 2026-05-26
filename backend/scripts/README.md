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
| [`clear-chat-messages.mjs`](clear-chat-messages.mjs) | **Destructive:** delete all chat messages, read cursors, attachment metadata | `SCRIPT_CONFIRM_CLEAR_CHAT=yes`, optional `SCRIPT_DRY_RUN=1` |
| [`normalize-legacy-users.mjs`](normalize-legacy-users.mjs) | Set `membershipStatus: active` for legacy users; optional admin role fix | `SCRIPT_ADMIN_USERNAME` (optional) |
| [`consolidate-obzhory.mjs`](consolidate-obzhory.mjs) | One-off team/chat consolidation | `SCRIPT_TEAM_TAG`, `SCRIPT_ALLIANCE_SCOPE`, `SCRIPT_MEMBER_USERNAMES` |
| [`lookup-user.mjs`](lookup-user.mjs) | Find user by username/email fragment | — |
| [`test-smtp.mjs`](test-smtp.mjs) | Send test email via `SMTP_*` in `.env` | `SMTP_*` |
| [`setup-smtp.ps1`](setup-smtp.ps1) | Interactive wizard: write `.env` + Render checklist (Windows) | — |
| [`setup-firebase.ps1`](setup-firebase.ps1) | FCM: service account → `.env`, Android `local.properties`, Render checklist | — |
| [`test-fcm-config.mjs`](test-fcm-config.mjs) | Verify `FIREBASE_SERVICE_ACCOUNT_JSON`; optional send if `TEST_FCM_TOKEN` set | `TEST_FCM_TOKEN` (optional) |

### Push notifications (Firebase / FCM)

Production push is **off** until `FIREBASE_SERVICE_ACCOUNT_JSON` is set on Render. Android APK needs `squadrelay.firebase.*` in `local.properties` (see repo `local.properties.example`).

```powershell
# From repo root (recommended):
.\setup-firebase.ps1

# Or only sync Android + .env when service account file is present:
cd backend
node scripts/apply-firebase-from-google-services.mjs
node scripts/test-fcm-config.mjs
```

Drop Firebase **service account** JSON at `backend/firebase-service-account.json` (gitignored), then re-run.

Paste `FIREBASE_SERVICE_ACCOUNT_JSON` into **Render → Environment**, rebuild/reinstall the app, then check Mongo `pushFcmTokens` after login.

```bash
node scripts/diagnose-excavation-push.mjs
node scripts/diagnose-excavation-push.mjs <senderMongoUserId>
```

### Password reset email (SMTP)

Production mail is **off** until `SMTP_HOST` is set on the server (Render Environment). Local wizard:

```powershell
.\backend\scripts\setup-smtp.ps1
```

Then paste the same variables into **Render → Environment** and redeploy.

## Safety

- Take a MongoDB backup before destructive or mass updates.
- Run only against databases you own.
- Do not put real player names or production credentials in tracked source — use `SCRIPT_*` in `.env`.
