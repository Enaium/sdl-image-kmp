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

/*
 * Glue between the Emscripten-compiled SDL_image module (sdl_image_wasm.js)
 * and Kotlin/Wasm. Instantiates the module and exposes the sdl_image_kmp_*
 * shim functions as globals so Kotlin's external declarations can call them.
 *
 * Marshaling conventions (see sdl_image_wasm_shim.c):
 *   - scalars pass straight through (numbers);
 *   - strings are converted with UTF8ToString/stringToUTF8;
 *   - byte arrays are copied into a heap buffer for the call and freed after;
 *   - encoded output is returned as a heap pointer whose length is written to
 *     the global sdlImageKmpResultI32[0]; the caller copies the bytes out and
 *     releases the buffer with sdl_image_kmp_Free().
 *
 * File and stream I/O deliberately stays on the Kotlin side: it goes through
 * sdl-kmp's SDLIOStream so the app sees a single Emscripten file system (this
 * module is a separate wasm instance with its own memory).
 */

/*
 * No canvas: this module only decodes/encodes images (it is built without a
 * video driver), so it never creates a WebGL context. SDL3's module owns the
 * canvas; the two instances share nothing but the pixel bytes the Kotlin side
 * copies between them.
 */
export async function initSdlImageKmp() {
    const mod = await import('./sdl_image_wasm.js');
    const loadModule = typeof mod.default === 'function' ? mod.default : mod;
    const Module = await loadModule({});

    const UTF8 = Module.UTF8ToString;
    const malloc = Module._malloc;
    const free = Module._free;

    const R32 = new Int32Array(8);
    globalThis.sdlImageKmpResultI32 = R32;
    globalThis.sdlImageKmpResultI32Get = (i) => R32[i];

    const allocStr = (s) => {
        if (s === null || s === undefined) return 0;
        const bytes = Module.lengthBytesUTF8(s) + 1;
        const p = malloc(bytes);
        Module.stringToUTF8(s, p, bytes);
        return p;
    };
    const freeStr = (p) => { if (p) free(p); };
    const readCString = (p) => (p ? UTF8(p) : null);

    /* Copies a Kotlin Int8Array into the heap; returns [ptr, len]. */
    const allocBytes = (bytes) => {
        const len = bytes.length;
        if (len === 0) return [0, 0];
        const p = malloc(len);
        Module.HEAPU8.set(bytes, p);
        return [p, len];
    };

    /* Live view over a region of the Emscripten heap. */
    globalThis.sdlImageKmpHeapBytes = (ptr, len) => Module.HEAPU8.subarray(ptr, ptr + len);

    const setRet = (f) => { globalThis[f] = Module['_' + f]; };

    /* ------------------------------------------------------------------
     * Core
     * ------------------------------------------------------------------ */
    setRet('sdl_image_kmp_Version');
    globalThis.sdl_image_kmp_GetError = () => readCString(Module._sdl_image_kmp_GetError());
    setRet('sdl_image_kmp_ClearError');

    /* ------------------------------------------------------------------
     * Decoding (byte buffer in, RGBA8888 surface handle out)
     * ------------------------------------------------------------------ */
    globalThis.sdl_image_kmp_Load = (bytes, type) => {
        const [p, len] = allocBytes(bytes);
        const t = allocStr(type);
        const r = Module._sdl_image_kmp_Load(p, len, t);
        freeStr(t);
        if (p) free(p);
        return r;
    };

    globalThis.sdl_image_kmp_LoadXPM = (text, rgb888) => {
        const p = allocStr(text);
        const r = Module._sdl_image_kmp_LoadXPM(p, rgb888 ? 1 : 0);
        freeStr(p);
        return r;
    };

    globalThis.sdl_image_kmp_LoadSizedSVG = (bytes, width, height) => {
        const [p, len] = allocBytes(bytes);
        const r = Module._sdl_image_kmp_LoadSizedSVG(p, len, width, height);
        if (p) free(p);
        return r;
    };

    globalThis.sdl_image_kmp_Is = (bytes, type) => {
        const [p, len] = allocBytes(bytes);
        const t = allocStr(type);
        const r = Module._sdl_image_kmp_Is(p, len, t);
        freeStr(t);
        if (p) free(p);
        return r;
    };

    /* ------------------------------------------------------------------
     * Surface accessors
     * ------------------------------------------------------------------ */
    setRet('sdl_image_kmp_SurfaceWidth');
    setRet('sdl_image_kmp_SurfaceHeight');
    setRet('sdl_image_kmp_SurfacePitch');
    setRet('sdl_image_kmp_SurfacePixels');
    globalThis.sdl_image_kmp_SurfaceFree = (surface) => {
        if (surface) Module._sdl_image_kmp_SurfaceFree(surface);
    };

    /* ------------------------------------------------------------------
     * Animations
     * ------------------------------------------------------------------ */
    globalThis.sdl_image_kmp_LoadAnimation = (bytes, type) => {
        const [p, len] = allocBytes(bytes);
        const t = allocStr(type);
        const r = Module._sdl_image_kmp_LoadAnimation(p, len, t);
        freeStr(t);
        if (p) free(p);
        return r;
    };
    setRet('sdl_image_kmp_AnimationWidth');
    setRet('sdl_image_kmp_AnimationHeight');
    setRet('sdl_image_kmp_AnimationCount');
    setRet('sdl_image_kmp_AnimationFrameSurface');
    setRet('sdl_image_kmp_AnimationFrameDelay');
    globalThis.sdl_image_kmp_AnimationFree = (animation) => {
        if (animation) Module._sdl_image_kmp_AnimationFree(animation);
    };

    /* ------------------------------------------------------------------
     * Encoding (pixels in, malloc'd buffer out; length in R32[0])
     * ------------------------------------------------------------------ */
    globalThis.sdl_image_kmp_Encode = (bytes, w, h, pitch, type, quality) => {
        const [p] = allocBytes(bytes);
        const t = allocStr(type);
        const lenPtr = malloc(4);
        const ptr = Module._sdl_image_kmp_Encode(p, w, h, pitch, t, quality, lenPtr);
        R32[0] = Module.HEAP32[lenPtr >> 2];
        free(lenPtr);
        freeStr(t);
        if (p) free(p);
        return ptr;
    };

    globalThis.sdl_image_kmp_EncodeAnimation = (bytes, w, h, count, delays, type) => {
        const [p] = allocBytes(bytes);
        const t = allocStr(type);
        const delaysPtr = malloc(delays.length * 4);
        Module.HEAP32.set(delays, delaysPtr >> 2);
        const lenPtr = malloc(4);
        const ptr = Module._sdl_image_kmp_EncodeAnimation(
            p, w, h, count, delaysPtr, t, lenPtr);
        R32[0] = Module.HEAP32[lenPtr >> 2];
        free(lenPtr);
        free(delaysPtr);
        freeStr(t);
        if (p) free(p);
        return ptr;
    };

    setRet('sdl_image_kmp_Free');

    return Module;
}
