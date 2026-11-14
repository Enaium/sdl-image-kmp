import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    macosArm64 {
        binaries.executable()
    }

    macosX64 {
        binaries.executable()
    }

    linuxX64 {
        binaries.executable()
    }

    mingwX64 {
        binaries.executable()
    }

    wasmJs {
        binaries.executable()
        browser()
    }

    sourceSets {
        // Kotlin 2.4's default hierarchy template does not create nativeMain
        // automatically; declare it and attach the native targets.
        val nativeMain = create("nativeMain") {
            dependsOn(getByName("commonMain"))
        }
        macosArm64Main {
            dependsOn(nativeMain)
        }
        macosX64Main {
            dependsOn(nativeMain)
        }
        linuxX64Main {
            dependsOn(nativeMain)
        }
        mingwX64Main {
            dependsOn(nativeMain)
        }

        jvm {
            mainRun {
                mainClass = "cn.enaium.sdl.example.image.Main_jvmKt"
            }
        }

        commonMain {
            dependencies {
                implementation(project(":sdl-image-kmp"))
                // The example drives the 2D renderer directly.
                implementation(libs.sdl.kmp)
            }
        }
    }
}

tasks.withType(JavaExec::class.java).configureEach {
    if (OperatingSystem.current().isMacOsX && name == "jvmRun") {
        jvmArgs("--enable-native-access=ALL-UNNAMED", "-XstartOnFirstThread")
    }
}

// ==================== wasm: static web app ====================
// Both Emscripten modules are consumed as npm packages: @enaium/sdl-kmp (SDL3,
// from the registry) and @enaium/sdl-image-kmp (SDL_image, staged by this
// repository's assembleWasmNpm task).
//
// The wasmJs webpack distribution requires npm, which is not always available,
// so assemble a self-contained static app directory instead:
//   build/browserApp/
//     index.html                     (entry page; import map -> packages)
//     node_modules/@enaium/sdl-kmp/  (glue + SDL3 wasm)
//     node_modules/@enaium/sdl-image-kmp/ (glue + SDL_image wasm)
//     browser-node-test.mjs          (headless Node runner, bare imports)
//     *.mjs/*.wasm                   (the Kotlin/Wasm executable output)
// Serve it with any static file server (e.g. `python3 -m http.server`).

val sdlImageKmpProject = project(":sdl-image-kmp")
val kotlinWasmOutput = layout.buildDirectory.dir("compileSync/wasmJs/main/developmentExecutable/kotlin").get().asFile
val installedSdlPackage = layout.projectDirectory.dir("node_modules/@enaium/sdl-kmp")
val installedImagePackage = layout.projectDirectory.dir("node_modules/@enaium/sdl-image-kmp")

val npmInstall = tasks.register<Exec>("npmInstall") {
    description = "Installs the example's npm dependencies (@enaium/sdl-kmp, @enaium/sdl-image-kmp)."
    dependsOn(sdlImageKmpProject.tasks.named("assembleWasmNpm"))
    workingDir = layout.projectDirectory.asFile
    commandLine("npm", "install", "--no-audit", "--no-fund")
    inputs.file(layout.projectDirectory.file("package.json"))
    outputs.dir(layout.projectDirectory.dir("node_modules"))
}

val copyWebAssets = tasks.register<Copy>("copyWebAssets") {
    description = "Stages the wasm app (Kotlin/Wasm output + both npm packages)."
    dependsOn("compileDevelopmentExecutableKotlinWasmJs")
    dependsOn(npmInstall)
    from("src/wasmJsMain/resources/web") {
        include("**")
    }
    from(layout.projectDirectory) {
        include("browser-node-test.mjs")
    }
    // npm installs file: dependencies as symlinks; copy the real package
    // contents so the static app resolves them through the import map
    // (browser) and through node_modules (Node).
    from(provider { installedSdlPackage.asFile.canonicalFile }) {
        into("node_modules/@enaium/sdl-kmp")
    }
    from(provider { installedImagePackage.asFile.canonicalFile }) {
        into("node_modules/@enaium/sdl-image-kmp")
    }
    from(kotlinWasmOutput) {
        include("**")
    }
    into(layout.buildDirectory.dir("browserApp"))
}
