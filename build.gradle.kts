plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}

// Optional isolated output avoids external desktop folder synchronizers touching intermediates.
System.getenv("QINGFRAME_BUILD_ROOT")?.let { outputRoot ->
    layout.buildDirectory.set(file("$outputRoot/root"))
    subprojects { layout.buildDirectory.set(file("$outputRoot/$name")) }
}
