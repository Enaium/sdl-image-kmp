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

import org.khronos.webgl.Int8Array
import org.khronos.webgl.Int32Array
import kotlin.js.ExperimentalJsExport

/*
 * Declarations for the JS glue (sdl_image_glue.js, shipped in the
 * @enaium/sdl-image-kmp npm package) that wraps the Emscripten-compiled
 * SDL_image module. The glue is loaded and initialized by the host page BEFORE
 * the Kotlin module runs, so these globals always exist.
 *
 * Result-buffer convention: encoders write the produced length into the global
 * sdlImageKmpResultI32 array (slot 0), which the Kotlin caller reads after the
 * call.
 */

@OptIn(ExperimentalJsExport::class)
internal external val sdlImageKmpResultI32: Int32Array

/** Live read-only view over the Emscripten heap starting at [ptr]. */
internal external fun sdlImageKmpHeapBytes(ptr: Int, len: Int): Int8Array

/** Reads a single element of the global int result buffer. */
internal external fun sdlImageKmpResultI32Get(index: Int): Int

// =========================================================================
// Core
// =========================================================================

internal external fun sdl_image_kmp_Version(): Int
internal external fun sdl_image_kmp_GetError(): String?
internal external fun sdl_image_kmp_ClearError()

// =========================================================================
// Decoding
// =========================================================================

/** Decodes [bytes]; [type] is null for autodetection. Returns an RGBA8888 surface handle. */
internal external fun sdl_image_kmp_Load(bytes: Int8Array, type: String?): Int

/** Decodes XPM lines joined with '\n' ([rgb888] selects RGB888 instead of indexed). */
internal external fun sdl_image_kmp_LoadXPM(text: String, rgb888: Int): Int

/** Decodes an SVG scaled to [width]x[height]. */
internal external fun sdl_image_kmp_LoadSizedSVG(bytes: Int8Array, width: Int, height: Int): Int

/** Probes [bytes] for the [type] format (e.g. "png", "gif"). */
internal external fun sdl_image_kmp_Is(bytes: Int8Array, type: String?): Int

// =========================================================================
// Surface accessors (handles produced by the decoders)
// =========================================================================

internal external fun sdl_image_kmp_SurfaceWidth(surface: Int): Int
internal external fun sdl_image_kmp_SurfaceHeight(surface: Int): Int
internal external fun sdl_image_kmp_SurfacePitch(surface: Int): Int
internal external fun sdl_image_kmp_SurfacePixels(surface: Int): Int
internal external fun sdl_image_kmp_SurfaceFree(surface: Int)

// =========================================================================
// Animations
// =========================================================================

internal external fun sdl_image_kmp_LoadAnimation(bytes: Int8Array, type: String?): Int
internal external fun sdl_image_kmp_AnimationWidth(animation: Int): Int
internal external fun sdl_image_kmp_AnimationHeight(animation: Int): Int
internal external fun sdl_image_kmp_AnimationCount(animation: Int): Int
internal external fun sdl_image_kmp_AnimationFrameSurface(animation: Int, index: Int): Int
internal external fun sdl_image_kmp_AnimationFrameDelay(animation: Int, index: Int): Int
internal external fun sdl_image_kmp_AnimationFree(animation: Int)

// =========================================================================
// Encoding
// =========================================================================

/**
 * Encodes RGBA8888 [bytes] as [type]; [quality] applies to the lossy encoders.
 * Returns a heap pointer whose length is read from `sdlImageKmpResultI32[0]`,
 * or 0 on failure.
 */
internal external fun sdl_image_kmp_Encode(
    bytes: Int8Array,
    width: Int,
    height: Int,
    pitch: Int,
    type: String?,
    quality: Int,
): Int

/**
 * Encodes [count] RGBA8888 frames ([width]x[height], tightly packed in
 * [bytes]) with [delays] as an animation of [type]. Returns a heap pointer
 * whose length is read from `sdlImageKmpResultI32[0]`, or 0 on failure.
 */
internal external fun sdl_image_kmp_EncodeAnimation(
    bytes: Int8Array,
    width: Int,
    height: Int,
    count: Int,
    delays: Int32Array,
    type: String?,
): Int

internal external fun sdl_image_kmp_Free(ptr: Int)
