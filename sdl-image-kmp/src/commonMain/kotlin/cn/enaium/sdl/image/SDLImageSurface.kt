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

import cn.enaium.sdl.SDLSurface

/**
 * Internal: wraps a raw SDL_Surface handle created by SDL_image's load
 * functions in an [SDLSurface], or returns null for a null pointer.
 *
 * [owned] controls whether [SDLSurface.close] destroys the underlying
 * surface. Note that the returned surface is NOT the sdl-kmp platform
 * implementation, so APIs that downcast it (such as
 * [cn.enaium.sdl.SDLRenderer.createTextureFromSurface]) will reject it;
 * upload the surface to a texture with
 * `renderer.createTexture(format, access, width, height)` +
 * [cn.enaium.sdl.SDLTexture.update] instead, or blit it with the
 * [cn.enaium.sdl.SDLSurface] API directly.
 */
internal expect fun Long.toSDLSurface(owned: Boolean): SDLSurface?
