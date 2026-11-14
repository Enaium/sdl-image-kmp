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
 * Flat, handle-based wrapper around SDL_image for Kotlin/Wasm.
 *
 * Kotlin/Wasm has no cinterop and no raw memory access, so this shim hides
 * every SDL_image pointer behind a 32-bit handle (wasm32 pointers) and works
 * exclusively on in-memory buffers:
 *
 *   - decoding takes a byte buffer (the JS glue copies it into the heap) and
 *     returns an RVBA8888 surface handle. Surfaces are converted to
 *     SDL_PIXELFORMAT_RGBA8888 so the Kotlin side can hand the pixels to
 *     sdl-kmp, which creates its own surface in its own Emscripten module
 *     (the two libraries are separate wasm instances, so pointers cannot
 *     cross; pixels can).
 *   - encoding takes RGBA8888 pixels and returns a malloc'd byte buffer the
 *     caller frees with sdl_image_kmp_Free().
 *   - file/stream I/O is deliberately absent: the glue has no file system of
 *     its own, so the Kotlin side reads and writes through sdl-kmp's
 *     SDLIOStream (the single Emscripten FS the app sees).
 */

#include <SDL3/SDL.h>
#include <SDL3_image/SDL_image.h>

/* Every decoded surface is converted to this layout before it leaves the shim. */
#define KMP_SURFACE_FORMAT SDL_PIXELFORMAT_RGBA8888

static SDL_Surface *kmp_to_rgba(SDL_Surface *surface) {
    if (!surface) {
        return NULL;
    }
    if (surface->format == KMP_SURFACE_FORMAT) {
        return surface;
    }
    SDL_Surface *converted = SDL_ConvertSurface(surface, KMP_SURFACE_FORMAT);
    SDL_DestroySurface(surface);
    return converted;
}

/* Wraps [data]/[len] in a read-only stream for the IMG_*_IO entry points. */
static SDL_IOStream *kmp_mem_stream(const void *data, int len) {
    return SDL_IOFromConstMem(data, (size_t) len);
}

/* `type` selects a typed loader; a null/empty type means autodetection. */
static SDL_Surface *kmp_load_mem(const void *data, int len, const char *type) {
    SDL_IOStream *stream = kmp_mem_stream(data, len);
    if (!stream) {
        return NULL;
    }
    if (type && *type) {
        return IMG_LoadTyped_IO(stream, true, type);
    }
    return IMG_Load_IO(stream, true);
}

int sdl_image_kmp_Version(void) {
    return IMG_Version();
}

const char *sdl_image_kmp_GetError(void) {
    return SDL_GetError();
}

void sdl_image_kmp_ClearError(void) {
    SDL_ClearError();
}

/* -------------------------------------------------------------------------
 * Decoding
 * ------------------------------------------------------------------------- */

int sdl_image_kmp_Load(const void *data, int len, const char *type) {
    return (int) (intptr_t) kmp_to_rgba(kmp_load_mem(data, len, type));
}

/* Splits `xpm` on newlines and hands the lines to IMG_ReadXPMFromArray. The
 * caller passes the array as one '\n'-joined string. */
int sdl_image_kmp_LoadXPM(const char *xpm, int rgb888) {
    if (!xpm) {
        return 0;
    }
    int lines = 1;
    for (const char *p = xpm; *p; ++p) {
        if (*p == '\n') {
            ++lines;
        }
    }
    char **array = (char **) SDL_malloc(sizeof(char *) * (size_t) (lines + 1));
    if (!array) {
        return 0;
    }
    char *copy = SDL_strdup(xpm);
    if (!copy) {
        SDL_free(array);
        return 0;
    }
    int index = 0;
    array[index++] = copy;
    for (char *p = copy; *p; ++p) {
        if (*p == '\n') {
            *p = '\0';
            array[index++] = p + 1;
        }
    }
    array[index] = NULL;
    SDL_Surface *surface = rgb888 ? IMG_ReadXPMFromArrayToRGB888(array)
                                  : IMG_ReadXPMFromArray(array);
    SDL_free(copy);
    SDL_free(array);
    return (int) (intptr_t) kmp_to_rgba(surface);
}

/* Decodes an SVG scaled to [width]x[height]. */
int sdl_image_kmp_LoadSizedSVG(const void *data, int len, int width, int height) {
    SDL_IOStream *stream = kmp_mem_stream(data, len);
    if (!stream) {
        return 0;
    }
    return (int) (intptr_t) kmp_to_rgba(
        IMG_LoadSizedSVG_IO(stream, width, height));
}

/* -------------------------------------------------------------------------
 * Surface accessors
 * ------------------------------------------------------------------------- */

int sdl_image_kmp_SurfaceWidth(int surface) {
    SDL_Surface *s = (SDL_Surface *) (intptr_t) surface;
    return s ? s->w : 0;
}

int sdl_image_kmp_SurfaceHeight(int surface) {
    SDL_Surface *s = (SDL_Surface *) (intptr_t) surface;
    return s ? s->h : 0;
}

int sdl_image_kmp_SurfacePitch(int surface) {
    SDL_Surface *s = (SDL_Surface *) (intptr_t) surface;
    return s ? s->pitch : 0;
}

const void *sdl_image_kmp_SurfacePixels(int surface) {
    SDL_Surface *s = (SDL_Surface *) (intptr_t) surface;
    return s ? s->pixels : NULL;
}

void sdl_image_kmp_SurfaceFree(int surface) {
    SDL_DestroySurface((SDL_Surface *) (intptr_t) surface);
}

/* -------------------------------------------------------------------------
 * Format detection
 * ------------------------------------------------------------------------- */

/* `type` is one of the IMG_is* suffixes (png, jpg, gif, webp, ...). */
int sdl_image_kmp_Is(const void *data, int len, const char *type) {
    SDL_IOStream *stream = kmp_mem_stream(data, len);
    if (!stream || !type) {
        return 0;
    }
    bool result = false;
    if (SDL_strcasecmp(type, "ani") == 0) result = IMG_isANI(stream);
    else if (SDL_strcasecmp(type, "avif") == 0) result = IMG_isAVIF(stream);
    else if (SDL_strcasecmp(type, "bmp") == 0) result = IMG_isBMP(stream);
    else if (SDL_strcasecmp(type, "cur") == 0) result = IMG_isCUR(stream);
    else if (SDL_strcasecmp(type, "gif") == 0) result = IMG_isGIF(stream);
    else if (SDL_strcasecmp(type, "ico") == 0) result = IMG_isICO(stream);
    else if (SDL_strcasecmp(type, "jpg") == 0) result = IMG_isJPG(stream);
    else if (SDL_strcasecmp(type, "jxl") == 0) result = IMG_isJXL(stream);
    else if (SDL_strcasecmp(type, "lbm") == 0) result = IMG_isLBM(stream);
    else if (SDL_strcasecmp(type, "pcx") == 0) result = IMG_isPCX(stream);
    else if (SDL_strcasecmp(type, "png") == 0) result = IMG_isPNG(stream);
    else if (SDL_strcasecmp(type, "pnm") == 0) result = IMG_isPNM(stream);
    else if (SDL_strcasecmp(type, "qoi") == 0) result = IMG_isQOI(stream);
    else if (SDL_strcasecmp(type, "svg") == 0) result = IMG_isSVG(stream);
    else if (SDL_strcasecmp(type, "tif") == 0) result = IMG_isTIF(stream);
    else if (SDL_strcasecmp(type, "webp") == 0) result = IMG_isWEBP(stream);
    else if (SDL_strcasecmp(type, "xcf") == 0) result = IMG_isXCF(stream);
    else if (SDL_strcasecmp(type, "xpm") == 0) result = IMG_isXPM(stream);
    else if (SDL_strcasecmp(type, "xv") == 0) result = IMG_isXV(stream);
    SDL_CloseIO(stream);
    return result ? 1 : 0;
}

/* -------------------------------------------------------------------------
 * Animations
 * ------------------------------------------------------------------------- */

int sdl_image_kmp_LoadAnimation(const void *data, int len, const char *type) {
    SDL_IOStream *stream = kmp_mem_stream(data, len);
    if (!stream) {
        return 0;
    }
    IMG_Animation *animation = (type && *type)
        ? IMG_LoadAnimationTyped_IO(stream, true, type)
        : IMG_LoadAnimation_IO(stream, true);
    return (int) (intptr_t) animation;
}

int sdl_image_kmp_AnimationWidth(int animation) {
    IMG_Animation *a = (IMG_Animation *) (intptr_t) animation;
    return a ? a->w : 0;
}

int sdl_image_kmp_AnimationHeight(int animation) {
    IMG_Animation *a = (IMG_Animation *) (intptr_t) animation;
    return a ? a->h : 0;
}

int sdl_image_kmp_AnimationCount(int animation) {
    IMG_Animation *a = (IMG_Animation *) (intptr_t) animation;
    return a ? a->count : 0;
}

/* Returns a NEW RVBA8888 surface for frame [index]; free it with
 * sdl_image_kmp_SurfaceFree(). */
int sdl_image_kmp_AnimationFrameSurface(int animation, int index) {
    IMG_Animation *a = (IMG_Animation *) (intptr_t) animation;
    if (!a || index < 0 || index >= a->count) {
        return 0;
    }
    SDL_Surface *frame = a->frames[index];
    if (!frame) {
        return 0;
    }
    return (int) (intptr_t) kmp_to_rgba(SDL_ConvertSurface(frame, KMP_SURFACE_FORMAT));
}

int sdl_image_kmp_AnimationFrameDelay(int animation, int index) {
    IMG_Animation *a = (IMG_Animation *) (intptr_t) animation;
    if (!a || index < 0 || index >= a->count) {
        return 0;
    }
    return a->delays[index];
}

void sdl_image_kmp_AnimationFree(int animation) {
    IMG_FreeAnimation((IMG_Animation *) (intptr_t) animation);
}

/* -------------------------------------------------------------------------
 * Encoding
 * ------------------------------------------------------------------------- */

/* Wraps caller pixels in a surface without copying them. */
static SDL_Surface *kmp_surface_from(const void *pixels, int w, int h, int pitch) {
    return SDL_CreateSurfaceFrom(w, h, KMP_SURFACE_FORMAT, (void *) pixels, pitch);
}

/* Drains a memory stream into a malloc'd buffer and returns its length via
 * [outLen]. The buffer must be released with sdl_image_kmp_Free(). */
static void *kmp_stream_take(SDL_IOStream *stream, int *outLen) {
    SDL_FlushIO(stream);
    Sint64 size = SDL_GetIOSize(stream);
    if (size <= 0) {
        SDL_CloseIO(stream);
        *outLen = 0;
        return NULL;
    }
    void *buffer = SDL_malloc((size_t) size);
    if (!buffer) {
        SDL_CloseIO(stream);
        *outLen = 0;
        return NULL;
    }
    SDL_SeekIO(stream, 0, SDL_IO_SEEK_SET);
    Sint64 read = SDL_ReadIO(stream, buffer, (size_t) size);
    SDL_CloseIO(stream);
    if (read <= 0) {
        SDL_free(buffer);
        *outLen = 0;
        return NULL;
    }
    *outLen = (int) read;
    return buffer;
}

/* Encodes RGBA8888 pixels as [type] ("png", "jpg", "webp", ...). [quality] is
 * only used by the lossy encoders (JPG 0..100, WebP 0..100). */
void *sdl_image_kmp_Encode(const void *pixels, int w, int h, int pitch,
                           const char *type, int quality, int *outLen) {
    *outLen = 0;
    if (!type || !*type) {
        return NULL;
    }
    SDL_Surface *surface = kmp_surface_from(pixels, w, h, pitch);
    if (!surface) {
        return NULL;
    }
    SDL_IOStream *stream = SDL_IOFromDynamicMem();
    if (!stream) {
        SDL_DestroySurface(surface);
        return NULL;
    }
    bool ok;
    if (SDL_strcasecmp(type, "jpg") == 0 || SDL_strcasecmp(type, "jpeg") == 0) {
        ok = IMG_SaveJPG_IO(surface, stream, false, quality);
    } else if (SDL_strcasecmp(type, "webp") == 0) {
        ok = IMG_SaveWEBP_IO(surface, stream, false, (float) quality);
    } else {
        ok = IMG_SaveTyped_IO(surface, stream, false, type);
    }
    SDL_DestroySurface(surface);
    if (!ok) {
        SDL_CloseIO(stream);
        return NULL;
    }
    return kmp_stream_take(stream, outLen);
}

/* Encodes [count] RGBA8888 frames ([w]x[h] each, tightly packed in [frames])
 * with [delays] (milliseconds) as an animation of [type]. */
void *sdl_image_kmp_EncodeAnimation(const void *frames, int w, int h, int count,
                                    const int *delays, const char *type, int *outLen) {
    *outLen = 0;
    if (!type || !*type || count <= 0 || !delays) {
        return NULL;
    }
    const int stride = w * h * 4;
    SDL_Surface **surfaces = (SDL_Surface **) SDL_malloc(sizeof(SDL_Surface *) * (size_t) count);
    if (!surfaces) {
        return NULL;
    }
    int built = 0;
    for (; built < count; ++built) {
        surfaces[built] = kmp_surface_from((const void *) ((const uint8_t *) frames + (size_t) built * stride), w, h, stride);
        if (!surfaces[built]) {
            break;
        }
    }
    void *result = NULL;
    if (built == count) {
        IMG_Animation animation;
        animation.w = w;
        animation.h = h;
        animation.count = count;
        animation.frames = surfaces;
        animation.delays = (int *) delays;
        SDL_IOStream *stream = SDL_IOFromDynamicMem();
        if (stream) {
            if (IMG_SaveAnimationTyped_IO(&animation, stream, false, type)) {
                result = kmp_stream_take(stream, outLen);
            } else {
                SDL_CloseIO(stream);
            }
        }
    }
    for (int i = 0; i < built; ++i) {
        SDL_DestroySurface(surfaces[i]);
    }
    SDL_free(surfaces);
    return result;
}

void sdl_image_kmp_Free(void *ptr) {
    SDL_free(ptr);
}
