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

import cn.enaium.sdl.SDLGPUDevice
import cn.enaium.sdl.SDLGPUTexture
import cn.enaium.sdl.SDLIOStream
import cn.enaium.sdl.SDLRenderer
import cn.enaium.sdl.SDLSurface
import cn.enaium.sdl.SDLTexture

/** Status of an [SDLImageAnimationDecoder] (values match IMG_AnimationDecoderStatus). */
object SDLImageDecoderStatus {
    /** The decoder is invalid. */
    const val INVALID = -1

    /** The decoder is ready to decode the next frame. */
    const val OK = 0

    /** The decoder failed to decode a frame, see [SDLImage.error]. */
    const val FAILED = 1

    /** No more frames are available. */
    const val COMPLETE = 2
}

/** A loaded animated image, created with [SDLImage.loadAnimation] or friends. */
interface SDLImageAnimation : AutoCloseable {

    /** The raw IMG_Animation handle address, or 0 after [close]. */
    val ptr: Long

    /** The width of the frames. */
    val width: Int

    /** The height of the frames. */
    val height: Int

    /** The number of frames. */
    val count: Int

    /**
     * The frames as [SDLSurface]s (owned by the animation; do not close
     * them individually).
     */
    val frames: List<SDLSurface>

    /** The frame delays in milliseconds, one per frame. */
    val delays: IntArray

    /** Releases the animation (and all of its frames). */
    override fun close()
}

/** A frame decoded by an [SDLImageAnimationDecoder]. */
data class SDLImageDecoderFrame(
    /** The decoded frame (owned by the decoder; do not close it). */
    val surface: SDLSurface,
    /** The frame duration in milliseconds. */
    val duration: Long,
)

/**
 * A streaming animation encoder, created with
 * [SDLImage.createAnimationEncoder] or friends. Frames are appended with
 * [addFrame] and the encoder is finalized with [close].
 */
interface SDLImageAnimationEncoder : AutoCloseable {

    /** The raw IMG_AnimationEncoder handle address, or 0 after [close]. */
    val ptr: Long

    /** Appends [surface] as a frame lasting [duration] milliseconds. */
    fun addFrame(surface: SDLSurface, duration: Long): Boolean

    /** Finalizes the file and releases the encoder. */
    override fun close()
}

/**
 * A streaming animation decoder, created with [SDLImage.createAnimationDecoder]
 * or friends. Frames are decoded one at a time with [getFrame].
 */
interface SDLImageAnimationDecoder : AutoCloseable {

    /** The raw IMG_AnimationDecoder handle address, or 0 after [close]. */
    val ptr: Long

    /** The decoder state, see [SDLImageDecoderStatus]. */
    val status: Int

    /** The SDL_PropertiesID of the decoder (0 when not initialized). */
    fun properties(): ULong

    /** Decodes the next frame, or null when no frame is available. */
    fun getFrame(): SDLImageDecoderFrame?

    /** Resets the decoder to the beginning of the stream. */
    fun reset(): Boolean

    /** Releases the decoder. */
    override fun close()
}

/** The result of [SDLImage.loadGPUTexture] and friends. */
data class SDLImageGPUTexture(
    /** The loaded GPU texture (release it with [SDLGPUTexture.close]). */
    val texture: SDLGPUTexture,
    /** The texture width in pixels. */
    val width: Int,
    /** The texture height in pixels. */
    val height: Int,
)

/**
 * Kotlin Multiplatform bindings for SDL_image 3.x, built on top of sdl-kmp.
 *
 *  - loading: [load] and [loadIO] return [SDLSurface]s; [loadTexture] and
 *    [loadTextureIO] return [SDLTexture]s bound to an sdl-kmp [SDLRenderer];
 *    [loadGPUTexture] and friends return GPU textures
 *  - detection: [isANI] and friends probe an [SDLIOStream] for a format
 *  - typed loads: [loadPNG] and friends skip format autodetection;
 *    [loadSizedSVG] renders an SVG at an explicit size
 *  - saving: [save] and the format-specific [savePNG]/[saveJPG]/[saveWEBP]/...
 *    write an [SDLSurface] to a file or an [SDLIOStream]
 *  - animations: [loadAnimation] and friends return [SDLImageAnimation]s;
 *    [createAnimationEncoder]/[createAnimationDecoder] stream frames
 *
 * On the JVM the bindings delegate to libsdl_image_jni, a self-contained JNI
 * shared library whose SDL3 symbols are resolved at runtime from sdl-kmp's
 * libsdl_jni; on native platforms they delegate to the SDL_image static
 * library embedded in the published klib (see the image.def cinterop file).
 */
expect object SDLImage {

    /** The version of the underlying SDL_image library. */
    fun version(): Int

    /** The last error set by SDL_image, or null if there is none. */
    fun error(): String?

    /** Clears the last error set by SDL_image. */
    fun clearError()

    // =========================================================================
    // Loading (autodetected formats)
    // =========================================================================

    /** Loads the image at [file] into a new surface, or null on failure. */
    fun load(file: String): SDLSurface?

    /**
     * Loads the image from the SDL [stream] into a new surface, or null on
     * failure. When [closeIO] is true the stream is closed before returning,
     * whether the call succeeds or not.
     */
    fun loadIO(stream: SDLIOStream, closeIO: Boolean = false): SDLSurface?

    /**
     * Loads the image from the SDL [stream] with an explicit [type] hint
     * (e.g. "BMP", "JPG", "PNG"), or null on failure.
     */
    fun loadTypedIO(stream: SDLIOStream, closeIO: Boolean = false, type: String): SDLSurface?

    /** Loads the image at [file] into a texture of [renderer], or null on failure. */
    fun loadTexture(renderer: SDLRenderer, file: String): SDLTexture?

    /** Loads the image from the SDL [stream] into a texture of [renderer]. */
    fun loadTextureIO(renderer: SDLRenderer, stream: SDLIOStream, closeIO: Boolean = false): SDLTexture?

    /** Loads the image from the SDL [stream] with an explicit [type] hint. */
    fun loadTextureTypedIO(
        renderer: SDLRenderer,
        stream: SDLIOStream,
        closeIO: Boolean = false,
        type: String,
    ): SDLTexture?

    /**
     * Loads the image at [file] into an R8G8B8A8 GPU texture.
     *
     * [copyPass] is the raw SDL_GPUCopyPass handle (obtained from an active
     * command buffer; sdl-kmp does not expose copy passes yet), or null to
     * let this binding create and submit an internal copy pass on the GPU
     * queue of [device]. Returns the texture and its size, or null on failure.
     */
    fun loadGPUTexture(device: SDLGPUDevice, copyPass: Long = 0L, file: String): SDLImageGPUTexture?

    /** Loads the image from the SDL [stream] into an R8G8B8A8 GPU texture. */
    fun loadGPUTextureIO(
        device: SDLGPUDevice,
        copyPass: Long = 0L,
        stream: SDLIOStream,
        closeIO: Boolean = false,
    ): SDLImageGPUTexture?

    /** Loads the image from the SDL [stream] with an explicit [type] hint. */
    fun loadGPUTextureTypedIO(
        device: SDLGPUDevice,
        copyPass: Long = 0L,
        stream: SDLIOStream,
        closeIO: Boolean = false,
        type: String,
    ): SDLImageGPUTexture?

    /** The image currently in the clipboard, or null if none is available. */
    fun getClipboardImage(): SDLSurface?

    // =========================================================================
    // Format detection (seeks [stream] back to its original position)
    // =========================================================================

    /** Whether the data on [stream] is ANI animated cursor data. */
    fun isANI(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is AVIF data. */
    fun isAVIF(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is CUR data. */
    fun isCUR(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is BMP data. */
    fun isBMP(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is GIF data. */
    fun isGIF(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is ICO data. */
    fun isICO(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is JPG data. */
    fun isJPG(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is JXL data. */
    fun isJXL(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is LBM data. */
    fun isLBM(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is PCX data. */
    fun isPCX(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is PNG data. */
    fun isPNG(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is PNM data. */
    fun isPNM(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is QOI data. */
    fun isQOI(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is SVG data. */
    fun isSVG(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is TIFF data. */
    fun isTIF(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is WEBP data. */
    fun isWEBP(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is XCF data. */
    fun isXCF(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is XPM data. */
    fun isXPM(stream: SDLIOStream): Boolean

    /** Whether the data on [stream] is XV data. */
    fun isXV(stream: SDLIOStream): Boolean

    // =========================================================================
    // Typed loads (skip format autodetection)
    // =========================================================================

    /** Loads an AVIF image from [stream]. */
    fun loadAVIF(stream: SDLIOStream): SDLSurface?

    /** Loads a BMP image from [stream]. */
    fun loadBMP(stream: SDLIOStream): SDLSurface?

    /** Loads a CUR image from [stream]. */
    fun loadCUR(stream: SDLIOStream): SDLSurface?

    /** Loads a GIF image from [stream]. */
    fun loadGIF(stream: SDLIOStream): SDLSurface?

    /** Loads an ICO image from [stream]. */
    fun loadICO(stream: SDLIOStream): SDLSurface?

    /** Loads a JPG image from [stream]. */
    fun loadJPG(stream: SDLIOStream): SDLSurface?

    /** Loads a JXL image from [stream]. */
    fun loadJXL(stream: SDLIOStream): SDLSurface?

    /** Loads an LBM image from [stream]. */
    fun loadLBM(stream: SDLIOStream): SDLSurface?

    /** Loads a PCX image from [stream]. */
    fun loadPCX(stream: SDLIOStream): SDLSurface?

    /** Loads a PNG image from [stream]. */
    fun loadPNG(stream: SDLIOStream): SDLSurface?

    /** Loads a PNM image from [stream]. */
    fun loadPNM(stream: SDLIOStream): SDLSurface?

    /** Loads a QOI image from [stream]. */
    fun loadQOI(stream: SDLIOStream): SDLSurface?

    /** Loads an SVG image from [stream]. */
    fun loadSVG(stream: SDLIOStream): SDLSurface?

    /** Renders the SVG from [stream] at [width]x[height] pixels. */
    fun loadSizedSVG(stream: SDLIOStream, width: Int, height: Int): SDLSurface?

    /** Loads a TGA image from [stream]. */
    fun loadTGA(stream: SDLIOStream): SDLSurface?

    /** Loads a TIFF image from [stream]. */
    fun loadTIF(stream: SDLIOStream): SDLSurface?

    /** Loads a WEBP image from [stream]. */
    fun loadWEBP(stream: SDLIOStream): SDLSurface?

    /** Loads an XCF image from [stream]. */
    fun loadXCF(stream: SDLIOStream): SDLSurface?

    /** Loads an XPM image from [stream]. */
    fun loadXPM(stream: SDLIOStream): SDLSurface?

    /** Loads an XV image from [stream]. */
    fun loadXV(stream: SDLIOStream): SDLSurface?

    /** Loads an XPM image from an array of strings (the .xpm text). */
    fun readXPMFromArray(xpm: List<String>): SDLSurface?

    /** Loads an XPM image from an array of strings, converted to RGB888. */
    fun readXPMFromArrayToRGB888(xpm: List<String>): SDLSurface?

    // =========================================================================
    // Saving
    // =========================================================================

    /** Saves [surface] to [file], choosing the format from the extension. */
    fun save(surface: SDLSurface, file: String): Boolean

    /** Saves [surface] to the stream [dst] with an explicit [type] hint. */
    fun saveTypedIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean = false, type: String): Boolean

    /** Saves [surface] to [file] as AVIF with the given [quality] (0-100). */
    fun saveAVIF(surface: SDLSurface, file: String, quality: Int): Boolean

    /** Saves [surface] to the stream [dst] as AVIF with the given [quality]. */
    fun saveAVIFIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean = false, quality: Int): Boolean

    /** Saves [surface] to [file] as BMP. */
    fun saveBMP(surface: SDLSurface, file: String): Boolean

    /** Saves [surface] to the stream [dst] as BMP. */
    fun saveBMPIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean = false): Boolean

    /** Saves [surface] to [file] as CUR. */
    fun saveCUR(surface: SDLSurface, file: String): Boolean

    /** Saves [surface] to the stream [dst] as CUR. */
    fun saveCURIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean = false): Boolean

    /** Saves [surface] to [file] as GIF. */
    fun saveGIF(surface: SDLSurface, file: String): Boolean

    /** Saves [surface] to the stream [dst] as GIF. */
    fun saveGIFIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean = false): Boolean

    /** Saves [surface] to [file] as ICO. */
    fun saveICO(surface: SDLSurface, file: String): Boolean

    /** Saves [surface] to the stream [dst] as ICO. */
    fun saveICOIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean = false): Boolean

    /** Saves [surface] to [file] as JPG with the given [quality] (0-100). */
    fun saveJPG(surface: SDLSurface, file: String, quality: Int): Boolean

    /** Saves [surface] to the stream [dst] as JPG with the given [quality]. */
    fun saveJPGIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean = false, quality: Int): Boolean

    /** Saves [surface] to [file] as PNG. */
    fun savePNG(surface: SDLSurface, file: String): Boolean

    /** Saves [surface] to the stream [dst] as PNG. */
    fun savePNGIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean = false): Boolean

    /** Saves [surface] to [file] as TGA. */
    fun saveTGA(surface: SDLSurface, file: String): Boolean

    /** Saves [surface] to the stream [dst] as TGA. */
    fun saveTGAIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean = false): Boolean

    /** Saves [surface] to [file] as WEBP with the given [quality] (0.0-100.0). */
    fun saveWEBP(surface: SDLSurface, file: String, quality: Float): Boolean

    /** Saves [surface] to the stream [dst] as WEBP with the given [quality]. */
    fun saveWEBPIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean = false, quality: Float): Boolean

    // =========================================================================
    // Animations
    // =========================================================================

    /** Loads the animation at [file], or null on failure. */
    fun loadAnimation(file: String): SDLImageAnimation?

    /** Loads the animation from the SDL [stream], or null on failure. */
    fun loadAnimationIO(stream: SDLIOStream, closeIO: Boolean = false): SDLImageAnimation?

    /** Loads the animation from the SDL [stream] with an explicit [type] hint. */
    fun loadAnimationTypedIO(
        stream: SDLIOStream,
        closeIO: Boolean = false,
        type: String,
    ): SDLImageAnimation?

    /** Loads an ANI animation from [stream]. */
    fun loadANIAnimation(stream: SDLIOStream): SDLImageAnimation?

    /** Loads an APNG animation from [stream]. */
    fun loadAPNGAnimation(stream: SDLIOStream): SDLImageAnimation?

    /** Loads an AVIF animation from [stream]. */
    fun loadAVIFAnimation(stream: SDLIOStream): SDLImageAnimation?

    /** Loads a GIF animation from [stream]. */
    fun loadGIFAnimation(stream: SDLIOStream): SDLImageAnimation?

    /** Loads a WEBP animation from [stream]. */
    fun loadWEBPAnimation(stream: SDLIOStream): SDLImageAnimation?

    /** Saves [animation] to [file], choosing the format from the extension. */
    fun saveAnimation(animation: SDLImageAnimation, file: String): Boolean

    /** Saves [animation] to the stream [dst] with an explicit [type] hint. */
    fun saveAnimationTypedIO(
        animation: SDLImageAnimation,
        dst: SDLIOStream,
        closeIO: Boolean = false,
        type: String,
    ): Boolean

    /** Saves [animation] to the stream [dst] as ANI. */
    fun saveANIAnimationIO(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean = false): Boolean

    /** Saves [animation] to the stream [dst] as APNG. */
    fun saveAPNGAnimationIO(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean = false): Boolean

    /** Saves [animation] to the stream [dst] as AVIF with the given [quality]. */
    fun saveAVIFAnimationIO(
        animation: SDLImageAnimation,
        dst: SDLIOStream,
        closeIO: Boolean = false,
        quality: Int,
    ): Boolean

    /** Saves [animation] to the stream [dst] as GIF. */
    fun saveGIFAnimationIO(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean = false): Boolean

    /** Saves [animation] to the stream [dst] as WEBP with the given [quality]. */
    fun saveWEBPAnimationIO(
        animation: SDLImageAnimation,
        dst: SDLIOStream,
        closeIO: Boolean = false,
        quality: Int,
    ): Boolean

    /**
     * Creates an animated cursor from [animation] with the hotspot at
     * ([hotX], [hotY]); returns the raw SDL_Cursor handle, or 0 on failure.
     * sdl-kmp does not wrap SDL_Cursor yet, so the handle must be managed
     * with the SDL3 C API.
     */
    fun createAnimatedCursor(animation: SDLImageAnimation, hotX: Int, hotY: Int): Long

    // =========================================================================
    // Streaming animation encoder / decoder
    // =========================================================================

    /** Creates an animation encoder writing to [file], or null on failure. */
    fun createAnimationEncoder(file: String): SDLImageAnimationEncoder?

    /** Creates an animation encoder writing to the stream [dst]. */
    fun createAnimationEncoderIO(
        dst: SDLIOStream,
        closeIO: Boolean = false,
        type: String,
    ): SDLImageAnimationEncoder?

    /** Creates an animation encoder configured by the SDL properties [props]. */
    fun createAnimationEncoderWithProperties(props: ULong): SDLImageAnimationEncoder?

    /** Creates an animation decoder reading from [file], or null on failure. */
    fun createAnimationDecoder(file: String): SDLImageAnimationDecoder?

    /** Creates an animation decoder reading from the stream [src]. */
    fun createAnimationDecoderIO(
        src: SDLIOStream,
        closeIO: Boolean = false,
        type: String,
    ): SDLImageAnimationDecoder?

    /** Creates an animation decoder configured by the SDL properties [props]. */
    fun createAnimationDecoderWithProperties(props: ULong): SDLImageAnimationDecoder?
}
