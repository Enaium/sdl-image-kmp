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

import cn.enaium.sdl.SDLGPUTexture
import cn.enaium.sdl.SDLTexture

/**
 * Internal: wraps a raw SDL_Texture handle created by SDL_image's
 * [SDLImage.loadTexture] and friends in an [SDLTexture], or returns null for
 * a null pointer.
 *
 * [owned] controls whether [SDLTexture.close] destroys the underlying
 * texture. Like the surfaces of this library, the returned texture is NOT
 * the sdl-kmp platform implementation, so APIs that downcast it (such as
 * [cn.enaium.sdl.SDLRenderer.renderTexture]) will reject it; use the
 * [SDLTexture] interface methods instead.
 */
internal expect fun Long.toSDLTexture(owned: Boolean): SDLTexture?

/**
 * Internal: wraps a raw SDL_GPUTexture handle created by SDL_image's
 * [SDLImage.loadGPUTexture] and friends in an [SDLGPUTexture], or returns
 * null for a null pointer. [owned] controls whether [SDLGPUTexture.close]
 * releases the texture.
 */
internal expect fun Long.toSDLGPUTexture(owned: Boolean): SDLGPUTexture?
