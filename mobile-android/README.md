# SquadRelay (Android)

Клиент для голоса, чата и оверлея отряда в SquadRelay:

- Battle chat feed: **автоповтор отправки** (до 3 раз), баннер **«Не отправлено»** с «Повторить», индикатор **«печатает…»**, свайп для ответа, переход к цитируемому сообщению
- Оверлей поверх игры: FAB, лента, голос, быстрые команды и **быстрые реакции** (emoji)
- Профиль: тихий режим, компактный оверлей, **пресеты раскладки** (balanced / commander / minimal)
- JWT + refresh, **EncryptedSharedPreferences** для токенов, Socket.IO для чата
- Опционально: **FCM** + **Crashlytics** (ручная инициализация Firebase без `google-services.json`), **pinning** сертификата API

## Stack

- Kotlin, Jetpack Compose + Material 3, Navigation Compose
- Retrofit + Moshi, OkHttp, kotlinx-coroutines
- Foreground service + `TYPE_APPLICATION_OVERLAY`; перетаскивание FAB — [`OverlayWindowDragHelper`](app/src/main/java/com/lastasylum/alliance/overlay/OverlayWindowDragHelper.kt); буфер ленты — [`OverlayChatStripBuffer`](app/src/main/java/com/lastasylum/alliance/overlay/OverlayChatStripBuffer.kt); FGS-уведомление — [`OverlayForegroundNotifications`](app/src/main/java/com/lastasylum/alliance/overlay/OverlayForegroundNotifications.kt); флаги/вырезы окон — [`OverlayWindowLayout`](app/src/main/java/com/lastasylum/alliance/overlay/OverlayWindowLayout.kt)

## Run

1. Откройте `mobile-android` в Android Studio и синхронизируйте Gradle.
2. Вариант **dev** по умолчанию ходит на Render (`lastasylum-backend.onrender.com`). Для локального Nest в `local.properties` (корень репо или `mobile-android/`):

   `squadrelay.api.baseUrl=http://10.0.2.2:3000/` (эмулятор) или `http://<IP_ПК>:3000/` (телефон в Wi‑Fi).

3. **Firebase (опционально)** — в `local.properties` добавьте три строки из консоли Firebase (Project settings → Your apps):

   - `squadrelay.firebase.projectId=...`
   - `squadrelay.firebase.appId=1:...:android:...`
   - `squadrelay.firebase.apiKey=...`

   После этого приложение инициализирует Firebase в `SquadRelayApplication`, регистрирует FCM-токен на бэкенде (`POST /users/me/push-token`) и получает push при новых сообщениях в альянсе (если на сервере задан `FIREBASE_SERVICE_ACCOUNT_JSON`).

4. **Certificate pinning (опционально)** — `squadrelay.certPins=sha256/AAAA...,sha256/BBBB...` (отпечатки для хоста из `API_BASE_URL`).

5. Запуск на устройстве **Android 9+** (minSdk 28).

## Тесты

```bash
./gradlew :app:testDevDebugUnitTest
```

## Роль в UI

Вкладка «Админ» доступна для роли **R5**; остальные экраны — по правам бэкенда.
