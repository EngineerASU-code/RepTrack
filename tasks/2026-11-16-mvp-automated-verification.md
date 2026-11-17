# Проверка MVP этапов 1–3 — 16 ноября 2026

Текущий код после обновления интерфейса проверен на Android API 35 x86_64 эмуляторе `RepTrack_API35` (`emulator-5554`). Личные результаты импорта отчётов из этого исторического прогона удалены; актуальный повторяемый сценарий использует только синтетическую debug-fixture.

## Исправления, найденные проверкой

- Три теста ошибочно ожидали ровно один подход после создания упражнения с тремя подходами. Теперь выбирают первый подход для целевой проверки.
- Две проверки миграций ожидали SQLite schema v6, хотя текущая схема v7. Ожидания обновлены.
- Реальное выполнение пяти сценариев Stage 2/3 выявило `InvalidAlgorithmParameterException: Caller-provided IV not permitted`: `wrapSecret` передавал собственный nonce ключу Android Keystore с обязательной рандомизацией. Исправлен общий метод: IV создаётся провайдером Keystore и сохраняется перед ciphertext. Контейнер и расшифрование совместимы с прежним форматом. После исправления все пять сценариев прошли.
- Добавлены Node-проверки отклонения неверного webhook secret/невалидного update ID без изменений базы и восстановления обработки после сетевой ошибки с последующей дедупликацией завершённого update.

## Фактически выполненные автоматические проверки

Из `android/`, JDK 21.0.8, локальный SDK `.android-sdk`, Gradle 8.11.1:

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

Результат: **BUILD SUCCESSFUL**. **12 JVM-тестов, 0 failures, 0 errors**: DiaryTransfer (3), SecureExchange (1), DemoWorkoutReports (1), TrainingSession (7).

APK приложения и тестов установлены через `adb install -r`, без очистки данных. Запущено:

```powershell
adb -s emulator-5554 shell am instrument -w -e class app.reptrack.DiaryRepositoryInstrumentedTest,app.reptrack.DemoReportsInstrumentedTest app.reptrack.test/androidx.test.runner.AndroidJUnitRunner
```

Результат предыдущего прогона: **OK (18 tests)**, 6.445 секунды. Suite покрывает миграции, сохранность/восстановление backup, изоляцию профилей, независимые назначения и отложенные версии, приглашения/отзыв/подмену, защищённый обмен и дедупликацию результатов, PDF-контейнер и импорт синтетических тренировок с проверкой идемпотентности.

Эти тесты используют отдельные базы с уникальными именами; основная `reptrack.db` не затрагивается. `ManualPreviewSeedTest` не запускался. Протокол текущего запуска: `android/build/instrumented-mvp-results.txt` (локальный build artifact, игнорируется Git).

```powershell
node --check telegram-bot/src/index.mjs
node --test telegram-bot/test/index.test.mjs
git diff --check
```

Результат: **PASS**, **9 Node-тестов**, 0 ошибок.

## Состояние тестового профиля

Детали прошлой ручной проверки личных источников удалены. Текущий debug-путь импортирует синтетическую fixture в отдельный профиль и не изменяет основной дневник; проверяй его через `DemoReportsInstrumentedTest` и текущий эмуляторный прогон.

## Граница подтверждения Stage 3

PDF, адресный зашифрованный контейнер и его импорт реально проверены Android runtime. Webhook-парсинг, leases/retry и валидация маршрутов проверены локальными Node-тестами. Живой Bot API и Cloudflare D1 в этом запуске не использовались. В `telegram-bot/wrangler.toml` остаётся placeholder `database_id`; для фактического развёртывания необходимы ID D1 и секреты BOT_TOKEN/WEBHOOK_SECRET, вводимые владельцем вне чата. Секреты не читались, публикация Worker и отправка сообщений реальным Telegram-пользователям не выполнялись.
