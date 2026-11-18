import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

group = rootProject.group
version = rootProject.version

val imageDir = rootProject.projectDir.resolve("includes/SDL_image")
val sdlDir = rootProject.projectDir.resolve("includes/SDL")

val hostOs = OperatingSystem.current()
val hostArch = System.getProperty("os.arch").lowercase()

// Apple targets build on macOS via Xcode; linuxX64 is built on Linux hosts;
// linuxArm64 is built on Linux aarch64 hosts or cross-compiled from x86_64
// with the aarch64-linux-gnu toolchain; mingwX64 is cross-compiled on Linux
// hosts with the x86_64-w64-mingw32 toolchain.
fun hasMingwCrossToolchain(): Boolean {
    val name = "x86_64-w64-mingw32-gcc"
    return System.getenv("PATH")?.split(File.pathSeparator).orEmpty().any { dir ->
        val f = File(dir, name)
        f.isFile && f.canExecute()
    }
}

fun hasAarch64CrossToolchain(): Boolean {
    val name = "aarch64-linux-gnu-gcc"
    return System.getenv("PATH")?.split(File.pathSeparator).orEmpty().any { dir ->
        val f = File(dir, name)
        f.isFile && f.canExecute()
    }
}

// Reads sdk.dir from local.properties (the standard place Gradle's Android
// plugins put the SDK path, e.g. /Users/<user>/Library/Android/sdk).
fun localSdkDir(): String? {
    val f = rootProject.file("local.properties")
    if (!f.isFile) return null
    return f.readLines()
        .firstOrNull { it.trimStart().startsWith("sdk.dir=") }
        ?.substringAfter('=')
        ?.trim()
}

// Locates an installed Android NDK, preferring the highest version under
// $ANDROID_HOME (or $ANDROID_SDK_ROOT, or local.properties' sdk.dir, or
// ~/Android/Sdk). androidNative targets cross-compile the SDL_image static
// library with this toolchain.
fun androidNdkPath(): String? {
    val home = System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: localSdkDir()
        ?: System.getProperty("user.home") + "/Android/Sdk"
    val ndkDir = File(home, "ndk")
    if (!ndkDir.isDirectory) return null
    return ndkDir.listFiles()
        ?.filter { it.isDirectory && it.name.matches(Regex("\\d+\\.\\d+\\.\\d+.*")) }
        ?.sortedBy { it.name }
        ?.lastOrNull()
        ?.absolutePath
}

fun canBuildNativeTarget(targetName: String): Boolean {
    return when {
        hostOs.isMacOsX && (
                targetName.startsWith("macos") ||
                        targetName.startsWith("ios") ||
                        targetName.startsWith("tvos")
                ) -> true

        hostOs.isLinux && targetName == "linuxX64" -> true
        hostOs.isLinux && targetName == "linuxArm64" &&
                (hostArch == "aarch64" || hostArch == "arm64" || hasAarch64CrossToolchain()) -> true
        hostOs.isLinux && targetName == "mingwX64" && hasMingwCrossToolchain() -> true
        targetName.startsWith("androidNative") && androidNdkPath() != null -> true
        else -> false
    }
}

fun resolveCmakeExecutable(): String {
    val exeName = if (OperatingSystem.current().isWindows) "cmake.exe" else "cmake"

    System.getenv("PATH")?.split(File.pathSeparator).orEmpty().forEach { dir ->
        val candidate = File(dir, exeName)
        if (candidate.isFile && candidate.canExecute()) return candidate.absolutePath
    }

    val extraPaths = listOf(
        "/opt/homebrew/bin",
        "/usr/local/bin",
        "/usr/bin",
        "/opt/local/bin",
    )
    extraPaths.forEach { dir ->
        val candidate = File(dir, exeName)
        if (candidate.isFile && candidate.canExecute()) return candidate.absolutePath
    }

    return exeName
}

val cmakeExecutable: String by lazy { resolveCmakeExecutable() }

// Minimum deployment targets. These match Kotlin/Native 2.4 defaults
// (macos 12.0, ios/tvos 15.0) so the SDL_image static library objects never
// exceed the final binary's minimum version.
val appleDeploymentTargets = mapOf(
    "macos" to "12.0",
    "ios" to "15.0",
    "tvos" to "15.0",
)

fun deploymentTargetFor(targetName: String): String {
    val prefix = listOf("macos", "ios", "tvos").first { targetName.startsWith(it) }
    return appleDeploymentTargets.getValue(prefix)
}

kotlin {
    // ==================== JVM ====================
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    // ==================== Kotlin/Wasm ====================
    // Kotlin/Wasm has no cinterop, so SDL_image is compiled to a standalone
    // Emscripten module (see the wasm section below and wasm/); the wasmJs
    // actuals drive it through a JS glue layer.
    wasmJs()

    // ==================== Native ====================
    macosArm64()
    macosX64()

    linuxX64()
    linuxArm64()
    androidNativeArm64()
    androidNativeArm32()
    androidNativeX64()
    androidNativeX86()

    mingwX64()

    iosArm64()
    iosX64()
    iosSimulatorArm64()

    tvosArm64()
    tvosSimulatorArm64()

    // ==================== cinterop for all native targets ====================
    targets.withType<KotlinNativeTarget> {
        val targetName = this.name
        val canBuild = canBuildNativeTarget(targetName)

        compilations.getByName("main") {
            cinterops {
                create("image") {
                    defFile(project.file("src/nativeInterop/cinterop/image.def"))
                    includeDirs(
                        project.file("src/nativeInterop/cinterop"),
                        rootProject.file("includes/SDL/include"),
                        rootProject.file("includes/SDL_image/include"),
                    )
                    // crc32 intrinsics from the MinGW sysroot's <intrin.h>
                    // (and SDL headers) require SSE4.2 on x86_64.
                    if (targetName.endsWith("X64")) {
                        compilerOpts("-msse4.2")
                    }
                    if (canBuild) {
                        // Embed the per-target static library into the produced
                        // cinterop klib. Targets that can't be built on this host
                        // still get bindings (for klib publishing); the static
                        // library is built and embedded when building on the
                        // matching host.
                        val outputDir = layout.buildDirectory.dir("native/$targetName").get().asFile
                        extraOpts(
                            "-libraryPath", outputDir.absolutePath,
                            "-staticLibrary", "libSDL3_image.a",
                        )
                    }
                }
            }
            defaultSourceSet.kotlin.srcDir("src/nativeMain/kotlin")
        }
    }

    // ==================== Source sets ====================
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
                // SDL3 bindings; exposed as `api` because the public API
                // (SDLSurface, SDLTexture, SDLRenderer, SDLIOStream, ...)
                // references the cn.enaium.sdl types.
                api("cn.enaium.sdl:sdl-kmp:3.4.18.2")
            }
        }

        commonTest {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        wasmJsMain {
            dependsOn(getByName("commonMain"))
            dependencies {
                // Typed arrays (Int8Array/Int32Array) used to marshal data
                // across the JS bridge into the Emscripten-compiled module.
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.5.0")
            }
        }

        jvmMain {
            dependencies {
                // SDL_image on the JVM comes from our own JNI shared library
                // (libsdl_image_jni), which references the SDL3 symbols of
                // libsdl_jni (pulled in transitively by the sdl-kmp
                // dependency). Bundle all five JNI artifacts so consumers get
                // the right native binary out of the box; ImageNativeLoader
                // picks one at runtime by os.name/os.arch.
                runtimeOnly(project(":image-jni-jvm-linux-x86_64"))
                runtimeOnly(project(":image-jni-jvm-linux-aarch64"))
                runtimeOnly(project(":image-jni-jvm-darwin-x86_64"))
                runtimeOnly(project(":image-jni-jvm-darwin-aarch64"))
                runtimeOnly(project(":image-jni-jvm-windows-x86_64"))
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.junit.jupiter)
                runtimeOnly(libs.junit.platform.launcher)
            }
        }
    }
}

// ==================== Native: build static SDL_image library for each target ====================
val nativeDir = projectDir.resolve("native")

fun registerNativeBuildTasks(targetName: String, cmakeFlags: List<String> = emptyList()) {
    val outputDir = layout.buildDirectory.dir("native/$targetName").get().asFile
    val cmakeBuildDir = layout.buildDirectory.dir("cmake-$targetName").get().asFile

    val commonFlags = listOf(
        cmakeExecutable, nativeDir.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DSDL_SOURCE_DIR=${sdlDir.absolutePath}",
        "-DSDLIMAGE_SOURCE_DIR=${imageDir.absolutePath}",
        "-DSDLIMAGE_KMP_OUTPUT_DIR=${outputDir.absolutePath}",
    )

    val configureTask = tasks.register<Exec>("configureNative_$targetName") {
        onlyIf { canBuildNativeTarget(targetName) }
        doFirst {
            cmakeBuildDir.mkdirs()
            outputDir.mkdirs()
        }
        workingDir = cmakeBuildDir
        commandLine(commonFlags + cmakeFlags)
    }

    val buildTask = tasks.register<Exec>("buildNative_$targetName") {
        onlyIf { canBuildNativeTarget(targetName) }
        dependsOn(configureTask)
        workingDir = cmakeBuildDir
        commandLine(cmakeExecutable, "--build", ".", "--config", "Release")
    }

    tasks.matching {
        it.name.startsWith("cinteropImage") &&
                it.name.endsWith(targetName.replaceFirstChar { c -> c.uppercase() })
    }.configureEach {
        dependsOn(buildTask)
        // The cinterop task's custom up-to-date check only watches headers and
        // the .def file; register the static library as an input so a rebuild
        // of SDL_image re-embeds it (otherwise stale archives would leak into the
        // published klib on incremental builds).
        inputs.file(outputDir.resolve("libSDL3_image.a"))
    }
}

if (hostOs.isMacOsX) {
    registerNativeBuildTasks(
        "macosArm64",
        listOf(
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=${deploymentTargetFor("macosArm64")}",
        ),
    )
    registerNativeBuildTasks(
        "macosX64",
        listOf(
            "-DCMAKE_OSX_ARCHITECTURES=x86_64",
            "-DCMAKE_SYSTEM_PROCESSOR=x86_64",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=${deploymentTargetFor("macosX64")}",
        ),
    )
    registerNativeBuildTasks(
        "iosArm64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=iOS",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_SYSROOT=iphoneos",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=${deploymentTargetFor("iosArm64")}",
        ),
    )
    registerNativeBuildTasks(
        "iosX64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=iOS",
            "-DCMAKE_SYSTEM_PROCESSOR=x86_64",
            "-DCMAKE_OSX_ARCHITECTURES=x86_64",
            "-DCMAKE_OSX_SYSROOT=iphonesimulator",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=${deploymentTargetFor("iosX64")}",
        ),
    )
    registerNativeBuildTasks(
        "iosSimulatorArm64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=iOS",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_SYSROOT=iphonesimulator",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=${deploymentTargetFor("iosSimulatorArm64")}",
        ),
    )
    registerNativeBuildTasks(
        "tvosArm64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=tvOS",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_SYSROOT=appletvos",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=${deploymentTargetFor("tvosArm64")}",
        ),
    )
    registerNativeBuildTasks(
        "tvosSimulatorArm64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=tvOS",
            "-DCMAKE_SYSTEM_PROCESSOR=arm64",
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_SYSROOT=appletvsimulator",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=${deploymentTargetFor("tvosSimulatorArm64")}",
        ),
    )
} else if (hostOs.isLinux) {
    registerNativeBuildTasks("linuxX64")
    // linuxArm64 is cross-compiled on x86_64 hosts with the aarch64-linux-gnu
    // toolchain (canBuildNativeTarget gates on it).
    registerNativeBuildTasks(
        "linuxArm64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=Linux",
            "-DCMAKE_SYSTEM_PROCESSOR=aarch64",
            "-DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc",
            "-DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++",
        ),
    )
    // Cross-compile the MinGW static library with the
    // x86_64-w64-mingw32 toolchain (canBuildNativeTarget gates on it).
    registerNativeBuildTasks(
        "mingwX64",
        listOf(
            "-DCMAKE_SYSTEM_NAME=Windows",
            "-DCMAKE_SYSTEM_PROCESSOR=x86_64",
            "-DCMAKE_C_COMPILER=x86_64-w64-mingw32-gcc",
            "-DCMAKE_CXX_COMPILER=x86_64-w64-mingw32-g++",
        ),
    )
}

// Android native targets cross-compile SDL_image with the NDK (see
// canBuildNativeTarget); the NDK can be used from any host OS.
androidNdkPath()?.let { ndk ->
    val toolchain = "$ndk/build/cmake/android.toolchain.cmake"
    val androidFlags = { abi: String, platform: String ->
        listOf(
            "-DCMAKE_TOOLCHAIN_FILE=$toolchain",
            "-DANDROID_ABI=$abi",
            "-DANDROID_PLATFORM=$platform",
        )
    }
    registerNativeBuildTasks("androidNativeArm64", androidFlags("arm64-v8a", "android-24"))
    registerNativeBuildTasks("androidNativeArm32", androidFlags("armeabi-v7a", "android-24"))
    registerNativeBuildTasks("androidNativeX64", androidFlags("x86_64", "android-24"))
    registerNativeBuildTasks("androidNativeX86", androidFlags("x86", "android-24"))
}

// ==================== Publishing ====================
mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    coordinates(
        groupId = group.toString(),
        artifactId = "sdl-image-kmp",
        // null -> the plugin falls back to project.version
        version = null,
    )

    pom {
        name.set("sdl-image-kmp")
        description.set(
            "Kotlin Multiplatform bindings for SDL_image, built on top of sdl-kmp. " +
                    "JVM uses a self-contained JNI shared library that resolves its SDL3 " +
                    "symbols from sdl-kmp's libsdl_jni; native targets embed the statically " +
                    "compiled SDL_image library into the published klib.",
        )
        url.set("https://github.com/Enaium/sdl-image-kmp")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("Zlib")
                url.set("https://opensource.org/license/zlib")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("Enaium")
            }
        }

        scm {
            url.set("https://github.com/Enaium/sdl-image-kmp")
            connection.set("scm:git:git@github.com:Enaium/sdl-image-kmp.git")
            developerConnection.set("scm:git:git@github.com:Enaium/sdl-image-kmp.git")
        }

        issueManagement {
            system.set("GitHub")
            url.set("https://github.com/Enaium/sdl-image-kmp/issues")
        }
    }
}

// ==================== wasm: build the SDL_image module ====================
// Kotlin/Wasm cannot embed C libraries and has no cinterop, so SDL3 + SDL_image
// are compiled to a standalone Emscripten module (sdl_image_wasm.js +
// sdl_image_wasm.wasm) that the wasmJs actuals drive through the JS glue in
// src/wasmJsMain (see SDLImage.wasm.kt). The module is a separate wasm
// instance from sdl-kmp's, so only pixels, not pointers, cross the boundary:
// decoders convert to RGBA8888 and the pixels are handed to sdl-kmp's
// SDL.createSurface, which keeps the returned objects interoperable with the
// rest of sdl-kmp (renderers, textures, renderTexture).

fun emsdkPath(): String {
    System.getenv("EMSDK")?.takeIf { it.isNotBlank() }?.let { return it }
    providers.gradleProperty("wasm.emsdk").orNull?.takeIf { it.isNotBlank() }?.let { return it }
    val home = System.getProperty("user.home")
    listOf(File(home, "emsdk"), File(home, "sdk/emsdk"))
        .forEach { if (it.isDirectory()) return it.absolutePath }
    return ""
}

val wasmEmsdk = emsdkPath()
val wasmEmcc = File(wasmEmsdk, "upstream/emscripten/emcc")
val wasmToolchain = File(wasmEmsdk, "upstream/emscripten/cmake/Modules/Platform/Emscripten.cmake")
val wasmSourceDir = projectDir.resolve("wasm")
val wasmModuleOutput = layout.buildDirectory.dir("wasmModule").get().asFile

// Emscripten needs Python >= 3.10; the emsdk ships its own interpreter, which
// may not be on PATH (macOS ships Python 3.9 as /usr/bin/python3).
fun wasmEnv(): Map<String, String> {
    val pythonDir = File(wasmEmsdk, "python").listFiles()?.firstOrNull { it.isDirectory }
    return if (pythonDir != null) {
        mapOf("PATH" to "${pythonDir.absolutePath}/bin:${System.getenv("PATH")}")
    } else {
        emptyMap()
    }
}

tasks.register<Exec>("configureWasmModule") {
    group = "build"
    description = "Configures the Emscripten build of SDL3 + SDL_image."
    onlyIf { wasmEmsdk.isNotEmpty() }
    doFirst {
        File(wasmModuleOutput, "cmake").mkdirs()
        wasmModuleOutput.mkdirs()
    }
    workingDir = File(wasmModuleOutput, "cmake")
    environment(wasmEnv())
    // A toolchain file disables the generator's normal make discovery, so the
    // make program must be passed explicitly (emcmake does the same).
    val makeProgram = if (hostOs.isWindows) "nmake" else "/usr/bin/make"
    commandLine(
        cmakeExecutable, wasmSourceDir.absolutePath,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DCMAKE_TOOLCHAIN_FILE=${wasmToolchain.absolutePath}",
        "-DCMAKE_MAKE_PROGRAM=${makeProgram}",
        "-DSDL_SOURCE_DIR=${sdlDir.absolutePath}",
        "-DSDLIMAGE_SOURCE_DIR=${imageDir.absolutePath}",
        "-DSDLIMAGE_KMP_OUTPUT_DIR=${wasmModuleOutput.absolutePath}",
    )
}

tasks.register<Exec>("buildWasmModuleLibs") {
    group = "build"
    description = "Builds the SDL3 + SDL_image static archives for Emscripten."
    onlyIf { wasmEmsdk.isNotEmpty() }
    dependsOn("configureWasmModule")
    workingDir = File(wasmModuleOutput, "cmake")
    environment(wasmEnv())
    commandLine(cmakeExecutable, "--build", ".", "--config", "Release")
}

tasks.register<Exec>("linkWasmModule") {
    group = "build"
    description = "Links sdl_image_wasm.js/.wasm from the Emscripten archives."
    onlyIf { wasmEmsdk.isNotEmpty() }
    dependsOn("buildWasmModuleLibs")
    workingDir = wasmSourceDir
    environment(wasmEnv())
    doFirst {
        wasmModuleOutput.mkdirs()
        val exports = Regex("sdl_image_kmp_\\w+(?=\\()")
            .findAll(wasmSourceDir.resolve("sdl_image_wasm_shim.c").readText())
            .map { it.value }
            .distinct()
            .joinToString(",") { "_$it" }
        commandLine(
            wasmEmcc.absolutePath,
            wasmSourceDir.resolve("sdl_image_wasm_shim.c").absolutePath,
            File(wasmModuleOutput, "libSDL3_image_wasm.a").absolutePath,
            "-I${sdlDir.absolutePath}/include",
            "-I${imageDir.absolutePath}/include",
            "-o", File(wasmModuleOutput, "sdl_image_wasm.js").absolutePath,
            "-sMODULARIZE=1", "-sEXPORT_NAME=SDLImageModule", "-sEXPORT_ES6=1",
            "-sEXPORTED_FUNCTIONS=${exports},_malloc,_free",
            "-sEXPORTED_RUNTIME_METHODS=ccall,cwrap,UTF8ToString,stringToUTF8,lengthBytesUTF8,HEAPU8,HEAP32",
            "-sALLOW_MEMORY_GROWTH=1", "-sINITIAL_MEMORY=33554432",
            "-sENVIRONMENT=web,worker,node", "-sFILESYSTEM=0", "-sMAX_WEBGL_VERSION=2",
            "--no-entry",
        )
    }
    inputs.file(wasmSourceDir.resolve("sdl_image_wasm_shim.c"))
    inputs.file(wasmSourceDir.resolve("CMakeLists.txt"))
    outputs.file(File(wasmModuleOutput, "sdl_image_wasm.js"))
    outputs.file(File(wasmModuleOutput, "sdl_image_wasm.wasm"))
}

// ==================== wasm: npm package ====================
// The Emscripten module and the JS glue ship as an npm package
// (@enaium/sdl-image-kmp) instead of a Maven "assets" jar: web apps depend on
// it like any other npm package and `import { initSdlImageKmp } from
// "@enaium/sdl-image-kmp"` before running the Kotlin/Wasm module, so no asset
// extraction step is involved.
//
// The package version is the pinned SDL_image release plus this library's own
// revision: npm only accepts three-component semver, so the fourth component
// of the Gradle version (3.4.0.1) becomes a prerelease suffix (3.4.0-1).

val sdlImageVersion: String by lazy {
    val header = imageDir.resolve("include/SDL3_image/SDL_image.h").readText()
    fun macro(name: String): String =
        Regex("""#define\s+$name\s+(\d+)""").find(header)?.groupValues?.get(1)
            ?: error("$name is missing from SDL_image.h")
    "${macro("SDL_IMAGE_MAJOR_VERSION")}.${macro("SDL_IMAGE_MINOR_VERSION")}.${macro("SDL_IMAGE_MICRO_VERSION")}"
}

val npmPackageVersion: String by lazy {
    // The Gradle version already reads <this library's version>-fixed.<pinned
    // SDL_image release> (1.0.2-fixed.3.4.8), which is valid semver: npm takes
    // it verbatim, and the `fixed.*` prerelease suffix is published under the
    // `latest` tag.
    rootProject.version.toString()
}

val wasmNpmDir = layout.buildDirectory.dir("npm").get().asFile
val wasmGlue = wasmSourceDir.resolve("sdl_image_glue.js")

tasks.register("assembleWasmNpm") {
    group = "build"
    description = "Stages the @enaium/sdl-image-kmp npm package in build/npm."
    dependsOn("linkWasmModule")
    inputs.file(wasmGlue)
    // The version comes from this header and the Gradle version, so either
    // bump must re-stage.
    inputs.file(imageDir.resolve("include/SDL3_image/SDL_image.h"))
    inputs.property("npmPackageVersion", npmPackageVersion)
    inputs.file(File(wasmModuleOutput, "sdl_image_wasm.js"))
    inputs.file(File(wasmModuleOutput, "sdl_image_wasm.wasm"))
    outputs.dir(wasmNpmDir)
    doLast {
        // The version string claims the pinned SDL_image release; fail rather
        // than publish a version that names a different one.
        check(npmPackageVersion.endsWith("-fixed.$sdlImageVersion")) {
            "version $npmPackageVersion does not pin the vendored SDL_image $sdlImageVersion"
        }
        wasmNpmDir.deleteRecursively()
        wasmNpmDir.mkdirs()
        wasmGlue.copyTo(wasmNpmDir.resolve("sdl_image_glue.js"), overwrite = true)
        File(wasmModuleOutput, "sdl_image_wasm.js").copyTo(wasmNpmDir.resolve("sdl_image_wasm.js"), overwrite = true)
        File(wasmModuleOutput, "sdl_image_wasm.wasm").copyTo(wasmNpmDir.resolve("sdl_image_wasm.wasm"), overwrite = true)
        wasmNpmDir.resolve("package.json").writeText(
            """
            {
              "name": "@enaium/sdl-image-kmp",
              "version": "$npmPackageVersion",
              "description": "Emscripten-compiled SDL_image module and JS glue for the sdl-image-kmp wasmJs target",
              "license": "Zlib",
              "type": "module",
              "exports": {
                ".": "./sdl_image_glue.js",
                "./sdl_image_wasm.js": "./sdl_image_wasm.js",
                "./package.json": "./package.json"
              },
              "files": ["sdl_image_glue.js", "sdl_image_wasm.js", "sdl_image_wasm.wasm"],
              "homepage": "https://github.com/Enaium/sdl-image-kmp",
              "repository": {
                "type": "git",
                "url": "git+https://github.com/Enaium/sdl-image-kmp.git"
              }
            }
            """.trimIndent() + "\n",
        )
    }
}

tasks.register<Exec>("publishWasmNpm") {
    group = "publishing"
    description = "Publishes @enaium/sdl-image-kmp to npm (token: npm.publish.key)."
    dependsOn("assembleWasmNpm")
    workingDir = wasmNpmDir
    // The token lives in the user-level Gradle properties file
    // (~/.gradle/gradle.properties, next to mavenCentralUsername and the
    // signing.* entries); CI writes the same key there from a repository
    // secret. `-Pnpm.publish.key=<token>` overrides it for one-off runs.
    val npmKey = providers.gradleProperty("npm.publish.key")
    doFirst {
        val key = npmKey.orNull?.takeIf { it.isNotBlank() }
            ?: throw GradleException(
                "npm.publish.key is not set: add it to ~/.gradle/gradle.properties " +
                    "or pass -Pnpm.publish.key=<token>",
            )
        // NODE_AUTH_TOKEN is expanded by npm through the environment, so the
        // token never lands in the package.json/tarball; .npmrc is on npm's
        // never-publish list anyway.
        environment("NODE_AUTH_TOKEN", key)
        wasmNpmDir.resolve(".npmrc").writeText(
            "//registry.npmjs.org/:_authToken=\${NODE_AUTH_TOKEN}\n",
        )
    }
    // The version (1.0.2-fixed.3.4.8) is a semver prerelease, so npm requires
    // an explicit tag; `latest` keeps `npm i @enaium/sdl-image-kmp` working.
    commandLine("npm", "publish", "--access", "public", "--tag", "latest")
}
