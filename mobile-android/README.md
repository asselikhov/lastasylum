# Last Asylum Android App

Premium Android client for alliance communication in Last Asylum:
- Battle chat feed
- Overlay control entrypoint
- Profile/preferences
- Dark premium design system

## Stack

- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose

## Run

1. Open `mobile-android` in Android Studio.
2. Sync Gradle files.
3. Set `API_BASE_URL` in `app/build.gradle.kts` to your Render backend URL.
4. On the server, set `OPENAI_API_KEY` for real Whisper STT (optional; without it the API uses a mock transcript).
5. Run app on Android 9+ device/emulator.

## Next implementation steps

- Auth flow against backend (`/auth/login`, `/auth/refresh`)
- Access and refresh token storage via encrypted preferences
- Realtime chat via Socket.IO
- Foreground service + floating overlay controls
- Push-to-talk recording -> STT endpoint -> chat message pipeline
- Role-aware UI (R2/R3/R4/R5)
