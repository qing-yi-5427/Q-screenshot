# Locked toolchain

Observed 2026-10-29 on macOS arm64:
- Temurin JDK 21.0.12; bytecode target Java17.
- Gradle Wrapper9.8.0; Android Gradle Plugin9.4.1; Kotlin / Compose compiler plugin2.4.20.
- compileSdk37.0 (already installed); targetSdk36; minSdk29; build-tools36.0.0.
- Compose BOM2026.09.00, Activity1.13.0, Lifecycle2.10.0, ExifInterface1.4.1.
- Filament / gltfio / utils / matc / cmgen1.79.0, official tag short commit a854ad6 (release2026-10-26).
- Blender4.5.0 official portable project install, SHA256 checked in asset evidence.
- arm64-v8a and x86_64 packaged; emulator validation runs arm64.

The official1.79.0 release is newer than Maven Central's published metadata (latest1.77.3 when fetched). To keep compiler and runtime exactly matched, app/libs contains unmodified official GitHub release AARs. GitHub API digests checked:

| Release file | SHA256 |
|---|---|
| filament-v1.79.0-android.aar | c99e42a25e07610528f985932e496008a5ba1874282759aaa9f72a12a5226d42 |
| gltfio-v1.79.0-android.aar | 43fe381332c7358286e4e4ae86c31d2330b116eddd7cdfb7d6f45719ee9615bc |
| filament-utils-v1.79.0-android.aar | cedf4e8f1eadb4c82ecbcc8a2fc2fac3bf13b1411bef601518984818ceb0c11a |
| filament-v1.79.0-mac.tgz | a1c4dd85b2ec5789d202630026febc019bc8a774e231ce0bb4407032c2bd2103 |

Primary references opened/read: [AGP9.4 compatibility](https://developer.android.com/build/releases/agp-9-4-0-release-notes), [Activity stable1.13.0](https://developer.android.com/jetpack/androidx/releases/activity), [Filament release](https://github.com/google/filament/releases/tag/v1.79.0). Runtime API calls checked against the tag Java/Kotlin source and actual AAR javap, including Renderer.readPixels/renderStandaloneView, Texture, View, MaterialInstance, Camera, ResourceLoader, ModelViewer and KTX1Loader.

No SDK licenses accepted during this task. Existing SDK licenses and emulator used. No global configuration or proxy edits.
