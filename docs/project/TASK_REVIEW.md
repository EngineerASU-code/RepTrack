# Независимое ревью: каталог, главная и обмен

**Вердикт: PASS — готово к публикации.** Существенных подтверждённых дефектов не обнаружено; свежие unit, build, lint, форматирование изменённых Kotlin-файлов и instrumented UI проверки прошли.

## База и охват

- ТЗ: [`docs/tasks/gym-catalog-home-exchange.md`](../tasks/gym-catalog-home-exchange.md).
- База сравнения: `d6f8c4722b3f5282e3bfa7fec434d5af4b43551b` → текущее рабочее дерево, включая новые неотслеживаемые файлы и удаления. Изменения не ограничивались diff только закоммиченных файлов.
- Основной охват: каталог и иллюстрации, главная/зал, профили и обмен, снимки тренировок, миграции и тестовые материалы. Telegram Worker и web-прототип не менялись в рамках этой задачи.
- Graphify относится к исходному коммиту, поэтому использован только как карта; выводы сверялись с текущими файлами.

## Критерии приёмки

| Критерий из ТЗ | Статус | Основание |
| --- | --- | --- |
| Самостоятельный каталог шире истории и покрытие согласованной таксономии | **MET** | В рабочем обсуждении согласован минимум 120 упражнений; в `ExerciseCatalogData.kt` сейчас 123 записи. `ExerciseCatalogDataTest.catalogEntriesAreCompleteUniqueAndCoverAllMuscleGroups` проверяет уникальность, заполненность и покрытие групп мышц. |
| Заполненные мышцы, техника и локальная иллюстрация для каждой записи | **MET** | Структуру записей проверяет `ExerciseCatalogDataTest`; `ExerciseCatalogArtworkTest.everyCatalogExerciseHasAnAvailableLocalImage` проверяет наличие локальных ресурсов. Сгенерированные листы были визуально просмотрены catalog_ui агентом, включая обновлённый `donkey calf raise sheet03`; отображение/сопоставление проверяют тесты каталога. Визуальный стиль согласован как фотореалистичный зал в тёмно-фиолетовой гамме. |
| Поиск/синонимы и фильтр по мышце; нет фильтров оборудования/проверенности | **MET** | `ExerciseCatalogContentTest.searchByAliasAndFilterByMuscleWithoutEquipmentOrVerificationFilters`; UI каталога реализован в `ExerciseCatalogScreen.kt`. |
| Сводка Push содержит только целевые мышцы упражнений плана | **MET** | `ExerciseCatalogDataTest.catalogResolvesNamesAndAliasesAndPlanSummariesUseOnlyPrimaryGroups`; сводки используют разметку каталога в `ExerciseCatalogData.kt`. |
| Новое упражнение проходит план/тренировку/историю, исторические снимки не меняются | **MET** | `ExerciseCatalogContentTest.catalogSelectionAddsExerciseToPlanCallback` проверяет добавление; `VisualAcceptanceTest` открывает каталог в сценарии активной тренировки; `DiaryRepositoryInstrumentedTest` проверяет завершение, историю и сохранение снимка сессии. `TrainingSessionTest` проверяет доменную логику. |
| Главная даёт обзор и прогресс, рабочие действия остаются в «Зале» | **MET** | Разделение маршрутов в `MainActivity.kt`; свежий `VisualAcceptanceTest` завершился успешно. Скриншот главной сохранён вне репозитория и проверен основным ревьюером. |
| Начать/продолжить с главной ведёт в «Зал» | **MET** | `VisualAcceptanceTest` и `WorkoutImeAcceptanceTest` прошли в полном instrumented наборе; тест создаёт сессию из главной и проверяет рабочий экран «Зала». |
| Элемент «Обмен» в debug/release, debug-инструменты остаются отдельными | **MET** | Действие «Обмен» реализовано в `MainActivity.kt`; `ManualPreviewSeedTest` и визуальный тест прошли. Debug preview seed остаётся в отдельном debug-тестовом потоке. |
| Роль и локальная запись подопечного со стабильным ID | **MET** | По уточнённому сценарию подопечный создаёт собственный локальный профиль и делится его ID; тренер может также вести отдельную локальную запись. `DiaryRepository.kt` добавляет `public_id`, миграция схемы v8 назначает уникальные UUID; `ProfileIdentityMigrationTest.versionSevenProfilesReceiveUniqueStableIdsWithoutChangingNames` проверяет миграцию, `LocalIdentityTest` — сохранение ID и роли. |
| Автор отчёта виден до импорта и не меняется после переключения профиля | **MET** | `DiaryRepository.createResultPackage` фиксирует автора с профиля связи в экспорт v2; `LocalIdentityTest.resultExportV1StaysCompatibleAndV2CarriesAuthorMetadata` проверяет envelope, `DiaryRepositoryInstrumentedTest.pairedDevicesExchangeAndDeduplicateEncryptedResults` проверяет предпросмотр и импорт. После формирования автор находится в байтах зашифрованного пакета и не зависит от активного профиля. |
| Раздел «Обмен» содержит связи и файлы/резервные копии, настройки отделены | **MET** | Экран обмена и настройки разведены в `MainActivity.kt`; полный `VisualAcceptanceTest` и прямой финальный захват прошли. Основной ревьюер визуально проверил экран Exchange с ID профиля, связями и снимками. |
| Снимки разных периодов/тренировок самостоятельны и не меняют дневник | **MET** | `TrainingSnapshotArchiveTest` проверяет кодирование, границы дат и ошибки; `ConnectedSnapshotStoreInstrumentedTest.snapshotsStaySeparateAndDuplicateIdCannotReplaceAnExistingSnapshot` проверяет изоляцию и дубликат ID. Хранилище расположено в `filesDir/training-snapshots`. |
| Полное восстановление backup отделено и требует подтверждения | **MET** | Подключение снимка использует отдельный файловый picker и `ConnectedSnapshotStore`; восстановление `.rptbackup` проходит отдельный экран подтверждения в `MainActivity.kt`. Существующие backup тесты находятся в `DiaryRepositoryInstrumentedTest.kt`. |
| Добавлены unit/UI/instrumented тесты для основных сценариев и ошибок импорта | **MET** | Добавлены тесты каталога, идентификаторов, миграции и снимков; расширены `DiaryRepositoryInstrumentedTest.kt`, `VisualAcceptanceTest.kt` и `VisualAcceptanceSupplementTest.kt`. Полный instrumented набор прошёл: 38/38. |
| KtLint, lint, unit, debug-сборка, установка/запуск и визуальная проверка | **MET** | Свежие результаты приведены ниже: 20 unit-тестов и 38/38 instrumented тестов прошли; Android lint, debug-сборка и изменённые Kotlin-файлы прошли проверки. Исправленный прямой `VisualAcceptanceTest` прошёл и сделал финальные скриншоты вне репозитория. |

## Проверки

| Проверка | Результат | Примечание |
| --- | --- | --- |
| `:app:testDebugUnitTest` | **PASS** | `android/build-verification.log`; 20 unit-тестов. |
| `:app:assembleDebug` | **PASS** | `android/build-verification.log`, также повторно в `android/build-style-final.log`. |
| `:app:lintDebug` | **PASS** | `android/build-verification.log`. |
| `:app:connectedDebugAndroidTest` | **PASS** | `android/build-verification.log`; 38/38 на RepTrack_Test_API35 (Android 15). Прямой финальный `VisualAcceptanceTest` также прошёл. |
| Изменённые Kotlin-файлы: `:app:ktlintCheck -PktlintChangedFiles=<all modified/new kt>` | **PASS** | `android/build-style-final.log`; глобальный KtLint имеет ранее существовавшие замечания в незатронутых файлах, поэтому проверена область изменённых файлов по политике CI. |
| `:app:assembleDebugAndroidTest` | **PASS** | `android/build-style-final.log`. |
| Telegram `node --check src/index.mjs` и `node --test test/index.test.mjs` | **PASS** | По результату финальной проверки: 10/10; Telegram-код не входит в изменённый функциональный охват. |
| `git diff --check` | **NOT RUN** | Не запускался в рамках этого независимого обзора; сборка, тесты и форматирование прошли. |

В ходе этого независимого ревью команды повторно не запускались; перечисленные результаты взяты из свежих `android/build-verification.log` и `android/build-style-final.log` и от основного ревьюера. Ранее сохранённый HTML с падением проверки видимости был снят до добавления `performScrollTo`; исправленный полный набор и прямой визуальный тест прошли.

## Замечания и очистка

- Подтверждённых проблем уровня BLOCKER/MAJOR/MINOR в просмотренных участках не выявлено. Проверенные инварианты: отдельные файлы снимков с проверкой ID, неперезаписываемое подключение повторного ID, миграция профилей с уникальными UUID, совместимое чтение старых форматов отчётов/резервных копий.
- В рамках согласованных критериев проверены заполненность и соответствие иллюстраций каталогу; отдельная профессиональная клиническая сертификация методики не входила в требования.
- В рабочем diff удалены сгенерированные APK и SHA-256 из `artifacts/`, устаревший отчёт и наборы визуальных прогонов/сравнений из `design/acceptance/visual-acceptance/`, а также прежние датированные task/visual отчёты из `tasks/`. README и актуальные тесты обновлены; очистка не меняет исходники приложения. Финальные acceptance-скриншоты хранятся вне репозитория.
- Решение: **готово к публикации**. Это решение основано на свежем полном Android прогоне, исправленном визуальном тесте и сверке с согласованными критериями.
