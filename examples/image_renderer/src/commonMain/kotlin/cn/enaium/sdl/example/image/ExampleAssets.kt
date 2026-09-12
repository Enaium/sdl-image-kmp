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
import cn.enaium.sdl.SDLPixelFormat
import cn.enaium.sdl.SDLRect
import cn.enaium.sdl.SDLSurface
import cn.enaium.sdl.image.SDLImage

/**
 * The image files the demos load when no path is given. The example ships no
 * binary assets; everything is generated in memory and written with the
 * bindings themselves, so each format exercises both directions (encode and
 * decode) of the SDL_image backend:
 *
 *  - [png] — a still checkerboard written with [SDLImage.savePNG];
 *  - [jpg] — the same image written lossily with [SDLImage.saveJPG];
 *  - [gif] — an 8-frame animation written with the streaming
 *    [SDLImage.createAnimationEncoder] / `addFrame` API.
 */
data class ExampleAssets(
    val png: String,
    val jpg: String,
    val gif: String,
)

private const val STILL_SIZE = 128
private const val FRAME_SIZE = 96
private const val FRAME_COUNT = 8
private const val FRAME_DELAY_MS = 100L

/** Generates the PNG/JPG/GIF sample files and returns their paths. */
internal fun generateExampleAssets(): ExampleAssets {
    val png = "sdl-image-kmp-demo.png"
    val jpg = "sdl-image-kmp-demo.jpg"
    val gif = "sdl-image-kmp-demo.gif"

    // Still image: checkerboard, written as PNG (lossless) and JPG (lossy).
    checkerboard(STILL_SIZE, STILL_SIZE, 16).use { surface ->
        check(SDLImage.savePNG(surface, png)) { "IMG_SavePNG failed: ${SDLImage.error()}" }
        check(SDLImage.saveJPG(surface, jpg, quality = 90)) { "IMG_SaveJPG failed: ${SDLImage.error()}" }
    }

    // Animated image: a moving highlight, written frame by frame with the
    // streaming encoder.
    val encoder = SDLImage.createAnimationEncoder(gif)
        ?: error("IMG_CreateAnimationEncoder failed: ${SDLImage.error()}")
    try {
        for (frame in 0 until FRAME_COUNT) {
            animationFrame(frame).use { surface ->
                check(encoder.addFrame(surface, FRAME_DELAY_MS)) {
                    "IMG_AddAnimationEncoderFrame failed: ${SDLImage.error()}"
                }
            }
        }
    } finally {
        encoder.close()
    }

    return ExampleAssets(png, jpg, gif)
}

/**
 * Loads each generated file back and reports what was decoded, proving the
 * encoders and decoders agree. Returns the GIF path (the animated one), which
 * is what the demo plays by default.
 */
internal fun verifyExampleAssets(assets: ExampleAssets): String {
    SDLImage.load(assets.png)?.use { surface ->
        println("PNG   ${assets.png}: ${surface.width}x${surface.height}px, format=0x${surface.format.toString(16)}")
    } ?: error("IMG_Load(PNG) failed: ${SDLImage.error()}")

    SDLImage.load(assets.jpg)?.use { surface ->
        println("JPG   ${assets.jpg}: ${surface.width}x${surface.height}px, format=0x${surface.format.toString(16)}")
    } ?: error("IMG_Load(JPG) failed: ${SDLImage.error()}")

    SDLImage.loadAnimation(assets.gif)?.use { animation ->
        println(
            "GIF   ${assets.gif}: ${animation.width}x${animation.height}px, " +
                "${animation.count} frames, delays=${animation.delays.toList()}",
        )
    } ?: error("IMG_LoadAnimation(GIF) failed: ${SDLImage.error()}")

    return assets.gif
}

/** A [size]x[size] checkerboard surface. */
private fun checkerboard(size: Int, height: Int, cell: Int): SDLSurface {
    val surface = SDL.createSurface(size, height, SDLPixelFormat.RGBA8888)
    for (y in 0 until height step cell) {
        for (x in 0 until size step cell) {
            val dark = ((x / cell) + (y / cell)) % 2 == 0
            val color = if (dark) SDLColor(0x40, 0x40, 0x50) else SDLColor(0xC0, 0xC0, 0xB0)
            surface.fillRect(SDLRect(x, y, cell, cell), color)
        }
    }
    return surface
}

/** One animation frame: a bright square travelling across a dark background. */
private fun animationFrame(index: Int): SDLSurface {
    val surface = SDL.createSurface(FRAME_SIZE, FRAME_SIZE, SDLPixelFormat.RGBA8888)
    surface.fillRect(SDLRect(0, 0, FRAME_SIZE, FRAME_SIZE), SDLColor(0x10, 0x10, 0x18))
    val square = FRAME_SIZE / 4
    val travel = FRAME_SIZE - square
    val x = travel * index / (FRAME_COUNT - 1).coerceAtLeast(1)
    surface.fillRect(SDLRect(x, (FRAME_SIZE - square) / 2, square, square), SDLColor(0xE0, 0x50, 0x40))
    return surface
}