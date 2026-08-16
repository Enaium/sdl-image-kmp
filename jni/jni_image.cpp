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
 * JNI bridge for the sdl-image-kmp JVM target.
 *
 * Every `external fun` on the Kotlin `cn.enaium.sdl.image.Jni` object maps 1:1
 * to a `Java_cn_enaium_sdl_image_Jni_<name>` function in this file (see the
 * naming convention in sdl-kmp's jni_bridge.h).
 *
 * The library statically links its own SDL3 and SDL_image (like sdl-kmp's
 * libsdl_jni), so IMG_Animation, SDL_Surface, SDL_Texture and the other
 * handles are passed across as opaque 64-bit pointers and the copies of SDL3
 * in the process do not interfere (see jni/CMakeLists.txt).
 *
 * Error convention: on failure the IMG functions set SDL's error string
 * (SDL_GetError). [Java_cn_enaium_sdl_image_Jni_getError] reads it from the
 * IMG-side copy of SDL3, which is the copy the error was written to.
 */

#include <jni.h>
#include <stdint.h>
#include <string>
#include <vector>

#include <SDL3/SDL.h>
#include <SDL3_image/SDL_image.h>

// JNI entry-point naming macro: every external fun on the Kotlin
// `cn.enaium.sdl.image.Jni` object maps to Java_cn.enaium.sdl.image_Jni_<name>.
#define IMGJNI_FUNC(ret) extern "C" JNIEXPORT ret JNICALL
#define IMGJNI_NAME(name) Java_cn_enaium_sdl_image_Jni_##name

// ---------------------------------------------------------------------------
// Marshaling helpers
// ---------------------------------------------------------------------------

static inline jstring img_jni_to_string(JNIEnv *env, const char *s) {
    return s ? env->NewStringUTF(s) : nullptr;
}

// Converts a Kotlin String to UTF-8. GetStringUTFChars() returns Modified
// UTF-8 (surrogate pairs are encoded in CESU-8), which is NOT what SDL_image
// expects, so the UTF-16 code units are converted properly (supplementary
// plane characters become 4-byte UTF-8 sequences).
static inline std::string img_jni_copy_string(JNIEnv *env, jstring s) {
    if (!s) return std::string();
    const jsize len = env->GetStringLength(s);
    if (len == 0) return std::string();
    const jchar *chars = env->GetStringChars(s, nullptr);
    if (!chars) return std::string();
    std::string out;
    out.reserve(static_cast<size_t>(len));
    for (jsize i = 0; i < len; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < len) {
            const uint32_t lo = chars[i + 1];
            if (lo >= 0xDC00 && lo <= 0xDFFF) {
                cp = 0x10000 + ((cp - 0xD800) << 10) + (lo - 0xDC00);
                ++i;
            }
        }
        if (cp < 0x80) {
            out.push_back(static_cast<char>(cp));
        } else if (cp < 0x800) {
            out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp < 0x10000) {
            out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    env->ReleaseStringChars(s, chars);
    return out;
}

static inline jintArray img_jni_new_jint_array(JNIEnv *env, const std::vector<jint> &values) {
    jintArray arr = env->NewIntArray(static_cast<jsize>(values.size()));
    if (arr && !values.empty()) {
        env->SetIntArrayRegion(arr, 0, static_cast<jsize>(values.size()), values.data());
    }
    return arr;
}

static inline jlongArray img_jni_new_jlong_array(JNIEnv *env, const std::vector<jlong> &values) {
    jlongArray arr = env->NewLongArray(static_cast<jsize>(values.size()));
    if (arr && !values.empty()) {
        env->SetLongArrayRegion(arr, 0, static_cast<jsize>(values.size()), values.data());
    }
    return arr;
}

static inline jfloatArray img_jni_new_jfloat_array(JNIEnv *env, const std::vector<jfloat> &values) {
    jfloatArray arr = env->NewFloatArray(static_cast<jsize>(values.size()));
    if (arr && !values.empty()) {
        env->SetFloatArrayRegion(arr, 0, static_cast<jsize>(values.size()), values.data());
    }
    return arr;
}

static inline jbyteArray img_jni_to_bytes(JNIEnv *env, const void *data, jsize len) {
    jbyteArray arr = env->NewByteArray(len);
    if (arr && data && len > 0) {
        env->SetByteArrayRegion(arr, 0, len, static_cast<const jbyte *>(data));
    }
    return arr;
}

static inline jlong img_jni_ptr(const void *p) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(p));
}

static inline void *img_jni_unptr(jlong ptr) {
    return reinterpret_cast<void *>(static_cast<intptr_t>(ptr));
}

static inline SDL_Surface *img_jni_surface(jlong ptr) {
    return static_cast<SDL_Surface *>(img_jni_unptr(ptr));
}

static inline SDL_Texture *img_jni_texture(jlong ptr) {
    return static_cast<SDL_Texture *>(img_jni_unptr(ptr));
}

static inline SDL_IOStream *img_jni_stream(jlong ptr) {
    return static_cast<SDL_IOStream *>(img_jni_unptr(ptr));
}

static inline SDL_Renderer *img_jni_renderer(jlong ptr) {
    return static_cast<SDL_Renderer *>(img_jni_unptr(ptr));
}

static inline SDL_GPUDevice *img_jni_device(jlong ptr) {
    return static_cast<SDL_GPUDevice *>(img_jni_unptr(ptr));
}

static inline SDL_GPUCopyPass *img_jni_copy_pass(jlong ptr) {
    return static_cast<SDL_GPUCopyPass *>(img_jni_unptr(ptr));
}

static inline IMG_Animation *img_jni_animation(jlong ptr) {
    return static_cast<IMG_Animation *>(img_jni_unptr(ptr));
}

static inline IMG_AnimationEncoder *img_jni_encoder(jlong ptr) {
    return static_cast<IMG_AnimationEncoder *>(img_jni_unptr(ptr));
}

static inline IMG_AnimationDecoder *img_jni_decoder(jlong ptr) {
    return static_cast<IMG_AnimationDecoder *>(img_jni_unptr(ptr));
}

// {surfacePtr, duration}: the frame decoded by IMG_GetAnimationDecoderFrame.
static inline jlongArray img_jni_decoder_frame(JNIEnv *env, SDL_Surface *surface, Uint64 duration) {
    return img_jni_new_jlong_array(env, {img_jni_ptr(surface), static_cast<jlong>(duration)});
}

// ---------------------------------------------------------------------------
// Core
// ---------------------------------------------------------------------------

IMGJNI_FUNC(jint) IMGJNI_NAME(version)(JNIEnv *, jclass) {
    return IMG_Version();
}

IMGJNI_FUNC(jstring) IMGJNI_NAME(getError)(JNIEnv *env, jclass) {
    return img_jni_to_string(env, SDL_GetError());
}

IMGJNI_FUNC(void) IMGJNI_NAME(clearError)(JNIEnv *, jclass) {
    SDL_ClearError();
}

// ---------------------------------------------------------------------------
// Loading (autodetected formats)
// ---------------------------------------------------------------------------

IMGJNI_FUNC(jlong) IMGJNI_NAME(load)(JNIEnv *env, jclass, jstring file) {
    std::string f = img_jni_copy_string(env, file);
    return img_jni_ptr(IMG_Load(f.c_str()));
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(loadIO)(JNIEnv *, jclass, jlong stream, jboolean closeio) {
    return img_jni_ptr(IMG_Load_IO(img_jni_stream(stream), closeio == JNI_TRUE));
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(loadTypedIO)(JNIEnv *env, jclass, jlong stream, jboolean closeio, jstring type) {
    std::string t = img_jni_copy_string(env, type);
    return img_jni_ptr(IMG_LoadTyped_IO(img_jni_stream(stream), closeio == JNI_TRUE, t.c_str()));
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(loadTexture)(JNIEnv *env, jclass, jlong renderer, jstring file) {
    std::string f = img_jni_copy_string(env, file);
    return img_jni_ptr(IMG_LoadTexture(img_jni_renderer(renderer), f.c_str()));
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(loadTextureIO)(JNIEnv *, jclass, jlong renderer, jlong stream, jboolean closeio) {
    return img_jni_ptr(IMG_LoadTexture_IO(img_jni_renderer(renderer), img_jni_stream(stream), closeio == JNI_TRUE));
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(loadTextureTypedIO)(JNIEnv *env, jclass, jlong renderer, jlong stream,
                                                   jboolean closeio, jstring type) {
    std::string t = img_jni_copy_string(env, type);
    return img_jni_ptr(IMG_LoadTextureTyped_IO(
        img_jni_renderer(renderer), img_jni_stream(stream), closeio == JNI_TRUE, t.c_str()));
}

// {texturePtr, width, height}.
static inline jlongArray img_jni_gpu_texture(JNIEnv *env, SDL_GPUTexture *texture, int w, int h) {
    return img_jni_new_jlong_array(env, {img_jni_ptr(texture), w, h});
}

IMGJNI_FUNC(jlongArray) IMGJNI_NAME(loadGPUTexture)(JNIEnv *env, jclass, jlong device, jlong copyPass,
                                                    jstring file) {
    std::string f = img_jni_copy_string(env, file);
    int w = 0, h = 0;
    SDL_GPUTexture *texture = IMG_LoadGPUTexture(
        img_jni_device(device), img_jni_copy_pass(copyPass), f.c_str(), &w, &h);
    return texture ? img_jni_gpu_texture(env, texture, w, h) : nullptr;
}

IMGJNI_FUNC(jlongArray) IMGJNI_NAME(loadGPUTextureIO)(JNIEnv *env, jclass, jlong device, jlong copyPass,
                                                      jlong stream, jboolean closeio) {
    int w = 0, h = 0;
    SDL_GPUTexture *texture = IMG_LoadGPUTexture_IO(
        img_jni_device(device), img_jni_copy_pass(copyPass), img_jni_stream(stream),
        closeio == JNI_TRUE, &w, &h);
    return texture ? img_jni_gpu_texture(env, texture, w, h) : nullptr;
}

IMGJNI_FUNC(jlongArray) IMGJNI_NAME(loadGPUTextureTypedIO)(JNIEnv *env, jclass, jlong device, jlong copyPass,
                                                           jlong stream, jboolean closeio, jstring type) {
    std::string t = img_jni_copy_string(env, type);
    int w = 0, h = 0;
    SDL_GPUTexture *texture = IMG_LoadGPUTextureTyped_IO(
        img_jni_device(device), img_jni_copy_pass(copyPass), img_jni_stream(stream),
        closeio == JNI_TRUE, t.c_str(), &w, &h);
    return texture ? img_jni_gpu_texture(env, texture, w, h) : nullptr;
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(getClipboardImage)(JNIEnv *, jclass) {
    return img_jni_ptr(IMG_GetClipboardImage());
}

// ---------------------------------------------------------------------------
// Format detection
// ---------------------------------------------------------------------------

#define IMG_IS_FN(NAME, CALL)                                       \
    IMGJNI_FUNC(jboolean) IMGJNI_NAME(NAME)(JNIEnv *, jclass, jlong stream) { \
        return CALL(img_jni_stream(stream)) ? JNI_TRUE : JNI_FALSE; \
    }

IMG_IS_FN(isANI, IMG_isANI)
IMG_IS_FN(isAVIF, IMG_isAVIF)
IMG_IS_FN(isCUR, IMG_isCUR)
IMG_IS_FN(isBMP, IMG_isBMP)
IMG_IS_FN(isGIF, IMG_isGIF)
IMG_IS_FN(isICO, IMG_isICO)
IMG_IS_FN(isJPG, IMG_isJPG)
IMG_IS_FN(isJXL, IMG_isJXL)
IMG_IS_FN(isLBM, IMG_isLBM)
IMG_IS_FN(isPCX, IMG_isPCX)
IMG_IS_FN(isPNG, IMG_isPNG)
IMG_IS_FN(isPNM, IMG_isPNM)
IMG_IS_FN(isQOI, IMG_isQOI)
IMG_IS_FN(isSVG, IMG_isSVG)
IMG_IS_FN(isTIF, IMG_isTIF)
IMG_IS_FN(isWEBP, IMG_isWEBP)
IMG_IS_FN(isXCF, IMG_isXCF)
IMG_IS_FN(isXPM, IMG_isXPM)
IMG_IS_FN(isXV, IMG_isXV)

// ---------------------------------------------------------------------------
// Typed loads
// ---------------------------------------------------------------------------

#define IMG_LOAD_IO_FN(NAME, CALL)                                                       \
    IMGJNI_FUNC(jlong) IMGJNI_NAME(NAME)(JNIEnv *, jclass, jlong stream) {               \
        return img_jni_ptr(CALL(img_jni_stream(stream)));                                 \
    }

IMG_LOAD_IO_FN(loadAVIF, IMG_LoadAVIF_IO)
IMG_LOAD_IO_FN(loadBMP, IMG_LoadBMP_IO)
IMG_LOAD_IO_FN(loadCUR, IMG_LoadCUR_IO)
IMG_LOAD_IO_FN(loadGIF, IMG_LoadGIF_IO)
IMG_LOAD_IO_FN(loadICO, IMG_LoadICO_IO)
IMG_LOAD_IO_FN(loadJPG, IMG_LoadJPG_IO)
IMG_LOAD_IO_FN(loadJXL, IMG_LoadJXL_IO)
IMG_LOAD_IO_FN(loadLBM, IMG_LoadLBM_IO)
IMG_LOAD_IO_FN(loadPCX, IMG_LoadPCX_IO)
IMG_LOAD_IO_FN(loadPNG, IMG_LoadPNG_IO)
IMG_LOAD_IO_FN(loadPNM, IMG_LoadPNM_IO)
IMG_LOAD_IO_FN(loadQOI, IMG_LoadQOI_IO)
IMG_LOAD_IO_FN(loadSVG, IMG_LoadSVG_IO)
IMG_LOAD_IO_FN(loadTGA, IMG_LoadTGA_IO)
IMG_LOAD_IO_FN(loadTIF, IMG_LoadTIF_IO)
IMG_LOAD_IO_FN(loadWEBP, IMG_LoadWEBP_IO)
IMG_LOAD_IO_FN(loadXCF, IMG_LoadXCF_IO)
IMG_LOAD_IO_FN(loadXPM, IMG_LoadXPM_IO)
IMG_LOAD_IO_FN(loadXV, IMG_LoadXV_IO)

IMGJNI_FUNC(jlong) IMGJNI_NAME(loadSizedSVG)(JNIEnv *, jclass, jlong stream, jint width, jint height) {
    return img_jni_ptr(IMG_LoadSizedSVG_IO(img_jni_stream(stream), width, height));
}

static inline SDL_Surface *img_jni_read_xpm(JNIEnv *env, jobjectArray xpm, bool rgb888) {
    const jsize count = env->GetArrayLength(xpm);
    std::vector<std::string> lines;
    std::vector<char *> ptrs;
    lines.reserve(static_cast<size_t>(count));
    ptrs.reserve(static_cast<size_t>(count) + 1);
    for (jsize i = 0; i < count; ++i) {
        auto str = static_cast<jstring>(env->GetObjectArrayElement(xpm, i));
        lines.push_back(img_jni_copy_string(env, str));
        env->DeleteLocalRef(str);
        ptrs.push_back(const_cast<char *>(lines.back().c_str()));
    }
    ptrs.push_back(nullptr);
    return rgb888 ? IMG_ReadXPMFromArrayToRGB888(ptrs.data()) : IMG_ReadXPMFromArray(ptrs.data());
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(readXPMFromArray)(JNIEnv *env, jclass, jobjectArray xpm) {
    return img_jni_ptr(img_jni_read_xpm(env, xpm, false));
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(readXPMFromArrayToRGB888)(JNIEnv *env, jclass, jobjectArray xpm) {
    return img_jni_ptr(img_jni_read_xpm(env, xpm, true));
}

// ---------------------------------------------------------------------------
// Saving
// ---------------------------------------------------------------------------

#define IMG_SAVE_FN(NAME, CALL)                                                          \
    IMGJNI_FUNC(jboolean) IMGJNI_NAME(NAME)(JNIEnv *env, jclass, jlong surface,          \
                                            jstring file) {                              \
        std::string f = img_jni_copy_string(env, file);                                  \
        return CALL(img_jni_surface(surface), f.c_str()) ? JNI_TRUE : JNI_FALSE;         \
    }

#define IMG_SAVE_IO_FN(NAME, CALL)                                                       \
    IMGJNI_FUNC(jboolean) IMGJNI_NAME(NAME)(JNIEnv *, jclass, jlong surface, jlong dst, \
                                            jboolean closeio) {                          \
        return CALL(img_jni_surface(surface), img_jni_stream(dst),                       \
                    closeio == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;                         \
    }

#define IMG_SAVE_QUALITY_FN(NAME, CALL)                                                  \
    IMGJNI_FUNC(jboolean) IMGJNI_NAME(NAME)(JNIEnv *env, jclass, jlong surface,          \
                                            jstring file, jint quality) {                \
        std::string f = img_jni_copy_string(env, file);                                  \
        return CALL(img_jni_surface(surface), f.c_str(), quality) ? JNI_TRUE : JNI_FALSE;\
    }

#define IMG_SAVE_QUALITY_IO_FN(NAME, CALL)                                               \
    IMGJNI_FUNC(jboolean) IMGJNI_NAME(NAME)(JNIEnv *, jclass, jlong surface, jlong dst, \
                                            jboolean closeio, jint quality) {            \
        return CALL(img_jni_surface(surface), img_jni_stream(dst),                       \
                    closeio == JNI_TRUE, quality) ? JNI_TRUE : JNI_FALSE;                \
    }

IMGJNI_FUNC(jboolean) IMGJNI_NAME(save)(JNIEnv *env, jclass, jlong surface, jstring file) {
    std::string f = img_jni_copy_string(env, file);
    return IMG_Save(img_jni_surface(surface), f.c_str()) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(saveTypedIO)(JNIEnv *env, jclass, jlong surface, jlong dst,
                                               jboolean closeio, jstring type) {
    std::string t = img_jni_copy_string(env, type);
    return IMG_SaveTyped_IO(img_jni_surface(surface), img_jni_stream(dst),
                            closeio == JNI_TRUE, t.c_str()) ? JNI_TRUE : JNI_FALSE;
}

IMG_SAVE_QUALITY_FN(saveAVIF, IMG_SaveAVIF)
IMG_SAVE_QUALITY_IO_FN(saveAVIFIO, IMG_SaveAVIF_IO)
IMG_SAVE_FN(saveBMP, IMG_SaveBMP)
IMG_SAVE_IO_FN(saveBMPIO, IMG_SaveBMP_IO)
IMG_SAVE_FN(saveCUR, IMG_SaveCUR)
IMG_SAVE_IO_FN(saveCURIO, IMG_SaveCUR_IO)
IMG_SAVE_FN(saveGIF, IMG_SaveGIF)
IMG_SAVE_IO_FN(saveGIFIO, IMG_SaveGIF_IO)
IMG_SAVE_FN(saveICO, IMG_SaveICO)
IMG_SAVE_IO_FN(saveICOIO, IMG_SaveICO_IO)
IMG_SAVE_QUALITY_FN(saveJPG, IMG_SaveJPG)
IMG_SAVE_QUALITY_IO_FN(saveJPGIO, IMG_SaveJPG_IO)
IMG_SAVE_FN(savePNG, IMG_SavePNG)
IMG_SAVE_IO_FN(savePNGIO, IMG_SavePNG_IO)
IMG_SAVE_FN(saveTGA, IMG_SaveTGA)
IMG_SAVE_IO_FN(saveTGAIO, IMG_SaveTGA_IO)

IMGJNI_FUNC(jboolean) IMGJNI_NAME(saveWEBP)(JNIEnv *env, jclass, jlong surface, jstring file, jfloat quality) {
    std::string f = img_jni_copy_string(env, file);
    return IMG_SaveWEBP(img_jni_surface(surface), f.c_str(), quality) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(saveWEBPIO)(JNIEnv *, jclass, jlong surface, jlong dst,
                                              jboolean closeio, jfloat quality) {
    return IMG_SaveWEBP_IO(img_jni_surface(surface), img_jni_stream(dst),
                           closeio == JNI_TRUE, quality) ? JNI_TRUE : JNI_FALSE;
}

// ---------------------------------------------------------------------------
// Animations
// ---------------------------------------------------------------------------

IMGJNI_FUNC(jlong) IMGJNI_NAME(loadAnimation)(JNIEnv *env, jclass, jstring file) {
    std::string f = img_jni_copy_string(env, file);
    return img_jni_ptr(IMG_LoadAnimation(f.c_str()));
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(loadAnimationIO)(JNIEnv *, jclass, jlong stream, jboolean closeio) {
    return img_jni_ptr(IMG_LoadAnimation_IO(img_jni_stream(stream), closeio == JNI_TRUE));
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(loadAnimationTypedIO)(JNIEnv *env, jclass, jlong stream,
                                                     jboolean closeio, jstring type) {
    std::string t = img_jni_copy_string(env, type);
    return img_jni_ptr(IMG_LoadAnimationTyped_IO(img_jni_stream(stream), closeio == JNI_TRUE, t.c_str()));
}

#define IMG_LOAD_ANIMATION_IO_FN(NAME, CALL)                                              \
    IMGJNI_FUNC(jlong) IMGJNI_NAME(NAME)(JNIEnv *, jclass, jlong stream) {                \
        return img_jni_ptr(CALL(img_jni_stream(stream)));                                  \
    }

IMG_LOAD_ANIMATION_IO_FN(loadANIAnimationIO, IMG_LoadANIAnimation_IO)
IMG_LOAD_ANIMATION_IO_FN(loadAPNGAnimationIO, IMG_LoadAPNGAnimation_IO)
IMG_LOAD_ANIMATION_IO_FN(loadAVIFAnimationIO, IMG_LoadAVIFAnimation_IO)
IMG_LOAD_ANIMATION_IO_FN(loadGIFAnimationIO, IMG_LoadGIFAnimation_IO)
IMG_LOAD_ANIMATION_IO_FN(loadWEBPAnimationIO, IMG_LoadWEBPAnimation_IO)

IMGJNI_FUNC(void) IMGJNI_NAME(freeAnimation)(JNIEnv *, jclass, jlong anim) {
    IMG_FreeAnimation(img_jni_animation(anim));
}

IMGJNI_FUNC(jint) IMGJNI_NAME(animationWidth)(JNIEnv *, jclass, jlong anim) {
    return img_jni_animation(anim)->w;
}

IMGJNI_FUNC(jint) IMGJNI_NAME(animationHeight)(JNIEnv *, jclass, jlong anim) {
    return img_jni_animation(anim)->h;
}

IMGJNI_FUNC(jint) IMGJNI_NAME(animationCount)(JNIEnv *, jclass, jlong anim) {
    return img_jni_animation(anim)->count;
}

IMGJNI_FUNC(jlongArray) IMGJNI_NAME(animationFrames)(JNIEnv *env, jclass, jlong anim) {
    IMG_Animation *a = img_jni_animation(anim);
    std::vector<jlong> frames;
    frames.reserve(static_cast<size_t>(a->count));
    for (int i = 0; i < a->count; ++i) {
        frames.push_back(img_jni_ptr(a->frames[i]));
    }
    return img_jni_new_jlong_array(env, frames);
}

IMGJNI_FUNC(jintArray) IMGJNI_NAME(animationDelays)(JNIEnv *env, jclass, jlong anim) {
    IMG_Animation *a = img_jni_animation(anim);
    std::vector<jint> delays;
    delays.reserve(static_cast<size_t>(a->count));
    for (int i = 0; i < a->count; ++i) {
        delays.push_back(a->delays[i]);
    }
    return img_jni_new_jint_array(env, delays);
}

#define IMG_SAVE_ANIMATION_FN(NAME, CALL)                                                 \
    IMGJNI_FUNC(jboolean) IMGJNI_NAME(NAME)(JNIEnv *env, jclass, jlong anim,              \
                                            jstring file) {                               \
        std::string f = img_jni_copy_string(env, file);                                   \
        return CALL(img_jni_animation(anim), f.c_str()) ? JNI_TRUE : JNI_FALSE;           \
    }

#define IMG_SAVE_ANIMATION_IO_FN(NAME, CALL)                                              \
    IMGJNI_FUNC(jboolean) IMGJNI_NAME(NAME)(JNIEnv *, jclass, jlong anim, jlong dst,      \
                                            jboolean closeio) {                           \
        return CALL(img_jni_animation(anim), img_jni_stream(dst),                         \
                    closeio == JNI_TRUE) ? JNI_TRUE : JNI_FALSE;                          \
    }

#define IMG_SAVE_ANIMATION_QUALITY_IO_FN(NAME, CALL)                                      \
    IMGJNI_FUNC(jboolean) IMGJNI_NAME(NAME)(JNIEnv *, jclass, jlong anim, jlong dst,      \
                                            jboolean closeio, jint quality) {             \
        return CALL(img_jni_animation(anim), img_jni_stream(dst),                         \
                    closeio == JNI_TRUE, quality) ? JNI_TRUE : JNI_FALSE;                 \
    }

IMGJNI_FUNC(jboolean) IMGJNI_NAME(saveAnimation)(JNIEnv *env, jclass, jlong anim, jstring file) {
    std::string f = img_jni_copy_string(env, file);
    return IMG_SaveAnimation(img_jni_animation(anim), f.c_str()) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(saveAnimationTypedIO)(JNIEnv *env, jclass, jlong anim, jlong dst,
                                                        jboolean closeio, jstring type) {
    std::string t = img_jni_copy_string(env, type);
    return IMG_SaveAnimationTyped_IO(img_jni_animation(anim), img_jni_stream(dst),
                                     closeio == JNI_TRUE, t.c_str()) ? JNI_TRUE : JNI_FALSE;
}

IMG_SAVE_ANIMATION_IO_FN(saveANIAnimationIO, IMG_SaveANIAnimation_IO)
IMG_SAVE_ANIMATION_IO_FN(saveAPNGAnimationIO, IMG_SaveAPNGAnimation_IO)
IMG_SAVE_ANIMATION_QUALITY_IO_FN(saveAVIFAnimationIO, IMG_SaveAVIFAnimation_IO)
IMG_SAVE_ANIMATION_IO_FN(saveGIFAnimationIO, IMG_SaveGIFAnimation_IO)
IMG_SAVE_ANIMATION_QUALITY_IO_FN(saveWEBPAnimationIO, IMG_SaveWEBPAnimation_IO)

IMGJNI_FUNC(jlong) IMGJNI_NAME(createAnimatedCursor)(JNIEnv *, jclass, jlong anim, jint hotX, jint hotY) {
    return img_jni_ptr(IMG_CreateAnimatedCursor(img_jni_animation(anim), hotX, hotY));
}

// ---------------------------------------------------------------------------
// Streaming animation encoder / decoder
// ---------------------------------------------------------------------------

IMGJNI_FUNC(jlong) IMGJNI_NAME(createAnimationEncoder)(JNIEnv *env, jclass, jstring file) {
    std::string f = img_jni_copy_string(env, file);
    return img_jni_ptr(IMG_CreateAnimationEncoder(f.c_str()));
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(createAnimationEncoderIO)(JNIEnv *env, jclass, jlong dst,
                                                         jboolean closeio, jstring type) {
    std::string t = img_jni_copy_string(env, type);
    return img_jni_ptr(IMG_CreateAnimationEncoder_IO(img_jni_stream(dst), closeio == JNI_TRUE, t.c_str()));
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(createAnimationEncoderWithProperties)(JNIEnv *, jclass, jlong props) {
    return img_jni_ptr(IMG_CreateAnimationEncoderWithProperties(static_cast<SDL_PropertiesID>(props)));
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(addAnimationEncoderFrame)(JNIEnv *, jclass, jlong encoder,
                                                            jlong surface, jlong duration) {
    return IMG_AddAnimationEncoderFrame(
        img_jni_encoder(encoder), img_jni_surface(surface), static_cast<Uint64>(duration)) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(closeAnimationEncoder)(JNIEnv *, jclass, jlong encoder) {
    return IMG_CloseAnimationEncoder(img_jni_encoder(encoder)) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(createAnimationDecoder)(JNIEnv *env, jclass, jstring file) {
    std::string f = img_jni_copy_string(env, file);
    return img_jni_ptr(IMG_CreateAnimationDecoder(f.c_str()));
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(createAnimationDecoderIO)(JNIEnv *env, jclass, jlong src,
                                                         jboolean closeio, jstring type) {
    std::string t = img_jni_copy_string(env, type);
    return img_jni_ptr(IMG_CreateAnimationDecoder_IO(img_jni_stream(src), closeio == JNI_TRUE, t.c_str()));
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(createAnimationDecoderWithProperties)(JNIEnv *, jclass, jlong props) {
    return img_jni_ptr(IMG_CreateAnimationDecoderWithProperties(static_cast<SDL_PropertiesID>(props)));
}

IMGJNI_FUNC(jint) IMGJNI_NAME(getAnimationDecoderProperties)(JNIEnv *, jclass, jlong decoder) {
    return static_cast<jint>(IMG_GetAnimationDecoderProperties(img_jni_decoder(decoder)));
}

IMGJNI_FUNC(jlongArray) IMGJNI_NAME(getAnimationDecoderFrame)(JNIEnv *env, jclass, jlong decoder) {
    SDL_Surface *frame = nullptr;
    Uint64 duration = 0;
    if (!IMG_GetAnimationDecoderFrame(img_jni_decoder(decoder), &frame, &duration)) {
        return nullptr;
    }
    return img_jni_decoder_frame(env, frame, duration);
}

IMGJNI_FUNC(jint) IMGJNI_NAME(getAnimationDecoderStatus)(JNIEnv *, jclass, jlong decoder) {
    return static_cast<jint>(IMG_GetAnimationDecoderStatus(img_jni_decoder(decoder)));
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(resetAnimationDecoder)(JNIEnv *, jclass, jlong decoder) {
    return IMG_ResetAnimationDecoder(img_jni_decoder(decoder)) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(closeAnimationDecoder)(JNIEnv *, jclass, jlong decoder) {
    return IMG_CloseAnimationDecoder(img_jni_decoder(decoder)) ? JNI_TRUE : JNI_FALSE;
}

// ---------------------------------------------------------------------------
// Surfaces
//
// These wrap the SDL_Surface objects allocated by SDL_image's load functions
// into the sdl-kmp SDLSurface interface (see Long.toSDLSurface in the Kotlin
// bindings). The SDL calls are resolved at runtime from libsdl_jni, which
// owns the SDL3 instance the surfaces were created with.
// ---------------------------------------------------------------------------

IMGJNI_FUNC(jint) IMGJNI_NAME(surfaceWidth)(JNIEnv *, jclass, jlong surface) {
    return img_jni_surface(surface)->w;
}

IMGJNI_FUNC(jint) IMGJNI_NAME(surfaceHeight)(JNIEnv *, jclass, jlong surface) {
    return img_jni_surface(surface)->h;
}

IMGJNI_FUNC(jint) IMGJNI_NAME(surfaceFormat)(JNIEnv *, jclass, jlong surface) {
    return static_cast<jint>(img_jni_surface(surface)->format);
}

IMGJNI_FUNC(jint) IMGJNI_NAME(surfacePitch)(JNIEnv *, jclass, jlong surface) {
    return img_jni_surface(surface)->pitch;
}

IMGJNI_FUNC(jint) IMGJNI_NAME(surfaceColorspace)(JNIEnv *, jclass, jlong surface) {
    return static_cast<jint>(SDL_GetSurfaceColorspace(img_jni_surface(surface)));
}

IMGJNI_FUNC(jbyteArray) IMGJNI_NAME(surfacePixels)(JNIEnv *env, jclass, jlong surface) {
    SDL_Surface *s = img_jni_surface(surface);
    const int bytes = s->pitch * s->h;
    return img_jni_to_bytes(env, s->pixels, bytes);
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(lockSurface)(JNIEnv *, jclass, jlong surface) {
    return SDL_LockSurface(img_jni_surface(surface)) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(void) IMGJNI_NAME(unlockSurface)(JNIEnv *, jclass, jlong surface) {
    SDL_UnlockSurface(img_jni_surface(surface));
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(surfaceFillRect)(JNIEnv *env, jclass, jlong surface,
                                                   jintArray rect, jint r, jint g, jint b, jint a) {
    SDL_Surface *s = img_jni_surface(surface);
    SDL_Rect rct;
    SDL_Rect *rctPtr = nullptr;
    if (rect) {
        jint *elems = env->GetIntArrayElements(rect, nullptr);
        rct.x = elems[0];
        rct.y = elems[1];
        rct.w = elems[2];
        rct.h = elems[3];
        env->ReleaseIntArrayElements(rect, elems, JNI_ABORT);
        rctPtr = &rct;
    }
    return SDL_FillSurfaceRect(s, rctPtr, static_cast<Uint32>((r << 24) | (g << 16) | (b << 8) | a)) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(surfaceFillRects)(JNIEnv *env, jclass, jlong surface,
                                                    jintArray rects, jint r, jint g, jint b, jint a) {
    SDL_Surface *s = img_jni_surface(surface);
    jsize count = env->GetArrayLength(rects) / 4;
    std::vector<SDL_Rect> rct(static_cast<size_t>(count));
    jint *elems = env->GetIntArrayElements(rects, nullptr);
    for (jsize i = 0; i < count; i++) {
        rct[static_cast<size_t>(i)].x = elems[i * 4];
        rct[static_cast<size_t>(i)].y = elems[i * 4 + 1];
        rct[static_cast<size_t>(i)].w = elems[i * 4 + 2];
        rct[static_cast<size_t>(i)].h = elems[i * 4 + 3];
    }
    env->ReleaseIntArrayElements(rects, elems, JNI_ABORT);
    return SDL_FillSurfaceRects(s, rct.data(), count, static_cast<Uint32>((r << 24) | (g << 16) | (b << 8) | a)) ? JNI_TRUE : JNI_FALSE;
}

static inline bool img_jni_read_rect(JNIEnv *env, jintArray arr, SDL_Rect &out) {
    if (!arr) return false;
    jint *elems = env->GetIntArrayElements(arr, nullptr);
    out.x = elems[0];
    out.y = elems[1];
    out.w = elems[2];
    out.h = elems[3];
    env->ReleaseIntArrayElements(arr, elems, JNI_ABORT);
    return true;
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(surfaceBlit)(JNIEnv *env, jclass, jlong src, jintArray srcRect,
                                               jlong dst, jintArray dstRect) {
    SDL_Rect sr, dr;
    return SDL_BlitSurface(img_jni_surface(src),
                           img_jni_read_rect(env, srcRect, sr) ? &sr : nullptr,
                           img_jni_surface(dst),
                           img_jni_read_rect(env, dstRect, dr) ? &dr : nullptr) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(surfaceBlitScaled)(JNIEnv *env, jclass, jlong src, jintArray srcRect,
                                                     jlong dst, jintArray dstRect, jint scaleMode) {
    SDL_Rect sr, dr;
    return SDL_BlitSurfaceScaled(img_jni_surface(src),
                                 img_jni_read_rect(env, srcRect, sr) ? &sr : nullptr,
                                 img_jni_surface(dst),
                                 img_jni_read_rect(env, dstRect, dr) ? &dr : nullptr,
                                 static_cast<SDL_ScaleMode>(scaleMode)) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(surfaceSaveBMP)(JNIEnv *env, jclass, jlong surface, jstring path) {
    std::string p = img_jni_copy_string(env, path);
    return SDL_SaveBMP(img_jni_surface(surface), p.c_str()) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jlong) IMGJNI_NAME(convertSurface)(JNIEnv *, jclass, jlong surface, jint format) {
    return img_jni_ptr(SDL_ConvertSurface(img_jni_surface(surface), static_cast<SDL_PixelFormat>(format)));
}

IMGJNI_FUNC(void) IMGJNI_NAME(destroySurface)(JNIEnv *, jclass, jlong surface) {
    SDL_DestroySurface(img_jni_surface(surface));
}

// ---------------------------------------------------------------------------
// Textures
// ---------------------------------------------------------------------------

IMGJNI_FUNC(jint) IMGJNI_NAME(textureFormat)(JNIEnv *, jclass, jlong texture) {
    // SDL3 has no SDL_GetTextureFormat; the format is a struct field.
    return static_cast<jint>(img_jni_texture(texture)->format);
}

IMGJNI_FUNC(jint) IMGJNI_NAME(textureAccess)(JNIEnv *, jclass, jlong texture) {
    // SDL3 has no SDL_GetTextureAccess; the access is a texture property.
    SDL_PropertiesID props = SDL_GetTextureProperties(img_jni_texture(texture));
    return static_cast<jint>(SDL_GetNumberProperty(props, "SDL.texture.access", -1));
}

IMGJNI_FUNC(jfloatArray) IMGJNI_NAME(textureSize)(JNIEnv *env, jclass, jlong texture) {
    float w = 0, h = 0;
    SDL_GetTextureSize(img_jni_texture(texture), &w, &h);
    return img_jni_new_jfloat_array(env, {w, h});
}

IMGJNI_FUNC(jintArray) IMGJNI_NAME(textureColorMod)(JNIEnv *env, jclass, jlong texture) {
    Uint8 r = 0, g = 0, b = 0;
    SDL_GetTextureColorMod(img_jni_texture(texture), &r, &g, &b);
    return img_jni_new_jint_array(env, {r, g, b});
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(setTextureColorMod)(JNIEnv *, jclass, jlong texture,
                                                      jint r, jint g, jint b) {
    return SDL_SetTextureColorMod(img_jni_texture(texture), static_cast<Uint8>(r),
                                  static_cast<Uint8>(g), static_cast<Uint8>(b)) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jint) IMGJNI_NAME(textureAlphaMod)(JNIEnv *env, jclass, jlong texture) {
    Uint8 a = 0;
    SDL_GetTextureAlphaMod(img_jni_texture(texture), &a);
    return a;
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(setTextureAlphaMod)(JNIEnv *, jclass, jlong texture, jint a) {
    return SDL_SetTextureAlphaMod(img_jni_texture(texture), static_cast<Uint8>(a)) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jint) IMGJNI_NAME(textureBlendMode)(JNIEnv *, jclass, jlong texture) {
    SDL_BlendMode mode;
    SDL_GetTextureBlendMode(img_jni_texture(texture), &mode);
    return static_cast<jint>(mode);
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(setTextureBlendMode)(JNIEnv *, jclass, jlong texture, jint mode) {
    return SDL_SetTextureBlendMode(img_jni_texture(texture), static_cast<SDL_BlendMode>(mode)) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jint) IMGJNI_NAME(textureScaleMode)(JNIEnv *, jclass, jlong texture) {
    SDL_ScaleMode mode;
    SDL_GetTextureScaleMode(img_jni_texture(texture), &mode);
    return static_cast<jint>(mode);
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(setTextureScaleMode)(JNIEnv *, jclass, jlong texture, jint mode) {
    return SDL_SetTextureScaleMode(img_jni_texture(texture), static_cast<SDL_ScaleMode>(mode)) ? JNI_TRUE : JNI_FALSE;
}

IMGJNI_FUNC(jboolean) IMGJNI_NAME(textureUpdate)(JNIEnv *env, jclass, jlong texture, jintArray rect,
                                                 jbyteArray pixels, jint pitch) {
    SDL_Rect rct;
    SDL_Rect *rctPtr = nullptr;
    if (rect) {
        jint *elems = env->GetIntArrayElements(rect, nullptr);
        rct.x = elems[0];
        rct.y = elems[1];
        rct.w = elems[2];
        rct.h = elems[3];
        env->ReleaseIntArrayElements(rect, elems, JNI_ABORT);
        rctPtr = &rct;
    }
    jsize len = env->GetArrayLength(pixels);
    jbyte *elems = env->GetByteArrayElements(pixels, nullptr);
    const bool ok = SDL_UpdateTexture(img_jni_texture(texture), rctPtr, elems, pitch);
    env->ReleaseByteArrayElements(pixels, elems, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// {ByteArray pixels, Integer pitch}: the locked texture data.
IMGJNI_FUNC(jobjectArray) IMGJNI_NAME(textureLock)(JNIEnv *env, jclass, jlong texture, jintArray rect) {
    SDL_Rect rct;
    SDL_Rect *rctPtr = nullptr;
    if (rect) {
        jint *elems = env->GetIntArrayElements(rect, nullptr);
        rct.x = elems[0];
        rct.y = elems[1];
        rct.w = elems[2];
        rct.h = elems[3];
        env->ReleaseIntArrayElements(rect, elems, JNI_ABORT);
        rctPtr = &rct;
    }
    void *pixels = nullptr;
    int pitch = 0;
    if (!SDL_LockTexture(img_jni_texture(texture), rctPtr, &pixels, &pitch)) {
        return nullptr;
    }
    const int height = rctPtr ? rctPtr->h : 0;
    jbyteArray pixelsArr = img_jni_to_bytes(env, pixels, pitch * height);
    jclass intClass = env->FindClass("java/lang/Integer");
    jmethodID intCtor = env->GetMethodID(intClass, "<init>", "(I)V");
    jobject pitchObj = env->NewObject(intClass, intCtor, pitch);
    jclass objectClass = env->FindClass("java/lang/Object");
    jobjectArray result = env->NewObjectArray(2, objectClass, nullptr);
    if (result) {
        env->SetObjectArrayElement(result, 0, pixelsArr);
        env->SetObjectArrayElement(result, 1, pitchObj);
    }
    env->DeleteLocalRef(pitchObj);
    return result;
}

IMGJNI_FUNC(void) IMGJNI_NAME(textureUnlock)(JNIEnv *, jclass, jlong texture) {
    SDL_UnlockTexture(img_jni_texture(texture));
}

IMGJNI_FUNC(void) IMGJNI_NAME(destroyTexture)(JNIEnv *, jclass, jlong texture) {
    SDL_DestroyTexture(img_jni_texture(texture));
}

// ---------------------------------------------------------------------------
// GPU textures
// ---------------------------------------------------------------------------

IMGJNI_FUNC(void) IMGJNI_NAME(releaseGPUTexture)(JNIEnv *, jclass, jlong device, jlong texture) {
    SDL_ReleaseGPUTexture(img_jni_device(device),
                          static_cast<SDL_GPUTexture *>(img_jni_unptr(texture)));
}
