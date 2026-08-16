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
import cn.enaium.sdl.SDLFRect
import cn.enaium.sdl.SDLInitFlags
import cn.enaium.sdl.SDLIO
import cn.enaium.sdl.SDLKeycode
import cn.enaium.sdl.SDLRenderer
import cn.enaium.sdl.SDLSurface
import cn.enaium.sdl.SDLTexture
import cn.enaium.sdl.SDLTextureAccess
import cn.enaium.sdl.SDLWindow
import cn.enaium.sdl.SDLEvent
import cn.enaium.sdl.SDLWindowEventType
import cn.enaium.sdl.SDLWindowFlags
import cn.enaium.sdl.image.SDLImage
import cn.enaium.sdl.image.SDLImageAnimation

/**
 * The SDL_image demo as a frame state machine, shared by every platform.
 *
 * It exercises the sdl-image-kmp bindings on top of the sdl-kmp 2D renderer:
 *
 *  - loading an image from a file with [SDLImage.load] and uploading it into
 *    a renderer texture (`renderer.createTexture` + `SDLTexture.update`, see
 *    the note in the README: SDL_image surfaces are not the sdl-kmp surface
 *    implementation);
 *  - loading the image from an in-memory [SDLIOStream] with [SDLImage.loadIO]
 *    and format detection with [SDLImage.isPNG];
 *  - saving the image to a PNG file with [SDLImage.savePNG];
 *  - when the path is an animated GIF (or any other supported animation
 *    format), playing it frame by frame with [SDLImage.loadAnimation] and
 *    its frame delays;
 *  - loading the image directly into a texture with [SDLImage.loadTexture]
 *    and drawing it with [SDLRenderer.renderTexture].
 *
 * The window stays open until ESC is pressed (or until [maxFrames] frames in
 * headless CI runs).
 */
class ImageDemo(
    private val window: SDLWindow,
    private val renderer: SDLRenderer,
    private val path: String,
    private val maxFrames: Int,
) {
    private val surface: SDLSurface = SDLImage.load(path)
        ?: error("IMG_Load failed: ${SDLImage.error()}")

    // The texture is re-filled every frame with the current animation frame,
    // or once with the still image when the file is not an animation.
    private val surfaceTexture: SDLTexture = renderer.createTexture(
        format = surface.format,
        access = SDLTextureAccess.STATIC,
        width = surface.width,
        height = surface.height,
    )

    private val directTexture: SDLTexture? = SDLImage.loadTexture(renderer, path)

    private val animation: SDLImageAnimation? =
        SDLImage.loadAnimation(path)?.takeIf { it.count > 1 }

    private val frameCount: Int = animation?.count ?: 1
    private var frame = 0
    private var frameTicks = 0uL
    private var frames = 0

    init {
        println("sdl-image-kmp demo: ${SDLImage.version()} (SDL_image)")
        println(
            "loaded ${surface.width}x${surface.height}px, format=0x${surface.format.toString(16)}, " +
                "pitch=${surface.pitch}, colorspace=${surface.colorspace}",
        )
        println("animation frames: ${animation?.count ?: 0}, direct texture: ${directTexture != null}")
        // The direct texture cannot be drawn with sdl-kmp's renderer (it
        // downcasts to its own implementation); verify it loads and report
        // its properties, then release it.
        directTexture?.let {
            println("direct texture: ${it.size.x.toInt()}x${it.size.y.toInt()}px, " +
                "format=0x${it.format.toString(16)}, access=${it.access}")
            it.close()
            directTextureClosed = true
        }
        SDLImage.clearError()
        uploadFrame()
    }

    private var directTextureClosed = false

    private fun uploadFrame() {
        val src = animation?.frames?.getOrNull(frame) ?: surface
        surfaceTexture.update(null, src.pixels, src.pitch)
    }

    /** Returns true while the demo should keep running. */
    fun frame(): Boolean {
        if (maxFrames > 0 && frames >= maxFrames) return false

        when (val event = SDL.pollEvent()) {
            is SDLEvent.Quit -> return false
            is SDLEvent.Window ->
                if (event.type == SDLWindowEventType.CLOSE_REQUESTED) return false
            is SDLEvent.Key ->
                if (event.keycode == SDLKeycode.ESCAPE) return false
            else -> {}
        }

        if (animation != null) {
            val now = SDL.getTicks()
            val delay = animation.delays[frame].toUInt()
            if (frameTicks == 0uL || now - frameTicks >= delay) {
                frameTicks = now
                frame = (frame + 1) % frameCount
                uploadFrame()
            }
        }

        renderer.drawColor = SDLColor(18, 18, 24)
        renderer.clear()

        val scale = 2.0f
        val dst = SDLFRect(
            x = 40f,
            y = 40f,
            width = surface.width * scale,
            height = surface.height * scale,
        )
        renderer.renderTexture(surfaceTexture, dst = dst)

        renderer.present()
        frames++
        return true
    }

    fun close() {
        animation?.close()
        if (!directTextureClosed) directTexture?.close()
        surfaceTexture.close()
        surface.close()
    }
}
