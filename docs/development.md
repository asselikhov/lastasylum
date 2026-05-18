# Разработка SquadRelay

Краткий гайд для команды. Секреты — только локально; в git попадают только шаблоны.

## Первый запуск

### Вариант A — автоматически (Windows)

```powershell
.\setup-auto.ps1
```

Скрипт запросит `MONGODB_URI` и URL API, создаст `backend/.env`, пропишет `squadrelay.api.baseUrl` в `local.properties`, выполнит `npm install`, lint, test и build.

### Вариант B — вручную

1. `cp backend/.env.example backend/.env` — заполните MongoDB и JWT (длинные случайные строки).
2. `cp local.properties.example local.properties` — при необходимости `squadrelay.api.baseUrl`.
3. Android Studio создаст `mobile-android/local.properties` с `sdk.dir`; при необходимости добавьте туда же ключи SquadRelay (см. шаблон в корне).

### Вариант C — Linux / macOS

```bash
chmod +x setup.sh
./setup.sh
```

## Ежедневная работа

| Задача | Команда |
|--------|---------|
| API локально | `cd backend && npm run start:dev` |
| Тесты backend | `cd backend && npm test` |
| Lint backend | `cd backend && npm run lint` |
| Сборка Android (debug) | `cd mobile-android && ./gradlew assembleDevDebug` |
| Unit-тесты Android | `cd mobile-android && ./gradlew :app:testDevDebugUnitTest` |

## Подключение телефона к локальному API

1. ПК и телефон в одной Wi‑Fi сети.
2. В `local.properties`: `squadrelay.api.baseUrl=http://<IP_ПК>:3000/`
3. На ПК разрешите входящие на порт 3000 (файрвол).
4. MongoDB Atlas: добавьте IP ПК в Network Access (или `0.0.0.0/0` только для dev).

Эмулятор: `http://10.0.2.2:3000/`.

## Firebase (опционально)

В `local.properties`:

```properties
squadrelay.firebase.projectId=...
squadrelay.firebase.appId=1:...:android:...
squadrelay.firebase.apiKey=...
```

На сервере — `FIREBASE_SERVICE_ACCOUNT_JSON` в `backend/.env`. Ключ API ограничьте в Google Cloud по package name и SHA-1.

## Продакшен (Render)

- Переменные окружения задаются в панели Render, не в репозитории.
- После merge в `main` с изменениями backend — redeploy сервиса.
- `ALLOWED_ORIGINS` — реальные origin приложения.

## Служебные скрипты БД

Только для своей базы, с бэкапом. См. [`backend/scripts/README.md`](../backend/scripts/README.md).

Параметры одноразовых операций — в `backend/.env` (`SCRIPT_*`), не в коде.
