# RepTrack launcher icon

The Android launcher uses an adaptive icon: a `#12101C` Purple Pro background and a recognizable symmetric dumbbell (two plates per side and a short handle), drawn as VectorDrawable. The paths sit within the adaptive icon's central safe zone, so circle and squircle launcher masks retain the full mark. Android 13+ receives the same dumbbell silhouette as a single-color themed icon.

- Preview: [RepTrack-launcher-icon-preview.svg](RepTrack-launcher-icon-preview.svg)
- Android 8–12: `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml`
- Android 13+: matching v33 resources add `ic_launcher_monochrome.xml`
- Minimum supported Android is API 26, so adaptive launcher resources cover the full supported range; no legacy raster icon is needed.
- The preview shows the actual launcher vector: bright purple plates and a lighter violet handle on the dark background. The app's separate in-screen glyphs remain unchanged.
