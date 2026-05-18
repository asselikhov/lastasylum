# Last Asylum (SquadRelay)

Monorepo: **NestJS** backend + **Android** client (alliance chat, teams, voice overlay, push).

| Path | Stack | Docs |
|------|--------|------|
| [`backend/`](backend/) | NestJS, MongoDB, Socket.IO, R2, FCM | [backend/README.md](backend/README.md) |
| [`mobile-android/`](mobile-android/) | Kotlin, Jetpack Compose | [mobile-android/README.md](mobile-android/README.md) |

[![CI](https://github.com/asselikhov/lastasylum/actions/workflows/ci.yml/badge.svg)](https://github.com/asselikhov/lastasylum/actions/workflows/ci.yml)

## Quick start

**1. Secrets (local only, never commit)**

| File | Template |
|------|----------|
| `backend/.env` | [`backend/.env.example`](backend/.env.example) |
| `local.properties` (repo root and/or `mobile-android/`) | [`local.properties.example`](local.properties.example) |

**2. Backend**

```bash
cd backend && npm install && npm run start:dev
```

**3. Android**

Open `mobile-android` in Android Studio → sync Gradle → run **dev** variant.

**Windows:** [`setup-auto.ps1`](setup-auto.ps1) creates `backend/.env` with random JWT secrets and writes `squadrelay.api.baseUrl` into `local.properties` (tracked Gradle files are not modified).

**Linux / macOS:** [`setup.sh`](setup.sh) — same idea.

Full walkthrough (RU): [`docs/development.md`](docs/development.md).

## Repository layout

```
LastAsylum/
├── backend/           # API, WebSocket chat, MongoDB
│   └── scripts/     # Operator DB tools (see scripts/README.md)
├── mobile-android/    # Android app
├── docs/              # Team guides
├── .github/workflows/ # CI
├── SECURITY.md        # Vulnerability reporting & secrets policy
└── CONTRIBUTING.md    # How we work with the repo
```

## Security

Do not commit `.env`, `local.properties`, keystores, or Firebase admin JSON.

See [`SECURITY.md`](SECURITY.md).

## CI

On every push and PR: backend `build` + `test` + `lint`, Android `assembleProdDebug` ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)).

## Быстрый старт (команда)

1. Скопируйте шаблоны `.env` и `local.properties` (см. таблицу выше).
2. Запустите бэкенд: `cd backend && npm run start:dev`.
3. Откройте `mobile-android` в Android Studio.
4. Для локального API на эмуляторе: `squadrelay.api.baseUrl=http://10.0.2.2:3000/` в `local.properties`.
5. Подробнее: [`docs/development.md`](docs/development.md).
