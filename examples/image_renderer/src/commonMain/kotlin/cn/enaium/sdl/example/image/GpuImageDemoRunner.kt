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
import cn.enaium.sdl.SDLGPU
import cn.enaium.sdl.SDLInitFlags
import cn.enaium.sdl.SDLWindowFlags

/**
 * Runs the GPU demo: loads [path] straight into an `SDL_GPUTexture` with
 * [cn.enaium.sdl.image.SDLImage.loadGPUTexture] and presents it with
 * `SDL_BlitGPUTexture` every frame. When [path] is null (or empty) the PNG,
 * JPG and animated GIF samples are generated with the bindings themselves
 * (see [generateExampleAssets]) and the GIF is played by re-uploading each
 * frame with `SDLGPUTexture.upload`.
 *
 * [maxFrames] limits the run in headless CI; pass 0 to run until the window
 * is closed.
 */
fun runGpuExample(path: String?, maxFrames: Int = 300) {
    SDL.setMainReady()

    if (!SDL.init(SDLInitFlags.VIDEO or SDLInitFlags.EVENTS)) {
        error("SDL_Init failed: ${SDL.error()}")
    }

    val device = SDLGPU.createDevice()
        ?: error("SDL_CreateGPUDevice failed: ${SDL.error()} (drivers: ${SDLGPU.drivers})")

    var imagePath = path
    if (imagePath.isNullOrEmpty()) {
        imagePath = verifyExampleAssets(generateExampleAssets())
        println("generated test images; playing $imagePath")
    }

    device.use {
        SDL.createWindow("sdl-image-kmp (GPU)", 800, 600, SDLWindowFlags.RESIZABLE).use { window ->
            val demo = GpuImageDemo(window, device, imagePath, maxFrames)
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