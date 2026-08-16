/*
 * Per-OS/arch JNI artifact: windows-x86_64.
 * Ships sdl_image_jni.dll as a classpath resource at
 * /cn/enaium/sdl/image/native/windows-x86_64/, which ImageNativeLoader
 * (in :sdl-image-kmp's jvmMain) extracts and System.load()s at runtime.
 *
 * The library references SDL3 symbols resolved from libsdl_jni.dll (shipped
 * by the sdl-kmp project, which exports the SDL3 symbols), so libsdl_jni.dll
 * must be loaded first.
 */
import java.util.zip.ZipFile
import org.gradle.internal.os.OperatingSystem

plugins {
    `java-library`
    alias(libs.plugins.maven.publish)
}

group = rootProject.group
version = rootProject.version

// Resolves the sdl-kmp JVM artifact that bundles libsdl_jni.dll; the JNI
// library is linked against it so its SDL3 exports resolve at load time
// (see jni/CMakeLists.txt: the SDL_JNI_DLL branch links the actual DLL
// instead of compiling a second SDL3 copy, which would collide with the
// SDL3::SDL3 alias target).
val sdlKmpJni by configurations.creating
dependencies {
    sdlKmpJni("cn.enaium.sdl:sdl-kmp-jni-jvm-windows-x86_64:1.0.7")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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

val jniOs = "windows"
val jniArch = "x86_64"
val classifier = "$jniOs-$jniArch"
val libFile = "sdl_image_jni.dll"
val resourceDir = "cn/enaium/sdl/image/native/$classifier"

// The DLL is built natively on Windows hosts only (MinGW). Other hosts still
// get the artifact for dependency resolution, but the JAR ships without the
// DLL; the CI publish-windows job produces the real one.
val host = OperatingSystem.current()
val hostArch = System.getProperty("os.arch").lowercase()
val hostIsWindowsX64 = host.isWindows && (hostArch == "amd64" || hostArch == "x86_64")
val canBuildHere = hostIsWindowsX64

val nativeOutputDir = layout.buildDirectory.dir("jni-native/$classifier")
val cmakeBuildDir = layout.buildDirectory.dir("cmake-jni/$classifier")

val configureJniLibrary by tasks.registering(Exec::class) {
    group = "build"
    description = "cmake-configures sdl_image_jni for $classifier."
    onlyIf { canBuildHere }
    val outDir = nativeOutputDir.get().asFile
    val buildDir = cmakeBuildDir.get().asFile
    doFirst {
        outDir.mkdirs()
        buildDir.mkdirs()
    }
    workingDir = buildDir
    val javaHome = System.getProperty("java.home") ?: System.getenv("JAVA_HOME") ?: ""
    val jniInclude = if (javaHome.isNotEmpty()) "$javaHome/include" else ""
    val makeGenerator = if (System.getenv("MSYSTEM") != null) "MSYS Makefiles" else "MinGW Makefiles"
    val args = mutableListOf(
        cmakeExecutable,
        rootProject.file("jni").absolutePath,
        "-G", makeGenerator,
        "-DCMAKE_BUILD_TYPE=Release",
        "-DJNI_INCLUDE_DIR=$jniInclude",
        "-DJNI_INCLUDE_DIR_PLATFORM=$jniInclude/win32",
        // DLLs are RUNTIME outputs in CMake, not LIBRARY outputs.
        "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=${outDir.absolutePath}",
        "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=${outDir.absolutePath}",
        // Statically link the MinGW runtime so the DLL has no dependency on
        // libstdc++-6.dll / libgcc_s_seh-1.dll, which are not on the JVM's
        // PATH.
        "-DCMAKE_SHARED_LINKER_FLAGS=-static-libgcc -static-libstdc++",
    )
    doFirst {
        // Extract libsdl_jni.dll from the sdl-kmp artifact and generate its
        // import library with gendef + dlltool (execution time only: the
        // tools only exist on Windows hosts, where this task runs).
        extractSdlJniDll(buildDir)?.let { dll ->
            args += "-DSDL_JNI_IMPLIB=${generateImportLib(dll, buildDir).absolutePath}"
        }
    }
    commandLine(args)
}

/**
 * Generates the import library (libsdl_jni.dll.a) for the extracted dll.
 * x86_64 MinGW ld cannot link directly against a DLL (unlike i386), so the
 * dll's exports are converted with gendef + dlltool first.
 */
fun generateImportLib(dll: File, buildDir: File): File {
    val def = File(buildDir, "sdl-jni/${dll.nameWithoutExtension}.def")
    val implib = File(buildDir, "sdl-jni/libsdl_jni.dll.a")
    runProcess("gendef", listOf(dll.absolutePath), buildDir)
    runProcess("dlltool", listOf("-d", def.absolutePath, "-l", implib.absolutePath, "-D", dll.name), buildDir)
    check(implib.isFile) { "dlltool did not produce $implib" }
    return implib
}

fun runProcess(command: String, args: List<String>, workDir: File) {
    val process = ProcessBuilder(listOf(command) + args)
        .directory(workDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    val exit = process.waitFor()
    check(exit == 0) { "$command failed (exit $exit): " + output }
}

/** Extracts libsdl_jni.dll from the sdl-kmp JNI artifact, or null when unavailable. */
fun extractSdlJniDll(buildDir: File): File? {
    val jar = sdlKmpJni.files.firstOrNull { it.name.endsWith(".jar") }
        ?: return null
    var entry: Pair<ByteArray, String>? = null
    ZipFile(jar).use { zip ->
        val e = zip.entries().asSequence()
            .firstOrNull { it.name.endsWith(".dll") } ?: return@use
        entry = zip.getInputStream(e).readBytes() to e.name
    }
    val data = entry ?: return null
    val target = File(buildDir, "sdl-jni/" + data.second.substringAfterLast('/'))
    target.parentFile.mkdirs()
    if (!target.isFile || target.length() != data.first.size.toLong()) {
        target.writeBytes(data.first)
    }
    return target
}

val buildJniLibrary by tasks.registering(Exec::class) {
    group = "build"
    description = "Builds sdl_image_jni.dll for $classifier."
    onlyIf { canBuildHere }
    dependsOn(configureJniLibrary)
    workingDir = cmakeBuildDir.get().asFile
    commandLine(cmakeExecutable, "--build", ".", "--config", "Release")
    inputs.files(
        rootProject.file("jni/CMakeLists.txt"),
        rootProject.file("jni/jni_image.cpp"),
    )
    inputs.dir(rootProject.file("includes/SDL_image"))
    inputs.dir(rootProject.file("includes/SDL"))
    outputs.file(nativeOutputDir.map { it.file(libFile) })
}

tasks.named<Copy>("processResources") {
    dependsOn(buildJniLibrary)
    from(buildJniLibrary.map { it.outputs.files }) {
        include(libFile)
        into(resourceDir)
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    coordinates(
        groupId = rootProject.group.toString(),
        artifactId = "sdl-image-kmp-jni-jvm-$classifier",
        version = rootProject.version.toString(),
    )
    pom {
        name.set("sdl-image-kmp-jni-jvm-$classifier")
        description.set(
            "Prebuilt JNI shared library for sdl-image-kmp on $jniOs/$jniArch. " +
                "Loaded automatically by ImageNativeLoader; not intended to be depended on directly.",
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
