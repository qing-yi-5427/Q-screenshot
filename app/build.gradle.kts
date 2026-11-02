plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose") }
// CI injects a deterministic tag version; normal local builds retain their defaults.
val ciVersionName = providers.gradleProperty("ciVersionName").orNull
val ciVersionCode = providers.gradleProperty("ciVersionCode").orNull
require((ciVersionName == null) == (ciVersionCode == null)) { "Supply both ciVersionName and ciVersionCode" }
if (ciVersionName != null) {
    val parts = ciVersionName.split('.').map { it.toIntOrNull() }
    require(parts.size == 3 && parts.all { it != null } && ciVersionName.matches(Regex("""(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)"""))) { "Invalid CI version name" }
    val major = parts[0]!!; val minor = parts[1]!!; val patch = parts[2]!!
    require(major in 0..2099 && minor in 0..999 && patch in 0..999) { "CI version components out of range" }
    val expected = major * 1_000_000 + minor * 1_000 + patch
    require(expected > 0 && ciVersionCode?.toIntOrNull() == expected) { "CI versionCode does not match versionName" }
}
android {
    namespace = "dev.qingframe"
    compileSdk { version = release(37) { minorApiLevel = 0 } }
    defaultConfig {
        applicationId = "dev.qingframe"
        minSdk = 29
        targetSdk = 36
        versionCode = ciVersionCode?.toInt() ?: 7006
        versionName = ciVersionName ?: "0.7.6"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { jniLibs { useLegacyPackaging = false } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
    implementation(files("libs/filament-v1.79.0-android.aar", "libs/gltfio-v1.79.0-android.aar", "libs/filament-utils-v1.79.0-android.aar"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
