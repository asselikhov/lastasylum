# Производительность SquadRelay (Android)

## Сборка с профилированием (debug)

- В `devDebug` включён `profileable` для Macrobenchmark / Android Studio Profiler.
- Baseline Profile: `androidx.profileinstaller` в release подхватывает `baseline-prof.txt` при наличии.

## Macrobenchmark

```bash
cd mobile-android
./gradlew :benchmark:connectedDevDebugAndroidTest
```

Модуль `:benchmark`:
- `StartupBenchmark` — cold start + frame timing.
- `UiJankBenchmark` — launch + swipe probe (frame timing) для быстрой проверки jank на auth/основном UI.
- `TabSwitchJankBenchmark` — frame timing при запуске с `EXTRA_START_TAB` (`chat/team/profile`).

Для запуска конкретного класса:

```bash
cd mobile-android
./gradlew :benchmark:connectedDevDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lastasylum.alliance.benchmark.UiJankBenchmark
```

## Мини-чеклист перед релизом

1. `:app:testDevDebugUnitTest` — регрессия логики.
2. `:benchmark:connectedDevDebugAndroidTest` на одном и том же девайсе/эмуляторе.
3. Проверить, что нет деградации в:
   - startup `timeToInitialDisplayMs`
   - frame timing (`p95`) в `UiJankBenchmark`.
4. Ручной smoke: скролл TeamNews/Forum, переходы вкладок, idle 3-5 минут с включенным overlay.

## Logcat (оверлей)

```text
adb logcat -s CombatOverlayService OverlayRuntimeReceiver GameForegroundGate SR_OverlayDiag
```

- `overlayGate usage=false` — нет полного доступа к статистике в фоне.
- `watchdog ensureStarted=false` — FGS не поднялся (батарея/OEM).

## Чеклист OEM

1. Поверх других приложений — SquadRelay  
2. Данные об использовании — **полный** доступ  
3. Батарея — без ограничений, автозапуск  
4. Уведомления (Android 13+)
