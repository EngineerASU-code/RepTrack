# Original exercise artwork

Generated for RepTrack on 2026-11-16 with Codex ImageGen. The six local images are original visual illustrations for exercise cards and the exercise detail media panel. They are not sourced from the Free Exercise DB or another third-party image catalog.

OpenAI's [Terms of Use](https://openai.com/policies/terms-of-use/) effective 2026-01-01 say that, as between the user and OpenAI and to the extent permitted by applicable law, the user owns generated output. The prompts below used no reference images. The Terms also note that outputs may not be unique and require users to evaluate output for accuracy and appropriateness.

These images are for visual recognition only. They do not establish or certify exercise technique; the app labels them as illustrative and displays its separately sourced written technique guidance where available.

| Asset | Output | Generation prompt summary |
|---|---|---|
| Bench press | `android/app/src/main/res/drawable-nodpi/exercise_art_bench_press.jpg` | Adult male athlete bench pressing with a medium grip; full bar and bench, safe-looking rack, charcoal gym with violet accent lighting, landscape 3:2, no logos/text/watermark. |
| Lat pulldown | `android/app/src/main/res/drawable-nodpi/exercise_art_lat_pulldown.jpg` | Adult male athlete performing a wide-grip lat pulldown toward the upper chest; full cable machine, charcoal gym with violet accent lighting, landscape 3:2, no logos/text/watermark. |
| Barbell squat | `android/app/src/main/res/drawable-nodpi/exercise_art_barbell_squat.jpg` | Adult male athlete in a controlled mid-depth barbell back squat; full body and safety rack visible, charcoal gym with violet accent lighting, landscape 3:2, no logos/text/watermark. |
| Leg press | `android/app/src/main/res/drawable-nodpi/exercise_art_leg_press.jpg` | Adult male athlete using a 45-degree leg press machine with feet shoulder-width on the platform; charcoal gym with violet accent lighting, landscape 3:2, no logos/text/watermark. |
| Seated cable row | `android/app/src/main/res/drawable-nodpi/exercise_art_seated_cable_row.jpg` | Adult male athlete performing a close-grip seated cable row with a narrow V-handle; cable and footplates visible, charcoal gym with violet accent lighting, landscape 3:2, no logos/text/watermark. |
| Alternating hammer curl | `android/app/src/main/res/drawable-nodpi/exercise_art_hammer_curl.jpg` | Adult male athlete performing an alternating dumbbell hammer curl with neutral grips; both dumbbells visible, charcoal gym with violet accent lighting, landscape 3:2, no logos/text/watermark. |

## Starter-cycle coverage additions

Generated for the visual-recognition role on 2026-11-16 with Codex ImageGen. No third-party image was used as an input. These are illustrative images only; none certifies or teaches the movement. Reuse is limited to starter names whose text and shown movement/equipment are compatible.

| Asset | Output | Generation prompt summary |
|---|---|---|
| Standing calf raise | `android/app/src/main/res/drawable-nodpi/exercise_art_standing_calf_raise.jpg` | Adult athlete performing a bodyweight calf raise on a low step; no machine. |
| Seated leg curl | `android/app/src/main/res/drawable-nodpi/exercise_art_seated_leg_curl.jpg` | Adult athlete seated at a hamstring curl machine with the ankle roller and backrest visible. |
| Leg extension | `android/app/src/main/res/drawable-nodpi/exercise_art_leg_extension.jpg` | Adult athlete seated at a leg-extension machine with the shin roller and full machine visible. |
| Incline treadmill walk | `android/app/src/main/res/drawable-nodpi/exercise_art_incline_treadmill_walk.jpg` | Adult athlete walking on an inclined treadmill, with the belt and incline visible. |
| Preacher curl | `android/app/src/main/res/drawable-nodpi/exercise_art_preacher_curl.jpg` | Adult athlete curling an EZ bar while seated at a preacher bench. |
| Plate-loaded row | `android/app/src/main/res/drawable-nodpi/exercise_art_hammer_row.jpg` | Adult athlete using a chest-supported, plate-loaded horizontal row machine. |
| Smith shoulder press | `android/app/src/main/res/drawable-nodpi/exercise_art_smith_shoulder_press.jpg` | Adult athlete seated at a Smith machine pressing its guided bar overhead. |
| Butterfly chest fly | `android/app/src/main/res/drawable-nodpi/exercise_art_pec_deck_fly.jpg` | Adult athlete seated at a pec-deck chest-fly machine with the arm pads visible. |
| Standing barbell curl | `android/app/src/main/res/drawable-nodpi/exercise_art_standing_barbell_curl.jpg` | Adult athlete standing with a straight barbell during a biceps curl. |
| Standing rear-delt cable fly | `android/app/src/main/res/drawable-nodpi/exercise_art_standing_rear_delt_cable_fly.jpg` | Adult athlete standing at a dual cable station with crossed cables during a rear-delt fly. |

The starter-cycle seed in `DiaryRepository.createStarterPlans` has 23 names. Exact-variant mappings cover 9 names, including compatible aliases to earlier artwork. The following 14 names remain unmapped because the starter label does not identify the depicted equipment or movement variant precisely enough:

| Starter-cycle name | Reason |
|---|---|
| `Жим угловой` | No implement or pressing angle is specified. |
| `Хаммер низ груди` | A generated chest-press candidate did not clearly depict a low-chest press. |
| `Тяга к поясу` | Generic row; implement and grip are unspecified. |
| `Трицепс` | No movement or equipment is specified. |
| `Разминка, ходьба в гору` | Hill walking is named but the environment/equipment is not. |
| `Сгибания ног` | Curling movement is named but the machine/variant is not. |
| `Разгибания ног` | Extension movement is named but the machine/variant is not. |
| `Подтягивания / тяга` | Two different alternatives are listed. |
| `Тяга нижнего блока` | Pulley is specified but grip/handle variant is not. |
| `Тяга на трицепс` | The exact triceps movement and handle are unspecified. |
| `Плечи Smith` | Smith machine is specified but the shoulder movement is not. |
| `Жим груди в наклоне` | Incline is specified but the implement is not. |
| `Smith дельта` | Smith machine is specified but the shoulder movement is not. |
| `Тяга верхнего блока` | Pulley is specified but grip/handle variant is not. |

These names use the existing missing-art state until their movement variants are clarified. The images do not verify technique.

Additional exact-name aliases use matching variants already shown by the supplied catalog names: `Армейский жим (Смит)` → Smith shoulder press; `Задняя дельта, канаты` → standing rear-delt cable fly; `Сгибания ног в тренажёре` → seated leg curl; `Разгибания ног в тренажёре` → leg extension; `Жим ногами в тренажёре` → leg press; `Тяга спина (Хаммер)` → plate-loaded row; `Бицепс на скамье Скотта` → preacher curl. The starter alias `Плечи стоя, задняя дельта` uses the cable-fly illustration alongside the matching cable variant; the unused dumbbell candidate is not mapped. No technique guidance was added for these names.

The original generation output files remain in the local Codex generated-images folder. Repository copies use the names in the manifest. No unverified Free Exercise DB photos are retained as exercise artwork in the app.

## Catalog expansion — 17 November 2026

Eight local 4×4 PNG sheets in `android/app/src/main/assets/exercise-artwork/generated/` extend the catalog in the same photorealistic dark gym / purple lighting setting. The mapping is `ExerciseCatalogArtworkMap.kt` (sheet and cell, zero-based cell indices). Sheet 03 was revised to clarify the donkey calf raise; unused cells are not published as exercises. Nine existing individual drawables are reused; 114 catalog entries use sheet cells, for 123 exercises total. These are generated illustrative assets, with no third-party photo attribution asserted. Visual review and decoder tests check usability; they do not establish clinical or professional technique certification.
