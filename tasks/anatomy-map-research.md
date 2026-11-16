# Карта мышц RepTrack: источники и соответствие

Дата проверки: 16 ноября 2026. Требование: S06 / контрольный кадр C07 из `design/VISUAL_SPEC.md`.

## Какой экран требуется

Во вкладке «Мышцы» упражнения должны быть показаны передняя и задняя проекции на тёмном фоне. Группы с основной нагрузкой выделяются сильным цветом темы, вспомогательные — более мягким. Подписи и легенда словами повторяют роли. Карта должна зависеть от точного варианта упражнения; в MVP это отдельное состояние экрана упражнения, без интерактивного вращения и без фиктивной анимации.

## Сравнение открытых материалов

- **Wikimedia Commons / Termininja:** подробные front/back схемы с лицензией CC BY-SA 3.0. В сохранённых SVG группы имеют непрозрачные Inkscape IDs, а соответствие контуров упражнениям не размечено. Не выбран источник для подсветки: ручное угадывание контуров было бы ненадёжным.
- **wger muscle fragments:** локальные файлы — разрозненные фрагменты, а не согласованные передняя/задняя карты. Для отдельных фрагментов не сохранены точный URL, автор и лицензия. Удалены из Android assets; не используются.
- **sunnyby/anatomical-muscle-map:** поддерживает роли мышц и детальные регионы, но README указывает, что лицензия проекта ещё не выбрана, сама основа была создана генератором и независимой проверки анатомом не было. Не распространяем его рисунки в приложении.
- **suryamolly/muscle_mapper, стиль minimal:** front/back SVG имеют именованные области для грудных мышц, дельт, бицепса, трицепса, широчайших, средней части спины, квадрицепсов, икр, ягодиц, бицепса бедра, поясницы и предплечий. Файлы весят около 48 KB и 37 KB, имеют одинаковый `viewBox 0 0 676.49 1203.49`. Репозиторий распространяется под MIT; полный текст лицензии и attribution notice сохранены рядом с ресурсами приложения. Указание CC BY 4.0 в upstream CREDITS относится к отдельному набору `advanced`; его SVG в приложении не копируются.

Выбранные файлы закреплены в manifest SHA содержимого:
- front: `e6be0e6fead3134bfd5b2a0e4ca06986dbc4ea13`
- back: `d1fd39dd34a2c66adddd88b10574bf43ec518821`

## Сопоставление шести вариантов стартового цикла

Мышечные группы взяты из точных записей Free Exercise DB `dist/exercises.json`; шаги техники по-прежнему ссылаются на NASM/ACE в карточке упражнения.

| Вариант | Основная нагрузка | Дополнительная нагрузка |
|---|---|---|
| Barbell Bench Press - Medium Grip | грудь | плечи, трицепс |
| Wide-Grip Lat Pulldown | широчайшие | бицепс, средняя часть спины, плечи |
| Barbell Squat | квадрицепсы | икры, ягодицы, задняя поверхность бедра, поясница |
| Leg Press | квадрицепсы | икры, ягодицы, задняя поверхность бедра |
| Seated Cable Rows | средняя часть спины | бицепс, широчайшие, плечи |
| Alternate Hammer Curl | бицепс | предплечья |

Локальные группы SVG сопоставлены по ID в `ExerciseMuscleMap.kt`. Основная заливка использует акцент Purple Pro, дополнительная — светлый фиолетовый с меньшей непрозрачностью. В приложении обе проекции отображаются рядом; карточка фиксированной высоты масштабирует SVG по ширине. Схема не заявляет, что оценивает биомеханику или медицинскую точность упражнения.

## Источники

- Exercise data: <https://github.com/yuhonas/free-exercise-db/blob/main/dist/exercises.json> (Unlicense).
- Anatomy assets: <https://github.com/suryamolly/muscle_mapper/tree/main/lib/src/assets/minimal> (MIT; source notices bundled in Android assets).
- Anatomy asset license and attribution: <https://github.com/suryamolly/muscle_mapper/blob/main/LICENSE> and <https://github.com/suryamolly/muscle_mapper/blob/main/CREDITS.md>.
- Existing local Wikimedia files: <https://commons.wikimedia.org/wiki/File:Muscular_system.svg> and <https://commons.wikimedia.org/wiki/File:Muscular_system-back.svg> (CC BY-SA 3.0); retained only as research references in this document.
