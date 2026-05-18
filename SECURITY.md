# Security

## Reporting vulnerabilities

If you find a security issue, please **do not** open a public GitHub issue with exploit details.

Contact the maintainers privately (e.g. email or direct message) with:

- Description and impact
- Steps to reproduce
- Affected version / commit (if known)

We will acknowledge receipt and work on a fix; coordinated disclosure is appreciated.

## What must stay out of git

Never commit:

- `backend/.env` (MongoDB URI, `JWT_SECRET`, `JWT_REFRESH_SECRET`, R2 keys, `FIREBASE_SERVICE_ACCOUNT_JSON`, SMTP passwords)
- `local.properties` (Firebase API key, optional cert pins, `sdk.dir`)
- `google-services.json`, Firebase Admin SDK JSON, Android signing keystores (`.jks`, `.keystore`)

Templates: `backend/.env.example`, `local.properties.example`.

## Production checklist (operators)

- Use long random values for `JWT_SECRET` and `JWT_REFRESH_SECRET` (64+ characters).
- Set `ALLOWED_ORIGINS` to real app origins (avoid `*` in production).
- Store secrets only in the host environment (Render, etc.), not in the repository.
- Rotate credentials if a `.env` or laptop backup was leaked.
- Restrict Firebase / Google API keys by Android package name and SHA-1 where applicable.

## Client

- Access and refresh tokens are stored in **EncryptedSharedPreferences** when the device supports it.
- Release builds use HTTPS to the configured API; cleartext is limited to localhost / emulator in **dev** builds only.

## Auditing this repo

The open-source tree is intended to contain **application source and example config only**, not production secrets. If you see a committed secret, report it immediately so it can be rotated and removed from history.
