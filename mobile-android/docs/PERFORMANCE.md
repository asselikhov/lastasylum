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

## CI benchmark (report-only)

- Workflow: [`.github/workflows/android-benchmark.yml`](../../.github/workflows/android-benchmark.yml)
- Режим по умолчанию: **report-only** (не фейлит CI).
- В PR/Run summary выводятся ключевые метрики startup/frame + предупреждения при деградации.
- Артефакты:
  - `benchmark/build/outputs/**`
  - `**/outputs/connected_android_test_additional_output/**`
  - `benchmark/results/latest_metrics.json`
- Для `schedule` и `workflow_dispatch` дополнительно публикуется baseline-candidate артефакт:
  - `android-benchmark-baseline-candidate-<run_id>`
- Baseline файл: [`benchmark/baseline/dev_debug.json`](../benchmark/baseline/dev_debug.json)

## Мини-чеклист перед релизом

1. `:app:testDevDebugUnitTest` — регрессия логики.
2. `:benchmark:connectedDevDebugAndroidTest` на одном и том же девайсе/эмуляторе.
3. Проверить, что нет деградации в:
   - startup `timeToInitialDisplayMs`
   - frame timing (`p95`) в `UiJankBenchmark`.
4. Ручной smoke: скролл TeamNews/Forum, переходы вкладок, idle 3-5 минут с включенным overlay.

## Runbook при регрессии в CI benchmark

1. Перезапустить benchmark workflow (исключить одноразовый шум эмулятора).
2. Сравнить `benchmark/results/latest_metrics.json` с baseline.
3. Если деградация воспроизводится:
   - локально прогнать `:benchmark:connectedDevDebugAndroidTest`,
   - сузить диапазон коммитов (git bisect по perf-изменениям),
   - проверить горячие сценарии: startup, tab switch, swipe.
4. После стабилизации обновить baseline на новой норме (только после нескольких стабильных прогонов).

## Rollout политики гейтов

- **Stage 1 (текущий):** report-only 1-2 недели, сбор baseline.
- **Stage 2:** soft-gate (warning/check-run) при повторяемой деградации.
- **Stage 3:** hard-gate только на откалиброванных метриках (обычно startup/frame p95).

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
