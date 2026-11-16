# Exercise artwork rights review

Status: the app's exercise artwork has been replaced with original generated images for the current visual MVP.

The previous Free Exercise DB photographs had unknown provenance and are not covered by the dataset's Unlicense. They have been removed from the app resources. The raw wger leg-press image was also removed because its individual source and license could not be verified. Exercise text/muscle metadata and the separately licensed anatomy SVGs are tracked independently in `assets-manifest.csv`.

Sixteen original visual illustrations now cover the mapped demo and starter-cycle exercises. The prompts and generation record are in [`asset-generation/exercise-artwork.md`](asset-generation/exercise-artwork.md); each image and its status is listed in `assets-manifest.csv`. No third-party image was used as an input. The images are labeled as illustrative and do not certify exercise technique. Fourteen starter-cycle entries remain intentionally unmapped because their names do not identify a single movement or matching variant; see the generation record. Written guidance remains separately attributed to its sources.

OpenAI's [Terms of Use](https://openai.com/policies/terms-of-use/) effective 2026-01-01 state that, as between the user and OpenAI and to the extent permitted by applicable law, the user owns generated output. The terms also say outputs may not be unique and require human evaluation for accuracy and suitability. This repository records the generation origin; it does not make a broader claim about legal protection or uniqueness.
