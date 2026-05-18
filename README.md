# Last Asylum (SquadRelay)

Monorepo for the SquadRelay alliance app: NestJS backend and Android client (chat, teams, voice overlay).

| Path | Description |
|------|-------------|
| [`backend/`](backend/) | REST API, WebSocket chat, MongoDB, push (FCM), file storage (R2) |
| [`mobile-android/`](mobile-android/) | Kotlin / Jetpack Compose Android app |

## Quick start (developers)

1. **Backend** — copy [`backend/.env.example`](backend/.env.example) to `backend/.env`, fill secrets locally. See [`backend/README.md`](backend/README.md).
2. **Android** — copy [`local.properties.example`](local.properties.example) to `local.properties` (root and/or `mobile-android/`). See [`mobile-android/README.md`](mobile-android/README.md).
3. Optional: run [`setup-auto.ps1`](setup-auto.ps1) on Windows to generate `backend/.env` and set `squadrelay.api.baseUrl` in `local.properties` (does not modify tracked Gradle files).

```bash
cd backend && npm install && npm run start:dev
```

Open `mobile-android` in Android Studio, sync Gradle, run the **dev** variant.

## Security

- Do not commit `.env`, `local.properties`, keystores, or Firebase admin JSON.
- See [`SECURITY.md`](SECURITY.md) for reporting vulnerabilities and operator checklist.

## CI

GitHub Actions runs backend tests/build and `assembleProdDebug` for Android on push and pull requests (see [`.github/workflows/ci.yml`](.github/workflows/ci.yml)).
