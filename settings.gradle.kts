pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "sdl-image-kmp"

// sdl-kmp provides the SDL3 bindings sdl-image-kmp builds on; the published
// artifacts (cn.enaium.sdl:sdl-kmp:1.0.7) are resolved from the repositories
// declared above (see the `api` dependency in sdl-image-kmp/build.gradle.kts).

include(":sdl-image-kmp")

include(":examples:image_renderer")

// Per-OS/arch JNI artifacts that bundle the prebuilt libsdl_image_jni shared
// library as a classpath resource. The IMG JNI library references the SDL3
// symbols exported by libsdl_jni (from the sdl-kmp project), so the matching
// sdl-kmp JNI artifact must be on the classpath too (sdl-kmp pulls it in
// automatically). ImageNativeLoader extracts the matching one at runtime.
listOf(
    "linux-x86_64",
    "linux-aarch64",
    "darwin-x86_64",
    "darwin-aarch64",
    "windows-x86_64",
).forEach { classifier ->
    val name = ":image-jni-jvm-$classifier"
    include(name)
    project(name).projectDir = file("jni/jvm/$classifier")
}
