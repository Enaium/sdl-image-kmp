/*
 * Copyright (c) 2026 Enaium
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package cn.enaium.sdl.example.image

import cn.enaium.sdl.SDL
import cn.enaium.sdl.SDLInitFlags
import cn.enaium.sdl.SDLWindowFlags
import kotlin.js.ExperimentalJsExport

/**
 * Browser (wasmJs) entry point.
 *
 * Browsers cannot repaint a busy main thread, so the shared [ImageDemo] frame
 * state machine (used by JVM/native through the blocking `runExample` loop) is
 * driven here from `requestAnimationFrame` instead — one frame per browser
 * paint.
 *
 * The host page initializes both Emscripten modules before importing this
 * module: `@enaium/sdl-kmp` (SDL3, the surfaces and the renderer) and
 * `@enaium/sdl-image-kmp` (SDL_image, the decoders). The samples are generated
 * with the bindings themselves, written into the Emscripten file system, and
 * loaded back — the same pipeline the JVM/native demos use.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
fun main() {
    SDL.setMainReady()

    if (!SDL.init(SDLInitFlags.VIDEO or SDLInitFlags.EVENTS)) {
        println("SDL_Init failed: ${SDL.error()}")
        return
    }

    // Headless (Node) runs have no quit event; stop after a fixed frame count.
    val headless = SDL.getCurrentVideoDriver() == "dummy"
    val maxFrames = if (headless) 120 else Int.MAX_VALUE

    val imagePath = verifyExampleAssets(generateExampleAssets())
    println("generated test images; playing $imagePath")

    val window = SDL.createWindow("sdl-image-kmp (wasm)", 800, 600, SDLWindowFlags.RESIZABLE)
    if (window == null) {
        println("SDL_CreateWindow failed: ${SDL.error()}")
        SDL.quit()
        return
    }
    val renderer = SDL.createRenderer(window)
    if (renderer == null) {
        println("SDL_CreateRenderer failed: ${SDL.error()}")
        window.close()
        SDL.quit()
        return
    }

    val demo = ImageDemo(window, renderer, imagePath, maxFrames)

    fun frame() {
        if (demo.frame()) {
            requestAnimationFrame { frame() }
        } else {
            println("ran the demo to completion")
            demo.close()
            renderer.close()
            window.close()
            SDL.quit()
        }
    }

    requestAnimationFrame { frame() }
}

private external fun requestAnimationFrame(callback: () -> Unit): Int
