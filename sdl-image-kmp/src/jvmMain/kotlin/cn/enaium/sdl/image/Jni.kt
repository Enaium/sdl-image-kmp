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

/**
 * JNI bridge for the JVM target.
 *
 * Every `external fun` maps 1:1 to a `Java_cn_enaium_sdl_image_Jni_<name>`
 * function in jni/jni_image.cpp (see the naming convention in sdl-kmp's
 * jni_bridge.h). All members are public (no `internal` modifier) so their JVM
 * names are not mangled by the Kotlin compiler.
 *
 * The underlying libsdl_image_jni shared library statically links its own SDL3
 * and SDL_image copies; the init block still touches sdl-kmp first so
 * libsdl_jni (and the SDL3 bindings) are usable by the time the IMG library
 * is loaded.
 */
internal object Jni {

    init {
        // Make sure sdl-kmp's Jni object is initialized (loading libsdl_jni)
        // before libsdl_image_jni is dlopen()ed.
        cn.enaium.sdl.SDL.error()
        ImageNativeLoader.load()
    }

    // =========================================================================
    // Core
    // =========================================================================

    external fun version(): Int
    external fun getError(): String?
    external fun clearError()

    // =========================================================================
    // Loading (autodetected formats)
    // =========================================================================

    external fun load(file: String): Long
    external fun loadIO(stream: Long, closeIO: Boolean): Long
    external fun loadTypedIO(stream: Long, closeIO: Boolean, type: String): Long
    external fun loadTexture(renderer: Long, file: String): Long
    external fun loadTextureIO(renderer: Long, stream: Long, closeIO: Boolean): Long
    external fun loadTextureTypedIO(renderer: Long, stream: Long, closeIO: Boolean, type: String): Long
    external fun loadGPUTexture(device: Long, copyPass: Long, file: String): LongArray?
    external fun loadGPUTextureIO(device: Long, copyPass: Long, stream: Long, closeIO: Boolean): LongArray?
    external fun loadGPUTextureTypedIO(
        device: Long,
        copyPass: Long,
        stream: Long,
        closeIO: Boolean,
        type: String,
    ): LongArray?
    external fun getClipboardImage(): Long

    // =========================================================================
    // Format detection
    // =========================================================================

    external fun isANI(stream: Long): Boolean
    external fun isAVIF(stream: Long): Boolean
    external fun isCUR(stream: Long): Boolean
    external fun isBMP(stream: Long): Boolean
    external fun isGIF(stream: Long): Boolean
    external fun isICO(stream: Long): Boolean
    external fun isJPG(stream: Long): Boolean
    external fun isJXL(stream: Long): Boolean
    external fun isLBM(stream: Long): Boolean
    external fun isPCX(stream: Long): Boolean
    external fun isPNG(stream: Long): Boolean
    external fun isPNM(stream: Long): Boolean
    external fun isQOI(stream: Long): Boolean
    external fun isSVG(stream: Long): Boolean
    external fun isTIF(stream: Long): Boolean
    external fun isWEBP(stream: Long): Boolean
    external fun isXCF(stream: Long): Boolean
    external fun isXPM(stream: Long): Boolean
    external fun isXV(stream: Long): Boolean

    // =========================================================================
    // Typed loads
    // =========================================================================

    external fun loadAVIF(stream: Long): Long
    external fun loadBMP(stream: Long): Long
    external fun loadCUR(stream: Long): Long
    external fun loadGIF(stream: Long): Long
    external fun loadICO(stream: Long): Long
    external fun loadJPG(stream: Long): Long
    external fun loadJXL(stream: Long): Long
    external fun loadLBM(stream: Long): Long
    external fun loadPCX(stream: Long): Long
    external fun loadPNG(stream: Long): Long
    external fun loadPNM(stream: Long): Long
    external fun loadQOI(stream: Long): Long
    external fun loadSVG(stream: Long): Long
    external fun loadSizedSVG(stream: Long, width: Int, height: Int): Long
    external fun loadTGA(stream: Long): Long
    external fun loadTIF(stream: Long): Long
    external fun loadWEBP(stream: Long): Long
    external fun loadXCF(stream: Long): Long
    external fun loadXPM(stream: Long): Long
    external fun loadXV(stream: Long): Long
    external fun readXPMFromArray(xpm: Array<String>): Long
    external fun readXPMFromArrayToRGB888(xpm: Array<String>): Long

    // =========================================================================
    // Saving
    // =========================================================================

    external fun save(surface: Long, file: String): Boolean
    external fun saveTypedIO(surface: Long, dst: Long, closeIO: Boolean, type: String): Boolean
    external fun saveAVIF(surface: Long, file: String, quality: Int): Boolean
    external fun saveAVIFIO(surface: Long, dst: Long, closeIO: Boolean, quality: Int): Boolean
    external fun saveBMP(surface: Long, file: String): Boolean
    external fun saveBMPIO(surface: Long, dst: Long, closeIO: Boolean): Boolean
    external fun saveCUR(surface: Long, file: String): Boolean
    external fun saveCURIO(surface: Long, dst: Long, closeIO: Boolean): Boolean
    external fun saveGIF(surface: Long, file: String): Boolean
    external fun saveGIFIO(surface: Long, dst: Long, closeIO: Boolean): Boolean
    external fun saveICO(surface: Long, file: String): Boolean
    external fun saveICOIO(surface: Long, dst: Long, closeIO: Boolean): Boolean
    external fun saveJPG(surface: Long, file: String, quality: Int): Boolean
    external fun saveJPGIO(surface: Long, dst: Long, closeIO: Boolean, quality: Int): Boolean
    external fun savePNG(surface: Long, file: String): Boolean
    external fun savePNGIO(surface: Long, dst: Long, closeIO: Boolean): Boolean
    external fun saveTGA(surface: Long, file: String): Boolean
    external fun saveTGAIO(surface: Long, dst: Long, closeIO: Boolean): Boolean
    external fun saveWEBP(surface: Long, file: String, quality: Float): Boolean
    external fun saveWEBPIO(surface: Long, dst: Long, closeIO: Boolean, quality: Float): Boolean

    // =========================================================================
    // Animations
    // =========================================================================

    external fun loadAnimation(file: String): Long
    external fun loadAnimationIO(stream: Long, closeIO: Boolean): Long
    external fun loadAnimationTypedIO(stream: Long, closeIO: Boolean, type: String): Long
    external fun loadANIAnimationIO(stream: Long): Long
    external fun loadAPNGAnimationIO(stream: Long): Long
    external fun loadAVIFAnimationIO(stream: Long): Long
    external fun loadGIFAnimationIO(stream: Long): Long
    external fun loadWEBPAnimationIO(stream: Long): Long
    external fun freeAnimation(anim: Long)
    external fun animationWidth(anim: Long): Int
    external fun animationHeight(anim: Long): Int
    external fun animationCount(anim: Long): Int
    external fun animationFrames(anim: Long): LongArray
    external fun animationDelays(anim: Long): IntArray
    external fun saveAnimation(anim: Long, file: String): Boolean
    external fun saveAnimationTypedIO(anim: Long, dst: Long, closeIO: Boolean, type: String): Boolean
    external fun saveANIAnimationIO(anim: Long, dst: Long, closeIO: Boolean): Boolean
    external fun saveAPNGAnimationIO(anim: Long, dst: Long, closeIO: Boolean): Boolean
    external fun saveAVIFAnimationIO(anim: Long, dst: Long, closeIO: Boolean, quality: Int): Boolean
    external fun saveGIFAnimationIO(anim: Long, dst: Long, closeIO: Boolean): Boolean
    external fun saveWEBPAnimationIO(anim: Long, dst: Long, closeIO: Boolean, quality: Int): Boolean
    external fun createAnimatedCursor(anim: Long, hotX: Int, hotY: Int): Long

    // =========================================================================
    // Streaming animation encoder / decoder
    // =========================================================================

    external fun createAnimationEncoder(file: String): Long
    external fun createAnimationEncoderIO(dst: Long, closeIO: Boolean, type: String): Long
    external fun createAnimationEncoderWithProperties(props: Long): Long
    external fun addAnimationEncoderFrame(encoder: Long, surface: Long, duration: Long): Boolean
    external fun closeAnimationEncoder(encoder: Long): Boolean
    external fun createAnimationDecoder(file: String): Long
    external fun createAnimationDecoderIO(src: Long, closeIO: Boolean, type: String): Long
    external fun createAnimationDecoderWithProperties(props: Long): Long
    external fun getAnimationDecoderProperties(decoder: Long): Int
    external fun getAnimationDecoderFrame(decoder: Long): LongArray?
    external fun getAnimationDecoderStatus(decoder: Long): Int
    external fun resetAnimationDecoder(decoder: Long): Boolean
    external fun closeAnimationDecoder(decoder: Long): Boolean

    // =========================================================================
    // Surfaces (wrap IMG-loaded SDL_Surfaces into SDLSurface)
    // =========================================================================

    external fun surfaceWidth(surface: Long): Int
    external fun surfaceHeight(surface: Long): Int
    external fun surfaceFormat(surface: Long): Int
    external fun surfacePitch(surface: Long): Int
    external fun surfaceColorspace(surface: Long): Int
    external fun surfacePixels(surface: Long): ByteArray?
    external fun lockSurface(surface: Long): Boolean
    external fun unlockSurface(surface: Long)
    external fun surfaceFillRect(surface: Long, rect: IntArray?, r: Int, g: Int, b: Int, a: Int): Boolean
    external fun surfaceFillRects(surface: Long, rects: IntArray, r: Int, g: Int, b: Int, a: Int): Boolean
    external fun surfaceBlit(src: Long, srcRect: IntArray?, dst: Long, dstRect: IntArray?): Boolean
    external fun surfaceBlitScaled(src: Long, srcRect: IntArray?, dst: Long, dstRect: IntArray?, scaleMode: Int): Boolean
    external fun surfaceSaveBMP(surface: Long, path: String): Boolean
    external fun convertSurface(surface: Long, format: Int): Long
    external fun destroySurface(surface: Long)

    // =========================================================================
    // Textures (wrap IMG-loaded SDL_Textures into SDLTexture)
    // =========================================================================

    external fun textureFormat(texture: Long): Int
    external fun textureAccess(texture: Long): Int
    external fun textureSize(texture: Long): FloatArray
    external fun textureColorMod(texture: Long): IntArray
    external fun setTextureColorMod(texture: Long, r: Int, g: Int, b: Int): Boolean
    external fun textureAlphaMod(texture: Long): Int
    external fun setTextureAlphaMod(texture: Long, a: Int): Boolean
    external fun textureBlendMode(texture: Long): Int
    external fun setTextureBlendMode(texture: Long, mode: Int): Boolean
    external fun textureScaleMode(texture: Long): Int
    external fun setTextureScaleMode(texture: Long, mode: Int): Boolean
    external fun textureUpdate(texture: Long, rect: IntArray?, pixels: ByteArray, pitch: Int): Boolean
    external fun textureLock(texture: Long, rect: IntArray?): Array<Any>?
    external fun textureUnlock(texture: Long)
    external fun destroyTexture(texture: Long)

    // =========================================================================
    // GPU textures
    // =========================================================================

    external fun releaseGPUTexture(device: Long, texture: Long)
}
