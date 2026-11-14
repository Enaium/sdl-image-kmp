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
import cn.enaium.sdl.SDLGPUDevice
import cn.enaium.sdl.SDLIO
import cn.enaium.sdl.SDLIOWhence
import cn.enaium.sdl.SDLIOStream
import cn.enaium.sdl.SDLPixelFormat
import cn.enaium.sdl.SDLRenderer
import cn.enaium.sdl.SDLSurface
import cn.enaium.sdl.SDLTexture
import org.khronos.webgl.Int32Array
import org.khronos.webgl.toByteArray
import org.khronos.webgl.toInt32Array
import org.khronos.webgl.toInt8Array

/*
 * wasmJs implementation of the SDL_image bindings.
 *
 * The module compiled by wasm/CMakeLists.txt contains SDL3 + SDL_image, which
 * is a *different* WebAssembly instance than sdl-kmp's: pointers cannot cross,
 * pixels can. Every decoder therefore converts to R8G8B8A8 and the pixels are
 * handed to [SDL.createSurface], so the returned objects are sdl-kmp's own
 * SDLSurface/SDLTexture implementations and work with the rest of the sdl-kmp
 * API (renderers, renderTexture, texture uploads).
 *
 * File and stream I/O goes through sdl-kmp's SDLIO so the application sees a
 * single Emscripten file system.
 */

private const val RGBA8888_PITCH_SHIFT = 2

/** Reads the whole [stream], leaving it open. */
private fun readAll(stream: SDLIOStream): ByteArray {
    val size = stream.size()
    if (size <= 0) return ByteArray(0)
    val out = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val chunk = stream.read(size - offset)
        if (chunk.isEmpty()) break
        chunk.copyInto(out, offset)
        offset += chunk.size
    }
    return if (offset == size) out else out.copyOf(offset)
}

private fun readFileBytes(path: String): ByteArray? = SDLIO.loadFile(path)

private fun writeFileBytes(path: String, bytes: ByteArray): Boolean {
    val stream = SDLIO.openFile(path, "wb") ?: return false
    return try {
        stream.write(bytes) == bytes.size
    } finally {
        stream.close()
    }
}

private fun writeStreamBytes(stream: SDLIOStream, bytes: ByteArray): Boolean =
    stream.write(bytes) == bytes.size

/** Copies a decoded handle's pixels into a new sdl-kmp surface. */
private fun surfaceFromHandle(handle: Int): SDLSurface? {
    val width = sdl_image_kmp_SurfaceWidth(handle)
    val height = sdl_image_kmp_SurfaceHeight(handle)
    val pitch = sdl_image_kmp_SurfacePitch(handle)
    val pixelsPtr = sdl_image_kmp_SurfacePixels(handle)
    if (width <= 0 || height <= 0 || pitch <= 0 || pixelsPtr == 0) return null

    val source = sdlImageKmpHeapBytes(pixelsPtr, pitch * height).toByteArray()
    val surface = SDL.createSurface(width, height, SDLPixelFormat.RGBA8888)
    if (!surface.lock()) return null
    try {
        val destination = surface.pixels
        val destinationPitch = surface.pitch
        val rowBytes = minOf(pitch, destinationPitch)
        for (y in 0 until height) {
            source.copyInto(
                destination,
                destinationOffset = y * destinationPitch,
                startIndex = y * pitch,
                endIndex = y * pitch + rowBytes,
            )
        }
    } finally {
        surface.unlock()
    }
    return surface
}

/** Decodes [bytes] into a new sdl-kmp surface. */
private fun decode(bytes: ByteArray, type: String? = null): SDLSurface? {
    if (bytes.isEmpty()) return null
    val handle = sdl_image_kmp_Load(bytes.toInt8Array(), type)
    if (handle == 0) return null
    return try {
        surfaceFromHandle(handle)
    } finally {
        sdl_image_kmp_SurfaceFree(handle)
    }
}

/** Decodes an XPM array (lines joined with '\n'). */
private fun decodeXpm(lines: List<String>, rgb888: Boolean): SDLSurface? {
    val handle = sdl_image_kmp_LoadXPM(lines.joinToString("\n"), if (rgb888) 1 else 0)
    if (handle == 0) return null
    return try {
        surfaceFromHandle(handle)
    } finally {
        sdl_image_kmp_SurfaceFree(handle)
    }
}

/** Decodes SVG scaled to [width]x[height]. */
private fun decodeSizedSvg(bytes: ByteArray, width: Int, height: Int): SDLSurface? {
    val handle = sdl_image_kmp_LoadSizedSVG(bytes.toInt8Array(), width, height)
    if (handle == 0) return null
    return try {
        surfaceFromHandle(handle)
    } finally {
        sdl_image_kmp_SurfaceFree(handle)
    }
}

/** Runs [block] with the surface pixels locked, copying them out. */
private fun surfacePixels(surface: SDLSurface): Pair<ByteArray, Int>? {
    if (!surface.lock()) return null
    return try {
        surface.pixels.copyOf() to surface.pitch
    } finally {
        surface.unlock()
    }
}

/** Encodes a surface to [type]; returns null when the encoder fails. */
private fun encodeSurface(surface: SDLSurface, type: String, quality: Int): ByteArray? {
    val (pixels, pitch) = surfacePixels(surface) ?: return null
    val handle = sdl_image_kmp_Encode(
        pixels.toInt8Array(),
        surface.width,
        surface.height,
        pitch,
        type,
        quality,
    )
    if (handle == 0) return null
    return try {
        sdlImageKmpHeapBytes(handle, sdlImageKmpResultI32Get(0)).toByteArray()
    } finally {
        sdl_image_kmp_Free(handle)
    }
}

/** Encodes already-decoded RGBA8888 [frames] with [delays] as [type]. */
private fun encodeAnimation(
    frames: List<ByteArray>,
    width: Int,
    height: Int,
    delays: IntArray,
    type: String,
): ByteArray? {
    if (frames.isEmpty() || width <= 0 || height <= 0) return null
    val stride = width * height * 4
    val packed = ByteArray(stride * frames.size)
    frames.forEachIndexed { index, frame ->
        val copy = minOf(frame.size, stride)
        frame.copyInto(packed, index * stride, 0, copy)
    }
    val handle = sdl_image_kmp_EncodeAnimation(
        packed.toInt8Array(),
        width,
        height,
        frames.size,
        delays.toInt32Array(),
        type,
    )
    if (handle == 0) return null
    return try {
        sdlImageKmpHeapBytes(handle, sdlImageKmpResultI32Get(0)).toByteArray()
    } finally {
        sdl_image_kmp_Free(handle)
    }
}

/** Maps a file extension to the encoder type SDL_image expects. */
private fun typeOfFile(path: String): String =
    path.substringAfterLast('.', "").lowercase().ifEmpty { "png" }

/** True when [type] is a lossy format whose encoder takes a quality value. */
private fun isLossy(type: String): Boolean = type == "jpg" || type == "jpeg" || type == "webp"

actual object SDLImage {

    actual fun version(): Int = sdl_image_kmp_Version()

    actual fun error(): String? = sdl_image_kmp_GetError()?.takeIf { it.isNotEmpty() }

    actual fun clearError() = sdl_image_kmp_ClearError()

    // =========================================================================
    // Loading (autodetected formats)
    // =========================================================================

    actual fun load(file: String): SDLSurface? =
        readFileBytes(file)?.let { decode(it) }

    actual fun loadIO(stream: SDLIOStream, closeIO: Boolean): SDLSurface? = try {
        decode(readAll(stream))
    } finally {
        if (closeIO) stream.close()
    }

    actual fun loadTypedIO(stream: SDLIOStream, closeIO: Boolean, type: String): SDLSurface? = try {
        decode(readAll(stream), type)
    } finally {
        if (closeIO) stream.close()
    }

    actual fun loadTexture(renderer: SDLRenderer, file: String): SDLTexture? =
        readFileBytes(file)?.let { bytes ->
            decode(bytes)?.use { renderer.createTextureFromSurface(it) }
        }

    actual fun loadTextureIO(renderer: SDLRenderer, stream: SDLIOStream, closeIO: Boolean): SDLTexture? = try {
        decode(readAll(stream))?.use { renderer.createTextureFromSurface(it) }
    } finally {
        if (closeIO) stream.close()
    }

    actual fun loadTextureTypedIO(
        renderer: SDLRenderer,
        stream: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): SDLTexture? = try {
        decode(readAll(stream), type)?.use { renderer.createTextureFromSurface(it) }
    } finally {
        if (closeIO) stream.close()
    }

    actual fun loadGPUTexture(device: SDLGPUDevice, copyPass: Long, file: String): SDLImageGPUTexture? =
        unsupportedGpu()

    actual fun loadGPUTextureIO(
        device: SDLGPUDevice,
        copyPass: Long,
        stream: SDLIOStream,
        closeIO: Boolean,
    ): SDLImageGPUTexture? {
        if (closeIO) stream.close()
        return unsupportedGpu()
    }

    actual fun loadGPUTextureTypedIO(
        device: SDLGPUDevice,
        copyPass: Long,
        stream: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): SDLImageGPUTexture? {
        if (closeIO) stream.close()
        return unsupportedGpu()
    }

    /**
     * SDL's GPU API is not available on wasm (sdl-kmp's wasm backend reports
     * `SDLGPU.isSupported == false`), so there is no device to create the
     * texture on.
     */
    private fun unsupportedGpu(): SDLImageGPUTexture? {
        SDL.setError("SDL_image GPU textures are not supported on wasm")
        return null
    }

    actual fun getClipboardImage(): SDLSurface? {
        SDL.setError("IMG_GetClipboardImage is not supported on wasm")
        return null
    }

    // =========================================================================
    // Format detection
    // =========================================================================

    private fun detect(stream: SDLIOStream, type: String): Boolean {
        val size = stream.size()
        val position = stream.tell()
        val bytes = readAll(stream)
        if (position >= 0) stream.seek(position, SDLIOWhence.SET)
        if (bytes.isEmpty()) return false
        if (size >= 0 && bytes.size != size) return false
        return sdl_image_kmp_Is(bytes.toInt8Array(), type) != 0
    }

    actual fun isANI(stream: SDLIOStream): Boolean = detect(stream, "ani")
    actual fun isAVIF(stream: SDLIOStream): Boolean = detect(stream, "avif")
    actual fun isCUR(stream: SDLIOStream): Boolean = detect(stream, "cur")
    actual fun isBMP(stream: SDLIOStream): Boolean = detect(stream, "bmp")
    actual fun isGIF(stream: SDLIOStream): Boolean = detect(stream, "gif")
    actual fun isICO(stream: SDLIOStream): Boolean = detect(stream, "ico")
    actual fun isJPG(stream: SDLIOStream): Boolean = detect(stream, "jpg")
    actual fun isJXL(stream: SDLIOStream): Boolean = detect(stream, "jxl")
    actual fun isLBM(stream: SDLIOStream): Boolean = detect(stream, "lbm")
    actual fun isPCX(stream: SDLIOStream): Boolean = detect(stream, "pcx")
    actual fun isPNG(stream: SDLIOStream): Boolean = detect(stream, "png")
    actual fun isPNM(stream: SDLIOStream): Boolean = detect(stream, "pnm")
    actual fun isQOI(stream: SDLIOStream): Boolean = detect(stream, "qoi")
    actual fun isSVG(stream: SDLIOStream): Boolean = detect(stream, "svg")
    actual fun isTIF(stream: SDLIOStream): Boolean = detect(stream, "tif")
    actual fun isWEBP(stream: SDLIOStream): Boolean = detect(stream, "webp")
    actual fun isXCF(stream: SDLIOStream): Boolean = detect(stream, "xcf")
    actual fun isXPM(stream: SDLIOStream): Boolean = detect(stream, "xpm")
    actual fun isXV(stream: SDLIOStream): Boolean = detect(stream, "xv")

    // =========================================================================
    // Typed loads
    // =========================================================================

    private fun loadTyped(stream: SDLIOStream, type: String): SDLSurface? =
        decode(readAll(stream), type)

    actual fun loadAVIF(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "avif")
    actual fun loadBMP(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "bmp")
    actual fun loadCUR(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "cur")
    actual fun loadGIF(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "gif")
    actual fun loadICO(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "ico")
    actual fun loadJPG(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "jpg")
    actual fun loadJXL(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "jxl")
    actual fun loadLBM(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "lbm")
    actual fun loadPCX(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "pcx")
    actual fun loadPNG(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "png")
    actual fun loadPNM(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "pnm")
    actual fun loadQOI(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "qoi")
    actual fun loadSVG(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "svg")

    actual fun loadSizedSVG(stream: SDLIOStream, width: Int, height: Int): SDLSurface? =
        decodeSizedSvg(readAll(stream), width, height)

    actual fun loadTGA(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "tga")
    actual fun loadTIF(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "tif")
    actual fun loadWEBP(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "webp")
    actual fun loadXCF(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "xcf")
    actual fun loadXPM(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "xpm")
    actual fun loadXV(stream: SDLIOStream): SDLSurface? = loadTyped(stream, "xv")

    actual fun readXPMFromArray(xpm: List<String>): SDLSurface? = decodeXpm(xpm, rgb888 = false)

    actual fun readXPMFromArrayToRGB888(xpm: List<String>): SDLSurface? = decodeXpm(xpm, rgb888 = true)

    // =========================================================================
    // Saving
    // =========================================================================

    private fun saveTyped(surface: SDLSurface, type: String, quality: Int): ByteArray? =
        encodeSurface(surface, type, quality)

    actual fun save(surface: SDLSurface, file: String): Boolean {
        val type = typeOfFile(file)
        val bytes = saveTyped(surface, type, if (isLossy(type)) 90 else 0) ?: return false
        return writeFileBytes(file, bytes)
    }

    actual fun saveTypedIO(
        surface: SDLSurface,
        dst: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): Boolean = try {
        val bytes = saveTyped(surface, type, if (isLossy(type)) 90 else 0)
        bytes != null && writeStreamBytes(dst, bytes)
    } finally {
        if (closeIO) dst.close()
    }

    private fun saveWithType(surface: SDLSurface, file: String, type: String, quality: Int): Boolean {
        val bytes = saveTyped(surface, type, quality) ?: return false
        return writeFileBytes(file, bytes)
    }

    private fun saveIOWithType(
        surface: SDLSurface,
        dst: SDLIOStream,
        closeIO: Boolean,
        type: String,
        quality: Int,
    ): Boolean = try {
        val bytes = saveTyped(surface, type, quality)
        bytes != null && writeStreamBytes(dst, bytes)
    } finally {
        if (closeIO) dst.close()
    }

    actual fun saveAVIF(surface: SDLSurface, file: String, quality: Int): Boolean =
        saveWithType(surface, file, "avif", quality)

    actual fun saveAVIFIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean, quality: Int): Boolean =
        saveIOWithType(surface, dst, closeIO, "avif", quality)

    actual fun saveBMP(surface: SDLSurface, file: String): Boolean =
        saveWithType(surface, file, "bmp", 0)

    actual fun saveBMPIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        saveIOWithType(surface, dst, closeIO, "bmp", 0)

    actual fun saveCUR(surface: SDLSurface, file: String): Boolean =
        saveWithType(surface, file, "cur", 0)

    actual fun saveCURIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        saveIOWithType(surface, dst, closeIO, "cur", 0)

    actual fun saveGIF(surface: SDLSurface, file: String): Boolean =
        saveWithType(surface, file, "gif", 0)

    actual fun saveGIFIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        saveIOWithType(surface, dst, closeIO, "gif", 0)

    actual fun saveICO(surface: SDLSurface, file: String): Boolean =
        saveWithType(surface, file, "ico", 0)

    actual fun saveICOIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        saveIOWithType(surface, dst, closeIO, "ico", 0)

    actual fun saveJPG(surface: SDLSurface, file: String, quality: Int): Boolean =
        saveWithType(surface, file, "jpg", quality)

    actual fun saveJPGIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean, quality: Int): Boolean =
        saveIOWithType(surface, dst, closeIO, "jpg", quality)

    actual fun savePNG(surface: SDLSurface, file: String): Boolean =
        saveWithType(surface, file, "png", 0)

    actual fun savePNGIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        saveIOWithType(surface, dst, closeIO, "png", 0)

    actual fun saveTGA(surface: SDLSurface, file: String): Boolean =
        saveWithType(surface, file, "tga", 0)

    actual fun saveTGAIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        saveIOWithType(surface, dst, closeIO, "tga", 0)

    actual fun saveWEBP(surface: SDLSurface, file: String, quality: Float): Boolean =
        saveWithType(surface, file, "webp", quality.toInt())

    actual fun saveWEBPIO(
        surface: SDLSurface,
        dst: SDLIOStream,
        closeIO: Boolean,
        quality: Float,
    ): Boolean = saveIOWithType(surface, dst, closeIO, "webp", quality.toInt())

    // =========================================================================
    // Animations
    // =========================================================================

    private fun decodeAnimation(bytes: ByteArray, type: String?): SDLImageAnimation? {
        if (bytes.isEmpty()) return null
        val handle = sdl_image_kmp_LoadAnimation(bytes.toInt8Array(), type)
        if (handle == 0) return null
        return try {
            val count = sdl_image_kmp_AnimationCount(handle)
            if (count <= 0) return null
            val width = sdl_image_kmp_AnimationWidth(handle)
            val height = sdl_image_kmp_AnimationHeight(handle)
            val frames = ArrayList<SDLSurface>(count)
            val delays = IntArray(count)
            for (index in 0 until count) {
                val frameHandle = sdl_image_kmp_AnimationFrameSurface(handle, index)
                val frame = if (frameHandle != 0) {
                    try {
                        surfaceFromHandle(frameHandle)
                    } finally {
                        sdl_image_kmp_SurfaceFree(frameHandle)
                    }
                } else {
                    null
                }
                if (frame == null) {
                    frames.forEach { it.close() }
                    return null
                }
                frames += frame
                delays[index] = sdl_image_kmp_AnimationFrameDelay(handle, index)
            }
            WasmImageAnimation(width, height, frames, delays)
        } finally {
            sdl_image_kmp_AnimationFree(handle)
        }
    }

    actual fun loadAnimation(file: String): SDLImageAnimation? =
        readFileBytes(file)?.let { decodeAnimation(it, null) }

    actual fun loadAnimationIO(stream: SDLIOStream, closeIO: Boolean): SDLImageAnimation? = try {
        decodeAnimation(readAll(stream), null)
    } finally {
        if (closeIO) stream.close()
    }

    actual fun loadAnimationTypedIO(
        stream: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): SDLImageAnimation? = try {
        decodeAnimation(readAll(stream), type)
    } finally {
        if (closeIO) stream.close()
    }

    private fun loadAnimationTyped(stream: SDLIOStream, type: String): SDLImageAnimation? =
        decodeAnimation(readAll(stream), type)

    actual fun loadANIAnimation(stream: SDLIOStream): SDLImageAnimation? = loadAnimationTyped(stream, "ani")
    actual fun loadAPNGAnimation(stream: SDLIOStream): SDLImageAnimation? = loadAnimationTyped(stream, "apng")
    actual fun loadAVIFAnimation(stream: SDLIOStream): SDLImageAnimation? = loadAnimationTyped(stream, "avif")
    actual fun loadGIFAnimation(stream: SDLIOStream): SDLImageAnimation? = loadAnimationTyped(stream, "gif")
    actual fun loadWEBPAnimation(stream: SDLIOStream): SDLImageAnimation? = loadAnimationTyped(stream, "webp")

    /** Packs an animation's frames back into RGBA8888 buffers for the encoder. */
    private fun animationFrames(animation: SDLImageAnimation): List<ByteArray>? =
        animation.frames.map { frame -> surfacePixels(frame)?.first ?: return null }

    actual fun saveAnimation(animation: SDLImageAnimation, file: String): Boolean {
        val type = typeOfFile(file)
        val frames = animationFrames(animation) ?: return false
        val bytes = encodeAnimation(frames, animation.width, animation.height, animation.delays, type)
            ?: return false
        return writeFileBytes(file, bytes)
    }

    actual fun saveAnimationTypedIO(
        animation: SDLImageAnimation,
        dst: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): Boolean = try {
        val frames = animationFrames(animation)
        val bytes = frames?.let { encodeAnimation(it, animation.width, animation.height, animation.delays, type) }
        bytes != null && writeStreamBytes(dst, bytes)
    } finally {
        if (closeIO) dst.close()
    }

    private fun saveAnimationTyped(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean, type: String): Boolean =
        saveAnimationTypedIO(animation, dst, closeIO, type)

    actual fun saveANIAnimationIO(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean): Boolean =
        saveAnimationTyped(animation, dst, closeIO, "ani")

    actual fun saveAPNGAnimationIO(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean): Boolean =
        saveAnimationTyped(animation, dst, closeIO, "apng")

    actual fun saveAVIFAnimationIO(
        animation: SDLImageAnimation,
        dst: SDLIOStream,
        closeIO: Boolean,
        quality: Int,
    ): Boolean = saveAnimationTyped(animation, dst, closeIO, "avif")

    actual fun saveGIFAnimationIO(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean): Boolean =
        saveAnimationTyped(animation, dst, closeIO, "gif")

    actual fun saveWEBPAnimationIO(
        animation: SDLImageAnimation,
        dst: SDLIOStream,
        closeIO: Boolean,
        quality: Int,
    ): Boolean = saveAnimationTyped(animation, dst, closeIO, "webp")

    actual fun createAnimatedCursor(animation: SDLImageAnimation, hotX: Int, hotY: Int): Long {
        SDL.setError("IMG_CreateAnimatedCursor is not supported on wasm")
        return 0L
    }

    // =========================================================================
    // Streaming animation encoder / decoder
    // =========================================================================

    actual fun createAnimationEncoder(file: String): SDLImageAnimationEncoder? {
        val type = typeOfFile(file)
        return WasmAnimationEncoder(type) { bytes -> writeFileBytes(file, bytes) }
    }

    actual fun createAnimationEncoderIO(
        dst: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): SDLImageAnimationEncoder? = WasmAnimationEncoder(type) { bytes ->
        try {
            writeStreamBytes(dst, bytes)
        } finally {
            if (closeIO) dst.close()
        }
    }

    actual fun createAnimationEncoderWithProperties(props: ULong): SDLImageAnimationEncoder? {
        SDL.setError("IMG_CreateAnimationEncoderWithProperties is not supported on wasm")
        return null
    }

    actual fun createAnimationDecoder(file: String): SDLImageAnimationDecoder? =
        readFileBytes(file)?.let { WasmAnimationDecoder(decodeAnimation(it, null)) }

    actual fun createAnimationDecoderIO(
        src: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): SDLImageAnimationDecoder? = try {
        WasmAnimationDecoder(decodeAnimation(readAll(src), type))
    } finally {
        if (closeIO) src.close()
    }

    actual fun createAnimationDecoderWithProperties(props: ULong): SDLImageAnimationDecoder? {
        SDL.setError("IMG_CreateAnimationDecoderWithProperties is not supported on wasm")
        return null
    }
}

/** An animation decoded by this module, backed by sdl-kmp surfaces. */
private class WasmImageAnimation(
    override val width: Int,
    override val height: Int,
    private val frameList: List<SDLSurface>,
    override val delays: IntArray,
) : SDLImageAnimation {

    override val ptr: Long = 1L

    override val count: Int get() = frameList.size

    override val frames: List<SDLSurface> get() = frameList

    override fun close() {
        frameList.forEach { it.close() }
    }
}

/**
 * Streaming encoder: frames are buffered (as RGBA8888 pixels) and the whole
 * animation is encoded when the encoder is closed, because SDL_image's own
 * streaming encoder lives in the other wasm module.
 */
private class WasmAnimationEncoder(
    private val type: String,
    private val write: (ByteArray) -> Boolean,
) : SDLImageAnimationEncoder {

    override val ptr: Long = 1L

    private val frames = mutableListOf<ByteArray>()
    private val delays = mutableListOf<Int>()
    private var width = 0
    private var height = 0
    private var closed = false

    override fun addFrame(surface: SDLSurface, duration: Long): Boolean {
        if (closed) return false
        val (pixels, _) = surfacePixels(surface) ?: return false
        if (frames.isEmpty()) {
            width = surface.width
            height = surface.height
        } else if (surface.width != width || surface.height != height) {
            // The encoder writes frames of one size only.
            return false
        }
        frames += pixels
        delays += duration.toInt()
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        if (frames.isEmpty()) return
        val bytes = encodeAnimation(frames, width, height, delays.toIntArray(), type) ?: return
        write(bytes)
    }
}

/**
 * Streaming decoder: the whole animation is decoded up front and frames are
 * handed out one at a time.
 */
private class WasmAnimationDecoder(
    private val animation: SDLImageAnimation?,
) : SDLImageAnimationDecoder {

    override val ptr: Long = 1L

    private var index = 0
    private var closed = false

    override val status: Int
        get() = if (animation == null || index >= animation.count) {
            SDLImageDecoderStatus.COMPLETE
        } else {
            SDLImageDecoderStatus.OK
        }

    override fun properties(): ULong = 0uL

    override fun getFrame(): SDLImageDecoderFrame? {
        val decoded = animation ?: return null
        if (closed || index >= decoded.count) return null
        val frame = decoded.frames[index]
        val duration = decoded.delays.getOrElse(index) { 0 }.toLong()
        index++
        return SDLImageDecoderFrame(frame, duration)
    }

    override fun reset(): Boolean {
        if (closed) return false
        index = 0
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        animation?.close()
    }
}
