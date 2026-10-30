# Third-party notices

QingFrame new application code and original generators/assets use Apache License2.0. This does not confer Xiaomi brand or industrial design rights.

- Google Filament1.79.0 (`google/filament`, tag commit a854ad6), Apache2.0. Unmodified official filament/gltfio/utils Android AARs in app/libs; official matching tools are local build-only. LICENSE applies to their Apache-covered source. Upstream contains its own third-party notices in the release/repository; see https://github.com/google/filament/tree/v1.79.0/third_party .
- AndroidX Compose, Activity, Lifecycle, ExifInterface and Core, Apache2.0; Kotlin standard library, Apache2.0. Used as unmodified Gradle dependencies. Their packaged metadata/licenses remain in APK.
- JUnit4.13.2, EPL1.0; AndroidX Test runner1.7.0 / ext-junit1.3.0 Apache2.0. Tests only.
- Blender4.5.0, GPL3.0-or-later, tool only; not inAPK. Original script-generated meshes are independent outputs; see docs/OPEN_SOURCE_REUSE.md.
- Khronos glTF-Validator2.0.0-dev.3.10, Apache2.0, tool only.
- Pillow12.1.0, HPND/Pillow license, QA tool only.

No external device model, Apple/iPhone asset, official screenshot, logo, wallpaper or HDRI is distributed in the APK. Official Xiaomi references remain in ignored .cache/references, with rights not verified. This application is unaffiliated with Xiaomi. See assets-source/devices/*/research.json for estimated dimensions.

The packaged AARs are unmodified upstream release binaries. A full public-distribution audit of every embedded native third-party component remains a release prerequisite; this local task does not claim that the root Apache license overrides those component licenses.
