# Android: ресурсы и воспроизводимость сборки

Документ описывает то, что собирается в Android-приложение и что требуется для локальной сборки. Основной офлайн-клиент находится в `android/`; браузерный прототип, Telegram relay и служебные скрипты к Android APK не подключены.

## Сборочный набор

Сверяйте команды с [`android/README.md`](../../android/README.md). Для debug-сборки нужны:

- JDK 17 или 21; исходники и Kotlin bytecode нацелены на Java 17.
- Android SDK Platform API 36 и Build Tools 36.0.0 (`compileSdk = 36`, `targetSdk = 35`, `minSdk = 26`).
- Gradle Wrapper 8.11.1 из `android/gradle/wrapper/gradle-wrapper.properties`; SHA-256 дистрибутива указан там же. Используйте `android/gradlew.bat` в Windows или `android/gradlew` в Unix-подобной среде.
- Доступ к Google Maven, Maven Central и Gradle Plugin Portal при первом скачивании Gradle и зависимостей. Репозитории заданы в `android/settings.gradle.kts`.

Зависимости проекта объявлены непосредственно в `android/build.gradle.kts` и `android/app/build.gradle.kts`, без version catalog:

| Группа | Закреплённые версии / источник |
| --- | --- |
| Android Gradle Plugin | 8.10.1 |
| Kotlin Android и Compose plugin | 2.1.21 |
| KtLint Gradle plugin / ktlint | 14.2.0 / 1.5.0 |
| Jetpack Compose | BOM 2025.05.00; `activity-compose` 1.10.1; Material 3, Foundation, UI и preview берут версии из BOM |
| AndroidX | Core 1.15.0; Lifecycle 2.9.0 |
| Тестовые библиотеки | JUnit 4.13.2, `org.json` 20240303; AndroidX Test JUnit 1.2.1, Runner 1.6.2, Compose UI test из BOM |

В приложении используется SQLite через Android `SQLiteOpenHelper`, криптография Android/Java и Android Storage Access Framework; дополнительная SQLite-библиотека или сторонний runtime для криптографии не объявлены. В конфигурации нет custom source sets, product flavors или release signing config. Подписанный release APK и публикация не настроены. Параметры Compose, версии плагинов и зависимостей закреплены, но Gradle dependency locking/verification не настроены; это не гарантирует полностью изолированную или побитово воспроизводимую сборку.

## Файлы, входящие в Android-приложение

Android Gradle Plugin использует стандартные source sets `app/src/main` и `app/src/debug`; отдельной настройки путей ресурсов нет.

| Путь | Назначение / потребитель |
| --- | --- |
| `android/app/src/main/java/` | Kotlin-код приложения: Compose UI, модели, репозиторий SQLite, отчёты и обмен файлами. |
| `android/app/src/main/res/` | Android-ресурсы. `drawable-nodpi/exercise_art_*.jpg` — локальные иллюстрации упражнений, сопоставляемые в `ExerciseArtwork.kt` и `ExerciseCatalogArtworkMap.kt`; `drawable/` и `mipmap-*/` — значки приложения; `values/launcher_icon.xml` — цвета иконки; `xml/file_paths.xml` ограничивает область FileProvider кэш-папкой обмена. |
| `android/app/src/main/assets/exercise-artwork/generated/exercise-catalog-sheet-01.png` … `exercise-catalog-sheet-08.png` | Листы спрайтов, открываемые из `ExerciseArtwork.kt` для изображений каталога. Это файлы приложения, а не кэш генератора: при упаковке они должны быть доступны в этом пути. |
| `android/app/src/main/assets/exercise-artwork/anatomy/male_front_muscle_anatomy.svg` и `male_back_muscle_anatomy.svg` | Локальные карты мышц, читаемые в `ExerciseMuscleMap.kt` и раскрашиваемые во время работы. `NOTICE.txt` и `LICENSE-muscle-mapper.txt` сопровождают скопированные SVG и должны оставаться рядом при распространении. |
| `android/app/src/debug/assets/workout-reports.json` | Синтетическая фикстура демо-отчётов. Попадает в debug-вариант и читается только debug-путём импорта; не нужна release-сборке. Не заменяйте личными отчётами. |

Рисунок гантели на главном экране (`HomeHeroArtwork.kt`) рисуется кодом Canvas и отдельного файла изображения не требует. Иконки приложения — XML-ресурсы, а не внешние генераторы, вызываемые при каждой сборке.

## Потребление данных и внешние части репозитория

- Основной дневник хранится локально в приватной базе `reptrack.db` через `DiaryRepository.kt`/`SQLiteOpenHelper`. Включённые в сборку ресурсы изображений читаются из APK и не загружаются при запуске.
- Приватные снимки тренировок хранятся в `filesDir/training-snapshots/` (`ConnectedSnapshotStore.kt`). Временные файлы экспорта и обмена создаются в `cacheDir/exchange/`; FileProvider отдаёт URI только из этой папки согласно `res/xml/file_paths.xml`. Это пользовательские runtime-данные, не исходники и не ресурсы сборки.
- В Android manifest не объявлено разрешение `INTERNET`; запись дневника и локальный Stage 2 обмен не требуют сети. Системный Sharesheet и SAF позволяют пользователю передавать файлы через выбранные приложения; это внешние действия ОС, не сетевой backend RepTrack.
- `telegram-bot/` — отдельный необязательный Stage 3 Cloudflare Worker + D1. Он не является Gradle-модулем, не входит в APK и требует отдельного владельческого развёртывания.
- `web/` и `dist/index.html` — отдельный браузерный прототип и его статическая копия; Android Gradle их не использует.
- Корневой `package.json` и Prettier обслуживают форматирование JS/web; `tools/` и `tasks/` — служебные сценарии/материалы. Они не нужны для сборки Android и не являются Android-тестами или build inputs.

## Офлайн-режим и локальные кэши

После установки приложение работает без сети для записи, просмотра дневника, аналитики, резервного копирования и ручного обмена файлами. Полностью автономная сборка на чистой машине не предполагается: JDK, SDK platform/build tools, Gradle distribution и Maven-зависимости должны быть уже установлены/закэшированы либо скачаны заранее.

Типовые локальные каталоги:

- `android/.gradle/` — проектный кэш Gradle; исключён через `android/.gitignore`.
- `android/app/build/` и другие `**/build/` — промежуточные файлы и APK; исключены из Git.
- `android/local.properties` — локальный путь Android SDK; исключён из Git. В README путь задаётся через `ANDROID_HOME`; установку SDK выбирает разработчик.
- `GRADLE_USER_HOME/wrapper/dists/` — скачанные дистрибутивы Wrapper, а `GRADLE_USER_HOME/caches/` — Gradle и Maven-кэши. При стандартной конфигурации это каталог пользователя; значение `GRADLE_USER_HOME` можно настроить отдельно.
- Android SDK — внешний локальный каталог, обычно указываемый `ANDROID_HOME` или Android Studio. Его содержимое, пользовательские эмуляторы и системные образы не хранятся в проекте.

Для сборки из PowerShell, согласно README, задайте `JAVA_HOME` и `ANDROID_HOME`, перейдите в `android/` и выполните `./gradlew.bat :app:assembleDebug` (при необходимости вместе с `:app:testDebugUnitTest`). Основной README также приводит Android lint и подключаемые инструментальные тесты. Debug APK создаётся в `android/app/build/outputs/apk/debug/app-debug.apk`; этот результат генерируется, а не хранится в исходниках.
