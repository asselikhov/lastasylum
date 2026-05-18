# SquadRelay (Android)

Клиент: голос, чат, оверлей отряда, команды, лента новостей, форум.

## Возможности

- Чат: автоповтор отправки, «Не отправлено», «печатает…», ответ свайпом, вложения
- Оверлей поверх игры: FAB, лента, голос, быстрые команды и реакции
- Профиль: тихий режим, пресеты раскладки оверлея
- JWT + refresh, **EncryptedSharedPreferences**, Socket.IO
- Опционально: FCM, Crashlytics (без `google-services.json` в git), pinning API

## Stack

Kotlin · Jetpack Compose · Retrofit · OkHttp · Foreground service + overlay

Ключевые модули: [`overlay/`](app/src/main/java/com/lastasylum/alliance/overlay/), [`ui/chat/`](app/src/main/java/com/lastasylum/alliance/ui/chat/), [`data/auth/TokenStore.kt`](app/src/main/java/com/lastasylum/alliance/data/auth/TokenStore.kt).

## Быстрый старт

1. Скопируйте [`../local.properties.example`](../local.properties.example) → `local.properties` (корень репо и/или сюда). **Не коммитьте** файл с ключами.
2. Откройте папку `mobile-android` в Android Studio → Sync Gradle.
3. Запустите вариант **dev** (по умолчанию — публичный backend на Render).

Локальный Nest на эмуляторе в `local.properties`:

```properties
squadrelay.api.baseUrl=http://10.0.2.2:3000/
```

На телефоне в Wi‑Fi: `http://<IP_ПК>:3000/`.

Полный гайд: [`../docs/development.md`](../docs/development.md).

## Firebase (опционально)

1. Firebase Console → Android app `com.lastasylum.alliance` → скачайте `google-services.json` в **`app/google-services.json`** (шаблон: `app/google-services.json.example`, файл в git не коммитится).
2. Gradle подхватит Project ID / App ID / API key в `BuildConfig` (плагин `com.google.gms.google-services` + ручная инициализация в `SquadRelayApplication`).
3. Альтернатива: три ключа в `local.properties` (`squadrelay.firebase.*`) — перекрывают json.

На сервере (Render) нужен **`FIREBASE_SERVICE_ACCOUNT_JSON`** (сервисный аккаунт, не `google-services.json`).

Из корня репозитория:

```powershell
.\setup-firebase.ps1
```

Положите скачанный ключ в `backend/firebase-service-account.json` и запустите скрипт снова — он обновит `backend/.env` и покажет, что вставить в Render.

## Certificate pinning (опционально)

```properties
squadrelay.certPins=sha256/AAAA...,sha256/BBBB...
```

## Security

- [`../SECURITY.md`](../SECURITY.md)
- Токены: `TokenStore.kt` (EncryptedSharedPreferences)
- minSdk **28** (Android 9+)

## Тесты

```bash
./gradlew :app:testDevDebugUnitTest
```

## Роли в UI

Вкладка «Админ» — роль **R5**; остальное по правам API.
