# RepTrack

RepTrack — дневник тренировок с нативным Android-приложением и локальным браузерным прототипом. Android-приложение работает офлайн; Stage 1 и локальный тренерский журнал с ручным защищённым обменом Stage 2 находятся в `android/`.

## Где что лежит

- [`RepTrack — единый промпт.md`](<RepTrack — единый промпт.md>) — объединённые требования к продукту, визуальному направлению, этапам и проверке результата. В документе также описаны функции будущих этапов.
- `*.png` в корне — визуальные концепты и preview экранов RepTrack.
- [`android/`](android/) — основное приложение на Kotlin и Jetpack Compose, локальная база, миграции, аналитика, резервные копии и тесты. Инструкции по функциям и проверкам находятся в [`android/README.md`](android/README.md).
- [`telegram-bot/`](telegram-bot/) — необязательный Telegram relay на Cloudflare Worker + D1 для Stage 3: доставка PDF-отчётов, зашифрованных результатов и программ без собственного VPS. Настройка и ограничения — в [`telegram-bot/README.md`](telegram-bot/README.md).
- [`web/`](web/) — браузерный прототип. Это не сборка Android-приложения.
- [`dist/index.html`](dist/index.html) — статическая копия браузерного прототипа.
- [`.openai/hosting.json`](.openai/hosting.json) — конфигурация предпросмотра/хостинга.
- [`AGENTS.md`](AGENTS.md) — ограничения и заметки для работы с репозиторием.

## Сборка Android из свежего клона

Нужны Android Studio с Android SDK API 36, Build Tools 36.0.0 и JDK 17 или 21. Gradle 8.11.1 скачивается Gradle Wrapper из репозитория. В Android Studio откройте папку `android/`; задайте путь к установленному SDK в настройках IDE или через переменные среды `ANDROID_HOME` и `ANDROID_SDK_ROOT`. Файл `local.properties` создаётся локально и в Git не добавляется.

В PowerShell из папки `android/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Готовый debug APK появится в `android/app/build/outputs/apk/debug/app-debug.apk`. Каталог `build/`, SDK, AVD и Gradle-кэш локальные: они намеренно не включены в Git и создаются/скачиваются при работе на каждом компьютере.

Чтобы выполнить Android-инструментальные тесты, запустите Android-эмулятор и выполните:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## Запуск браузерного прототипа

Нужен Node.js. Из корня репозитория запустите:

```powershell
node .\web\server.mjs
```

Сервер прототипа откроет его на `http://127.0.0.1:4173`.
