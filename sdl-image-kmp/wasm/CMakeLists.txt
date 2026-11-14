# Copyright (c) 2026 Enaium
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in all
# copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.

# Builds SDL3 + SDL_image (with the vendored zlib/libpng/libwebp/libtiff) as
# static libraries for the Emscripten (wasmJs) target and merges them into a
# single archive. The Gradle linkWasmSdl task then links that archive together
# with sdl_image_wasm_shim.c (emcc) into sdl_image_wasm.js, which the wasmJs
# actuals drive through the JS glue.
#
# Unlike the Kotlin/Native build, SDL3 is compiled here too: the Kotlin/Wasm
# module plays the role of the final executable, so nothing else provides the
# SDL3 symbols.
cmake_minimum_required(VERSION 3.21)
project(sdl-image-kmp-wasm C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

if(NOT DEFINED SDL_SOURCE_DIR)
    set(SDL_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../includes/SDL")
endif()
if(NOT DEFINED SDLIMAGE_SOURCE_DIR)
    set(SDLIMAGE_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../includes/SDL_image")
endif()
if(NOT DEFINED SDLIMAGE_KMP_OUTPUT_DIR)
    set(SDLIMAGE_KMP_OUTPUT_DIR "${CMAKE_CURRENT_BINARY_DIR}/out")
endif()

# --- SDL3 -------------------------------------------------------------------
set(SDL_SHARED OFF CACHE BOOL "" FORCE)
set(SDL_STATIC ON CACHE BOOL "" FORCE)
set(SDL_TEST OFF CACHE BOOL "" FORCE)
set(SDL_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SDL_TESTS OFF CACHE BOOL "" FORCE)
set(SDL_INSTALL OFF CACHE BOOL "" FORCE)
set(SDL_INSTALL_DOCS OFF CACHE BOOL "" FORCE)
# The Kotlin/Wasm module drives its own loop and renders into a canvas; the
# unused subsystems only inflate the module.
set(SDL_DISABLE_INSTALL OFF CACHE BOOL "" FORCE)

add_subdirectory(${SDL_SOURCE_DIR} SDL)

# --- SDL_image --------------------------------------------------------------
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_VENDORED ON CACHE BOOL "" FORCE)
set(SDLIMAGE_INSTALL OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_SAMPLES OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_TESTS OFF CACHE BOOL "" FORCE)
# AVIF (libavif/dav1d/aom) and JXL are large vendored trees; the remaining
# formats cover the common cases.
set(SDLIMAGE_AVIF OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_JXL OFF CACHE BOOL "" FORCE)
# libtiff's FindCMath probes pow with check_symbol_exists, which cannot resolve
# the symbol at configure time on cross-compiled targets.
set(CMath_HAVE_LIBC_POW TRUE CACHE BOOL "pow is available in libc" FORCE)
# Link the vendored format backends statically (instead of dlopen()ing system
# copies at runtime) so everything ends up inside the merged archive.
set(SDLIMAGE_PNG_SHARED OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_TIF_SHARED OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_WEBP_SHARED OFF CACHE BOOL "" FORCE)
# libpng/WebP build command-line tools by default; they are useless here and
# their executables need a JS entry point Emscripten does not provide.
set(PNG_TOOLS OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_ANIM_UTILS OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_CWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_DWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_GIF2WEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_IMG2WEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_VWEBP OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBPMUX OFF CACHE BOOL "" FORCE)
set(WEBP_BUILD_WEBPINFO OFF CACHE BOOL "" FORCE)
# libwebp probes for CPU features with assembly/intrinsics that do not apply to
# wasm; the portable C paths are enough for image decoding.
set(WEBP_ENABLE_SIMD OFF CACHE BOOL "" FORCE)

add_subdirectory(${SDLIMAGE_SOURCE_DIR} SDL_image)

# --- merge into one self-contained archive ----------------------------------
# emcc links a single archive comfortably, and the Gradle task then passes one
# file path instead of a hand-maintained list of vendored archives.
set(SDLIMAGE_KMP_MERGED_ARCHIVE "${SDLIMAGE_KMP_OUTPUT_DIR}/libSDL3_image_wasm.a")
add_custom_command(
    OUTPUT ${SDLIMAGE_KMP_MERGED_ARCHIVE}
    COMMAND ${CMAKE_COMMAND} -E make_directory ${SDLIMAGE_KMP_OUTPUT_DIR}
    COMMAND ${CMAKE_COMMAND}
        -DAR=${CMAKE_AR}
        -DRANLIB=${CMAKE_RANLIB}
        -DOUTPUT=${SDLIMAGE_KMP_MERGED_ARCHIVE}
        "-DINPUTS=$<TARGET_FILE:SDL3-static>\;$<TARGET_FILE:SDL3_image-static>"
        "-DINPUT_GLOB_RECURSE=${CMAKE_CURRENT_BINARY_DIR}/SDL_image/external/*.a"
        -P ${CMAKE_CURRENT_SOURCE_DIR}/../native/merge_archives.cmake
    DEPENDS SDL3-static SDL3_image-static
    COMMENT "Merging SDL3, SDL_image and the vendored dependencies for wasm"
)
add_custom_target(sdl-image-kmp-wasm-merged ALL DEPENDS ${SDLIMAGE_KMP_MERGED_ARCHIVE})
