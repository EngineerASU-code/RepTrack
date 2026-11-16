# Exercise photo rights review

Status: unresolved blocker for public redistribution.

The repository's exercise text/data and its photographs have separate rights status. The Free Exercise DB maintainer states that they do not know where the photographs came from or whether they are royalty free: https://github.com/yuhonas/free-exercise-db/issues/2#issuecomment-1609232397

Therefore the Unlicense noted for the derived dataset must not be applied to its photos. The image rows in `assets-manifest.csv` are marked as rights-unverified and must be replaced or removed before publishing a release build. Source-page links, authors, and license terms for replacement imagery must be verified individually and recorded in the manifest. Exact movement matching and permission to redistribute are separate checks.

Potential replacement candidates were reviewed for future use, but are not yet imported into the app:

- [Barbell squat photo by Nenad Stojkovic, CC BY 2.0](https://commons.wikimedia.org/wiki/File:Woman_doing_squat_workout_in_gym_with_barbell.jpg)
- [Leg press photo by Nenad Stojkovic, CC BY 2.0](https://commons.wikimedia.org/wiki/File:Young_man_using_a_leg_press_machine_at_the_gym.jpg)
- [Lat pulldown photo by Aliva Sahoo, CC BY-SA 4.0](https://commons.wikimedia.org/wiki/File:Back_Pull_down.jpg)
- [Seated cable row photo by Miguel Angel Omaña Rojas, CC0 1.0](https://commons.wikimedia.org/wiki/File:Woman_using_a_seated_cable_row_machine_at_the_gym.jpg)

Candidates still require exact equipment/variant review and downloaded-file attribution metadata before app integration. This note does not claim the app currently meets the exercise-media requirement.
