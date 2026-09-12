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
import cn.enaium.sdl.SDLEvent
import cn.enaium.sdl.SDLFlipMode
import cn.enaium.sdl.SDLGPU
import cn.enaium.sdl.SDLGPUBlitInfo
import cn.enaium.sdl.SDLGPUBlitRegion
import cn.enaium.sdl.SDLGPUFilter
import cn.enaium.sdl.SDLGPUDevice
import cn.enaium.sdl.SDLGPULoadOp
import cn.enaium.sdl.SDLGPUTexture
import cn.enaium.sdl.SDLKeycode
import cn.enaium.sdl.SDLWindow
import cn.enaium.sdl.SDLWindowEventType
import cn.enaium.sdl.image.SDLImage
import cn.enaium.sdl.image.SDLImageAnimation
import cn.enaium.sdl.image.SDLImageGPUTexture

/**
 * The SDL_image GPU demo as a frame state machine, shared by every platform.
 *
 * It exercises the GPU side of the bindings on top of sdl-kmp's GPU API:
 *
 *  - [SDLImage.loadGPUTexture] decodes an image straight into an
 *    `SDL_GPUTexture` (R8G8B8A8_UNORM) owned by the device;
 *  - [SDLGPUTexture.upload] rewrites pixels of that texture, which is how
 *    animated images (or any dynamic content) are streamed to the GPU
 *    without re-decoding the file;
 *  - [SDLImage.loadAnimation] provides the frames for the animated case;
 *  - the texture is presented with `SDL_BlitGPUTexture`
 *    ([cn.enaium.sdl.SDLGPUCommandBuffer.blit]), scaling the source region
 *    onto the swapchain texture acquired from the window.
 *
 * The window stays open until ESC is pressed (or until [maxFrames] frames in
 * headless CI runs).
 */
class GpuImageDemo(
    private val window: SDLWindow,
    private val device: SDLGPUDevice,
    private val path: String,
    private val maxFrames: Int,
) {
    private val loaded: SDLImageGPUTexture = SDLImage.loadGPUTexture(device, 0L, path)
        ?: error("IMG_LoadGPUTexture failed: ${SDLImage.error()}")

    private val animation: SDLImageAnimation? =
        SDLImage.loadAnimation(path)?.takeIf { it.count > 1 }

    private val frameCount: Int = animation?.count ?: 1
    private var frame = 0
    private var frameTicks = 0uL
    private var frames = 0

    init {
        check(device.claimWindow(window)) { "SDL_ClaimWindowForGPUDevice failed: ${SDL.error()}" }
        println("sdl-image-kmp GPU demo: ${SDLImage.version()} (SDL_image)")
        println(
            "loaded ${loaded.width}x${loaded.height}px GPU texture, " +
                "animation frames: ${animation?.count ?: 0}",
        )
    }

    /** Re-uploads the current frame's pixels into the GPU texture. */
    private fun uploadFrame() {
        val src = animation?.frames?.getOrNull(frame) ?: return
        // Frames of an animated image can be smaller than the animation's
        // full size; upload only the overlapping region.
        val width = minOf(src.width, loaded.width)
        val height = minOf(src.height, loaded.height)
        val ok = loaded.texture.upload(src.pixels, src.pitch, 0, 0, width, height)
        check(ok) { "GPU texture upload failed: ${SDLImage.error()}" }
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

        val commandBuffer = device.beginCommandBuffer() ?: error("beginCommandBuffer failed: ${SDL.error()}")
        commandBuffer.use {
            val swapchain = device.acquireSwapchainTexture(commandBuffer, window)
            val target = swapchain?.texture ?: run {
                // The swapchain was not available this frame; submit the empty
                // command buffer so the acquired frame is released.
                device.submit(commandBuffer)
                return true
            }

            val width = swapchain.srcRect.width
            val height = swapchain.srcRect.height

            // Scale the image to fit the window while preserving its aspect
            // ratio, centred with a small margin.
            val margin = 0.9f
            val scale = minOf(
                width * margin / loaded.width,
                height * margin / loaded.height,
            )
            val dstW = (loaded.width * scale).toInt().coerceAtLeast(1)
            val dstH = (loaded.height * scale).toInt().coerceAtLeast(1)

            commandBuffer.blit(
                SDLGPUBlitInfo(
                    source = SDLGPUBlitRegion(
                        texture = loaded.texture,
                        width = loaded.width,
                        height = loaded.height,
                    ),
                    destination = SDLGPUBlitRegion(
                        texture = target,
                        x = (width - dstW) / 2,
                        y = (height - dstH) / 2,
                        width = dstW,
                        height = dstH,
                    ),
                    loadOp = SDLGPULoadOp.CLEAR,
                    flipMode = SDLFlipMode.NONE,
                    filter = SDLGPUFilter.LINEAR,
                ),
            )

            check(device.submit(commandBuffer)) { "submit failed: ${SDL.error()}" }
        }

        frames++
        return true
    }

    fun close() {
        animation?.close()
        loaded.texture.close()
        device.releaseDrawable(window)
    }
}