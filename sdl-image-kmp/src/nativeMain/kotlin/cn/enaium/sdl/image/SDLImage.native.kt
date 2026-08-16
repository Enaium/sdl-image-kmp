@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.enaium.sdl.image

import cn.enaium.sdl.SDL
import cn.enaium.sdl.SDLColor
import cn.enaium.sdl.SDLFloatPoint
import cn.enaium.sdl.SDLGPUDevice
import cn.enaium.sdl.SDLGPUTexture
import cn.enaium.sdl.SDLIOStream
import cn.enaium.sdl.SDLRect
import cn.enaium.sdl.SDLRenderer
import cn.enaium.sdl.SDLScaleMode
import cn.enaium.sdl.SDLSurface
import cn.enaium.sdl.SDLTexture
import cn.enaium.sdl.SDLTextureLock
import sdl_image.IMG_Animation
import cnames.structs.SDL_GPUCopyPass
import cnames.structs.SDL_GPUDevice
import cnames.structs.SDL_GPUTexture
import cnames.structs.SDL_IOStream
import cnames.structs.SDL_Renderer
import sdl3.*
import kotlinx.cinterop.*
import sdl_image.IMG_AddAnimationEncoderFrame
import sdl_image.IMG_CloseAnimationDecoder
import sdl_image.IMG_CloseAnimationEncoder
import sdl_image.IMG_CreateAnimatedCursor
import sdl_image.IMG_CreateAnimationDecoder
import sdl_image.IMG_CreateAnimationDecoder_IO
import sdl_image.IMG_CreateAnimationDecoderWithProperties
import sdl_image.IMG_CreateAnimationEncoder
import sdl_image.IMG_CreateAnimationEncoder_IO
import sdl_image.IMG_CreateAnimationEncoderWithProperties
import sdl_image.IMG_FreeAnimation
import sdl_image.IMG_GetAnimationDecoderFrame
import sdl_image.IMG_GetAnimationDecoderProperties
import sdl_image.IMG_GetAnimationDecoderStatus
import sdl_image.IMG_GetClipboardImage
import sdl_image.IMG_Load
import sdl_image.IMG_LoadANIAnimation_IO
import sdl_image.IMG_LoadAPNGAnimation_IO
import sdl_image.IMG_LoadAVIFAnimation_IO
import sdl_image.IMG_LoadAVIF_IO
import sdl_image.IMG_LoadAnimation
import sdl_image.IMG_LoadAnimationTyped_IO
import sdl_image.IMG_LoadAnimation_IO
import sdl_image.IMG_LoadBMP_IO
import sdl_image.IMG_LoadCUR_IO
import sdl_image.IMG_LoadGIFAnimation_IO
import sdl_image.IMG_LoadGIF_IO
import sdl_image.IMG_LoadGPUTexture
import sdl_image.IMG_LoadGPUTextureTyped_IO
import sdl_image.IMG_LoadGPUTexture_IO
import sdl_image.IMG_LoadICO_IO
import sdl_image.IMG_LoadJPG_IO
import sdl_image.IMG_LoadJXL_IO
import sdl_image.IMG_LoadLBM_IO
import sdl_image.IMG_LoadPCX_IO
import sdl_image.IMG_LoadPNG_IO
import sdl_image.IMG_LoadPNM_IO
import sdl_image.IMG_LoadQOI_IO
import sdl_image.IMG_LoadSVG_IO
import sdl_image.IMG_LoadSizedSVG_IO
import sdl_image.IMG_LoadTGA_IO
import sdl_image.IMG_LoadTIF_IO
import sdl_image.IMG_LoadTexture
import sdl_image.IMG_LoadTextureTyped_IO
import sdl_image.IMG_LoadTexture_IO
import sdl_image.IMG_LoadWEBPAnimation_IO
import sdl_image.IMG_LoadWEBP_IO
import sdl_image.IMG_LoadXCF_IO
import sdl_image.IMG_LoadXPM_IO
import sdl_image.IMG_LoadXV_IO
import sdl_image.IMG_LoadTyped_IO
import sdl_image.IMG_Load_IO
import sdl_image.IMG_ReadXPMFromArray
import sdl_image.IMG_ReadXPMFromArrayToRGB888
import sdl_image.IMG_ResetAnimationDecoder
import sdl_image.IMG_Save
import sdl_image.IMG_SaveANIAnimation_IO
import sdl_image.IMG_SaveAPNGAnimation_IO
import sdl_image.IMG_SaveAVIF
import sdl_image.IMG_SaveAVIFAnimation_IO
import sdl_image.IMG_SaveAVIF_IO
import sdl_image.IMG_SaveAnimation
import sdl_image.IMG_SaveAnimationTyped_IO
import sdl_image.IMG_SaveBMP
import sdl_image.IMG_SaveBMP_IO
import sdl_image.IMG_SaveCUR
import sdl_image.IMG_SaveCUR_IO
import sdl_image.IMG_SaveGIF
import sdl_image.IMG_SaveGIFAnimation_IO
import sdl_image.IMG_SaveGIF_IO
import sdl_image.IMG_SaveICO
import sdl_image.IMG_SaveICO_IO
import sdl_image.IMG_SaveJPG
import sdl_image.IMG_SaveJPG_IO
import sdl_image.IMG_SavePNG
import sdl_image.IMG_SavePNG_IO
import sdl_image.IMG_SaveTGA
import sdl_image.IMG_SaveTGA_IO
import sdl_image.IMG_SaveTyped_IO
import sdl_image.IMG_SaveWEBP
import sdl_image.IMG_SaveWEBPAnimation_IO
import sdl_image.IMG_SaveWEBP_IO
import sdl_image.IMG_Version
import sdl_image.IMG_isANI
import sdl_image.IMG_isAVIF
import sdl_image.IMG_isBMP
import sdl_image.IMG_isCUR
import sdl_image.IMG_isGIF
import sdl_image.IMG_isICO
import sdl_image.IMG_isJPG
import sdl_image.IMG_isJXL
import sdl_image.IMG_isLBM
import sdl_image.IMG_isPCX
import sdl_image.IMG_isPNG
import sdl_image.IMG_isPNM
import sdl_image.IMG_isQOI
import sdl_image.IMG_isSVG
import sdl_image.IMG_isTIF
import sdl_image.IMG_isWEBP
import sdl_image.IMG_isXCF
import sdl_image.IMG_isXPM
import sdl_image.IMG_isXV

private fun copyBytes(dst: ByteArray, dstOffset: Int, src: CPointer<out CPointed>?, length: Int) {
    if (src == null) return
    val bytes = src.reinterpret<ByteVar>()
    for (i in 0 until length) {
        dst[dstOffset + i] = bytes[i]
    }
}

/**
 * An [SDLSurface] wrapping an SDL_Surface created outside the sdl-kmp
 * library (e.g. by SDL_image's load functions). The SDL3 calls come from the
 * sdl3 cinterop package of the sdl-kmp klib, so they operate on the very
 * SDL3 instance the application uses.
 */
internal class NativeImageSurface internal constructor(
    ptr: CPointer<SDL_Surface>?,
    internal val owned: Boolean,
) : SDLSurface {

    internal var surface: CPointer<SDL_Surface>? = ptr

    override val ptr: Long get() = surface?.rawValue?.toLong() ?: 0L

    internal fun check(): CPointer<SDL_Surface> =
        surface ?: throw IllegalStateException("SDL surface is closed")

    override val width: Int get() = check().pointed.w
    override val height: Int get() = check().pointed.h
    override val format: Int get() = check().pointed.format.toInt()
    override val colorspace: Int get() = SDL_GetSurfaceColorspace(check()).toInt()
    override val pitch: Int get() = check().pointed.pitch

    override val pixels: ByteArray
        get() {
            val bytes = pitch * height
            val out = ByteArray(bytes)
            copyBytes(out, 0, check().pointed.pixels, bytes)
            return out
        }

    override fun lock(): Boolean = SDL_LockSurface(check())

    override fun unlock() {
        SDL_UnlockSurface(check())
    }

    override fun fillRect(rect: SDLRect?, color: SDLColor): Boolean = memScoped {
        val rectPtr = rect?.let {
            val r = alloc<sdl3.SDL_Rect>()
            r.x = it.x
            r.y = it.y
            r.w = it.width
            r.h = it.height
            r.ptr
        }
        SDL_FillSurfaceRect(check(), rectPtr, color.toMappedPixel())
    }

    override fun fillRects(rects: List<SDLRect>, color: SDLColor): Boolean = memScoped {
        if (rects.isEmpty()) return true
        val arr = allocArray<sdl3.SDL_Rect>(rects.size)
        for (i in rects.indices) {
            arr[i].x = rects[i].x
            arr[i].y = rects[i].y
            arr[i].w = rects[i].width
            arr[i].h = rects[i].height
        }
        SDL_FillSurfaceRects(check(), arr, rects.size, color.toMappedPixel())
    }

    override fun blit(src: SDLRect?, dst: SDLSurface, dstRect: SDLRect?): Boolean {
        if (dst.ptr == 0L) throw IllegalArgumentException("dst is closed")
        return memScoped {
            val srcPtr = src?.let {
                val r = alloc<sdl3.SDL_Rect>()
                r.x = it.x
                r.y = it.y
                r.w = it.width
                r.h = it.height
                r.ptr
            }
            val dstPtr = dstRect?.let {
                val r = alloc<sdl3.SDL_Rect>()
                r.x = it.x
                r.y = it.y
                r.w = it.width
                r.h = it.height
                r.ptr
            }
            sdl3.SDL_BlitSurface(check(), srcPtr, dst.ptr.toCPointer<SDL_Surface>(), dstPtr)
        }
    }

    override fun blitScaled(src: SDLRect?, dst: SDLSurface, dstRect: SDLRect?, scaleMode: Int): Boolean {
        if (dst.ptr == 0L) throw IllegalArgumentException("dst is closed")
        return memScoped {
            val srcPtr = src?.let {
                val r = alloc<sdl3.SDL_Rect>()
                r.x = it.x
                r.y = it.y
                r.w = it.width
                r.h = it.height
                r.ptr
            }
            val dstPtr = dstRect?.let {
                val r = alloc<sdl3.SDL_Rect>()
                r.x = it.x
                r.y = it.y
                r.w = it.width
                r.h = it.height
                r.ptr
            }
            sdl3.SDL_BlitSurfaceScaled(
                check(), srcPtr,
                dst.ptr.toCPointer<SDL_Surface>(), dstPtr,
                scaleMode,
            )
        }
    }

    override fun saveBMP(path: String): Boolean = SDL_SaveBMP(check(), path)

    override fun convert(format: Int): SDLSurface {
        val converted = SDL_ConvertSurface(check(), format.toUInt())
            ?: throw IllegalStateException("SDL_ConvertSurface failed: ${SDL.error()}")
        return NativeImageSurface(converted, owned = true)
    }

    override fun close() {
        val s = surface
        if (s == null) return
        surface = null
        if (owned) {
            SDL_DestroySurface(s)
        }
    }
}

private fun SDLColor.toMappedPixel(): UInt =
    (r.toUInt() shl 24) or (g.toUInt() shl 16) or (b.toUInt() shl 8) or a.toUInt()

internal actual fun Long.toSDLSurface(owned: Boolean): SDLSurface? {
    if (this == 0L) return null
    return NativeImageSurface(this.toCPointer<SDL_Surface>(), owned = owned)
}

/**
 * An [SDLTexture] wrapping an SDL_Texture created by SDL_image's
 * [SDLImage.loadTexture] and friends. All operations delegate to the SDL3
 * functions of the sdl-kmp klib, which share the SDL3 instance.
 */
internal class NativeImageTexture internal constructor(
    ptr: CPointer<SDL_Texture>?,
    internal val owned: Boolean,
) : SDLTexture {

    internal var texture: CPointer<SDL_Texture>? = ptr

    override val ptr: Long get() = texture?.rawValue?.toLong() ?: 0L

    internal fun check(): CPointer<SDL_Texture> =
        texture ?: throw IllegalStateException("SDL texture is closed")

    override val format: Int get() = check().pointed.format.toInt()

    override val access: Int
        get() = SDL_GetNumberProperty(
            SDL_GetTextureProperties(check()),
            SDL_PROP_TEXTURE_ACCESS_NUMBER,
            -1L,
        ).toInt()

    override val size: SDLFloatPoint
        get() = memScoped {
            val w = alloc<FloatVar>()
            val h = alloc<FloatVar>()
            SDL_GetTextureSize(check(), w.ptr, h.ptr)
            SDLFloatPoint(w.value, h.value)
        }

    override var colorMod: SDLColor
        get() = memScoped {
            val r = alloc<Uint8Var>()
            val g = alloc<Uint8Var>()
            val b = alloc<Uint8Var>()
            SDL_GetTextureColorMod(check(), r.ptr, g.ptr, b.ptr)
            SDLColor(r.value.toInt(), g.value.toInt(), b.value.toInt())
        }
        set(value) {
            SDL_SetTextureColorMod(check(), value.r.toUByte(), value.g.toUByte(), value.b.toUByte())
        }

    override var alphaMod: Int
        get() = memScoped {
            val a = alloc<Uint8Var>()
            SDL_GetTextureAlphaMod(check(), a.ptr)
            a.value.toInt()
        }
        set(value) {
            SDL_SetTextureAlphaMod(check(), value.toUByte())
        }

    override var blendMode: Int
        get() = memScoped {
            val mode = alloc<UIntVar>()
            SDL_GetTextureBlendMode(check(), mode.ptr)
            mode.value.toInt()
        }
        set(value) {
            SDL_SetTextureBlendMode(check(), value.toUInt())
        }

    override var scaleMode: Int
        get() = memScoped {
            val mode = alloc<IntVar>()
            SDL_GetTextureScaleMode(check(), mode.ptr)
            mode.value.toInt()
        }
        set(value) {
            SDL_SetTextureScaleMode(check(), value)
        }

    override fun update(rect: SDLRect?, pixels: ByteArray, pitch: Int): Boolean = memScoped {
        val rectPtr = rect?.let {
            val r = alloc<sdl3.SDL_Rect>()
            r.x = it.x
            r.y = it.y
            r.w = it.width
            r.h = it.height
            r.ptr
        }
        pixels.usePinned { pinned ->
            SDL_UpdateTexture(
                check(), rectPtr,
                pinned.addressOf(0).reinterpret<ByteVar>(),
                pitch,
            )
        }
    }

    override fun lock(rect: SDLRect?): SDLTextureLock? = memScoped {
        val rectPtr = rect?.let {
            val r = alloc<sdl3.SDL_Rect>()
            r.x = it.x
            r.y = it.y
            r.w = it.width
            r.h = it.height
            r.ptr
        }
        val pixelsPtr = alloc<CPointerVar<out CPointed>>()
        val pitchVar = alloc<IntVar>()
        if (!SDL_LockTexture(check(), rectPtr, pixelsPtr.ptr, pitchVar.ptr)) {
            null
        } else {
            val pitch = pitchVar.value
            val height = rect?.height ?: size.y.toInt().coerceAtLeast(0)
            val bytes = pitch * height
            val out = ByteArray(bytes)
            copyBytes(out, 0, pixelsPtr.value, bytes)
            SDLTextureLock(out, pitch)
        }
    }

    override fun unlock() {
        SDL_UnlockTexture(check())
    }

    override fun close() {
        val t = texture
        if (t == null) return
        texture = null
        if (owned) {
            SDL_DestroyTexture(t)
        }
    }
}

internal actual fun Long.toSDLTexture(owned: Boolean): SDLTexture? {
    if (this == 0L) return null
    return NativeImageTexture(this.toCPointer<SDL_Texture>(), owned = owned)
}

/**
 * An [SDLGPUTexture] wrapping an SDL_GPUTexture created by SDL_image's
 * [SDLImage.loadGPUTexture] and friends. Upload/download are unsupported;
 * [close] releases the texture on the device that created it.
 */
internal class NativeImageGPUTexture internal constructor(
    ptr: CPointer<SDL_GPUTexture>?,
    private val device: CPointer<SDL_GPUDevice>?,
    private val owned: Boolean,
) : SDLGPUTexture {

    internal var texture: CPointer<SDL_GPUTexture>? = ptr

    override val ptr: Long get() = texture?.rawValue?.toLong() ?: 0L

    override fun upload(data: ByteArray, bytesPerRow: Int, x: Int, y: Int, width: Int, height: Int): Boolean =
        false

    override fun download(width: Int, height: Int): ByteArray? = null

    override fun close() {
        val t = texture
        if (t == null) return
        texture = null
        if (owned) {
            SDL_ReleaseGPUTexture(device, t)
        }
    }
}

internal actual fun Long.toSDLGPUTexture(owned: Boolean): SDLGPUTexture? {
    if (this == 0L) return null
    return NativeImageGPUTexture(this.toCPointer<SDL_GPUTexture>(), null, owned = owned)
}

/** Native helper: wraps a GPU texture, remembering the [device] that owns it. */
internal fun Long.toSDLGPUTexture(device: CPointer<SDL_GPUDevice>?, owned: Boolean): SDLGPUTexture? {
    if (this == 0L) return null
    return NativeImageGPUTexture(this.toCPointer<SDL_GPUTexture>(), device, owned = owned)
}

internal class NativeSDLImageAnimation internal constructor(
    ptr: CPointer<IMG_Animation>?,
) : SDLImageAnimation {

    internal var animation: CPointer<IMG_Animation>? = ptr

    override val ptr: Long get() = animation?.rawValue?.toLong() ?: 0L

    internal fun check(): CPointer<IMG_Animation> =
        animation ?: throw IllegalStateException("SDL_image animation is closed")

    override val width: Int get() = check().pointed.w
    override val height: Int get() = check().pointed.h
    override val count: Int get() = check().pointed.count

    override val frames: List<SDLSurface>
        get() {
            val a = check().pointed
            return List(a.count) { i ->
                NativeImageSurface(a.frames?.get(i), owned = false)
            }
        }

    override val delays: IntArray
        get() {
            val a = check().pointed
            return IntArray(a.count) { i -> a.delays?.get(i) ?: 0 }
        }

    override fun close() {
        val a = animation
        if (a == null) return
        animation = null
        IMG_FreeAnimation(a)
    }
}

internal class NativeSDLImageAnimationEncoder internal constructor(
    ptr: CPointer<cnames.structs.IMG_AnimationEncoder>?,
) : SDLImageAnimationEncoder {

    internal var encoder: CPointer<cnames.structs.IMG_AnimationEncoder>? = ptr

    override val ptr: Long get() = encoder?.rawValue?.toLong() ?: 0L

    internal fun check(): CPointer<cnames.structs.IMG_AnimationEncoder> =
        encoder ?: throw IllegalStateException("SDL_image animation encoder is closed")

    override fun addFrame(surface: SDLSurface, duration: Long): Boolean {
        if (surface.ptr == 0L) throw IllegalStateException("SDL surface is closed")
        return IMG_AddAnimationEncoderFrame(check(), surface.ptr.toCPointer<SDL_Surface>(), duration.toULong())
    }

    override fun close() {
        val e = encoder
        if (e == null) return
        encoder = null
        IMG_CloseAnimationEncoder(e)
    }
}

internal class NativeSDLImageAnimationDecoder internal constructor(
    ptr: CPointer<cnames.structs.IMG_AnimationDecoder>?,
) : SDLImageAnimationDecoder {

    internal var decoder: CPointer<cnames.structs.IMG_AnimationDecoder>? = ptr

    override val ptr: Long get() = decoder?.rawValue?.toLong() ?: 0L

    internal fun check(): CPointer<cnames.structs.IMG_AnimationDecoder> =
        decoder ?: throw IllegalStateException("SDL_image animation decoder is closed")

    override val status: Int
        get() {
            val d = decoder ?: return SDLImageDecoderStatus.INVALID
            return IMG_GetAnimationDecoderStatus(d)
        }

    override fun properties(): ULong =
        IMG_GetAnimationDecoderProperties(check()).toULong()

    override fun getFrame(): SDLImageDecoderFrame? = memScoped {
        val frame = alloc<CPointerVar<SDL_Surface>>()
        val duration = alloc<ULongVar>()
        if (!IMG_GetAnimationDecoderFrame(check(), frame.ptr, duration.ptr)) {
            null
        } else {
            val surface = frame.value?.rawValue?.toLong()?.toSDLSurface(owned = true)
                ?: return@memScoped null
            SDLImageDecoderFrame(surface, duration.value.toLong())
        }
    }

    override fun reset(): Boolean = IMG_ResetAnimationDecoder(check())

    override fun close() {
        val d = decoder
        if (d == null) return
        decoder = null
        IMG_CloseAnimationDecoder(d)
    }
}

private inline fun SDLIOStream.streamOrNull(): CPointer<SDL_IOStream>? {
    if (ptr == 0L) throw IllegalStateException("SDL IO stream is closed")
    return ptr.toCPointer<SDL_IOStream>()
}

private inline fun SDLRenderer.rendererOrNull(): CPointer<SDL_Renderer>? {
    if (ptr == 0L) throw IllegalStateException("SDL renderer is closed")
    return ptr.toCPointer<SDL_Renderer>()
}

private inline fun SDLSurface.surfaceOrNull(): CPointer<SDL_Surface>? {
    if (ptr == 0L) throw IllegalStateException("SDL surface is closed")
    return ptr.toCPointer<SDL_Surface>()
}

private inline fun SDLGPUDevice.deviceOrNull(): CPointer<SDL_GPUDevice>? {
    if (ptr == 0L) throw IllegalStateException("SDL GPU device is closed")
    return ptr.toCPointer<SDL_GPUDevice>()
}

private fun Long.copyPassOrNull(): CPointer<SDL_GPUCopyPass>? =
    if (this == 0L) null else this.toCPointer<SDL_GPUCopyPass>()

private fun readXPM(xpm: List<String>, rgb888: Boolean): SDLSurface? = memScoped {
    val arr = allocArray<CPointerVar<ByteVar>>(xpm.size + 1)
    for (i in xpm.indices) {
        arr[i] = xpm[i].cstr.ptr
    }
    arr[xpm.size] = null
    val surf = if (rgb888) {
        IMG_ReadXPMFromArrayToRGB888(arr)
    } else {
        IMG_ReadXPMFromArray(arr)
    }
    (surf?.rawValue?.toLong() ?: 0L).toSDLSurface(owned = true)
}

actual object SDLImage {

    actual fun version(): Int = IMG_Version()

    actual fun error(): String? = SDL_GetError()?.toKString()?.takeIf { it.isNotEmpty() }

    actual fun clearError() {
        SDL_ClearError()
    }

    // =========================================================================
    // Loading (autodetected formats)
    // =========================================================================

    actual fun load(file: String): SDLSurface? =
        IMG_Load(file)?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadIO(stream: SDLIOStream, closeIO: Boolean): SDLSurface? =
        IMG_Load_IO(stream.streamOrNull(), closeIO)?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadTypedIO(stream: SDLIOStream, closeIO: Boolean, type: String): SDLSurface? =
        IMG_LoadTyped_IO(stream.streamOrNull(), closeIO, type)?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadTexture(renderer: SDLRenderer, file: String): SDLTexture? =
        IMG_LoadTexture(renderer.rendererOrNull(), file)?.rawValue?.toLong()?.toSDLTexture(owned = true)

    actual fun loadTextureIO(renderer: SDLRenderer, stream: SDLIOStream, closeIO: Boolean): SDLTexture? =
        IMG_LoadTexture_IO(renderer.rendererOrNull(), stream.streamOrNull(), closeIO)
            ?.rawValue?.toLong()?.toSDLTexture(owned = true)

    actual fun loadTextureTypedIO(
        renderer: SDLRenderer,
        stream: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): SDLTexture? =
        IMG_LoadTextureTyped_IO(renderer.rendererOrNull(), stream.streamOrNull(), closeIO, type)
            ?.rawValue?.toLong()?.toSDLTexture(owned = true)

    actual fun loadGPUTexture(device: SDLGPUDevice, copyPass: Long, file: String): SDLImageGPUTexture? =
        memScoped {
            val w = alloc<IntVar>()
            val h = alloc<IntVar>()
            val tex = IMG_LoadGPUTexture(
                device.deviceOrNull(), copyPass.copyPassOrNull(), file, w.ptr, h.ptr,
            ) ?: return null
            SDLImageGPUTexture(
                tex.rawValue.toLong().toSDLGPUTexture(device.deviceOrNull(), owned = true)!!,
                w.value, h.value,
            )
        }

    actual fun loadGPUTextureIO(        device: SDLGPUDevice,
        copyPass: Long,
        stream: SDLIOStream,
        closeIO: Boolean,
    ): SDLImageGPUTexture? = memScoped {
        val w = alloc<IntVar>()
        val h = alloc<IntVar>()
        val tex = IMG_LoadGPUTexture_IO(
            device.deviceOrNull(), copyPass.copyPassOrNull(), stream.streamOrNull(), closeIO, w.ptr, h.ptr,
        ) ?: return null
        SDLImageGPUTexture(
            tex.rawValue.toLong().toSDLGPUTexture(device.deviceOrNull(), owned = true)!!,
            w.value, h.value,
        )
    }

    actual fun loadGPUTextureTypedIO(
        device: SDLGPUDevice,
        copyPass: Long,
        stream: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): SDLImageGPUTexture? = memScoped {
        val w = alloc<IntVar>()
        val h = alloc<IntVar>()
        val tex = IMG_LoadGPUTextureTyped_IO(
            device.deviceOrNull(), copyPass.copyPassOrNull(), stream.streamOrNull(), closeIO, type, w.ptr, h.ptr,
        ) ?: return null
        SDLImageGPUTexture(
            tex.rawValue.toLong().toSDLGPUTexture(device.deviceOrNull(), owned = true)!!,
            w.value, h.value,
        )
    }

    actual fun getClipboardImage(): SDLSurface? =
        IMG_GetClipboardImage()?.rawValue?.toLong()?.toSDLSurface(owned = true)

    // =========================================================================
    // Format detection
    // =========================================================================

    actual fun isANI(stream: SDLIOStream): Boolean = IMG_isANI(stream.streamOrNull())
    actual fun isAVIF(stream: SDLIOStream): Boolean = IMG_isAVIF(stream.streamOrNull())
    actual fun isCUR(stream: SDLIOStream): Boolean = IMG_isCUR(stream.streamOrNull())
    actual fun isBMP(stream: SDLIOStream): Boolean = IMG_isBMP(stream.streamOrNull())
    actual fun isGIF(stream: SDLIOStream): Boolean = IMG_isGIF(stream.streamOrNull())
    actual fun isICO(stream: SDLIOStream): Boolean = IMG_isICO(stream.streamOrNull())
    actual fun isJPG(stream: SDLIOStream): Boolean = IMG_isJPG(stream.streamOrNull())
    actual fun isJXL(stream: SDLIOStream): Boolean = IMG_isJXL(stream.streamOrNull())
    actual fun isLBM(stream: SDLIOStream): Boolean = IMG_isLBM(stream.streamOrNull())
    actual fun isPCX(stream: SDLIOStream): Boolean = IMG_isPCX(stream.streamOrNull())
    actual fun isPNG(stream: SDLIOStream): Boolean = IMG_isPNG(stream.streamOrNull())
    actual fun isPNM(stream: SDLIOStream): Boolean = IMG_isPNM(stream.streamOrNull())
    actual fun isQOI(stream: SDLIOStream): Boolean = IMG_isQOI(stream.streamOrNull())
    actual fun isSVG(stream: SDLIOStream): Boolean = IMG_isSVG(stream.streamOrNull())
    actual fun isTIF(stream: SDLIOStream): Boolean = IMG_isTIF(stream.streamOrNull())
    actual fun isWEBP(stream: SDLIOStream): Boolean = IMG_isWEBP(stream.streamOrNull())
    actual fun isXCF(stream: SDLIOStream): Boolean = IMG_isXCF(stream.streamOrNull())
    actual fun isXPM(stream: SDLIOStream): Boolean = IMG_isXPM(stream.streamOrNull())
    actual fun isXV(stream: SDLIOStream): Boolean = IMG_isXV(stream.streamOrNull())

    // =========================================================================
    // Typed loads
    // =========================================================================

    actual fun loadAVIF(stream: SDLIOStream): SDLSurface? =
        IMG_LoadAVIF_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadBMP(stream: SDLIOStream): SDLSurface? =
        IMG_LoadBMP_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadCUR(stream: SDLIOStream): SDLSurface? =
        IMG_LoadCUR_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadGIF(stream: SDLIOStream): SDLSurface? =
        IMG_LoadGIF_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadICO(stream: SDLIOStream): SDLSurface? =
        IMG_LoadICO_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadJPG(stream: SDLIOStream): SDLSurface? =
        IMG_LoadJPG_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadJXL(stream: SDLIOStream): SDLSurface? =
        IMG_LoadJXL_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadLBM(stream: SDLIOStream): SDLSurface? =
        IMG_LoadLBM_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadPCX(stream: SDLIOStream): SDLSurface? =
        IMG_LoadPCX_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadPNG(stream: SDLIOStream): SDLSurface? =
        IMG_LoadPNG_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadPNM(stream: SDLIOStream): SDLSurface? =
        IMG_LoadPNM_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadQOI(stream: SDLIOStream): SDLSurface? =
        IMG_LoadQOI_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadSVG(stream: SDLIOStream): SDLSurface? =
        IMG_LoadSVG_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadSizedSVG(stream: SDLIOStream, width: Int, height: Int): SDLSurface? =
        IMG_LoadSizedSVG_IO(stream.streamOrNull(), width, height)?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadTGA(stream: SDLIOStream): SDLSurface? =
        IMG_LoadTGA_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadTIF(stream: SDLIOStream): SDLSurface? =
        IMG_LoadTIF_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadWEBP(stream: SDLIOStream): SDLSurface? =
        IMG_LoadWEBP_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadXCF(stream: SDLIOStream): SDLSurface? =
        IMG_LoadXCF_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadXPM(stream: SDLIOStream): SDLSurface? =
        IMG_LoadXPM_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun loadXV(stream: SDLIOStream): SDLSurface? =
        IMG_LoadXV_IO(stream.streamOrNull())?.rawValue?.toLong()?.toSDLSurface(owned = true)

    actual fun readXPMFromArray(xpm: List<String>): SDLSurface? = readXPM(xpm, rgb888 = false)

    actual fun readXPMFromArrayToRGB888(xpm: List<String>): SDLSurface? = readXPM(xpm, rgb888 = true)

    // =========================================================================
    // Saving
    // =========================================================================

    actual fun save(surface: SDLSurface, file: String): Boolean =
        IMG_Save(surface.surfaceOrNull(), file)

    actual fun saveTypedIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean, type: String): Boolean =
        IMG_SaveTyped_IO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO, type)

    actual fun saveAVIF(surface: SDLSurface, file: String, quality: Int): Boolean =
        IMG_SaveAVIF(surface.surfaceOrNull(), file, quality)

    actual fun saveAVIFIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean, quality: Int): Boolean =
        IMG_SaveAVIF_IO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO, quality)

    actual fun saveBMP(surface: SDLSurface, file: String): Boolean =
        IMG_SaveBMP(surface.surfaceOrNull(), file)

    actual fun saveBMPIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        IMG_SaveBMP_IO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveCUR(surface: SDLSurface, file: String): Boolean =
        IMG_SaveCUR(surface.surfaceOrNull(), file)

    actual fun saveCURIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        IMG_SaveCUR_IO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveGIF(surface: SDLSurface, file: String): Boolean =
        IMG_SaveGIF(surface.surfaceOrNull(), file)

    actual fun saveGIFIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        IMG_SaveGIF_IO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveICO(surface: SDLSurface, file: String): Boolean =
        IMG_SaveICO(surface.surfaceOrNull(), file)

    actual fun saveICOIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        IMG_SaveICO_IO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveJPG(surface: SDLSurface, file: String, quality: Int): Boolean =
        IMG_SaveJPG(surface.surfaceOrNull(), file, quality)

    actual fun saveJPGIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean, quality: Int): Boolean =
        IMG_SaveJPG_IO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO, quality)

    actual fun savePNG(surface: SDLSurface, file: String): Boolean =
        IMG_SavePNG(surface.surfaceOrNull(), file)

    actual fun savePNGIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        IMG_SavePNG_IO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveTGA(surface: SDLSurface, file: String): Boolean =
        IMG_SaveTGA(surface.surfaceOrNull(), file)

    actual fun saveTGAIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean): Boolean =
        IMG_SaveTGA_IO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO)

    actual fun saveWEBP(surface: SDLSurface, file: String, quality: Float): Boolean =
        IMG_SaveWEBP(surface.surfaceOrNull(), file, quality)

    actual fun saveWEBPIO(surface: SDLSurface, dst: SDLIOStream, closeIO: Boolean, quality: Float): Boolean =
        IMG_SaveWEBP_IO(surface.surfaceOrNull(), dst.streamOrNull(), closeIO, quality)

    // =========================================================================
    // Animations
    // =========================================================================

    actual fun loadAnimation(file: String): SDLImageAnimation? =
        IMG_LoadAnimation(file)?.let { NativeSDLImageAnimation(it) }

    actual fun loadAnimationIO(stream: SDLIOStream, closeIO: Boolean): SDLImageAnimation? =
        IMG_LoadAnimation_IO(stream.streamOrNull(), closeIO)?.let { NativeSDLImageAnimation(it) }

    actual fun loadAnimationTypedIO(stream: SDLIOStream, closeIO: Boolean, type: String): SDLImageAnimation? =
        IMG_LoadAnimationTyped_IO(stream.streamOrNull(), closeIO, type)?.let { NativeSDLImageAnimation(it) }

    actual fun loadANIAnimation(stream: SDLIOStream): SDLImageAnimation? =
        IMG_LoadANIAnimation_IO(stream.streamOrNull())?.let { NativeSDLImageAnimation(it) }

    actual fun loadAPNGAnimation(stream: SDLIOStream): SDLImageAnimation? =
        IMG_LoadAPNGAnimation_IO(stream.streamOrNull())?.let { NativeSDLImageAnimation(it) }

    actual fun loadAVIFAnimation(stream: SDLIOStream): SDLImageAnimation? =
        IMG_LoadAVIFAnimation_IO(stream.streamOrNull())?.let { NativeSDLImageAnimation(it) }

    actual fun loadGIFAnimation(stream: SDLIOStream): SDLImageAnimation? =
        IMG_LoadGIFAnimation_IO(stream.streamOrNull())?.let { NativeSDLImageAnimation(it) }

    actual fun loadWEBPAnimation(stream: SDLIOStream): SDLImageAnimation? =
        IMG_LoadWEBPAnimation_IO(stream.streamOrNull())?.let { NativeSDLImageAnimation(it) }

    actual fun saveAnimation(animation: SDLImageAnimation, file: String): Boolean {
        val a = (animation as? NativeSDLImageAnimation)?.check()
            ?: throw IllegalArgumentException("animation is not a native SDL_image animation")
        return IMG_SaveAnimation(a, file)
    }

    actual fun saveAnimationTypedIO(
        animation: SDLImageAnimation,
        dst: SDLIOStream,
        closeIO: Boolean,
        type: String,
    ): Boolean {
        val a = (animation as? NativeSDLImageAnimation)?.check()
            ?: throw IllegalArgumentException("animation is not a native SDL_image animation")
        return IMG_SaveAnimationTyped_IO(a, dst.streamOrNull(), closeIO, type)
    }

    actual fun saveANIAnimationIO(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean): Boolean {
        val a = (animation as? NativeSDLImageAnimation)?.check()
            ?: throw IllegalArgumentException("animation is not a native SDL_image animation")
        return IMG_SaveANIAnimation_IO(a, dst.streamOrNull(), closeIO)
    }

    actual fun saveAPNGAnimationIO(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean): Boolean {
        val a = (animation as? NativeSDLImageAnimation)?.check()
            ?: throw IllegalArgumentException("animation is not a native SDL_image animation")
        return IMG_SaveAPNGAnimation_IO(a, dst.streamOrNull(), closeIO)
    }

    actual fun saveAVIFAnimationIO(
        animation: SDLImageAnimation,
        dst: SDLIOStream,
        closeIO: Boolean,
        quality: Int,
    ): Boolean {
        val a = (animation as? NativeSDLImageAnimation)?.check()
            ?: throw IllegalArgumentException("animation is not a native SDL_image animation")
        return IMG_SaveAVIFAnimation_IO(a, dst.streamOrNull(), closeIO, quality)
    }

    actual fun saveGIFAnimationIO(animation: SDLImageAnimation, dst: SDLIOStream, closeIO: Boolean): Boolean {
        val a = (animation as? NativeSDLImageAnimation)?.check()
            ?: throw IllegalArgumentException("animation is not a native SDL_image animation")
        return IMG_SaveGIFAnimation_IO(a, dst.streamOrNull(), closeIO)
    }

    actual fun saveWEBPAnimationIO(
        animation: SDLImageAnimation,
        dst: SDLIOStream,
        closeIO: Boolean,
        quality: Int,
    ): Boolean {
        val a = (animation as? NativeSDLImageAnimation)?.check()
            ?: throw IllegalArgumentException("animation is not a native SDL_image animation")
        return IMG_SaveWEBPAnimation_IO(a, dst.streamOrNull(), closeIO, quality)
    }

    actual fun createAnimatedCursor(animation: SDLImageAnimation, hotX: Int, hotY: Int): Long {
        val a = (animation as? NativeSDLImageAnimation)?.check()
            ?: throw IllegalArgumentException("animation is not a native SDL_image animation")
        return IMG_CreateAnimatedCursor(a, hotX, hotY)?.rawValue?.toLong() ?: 0L
    }

    // =========================================================================
    // Streaming animation encoder / decoder
    // =========================================================================

    actual fun createAnimationEncoder(file: String): SDLImageAnimationEncoder? =
        IMG_CreateAnimationEncoder(file)?.let { NativeSDLImageAnimationEncoder(it) }

    actual fun createAnimationEncoderIO(dst: SDLIOStream, closeIO: Boolean, type: String): SDLImageAnimationEncoder? =
        IMG_CreateAnimationEncoder_IO(dst.streamOrNull(), closeIO, type)?.let { NativeSDLImageAnimationEncoder(it) }

    actual fun createAnimationEncoderWithProperties(props: ULong): SDLImageAnimationEncoder? =
        IMG_CreateAnimationEncoderWithProperties(props.toUInt())?.let { NativeSDLImageAnimationEncoder(it) }

    actual fun createAnimationDecoder(file: String): SDLImageAnimationDecoder? =
        IMG_CreateAnimationDecoder(file)?.let { NativeSDLImageAnimationDecoder(it) }

    actual fun createAnimationDecoderIO(src: SDLIOStream, closeIO: Boolean, type: String): SDLImageAnimationDecoder? =
        IMG_CreateAnimationDecoder_IO(src.streamOrNull(), closeIO, type)?.let { NativeSDLImageAnimationDecoder(it) }

    actual fun createAnimationDecoderWithProperties(props: ULong): SDLImageAnimationDecoder? =
        IMG_CreateAnimationDecoderWithProperties(props.toUInt())?.let { NativeSDLImageAnimationDecoder(it) }
}
