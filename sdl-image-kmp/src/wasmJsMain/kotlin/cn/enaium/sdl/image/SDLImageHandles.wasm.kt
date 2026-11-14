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
import cn.enaium.sdl.SDLSurface
import cn.enaium.sdl.SDLTexture

/*
 * The JVM and native implementations wrap raw SDL handles returned by their
 * cinterop/JNI layer. The wasmJs implementation never produces such handles:
 * SDL_image runs in its own WebAssembly instance, so decoded pixels are copied
 * into a surface created through sdl-kmp's SDL.createSurface (see
 * SDLImage.wasm.kt). These converters therefore exist only to satisfy the
 * common declarations.
 */

private const val WASM_MESSAGE =
    "raw SDL handles cannot be wrapped on wasm: SDL_image decodes into sdl-kmp surfaces"

internal actual fun Long.toSDLSurface(owned: Boolean): SDLSurface? =
    throw UnsupportedOperationException(WASM_MESSAGE)

internal actual fun Long.toSDLTexture(owned: Boolean): SDLTexture? =
    throw UnsupportedOperationException(WASM_MESSAGE)

internal actual fun Long.toSDLGPUTexture(device: SDLGPUDevice): SDLGPUTexture? =
    throw UnsupportedOperationException(WASM_MESSAGE)
