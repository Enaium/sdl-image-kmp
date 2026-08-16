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
import cn.enaium.sdl.SDLColor
import cn.enaium.sdl.SDLInitFlags
import cn.enaium.sdl.SDLPixelFormat
import cn.enaium.sdl.SDLRect
import cn.enaium.sdl.SDLWindowFlags
import cn.enaium.sdl.image.SDLImage

/**
 * Runs the demo. [path] is the image file to load; when it is null (or empty)
 * a procedural checkerboard image is generated in memory, saved to a PNG with
 * [cn.enaium.sdl.image.SDLImage.savePNG] and loaded back, exercising the
 * whole pipeline without shipping a sample image.
 *
 * [maxFrames] limits the run in headless CI (SDL_VIDEO_DRIVER=dummy); pass 0
 * to run until the window is closed.
 */
fun runExample(path: String?, maxFrames: Int = 300) {
    SDL.setMainReady()

    if (!SDL.init(SDLInitFlags.VIDEO or SDLInitFlags.EVENTS)) {
        error("SDL_Init failed: ${SDL.error()}")
    }

    var imagePath = path
    if (imagePath.isNullOrEmpty()) {
        imagePath = generateAndSaveImage()
        println("generated test image: $imagePath")
    }

    SDL.createWindow("sdl-image-kmp", 800, 600, SDLWindowFlags.RESIZABLE).use { window ->
        SDL.createRenderer(window).use { renderer ->
            val demo = ImageDemo(window, renderer, imagePath, maxFrames)
            try {
                while (demo.frame()) {
                    SDL.delay(16)
                }
            } finally {
                demo.close()
            }
        }
    }

    SDL.quit()
}

/** Creates a checkerboard surface, saves it as PNG next to the demo and returns its path. */
private fun generateAndSaveImage(): String {
    val size = 128
    val surface = SDL.createSurface(size, size, SDLPixelFormat.RGBA8888)
    for (y in 0 until size step 16) {
        for (x in 0 until size step 16) {
            val dark = ((x / 16) + (y / 16)) % 2 == 0
            val color = if (dark) SDLColor(0x40, 0x40, 0x50) else SDLColor(0xC0, 0xC0, 0xB0)
            surface.fillRect(SDLRect(x, y, 16, 16), color)
        }
    }

    val path = "sdl-image-kmp-demo.png"
    check(SDLImage.savePNG(surface, path)) { "IMG_SavePNG failed: ${SDLImage.error()}" }
    surface.close()
    return path
}
