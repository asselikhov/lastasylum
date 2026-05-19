# Производительность SquadRelay (Android)

## Сборка с профилированием (debug)

- В `devDebug` включён `profileable` для Macrobenchmark / Android Studio Profiler.
- Baseline Profile: `androidx.profileinstaller` в release подхватывает `baseline-prof.txt` при наличии.

## Macrobenchmark

```bash
cd mobile-android
./gradlew :benchmark:connectedDevDebugAndroidTest
```

Модуль `:benchmark` — холодный старт MainActivity (метрика `StartupTimingMetric`).

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
