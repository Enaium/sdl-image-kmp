# sdl-image-kmp

Kotlin Multiplatform bindings for [SDL_image 3](https://github.com/libsdl-org/SDL_image) (image loading with PNG/JPG/GIF/WebP/TIFF/... and animation support), built on top of [sdl-kmp](https://github.com/Enaium/sdl-kmp). The public API lives in the `cn.enaium.sdl.image` package and works directly with the sdl-kmp types (`SDLSurface`, `SDLTexture`, `SDLRenderer`, `SDLIOStream`, ...).

Two implementations, mirroring sdl-kmp:

- **JVM**: SDL3 and SDL_image (with the vendored zlib/libpng/libwebp/libtiff and the stb-based loaders from this repository's `SDL_image` submodule) are compiled by CMake (`jni/`) into a self-contained JNI shared library (`libsdl_image_jni`), shipped as per-OS/arch `sdl-image-kmp-jni-jvm-*` artifacts — the same self-contained approach as sdl-kmp's `libsdl_jni`. `ImageNativeLoader` extracts the matching binary at runtime. The process contains a second SDL3 copy; SDL_image errors are read through the IMG-side `SDL_GetError` (`SDLImage.error()`), and SDL objects from the sdl-kmp library are operated on through SDL3's function-pointer interfaces, so the copies do not interfere.
- **Native (Kotlin/Native)**: the SDL_image static library (including the vendored zlib/libpng/libwebp/libtiff, merged into a single archive) is compiled per target with CMake and **embedded into the published klib**. SDL3 itself is not compiled: the SDL3 symbols are resolved at the consumer's final link from the sdl-kmp klib, which is always present because the bindings use the `cn.enaium.sdl` types.

## Supported platforms

| Platform | Targets                                             | Implementation                                  |
|----------|-----------------------------------------------------|-------------------------------------------------|
| JVM      | `jvm` (Linux/macOS/Windows)                         | JNI shared library (`libsdl_image_jni`), SDL3 + SDL_image compiled from source |
| macOS    | `macosArm64`, `macosX64`                            | cinterop + embedded static SDL_image            |
| Linux    | `linuxX64`, `linuxArm64`                            | cinterop + embedded static SDL_image            |
| Windows  | `mingwX64`                                          | cinterop + embedded static SDL_image            |
| iOS      | `iosArm64`, `iosX64`, `iosSimulatorArm64`           | cinterop + embedded static SDL_image            |
| tvOS     | `tvosArm64`, `tvosSimulatorArm64`                   | cinterop + embedded static SDL_image            |
| Android  | `androidNativeArm64`, `androidNativeArm32`, `androidNativeX64`, `androidNativeX86` | cinterop + embedded static SDL_image (built with the NDK) |

Formats: PNG, JPG (via stb_image), WebP, TIFF, BMP, GIF, CUR/ICO, LBM, PCX, PNM, QOI, SVG, TGA, XCF, XPM, XV and ANI animations. The format backends are statically linked: the vendored zlib/libpng/libwebp/libtiff archives are merged into the embedded `libSDL3_image.a` on native and into `libsdl_image_jni` on the JVM, so every format works out of the box. AVIF and JXL are disabled to keep the vendored dependency tree small (`SDLIMAGE_AVIF=OFF`, `SDLIMAGE_JXL=OFF`), and WebP is disabled on iOS/tvOS (Apple's ImageIO backend reads WebP natively there).

## Usage

The published version requires [sdl-kmp](https://github.com/Enaium/sdl-kmp) `1.0.7` (it is an `api` dependency, pulled in automatically).

`build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("cn.enaium.sdl:sdl-image-kmp:1.0.0")
        }
    }
}
```

```kotlin
import cn.enaium.sdl.*
import cn.enaium.sdl.image.*

fun main() {
    SDL.setMainReady()

    if (!SDL.init(SDLInitFlags.VIDEO or SDLInitFlags.EVENTS)) {
        error("SDL_Init failed: ${SDL.error()}")
    }

    SDL.createWindow("sdl-image-kmp", 800, 600).use { window ->
        SDL.createRenderer(window).use { renderer ->
            // 1) Load an image into a surface, upload it into a texture and draw it.
            val image = SDLImage.load("image.png")
                ?: error("IMG_Load failed: ${SDLImage.error()}")
            val texture = renderer.createTexture(
                format = image.format,
                access = SDLTextureAccess.STATIC,
                width = image.width,
                height = image.height,
            )
            texture.update(null, image.pixels, image.pitch)
            image.close()

            renderer.drawColor = SDLColor(18, 18, 24)
            renderer.clear()
            renderer.renderTexture(texture, dst = SDLFRect(40f, 40f, texture.size.x, texture.size.y))
            renderer.present()

            texture.close()
        }
    }

    SDL.quit()
}
```

### API overview

- **Loading**: `SDLImage.load`, `SDLImage.loadIO` (from a `cn.enaium.sdl.SDLIOStream`), `SDLImage.loadTypedIO` return `SDLSurface`s. Typed loads skip format autodetection: `loadPNG`, `loadJPG`, `loadGIF`, `loadWEBP`, `loadTIF`, `loadSVG`, `loadSizedSVG`, `readXPMFromArray`, ...
- **Textures**: `SDLImage.loadTexture`/`loadTextureIO`/`loadTextureTypedIO` load directly into an sdl-kmp `SDLRenderer`'s textures; `loadGPUTexture` and friends load into `SDL_GPUTexture`s.
- **Detection**: `isBMP`, `isPNG`, `isGIF`, `isWEBP`, ... probe an `SDLIOStream` and seek it back.
- **Saving**: `SDLImage.save` and the format-specific `savePNG`, `saveJPG`, `saveWEBP`, `saveBMP`, `saveGIF`, ... write an `SDLSurface` to a file or an `SDLIOStream`.
- **Animations**: `SDLImage.loadAnimation` and friends return `SDLImageAnimation`s (frames + delays); the streaming `createAnimationEncoder`/`createAnimationDecoder` API adds and decodes frames one at a time.
- **Errors**: every function either returns null/false or throws; the last error is available via `SDLImage.error()`.

### Platform notes

- **Kotlin version compatibility**: the published klibs are built with Kotlin 2.4.x. Keep the consumer's Kotlin version in sync (the same rule applies to sdl-kmp).
- **macOS JVM**: requires `-XstartOnFirstThread` (the example `jvmRun` task already sets it).
- **JVM native library**: the matching `sdl-image-kmp-jni-jvm-{os}-{arch}` artifact is a transitive runtime dependency; `ImageNativeLoader` extracts `libsdl_image_jni` and `System.load()`s it. `libsdl_image_jni` bundles its own SDL3, so no `java.library.path` setup is needed.
- **Surfaces/textures**: like sdl-ttf-kmp's surfaces, SDL_image's surfaces are NOT sdl-kmp's platform implementation, so APIs that downcast them (such as `SDLRenderer.createTextureFromSurface` and `SDLRenderer.renderTexture`) will reject them; upload with `renderer.createTexture` + `SDLTexture.update` instead and draw with the texture.
- **Android**: building an `androidNative*` target requires an installed Android NDK (found under `$ANDROID_HOME/ndk`); the SDL_image static library is cross-compiled with its CMake toolchain.
- **Headless / CI**: set `SDL_VIDEO_DRIVER=dummy` (hint or environment variable) to run without a display; image loading itself does not need video.

## Examples

- **`examples/image_renderer`** — a renderer demo on top of sdl-kmp's 2D renderer: loads an image from a file (or generates a checkerboard, saves it as PNG with `SDLImage.savePNG` and loads it back when no path is given), uploads the surface into a texture, loads the image directly into a texture with `SDLImage.loadTexture`, and plays animated images frame by frame with `SDLImage.loadAnimation`. Runs on JVM, macOS, Linux and Windows (MinGW):

```bash
# headless (CI / servers)
SDL_VIDEO_DRIVER=dummy ./gradlew :examples:image_renderer:jvmRun
SDL_VIDEO_DRIVER=dummy ./gradlew :examples:image_renderer:runDebugExecutableMacosArm64

# with a window and your own image (PNG/JPG/GIF/...)
./gradlew :examples:image_renderer:jvmRun --args="image.png"
```

Controls: `ESC` quit.

## Building from source

Requirements: JDK 21, CMake, a C compiler; Xcode for Apple targets, the `x86_64-w64-mingw32-gcc` toolchain for MinGW cross-compiles (Linux host), the Android NDK for `androidNative*`.

The `SDL_image` submodule's vendored dependencies (zlib, libpng, jpeg, libwebp, libtiff) are git submodules of SDL_image itself; initialize them with the submodule's own download script:

```bash
git clone --recurse-submodules git@github.com:Enaium/sdl-image-kmp.git
cd sdl-image-kmp
sh includes/SDL_image/external/download.sh
```

Then build and test:

```bash
# compile + test the JVM target
./gradlew :sdl-image-kmp:jvmTest

# run the example headless
SDL_VIDEO_DRIVER=dummy ./gradlew :examples:image_renderer:jvmRun

# publish everything buildable on this host to Maven Local
./gradlew :sdl-image-kmp:publishToMavenLocal :image-jni-jvm-darwin-aarch64:publishToMavenLocal
```

## CI

Both workflows are **manually triggered** (Actions tab):

- **`test.yml`** — local Maven publish + test: publishes every artifact the runner can build to Maven Local, runs the JVM/native tests and the example headless. Use this to verify a change before publishing.
- **`publish.yml`** — formal Maven Central release: publishes the metadata + JVM module, all target klibs and the JNI artifacts to Maven Central, signed with PGP. Requires the repository secrets `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY`, `SIGNING_KEY_ID` and `SIGNING_PASSWORD`.

## License

MIT. The bundled SDL3 submodule is licensed under the [zlib license](https://github.com/libsdl-org/SDL/blob/main/LICENSE.txt).
