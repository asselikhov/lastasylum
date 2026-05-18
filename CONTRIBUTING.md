# Contributing

Thank you for helping improve SquadRelay. This repo is maintained as application source plus **example config only** — no production secrets in git.

## Before you start

1. Read [`SECURITY.md`](SECURITY.md).
2. Copy [`backend/.env.example`](backend/.env.example) → `backend/.env` and [`local.properties.example`](local.properties.example) → `local.properties`.
3. Follow [`docs/development.md`](docs/development.md) for day-to-day commands.

## Branching and PRs

- Work on a feature branch; open a PR into `main`.
- Keep PRs focused (one feature or fix per PR when possible).
- CI must pass: backend build/test, Android `assembleProdDebug`.
- Run `npm run lint` in `backend/` locally before large TS changes (lint is not in CI yet).

## Code style

- **Backend:** ESLint + Prettier (run `npm run lint` in `backend/`).
- **Android:** Kotlin style in existing files; 4-space indent (see [`.editorconfig`](.editorconfig)).
- Line endings: LF (see [`.gitattributes`](.gitattributes)).

## Commits

- Use clear messages in English or Russian — what changed and why.
- Do not commit `.env`, `local.properties`, keystores, APKs, or personal notes.

## Database scripts

Scripts under `backend/scripts/` are **operator tools**, not part of the runtime API. They read `backend/.env` locally. Never hardcode real usernames or production URIs in tracked files — use `SCRIPT_*` variables documented in `.env.example`.

## Questions

Open a GitHub issue for bugs or feature discussion. For security issues, follow [`SECURITY.md`](SECURITY.md) (private report, not a public exploit write-up).
