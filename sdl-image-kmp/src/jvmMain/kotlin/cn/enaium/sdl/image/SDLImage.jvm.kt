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

package cn.enaium.sdl.image

import cn.enaium.sdl.SDL
import cn.enaium.sdl.SDLColor
import cn.enaium.sdl.SDLFloatPoint
import cn.enaium.sdl.SDLGPUDevice
import cn.enaium.sdl.SDLGPUTexture
import cn.enaium.sdl.SDLIOStream
import cn.enaium.sdl.SDLRect
import cn.enaium.sdl.SDLRenderer
import cn.enaium.sdl.SDLSurface
import cn.enaium.sdl.SDLTexture
import cn.enaium.sdl.SDLTextureLock

/**
 * An [SDLSurface] wrapping an SDL_Surface created outside the sdl-kmp
 * library (e.g. by SDL_image's load functions). All operations delegate to
 * the IMG JNI library, which shares the SDL3 instance with libsdl_jni.
 */
internal class JvmImageSurface internal constructor(
    ptr: Long,
    internal val owned: Boolean,
) : SDLSurface {

    internal var surface: Long = ptr

    override val ptr: Long get() = surface

    internal fun check(): Long =
        surface.also { if (it == 0L) throw IllegalStateException("SDL surface is closed") }

    override val width: Int get() = Jni.surfaceWidth(check())
    override val height: Int get() = Jni.surfaceHeight(check())
    override val format: Int get() = Jni.surfaceFormat(check())
    override val colorspace: Int get() = Jni.surfaceColorspace(check())
    override val pitch: Int get() = Jni.surfacePitch(check())

    override val pixels: ByteArray
        get() = Jni.surfacePixels(check()) ?: ByteArray(0)

    override fun lock(): Boolean = Jni.lockSurface(check())

    override fun unlock() {
        Jni.unlockSurface(check())
    }

    override fun fillRect(rect: SDLRect?, color: SDLColor): Boolean {
        return Jni.surfaceFillRect(
            check(),
            rect?.let { intArrayOf(it.x, it.y, it.width, it.height) },
            color.r, color.g, color.b, color.a,
        )
    }

    override fun fillRects(rects: List<SDLRect>, color: SDLColor): Boolean {
        if (rects.isEmpty()) return true
        val arr = IntArray(rects.size * 4)
        for (i in rects.indices) {
            arr[i * 4] = rects[i].x
            arr[i * 4 + 1] = rects[i].y
            arr[i * 4 + 2] = rects[i].width
            arr[i * 4 + 3] = rects[i].height
        }
        return Jni.surfaceFillRects(check(), arr, color.r, color.g, color.b, color.a)
    }

    override fun blit(src: SDLRect?, dst: SDLSurface, dstRect: SDLRect?): Boolean {
        if (dst.ptr == 0L) throw IllegalArgumentException("dst is closed")
        return Jni.surfaceBlit(
            check(),
            src?.let { intArrayOf(it.x, it.y, it.width, it.height) },
            dst.ptr,
            dstRect?.let { intArrayOf(it.x, it.y, it.width, it.height) },
        )
    }

    override fun blitScaled(src: SDLRect?, dst: SDLSurface, dstRect: SDLRect?, scaleMode: Int): Boolean {
        if (dst.ptr == 0L) throw IllegalArgumentException("dst is closed")
        return Jni.surfaceBlitScaled(
            check(),
            src?.let { intArrayOf(it.x, it.y, it.width, it.height) },
            dst.ptr,
            dstRect?.let { intArrayOf(it.x, it.y, it.width, it.height) },
            scaleMode,
        )
    }

    override fun saveBMP(path: String): Boolean = Jni.surfaceSaveBMP(check(), path)

    override fun convert(format: Int): SDLSurface {
        val converted = Jni.convertSurface(check(), format)
        check(converted != 0L) { "SDL_ConvertSurface failed: ${SDL.error()}" }
        return JvmImageSurface(converted, owned = true)
    }

    override fun close() {
        val s = surface
        if (s == 0L) return
        surface = 0L
        if (owned) {
            Jni.destroySurface(s)
        }
    }
}

internal actual fun Long.toSDLSurface(owned: Boolean): SDLSurface? =
    if (this == 0L) null else JvmImageSurface(this, owned = owned)

/**
 * An [SDLTexture] wrapping an SDL_Texture created by SDL_image's
 * [SDLImage.loadTexture] and friends. All operations delegate to the IMG JNI
 * library, which shares the SDL3 instance with libsdl_jni.
 */
internal class JvmSDLTexture internal constructor(
    ptr: Long,
    internal val owned: Boolean,
) : SDLTexture {

    internal var texture: Long = ptr

    override val ptr: Long get() = texture

    internal fun check(): Long =
        texture.also { if (it == 0L) throw IllegalStateException("SDL texture is closed") }

    override val format: Int get() = Jni.textureFormat(check())
    override val access: Int get() = Jni.textureAccess(check())

    override val size: SDLFloatPoint
        get() = Jni.textureSize(check()).let { SDLFloatPoint(it[0], it[1]) }

    override var colorMod: SDLColor
        get() = Jni.textureColorMod(check()).let { SDLColor(it[0], it[1], it[2]) }
        set(value) {
            Jni.setTextureColorMod(check(), value.r, value.g, value.b)
        }

    override var alphaMod: Int
        get() = Jni.textureAlphaMod(check())
        set(value) {
            Jni.setTextureAlphaMod(check(), value)
        }

    override var blendMode: Int
        get() = Jni.textureBlendMode(check())
        set(value) {
            Jni.setTextureBlendMode(check(), value)
        }

    override var scaleMode: Int
        get() = Jni.textureScaleMode(check())
        set(value) {
            Jni.setTextureScaleMode(check(), value)
        }

    override fun update(rect: SDLRect?, pixels: ByteArray, pitch: Int): Boolean {
        return Jni.textureUpdate(
            check(),
            rect?.let { intArrayOf(it.x, it.y, it.width, it.height) },
            pixels,
            pitch,
        )
    }

    override fun lock(rect: SDLRect?): SDLTextureLock? {
        val locked = Jni.textureLock(
            check(),
            rect?.let { intArrayOf(it.x, it.y, it.width, it.height) },
        ) ?: return null
        return SDLTextureLock(locked[0] as ByteArray, locked[1] as Int)
    }

    override fun unlock() {
        Jni.textureUnlock(check())
    }

    override fun close() {
        val t = texture
        if (t == 0L) return
        texture = 0L
        if (owned) {
            Jni.destroyTexture(t)
        }
    }
}

internal actual fun Long.toSDLTexture(owned: Boolean): SDLTexture? =
    if (this == 0L) null else JvmSDLTexture(this, owned = owned)

/**
 * An [SDLGPUTexture] wrapping an SDL_GPUTexture created by SDL_image's
 * [SDLImage.loadGPUTexture] and friends. Upload/download are unsupported;
 * [close] releases the texture on the device that created it.
 */
internal class JvmImageGPUTexture internal constructor(
    ptr: Long,
    private val device: Long,
    private val owned: Boolean,
) : SDLGPUTexture {

    internal var texture: Long = ptr

    override val ptr: Long get() = texture

    override fun upload(data: ByteArray, bytesPerRow: Int, x: Int, y: Int, width: Int, height: Int): Boolean =
        false

    override fun download(width: Int, height: Int): ByteArray? = null

    override fun close() {
        val t = texture
        if (t == 0L) return
        texture = 0L
        if (owned) {
            Jni.releaseGPUTexture(device, t)
        }
    }
}

internal actual fun Long.toSDLGPUTexture(owned: Boolean): SDLGPUTexture? =
    if (this == 0L) null else JvmImageGPUTexture(this, device = 0L, owned = owned)

/** JVM helper: wraps a GPU texture, remembering the [device] that owns it. */
internal fun Long.toSDLGPUTexture(device: Long, owned: Boolean): SDLGPUTexture? =
    if (this == 0L) null else JvmImageGPUTexture(this, device = device, owned = owned)

internal class JvmSDLImageAnimation internal constructor(
    ptr: Long,
) : SDLImageAnimation {

    internal var animation: Long = ptr

    override val ptr: Long get() = animation

    internal fun check(): Long =
        animation.also { if (it == 0L) throw IllegalStateException("SDL_image animation is closed") }

    override val width: Int get() = Jni.animationWidth(check())
    override val height: Int get() = Jni.animationHeight(check())
    override val count: Int get() = Jni.animationCount(check())

    override val frames: List<SDLSurface>
        get() = Jni.animationFrames(check()).map { JvmImageSurface(it, owned = false) }

    override val delays: IntArray
        get() = Jni.animationDelays(check())

    override fun close() {
        val a = animation
        if (a == 0L) return
        animation = 0L
        Jni.freeAnimation(a)
    }
}

internal class JvmSDLImageAnimationEncoder internal constructor(
    ptr: Long,
) : SDLImageAnimationEncoder {

    internal var encoder: Long = ptr

    override val ptr: Long get() = encoder

    internal fun check(): Long =
        encoder.also { if (it == 0L) throw IllegalStateException("SDL_image animation encoder is closed") }

    override fun addFrame(surface: SDLSurface, duration: Long): Boolean {
        if (surface.ptr == 0L) throw IllegalStateException("SDL surface is closed")
        return Jni.addAnimationEncoderFrame(check(), surface.ptr, duration)
    }

    override fun close() {
        val e = encoder
        if (e == 0L) return
        encoder = 0L
        Jni.closeAnimationEncoder(e)
    }
}

internal class JvmSDLImageAnimationDecoder internal constructor(
    ptr: Long,
) : SDLImageAnimationDecoder {

    internal var decoder: Long = ptr

    override val ptr: Long get() = decoder

    internal fun check(): Long =
        decoder.also { if (it == 0L) throw IllegalStateException("SDL_image animation decoder is closed") }

    override val status: Int
        get() {
            val d = decoder
            if (d == 0L) return SDLImageDecoderStatus.INVALID
            return Jni.getAnimationDecoderStatus(d)
        }

    override fun properties(): ULong =
        Jni.getAnimationDecoderProperties(check()).toUInt().toULong()

    override fun getFrame(): SDLImageDecoderFrame? {
        val frame = Jni.getAnimationDecoderFrame(check()) ?: return null
        val surface = frame[0].toSDLSurface(owned = true)
            ?: return null
        return SDLImageDecoderFrame(surface, frame[1])
    }

    override fun reset(): Boolean = Jni.resetAnimationDecoder(check())

    override fun close() {
        val d = decoder
        if (d == 0L) return
        decoder = 0L
        Jni.closeAnimationDecoder(d)
    }
}

private fun SDLIOStream.streamOrNull(): Long {
    if (ptr == 0L) throw IllegalStateException("SDL IO stream is closed")
    return ptr
}

private fun SDLRenderer.rendererOrNull(): Long {
    if (ptr == 0L) throw IllegalStateException("SDL renderer is closed")
    return ptr
}

private fun SDLSurface.surfaceOrNull(): Long {
    if (ptr == 0L) throw IllegalStateException("SDL surface is closed")
    return ptr
}

private fun SDLGPUDevice.deviceOrNull(): Long {
    if (ptr == 0L) throw IllegalStateException("SDL GPU device is closed")
    return ptr
}

actual object SDLImage {

    actual fun version(): Int = Jni.version()

    actual fun error(): String? = Jni.getError()?.takeIf { it.isNotEmpty() }

    actual fun clearError() {
        Jni.clearError()
    }

    // =========================================================================
    // Loading (autodetected formats)
    // =========================================================================

    actual fun load(file: String): SDLSurface? =
        Jni.load(file).toSDLSurface(owned = true)

    actual fun loadIO(stream: SDLIOStream, closeIO: Boolean): SDLSurface? =
        Jni.loadIO(stream.streamOrNull(), closeIO).toSDLSurface(owned = true)

    actual fun loadTypedIO(stream: SDLIOStream, closeIO: Boolean, type: String): SDLSurface? =
        Jni.loadTypedIO(stream.streamOrNull(), closeIO, type).toSDLSurface(owned = true)

    actual fun loadTexture(renderer: SDLRenderer, file: String): SDLTexture? =
        Jni.loadTexture(renderer.rendererOrNull(), file).toSDLTexture(owned = true)

    actual fun loadTextureIO(renderer: SDLRenderer, stream: SDLIOStream, closeIO: Boolean): SDLTexture? =
        Jni.loadTextureIO(renderer.rendererOrNull(), stream.streamOrNull(), closeIO).toSDLTexture(owned = true)

    actual fun loadTextureTypedIO(
        renderer: SDLRenderer,
        stream: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): SDLTexture? =
        Jni.loadTextureTypedIO(renderer.rendererOrNull(), stream.streamOrNull(), closeIO, type)
            .toSDLTexture(owned = true)

    actual fun loadGPUTexture(device: SDLGPUDevice, copyPass: Long, file: String): SDLImageGPUTexture? {
        val result = Jni.loadGPUTexture(device.deviceOrNull(), copyPass, file) ?: return null
        return SDLImageGPUTexture(
            result[0].toSDLGPUTexture(device.ptr, owned = true)!!,
            result[1].toInt(),
            result[2].toInt(),
        )
    }

    actual fun loadGPUTextureIO(
        device: SDLGPUDevice,
        copyPass: Long,
        stream: SDLIOStream,
        closeIO: Boolean,
    ): SDLImageGPUTexture? {
        val result = Jni.loadGPUTextureIO(device.deviceOrNull(), copyPass, stream.streamOrNull(), closeIO)
            ?: return null
        return SDLImageGPUTexture(
            result[0].toSDLGPUTexture(device.ptr, owned = true)!!,
            result[1].toInt(),
            result[2].toInt(),
        )
    }

    actual fun loadGPUTextureTypedIO(
        device: SDLGPUDevice,
        copyPass: Long,
        stream: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): SDLImageGPUTexture? {
        val result = Jni.loadGPUTextureTypedIO(
            device.deviceOrNull(), copyPass, stream.streamOrNull(), closeIO, type,
        ) ?: return null
        return SDLImageGPUTexture(
            result[0].toSDLGPUTexture(device.ptr, owned = true)!!,
            result[1].toInt(),
            result[2].toInt(),
        )
    }

    actual fun getClipboardImage(): SDLSurface? =
        Jni.getClipboardImage().toSDLSurface(owned = true)

    // =========================================================================
    // Format detection
    // =========================================================================

    actual fun isANI(stream: SDLIOStream): Boolean = Jni.isANI(stream.streamOrNull())
    actual fun isAVIF(stream: SDLIOStream): Boolean = Jni.isAVIF(stream.streamOrNull())
    actual fun isCUR(stream: SDLIOStream): Boolean = Jni.isCUR(stream.streamOrNull())
    actual fun isBMP(stream: SDLIOStream): Boolean = Jni.isBMP(stream.streamOrNull())
    actual fun isGIF(stream: SDLIOStream): Boolean = Jni.isGIF(stream.streamOrNull())
    actual fun isICO(stream: SDLIOStream): Boolean = Jni.isICO(stream.streamOrNull())
    actual fun isJPG(stream: SDLIOStream): Boolean = Jni.isJPG(stream.streamOrNull())
    actual fun isJXL(stream: SDLIOStream): Boolean = Jni.isJXL(stream.streamOrNull())
    actual fun isLBM(stream: SDLIOStream): Boolean = Jni.isLBM(stream.streamOrNull())
    actual fun isPCX(stream: SDLIOStream): Boolean = Jni.isPCX(stream.streamOrNull())
    actual fun isPNG(stream: SDLIOStream): Boolean = Jni.isPNG(stream.streamOrNull())
    actual fun isPNM(stream: SDLIOStream): Boolean = Jni.isPNM(stream.streamOrNull())
    actual fun isQOI(stream: SDLIOStream): Boolean = Jni.isQOI(stream.streamOrNull())
    actual fun isSVG(stream: SDLIOStream): Boolean = Jni.isSVG(stream.streamOrNull())
    actual fun isTIF(stream: SDLIOStream): Boolean = Jni.isTIF(stream.streamOrNull())
    actual fun isWEBP(stream: SDLIOStream): Boolean = Jni.isWEBP(stream.streamOrNull())
    actual fun isXCF(stream: SDLIOStream): Boolean = Jni.isXCF(stream.streamOrNull())
    actual fun isXPM(stream: SDLIOStream): Boolean = Jni.isXPM(stream.streamOrNull())
    actual fun isXV(stream: SDLIOStream): Boolean = Jni.isXV(stream.streamOrNull())

    // =========================================================================
    // Typed loads
    // =========================================================================

    actual fun loadAVIF(stream: SDLIOStream): SDLSurface? =
        Jni.loadAVIF(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadBMP(stream: SDLIOStream): SDLSurface? =
        Jni.loadBMP(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadCUR(stream: SDLIOStream): SDLSurface? =
        Jni.loadCUR(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadGIF(stream: SDLIOStream): SDLSurface? =
        Jni.loadGIF(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadICO(stream: SDLIOStream): SDLSurface? =
        Jni.loadICO(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadJPG(stream: SDLIOStream): SDLSurface? =
        Jni.loadJPG(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadJXL(stream: SDLIOStream): SDLSurface? =
        Jni.loadJXL(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadLBM(stream: SDLIOStream): SDLSurface? =
        Jni.loadLBM(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadPCX(stream: SDLIOStream): SDLSurface? =
        Jni.loadPCX(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadPNG(stream: SDLIOStream): SDLSurface? =
        Jni.loadPNG(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadPNM(stream: SDLIOStream): SDLSurface? =
        Jni.loadPNM(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadQOI(stream: SDLIOStream): SDLSurface? =
        Jni.loadQOI(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadSVG(stream: SDLIOStream): SDLSurface? =
        Jni.loadSVG(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadSizedSVG(stream: SDLIOStream, width: Int, height: Int): SDLSurface? =
        Jni.loadSizedSVG(stream.streamOrNull(), width, height).toSDLSurface(owned = true)

    actual fun loadTGA(stream: SDLIOStream): SDLSurface? =
        Jni.loadTGA(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadTIF(stream: SDLIOStream): SDLSurface? =
        Jni.loadTIF(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadWEBP(stream: SDLIOStream): SDLSurface? =
        Jni.loadWEBP(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadXCF(stream: SDLIOStream): SDLSurface? =
        Jni.loadXCF(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadXPM(stream: SDLIOStream): SDLSurface? =
        Jni.loadXPM(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun loadXV(stream: SDLIOStream): SDLSurface? =
        Jni.loadXV(stream.streamOrNull()).toSDLSurface(owned = true)

    actual fun readXPMFromArray(xpm: List<String>): SDLSurface? =
        Jni.readXPMFromArray(xpm.toTypedArray()).toSDLSurface(owned = true)

    actual fun readXPMFromArrayToRGB888(xpm: List<String>): SDLSurface? =
        Jni.readXPMFromArrayToRGB888(xpm.toTypedArray()).toSDLSurface(owned = true)

    // =========================================================================
    // Saving
    // =========================================================================

    actual fun save(surface: SDLSurface, file: String): Boolean =
        Jni.save(surface.surfaceOrNull(), file)

    actual fun saveTypedIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean, type: String): Boolean =
        Jni.saveTypedIO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO, type)

    actual fun saveAVIF(surface: SDLSurface, file: String, quality: Int): Boolean =
        Jni.saveAVIF(surface.surfaceOrNull(), file, quality)

    actual fun saveAVIFIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean, quality: Int): Boolean =
        Jni.saveAVIFIO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO, quality)

    actual fun saveBMP(surface: SDLSurface, file: String): Boolean =
        Jni.saveBMP(surface.surfaceOrNull(), file)

    actual fun saveBMPIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        Jni.saveBMPIO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveCUR(surface: SDLSurface, file: String): Boolean =
        Jni.saveCUR(surface.surfaceOrNull(), file)

    actual fun saveCURIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        Jni.saveCURIO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveGIF(surface: SDLSurface, file: String): Boolean =
        Jni.saveGIF(surface.surfaceOrNull(), file)

    actual fun saveGIFIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        Jni.saveGIFIO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveICO(surface: SDLSurface, file: String): Boolean =
        Jni.saveICO(surface.surfaceOrNull(), file)

    actual fun saveICOIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        Jni.saveICOIO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveJPG(surface: SDLSurface, file: String, quality: Int): Boolean =
        Jni.saveJPG(surface.surfaceOrNull(), file, quality)

    actual fun saveJPGIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean, quality: Int): Boolean =
        Jni.saveJPGIO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO, quality)

    actual fun savePNG(surface: SDLSurface, file: String): Boolean =
        Jni.savePNG(surface.surfaceOrNull(), file)

    actual fun savePNGIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        Jni.savePNGIO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveTGA(surface: SDLSurface, file: String): Boolean =
        Jni.saveTGA(surface.surfaceOrNull(), file)

    actual fun saveTGAIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        Jni.saveTGAIO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveWEBP(surface: SDLSurface, file: String, quality: Float): Boolean =
        Jni.saveWEBP(surface.surfaceOrNull(), file, quality)

    actual fun saveWEBPIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean, quality: Float): Boolean =
        Jni.saveWEBPIO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO, quality)

    // =========================================================================
    // Animations
    // =========================================================================

    actual fun loadAnimation(file: String): SDLImageAnimation? =
        Jni.loadAnimation(file).let { if (it == 0L) null else JvmSDLImageAnimation(it) }

    actual fun loadAnimationIO(stream: SDLIOStream, closeIO: Boolean): SDLImageAnimation? =
        Jni.loadAnimationIO(stream.streamOrNull(), closeIO).let { if (it == 0L) null else JvmSDLImageAnimation(it) }

    actual fun loadAnimationTypedIO(stream: SDLIOStream, closeIO: Boolean, type: String): SDLImageAnimation? =
        Jni.loadAnimationTypedIO(stream.streamOrNull(), closeIO, type)
            .let { if (it == 0L) null else JvmSDLImageAnimation(it) }

    actual fun loadANIAnimation(stream: SDLIOStream): SDLImageAnimation? =
        Jni.loadANIAnimationIO(stream.streamOrNull()).let { if (it == 0L) null else JvmSDLImageAnimation(it) }

    actual fun loadAPNGAnimation(stream: SDLIOStream): SDLImageAnimation? =
        Jni.loadAPNGAnimationIO(stream.streamOrNull()).let { if (it == 0L) null else JvmSDLImageAnimation(it) }

    actual fun loadAVIFAnimation(stream: SDLIOStream): SDLImageAnimation? =
        Jni.loadAVIFAnimationIO(stream.streamOrNull()).let { if (it == 0L) null else JvmSDLImageAnimation(it) }

    actual fun loadGIFAnimation(stream: SDLIOStream): SDLImageAnimation? =
        Jni.loadGIFAnimationIO(stream.streamOrNull()).let { if (it == 0L) null else JvmSDLImageAnimation(it) }

    actual fun loadWEBPAnimation(stream: SDLIOStream): SDLImageAnimation? =
        Jni.loadWEBPAnimationIO(stream.streamOrNull()).let { if (it == 0L) null else JvmSDLImageAnimation(it) }

    private fun SDLImageAnimation.animationOrNull(): Long {
        val a = this as? JvmSDLImageAnimation
            ?: throw IllegalArgumentException("animation is not a JVM SDL_image animation")
        return a.check()
    }

    actual fun saveAnimation(animation: SDLImageAnimation, file: String): Boolean =
        Jni.saveAnimation(animation.animationOrNull(), file)

    actual fun saveAnimationTypedIO(
        animation: SDLImageAnimation,
        dst: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): Boolean = Jni.saveAnimationTypedIO(animation.animationOrNull(), dst.streamOrNull(), closeIO, type)

    actual fun saveANIAnimationIO(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean): Boolean =
        Jni.saveANIAnimationIO(animation.animationOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveAPNGAnimationIO(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean): Boolean =
        Jni.saveAPNGAnimationIO(animation.animationOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveAVIFAnimationIO(
        animation: SDLImageAnimation,
        dst: SDLIOStream,
        closeIO: Boolean,
        quality: Int,
    ): Boolean = Jni.saveAVIFAnimationIO(animation.animationOrNull(), dst.streamOrNull(), closeIO, quality)

    actual fun saveGIFAnimationIO(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean): Boolean =
        Jni.saveGIFAnimationIO(animation.animationOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveWEBPAnimationIO(
        animation: SDLImageAnimation,
        dst: SDLIOStream,
        closeIO: Boolean,
        quality: Int,
    ): Boolean = Jni.saveWEBPAnimationIO(animation.animationOrNull(), dst.streamOrNull(), closeIO, quality)

    actual fun createAnimatedCursor(animation: SDLImageAnimation, hotX: Int, hotY: Int): Long =
        Jni.createAnimatedCursor(animation.animationOrNull(), hotX, hotY)

    // =========================================================================
    // Streaming animation encoder / decoder
    // =========================================================================

    actual fun createAnimationEncoder(file: String): SDLImageAnimationEncoder? =
        Jni.createAnimationEncoder(file).let { if (it == 0L) null else JvmSDLImageAnimationEncoder(it) }

    actual fun createAnimationEncoderIO(
        dst: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): SDLImageAnimationEncoder? =
        Jni.createAnimationEncoderIO(dst.streamOrNull(), closeIO, type)
            .let { if (it == 0L) null else JvmSDLImageAnimationEncoder(it) }

    actual fun createAnimationEncoderWithProperties(props: ULong): SDLImageAnimationEncoder? =
        Jni.createAnimationEncoderWithProperties(props.toLong())
            .let { if (it == 0L) null else JvmSDLImageAnimationEncoder(it) }

    actual fun createAnimationDecoder(file: String): SDLImageAnimationDecoder? =
        Jni.createAnimationDecoder(file).let { if (it == 0L) null else JvmSDLImageAnimationDecoder(it) }

    actual fun createAnimationDecoderIO(
        src: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): SDLImageAnimationDecoder? =
        Jni.createAnimationDecoderIO(src.streamOrNull(), closeIO, type)
            .let { if (it == 0L) null else JvmSDLImageAnimationDecoder(it) }

    actual fun createAnimationDecoderWithProperties(props: ULong): SDLImageAnimationDecoder? =
        Jni.createAnimationDecoderWithProperties(props.toLong())
            .let { if (it == 0L) null else JvmSDLImageAnimationDecoder(it) }
}
