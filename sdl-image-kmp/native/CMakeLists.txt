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

# Builds SDL_image (with the vendored zlib/libpng/libwebp/libtiff) as a static
# library for the Kotlin/Native targets, and copies it to
# SDLIMAGE_KMP_OUTPUT_DIR/libSDL3_image.a where the image.def cinterop embeds
# it into the produced klib with -staticLibrary.
#
# SDL3 itself is NOT compiled: SDL_image only needs the SDL3 headers, and the
# SDL3 symbols are resolved at the consumer's final link from libSDL3.a, which
# the sdl-kmp klib embeds (the consumer always links it, since the
# sdl-image-kmp bindings use the cn.enaium.sdl types).
#
# The vendored dependency archives (zlib/libpng/libwebp/libtiff) are merged
# INTO libSDL3_image.a (see merge_archives.cmake) so the klib is
# self-contained: the cinterop -staticLibrary option embeds exactly one
# archive file, and SDL_image's own CMake only keeps the vendored libraries
# as separate archives linked through the target interface. The format
# backends are configured with SDLIMAGE_*_SHARED=OFF so they link the
# vendored libraries statically instead of dlopen()ing system copies at
# runtime.
cmake_minimum_required(VERSION 3.21)
project(sdl-image-kmp-native C)

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

# Fake SDL3 targets so SDL_image's CMake configures without building SDL3.
# SDL3::Headers provides the include directory; SDL3::SDL3 is never linked.
add_library(SDL3::Headers INTERFACE IMPORTED)
set_target_properties(SDL3::Headers PROPERTIES
    INTERFACE_INCLUDE_DIRECTORIES "${SDL_SOURCE_DIR}/include")
add_library(SDL3::SDL3 INTERFACE IMPORTED)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_VENDORED ON CACHE BOOL "" FORCE)
set(SDLIMAGE_INSTALL OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_SAMPLES OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_TESTS OFF CACHE BOOL "" FORCE)
# AVIF needs the vendored libavif/dav1d/aom (large); JXL is off by default.
# Both would also be linked into the klib without correspondingly more
# coverage, so they are disabled. Everything else (PNG, JPG via stb_image,
# WEBP, TIF, GIF, BMP, CUR/ICO, LBM, PCX, PNM, QOI, SVG, TGA, XCF, XPM, XV,
# ANI) builds from the in-repo sources and the vendored zlib/libpng/libwebp/
# libtiff submodules of SDL_image.
set(SDLIMAGE_AVIF OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_JXL OFF CACHE BOOL "" FORCE)
# Link the vendored format backends statically (instead of dlopen()ing system
# libraries at runtime) so everything ends up inside the merged archive.
set(SDLIMAGE_PNG_SHARED OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_TIF_SHARED OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_WEBP_SHARED OFF CACHE BOOL "" FORCE)
# libpng builds its command-line tools on tvOS (upstream only excludes
# Android/iOS), where executables are MACOSX_BUNDLEs and cannot be installed.
set(PNG_TOOLS OFF CACHE BOOL "" FORCE)
# The vendored libwebp forces WEBP_BUILD_WEBPMUX=ON, whose command-line tool
# cannot be built on iOS/tvOS (MACOSX_BUNDLE executables); disable the whole
# WebP backend there (Apple's ImageIO backend reads WebP natively anyway).
if(CMAKE_SYSTEM_NAME STREQUAL "iOS" OR CMAKE_SYSTEM_NAME STREQUAL "tvOS")
    set(SDLIMAGE_WEBP OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(${SDLIMAGE_SOURCE_DIR} SDL_image)

# Collect the vendored static archives (zlib/libpng/libwebp/libtiff) built in
# the SDL_image binary tree, and merge them with SDL3_image-static into one
# self-contained archive for the cinterop task. The glob is expanded inside
# the build-time custom command (merge_archives.cmake), after the archives
# have been built.
set(SDLIMAGE_KMP_MERGED_ARCHIVE "${SDLIMAGE_KMP_OUTPUT_DIR}/libSDL3_image.a")
add_custom_command(
    OUTPUT ${SDLIMAGE_KMP_MERGED_ARCHIVE}
    COMMAND ${CMAKE_COMMAND} -E make_directory ${SDLIMAGE_KMP_OUTPUT_DIR}
    COMMAND ${CMAKE_COMMAND}
        -DAR=${CMAKE_AR}
        -DRANLIB=${CMAKE_RANLIB}
        -DOUTPUT=${SDLIMAGE_KMP_MERGED_ARCHIVE}
        "-DINPUTS=$<TARGET_FILE:SDL3_image-static>"
        "-DINPUT_GLOB=${CMAKE_CURRENT_BINARY_DIR}/SDL_image/external/*/*.a"
        -P ${CMAKE_CURRENT_SOURCE_DIR}/merge_archives.cmake
    DEPENDS SDL3_image-static
    COMMENT "Merging SDL3_image static library and vendored dependencies for Kotlin/Native"
    COMMAND_EXPAND_LISTS
)
add_custom_target(sdl-image-kmp-merged ALL DEPENDS ${SDLIMAGE_KMP_MERGED_ARCHIVE})
