cmake_minimum_required(VERSION 3.16)

project(sdl-image-kmp-jni C CXX)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# The ImageIO backend (IMG_ImageIO.m) is only compiled on Apple platforms;
# MinGW has no Objective-C compiler.
if(APPLE)
    enable_language(OBJC)
endif()

# Builds the JNI shared library (libsdl_image_jni) for the JVM target.
#
# SDL3 is NOT linked: a single SDL3 instance per process is required, because
# SDL3 validates its objects (SDL_Texture, SDL_Surface, ...) with magic
# numbers against its own object registry - a second copy would reject every
# object created by the first. SDL_image (with the vendored zlib/libpng/
# libwebp/libtiff and the stb-based loaders) is compiled statically, and its
# SDL3 symbols are resolved at runtime from libsdl_jni, the JNI library of
# the sdl-kmp JVM artifact:
#
#  - macOS/Linux: the symbols stay undefined in libsdl_image_jni and are
#    resolved at dlopen time from libsdl_jni, which exports them (SDL3 is
#    compiled with default visibility there). libsdl_jni must therefore be
#    loaded first; the Kotlin Jni object enforces this.
#  - Windows: MinGW's linker refuses undefined symbols in DLLs, so the
#    build links the actual libsdl_jni.dll (extracted from the sdl-kmp JVM
#    artifact by the Gradle build and passed as SDL_JNI_DLL): its exports
#    become import entries and resolve at load time. When SDL_JNI_DLL is not
#    provided (local developer builds without the sdl-kmp jar), SDL3 is
#    compiled and linked in as a fallback (two copies: works for standalone
#    use, but cross-copy SDL3 objects are rejected).
if(NOT DEFINED SDL_SOURCE_DIR)
    set(SDL_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../includes/SDL")
endif()
if(NOT DEFINED SDLIMAGE_SOURCE_DIR)
    set(SDLIMAGE_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../includes/SDL_image")
endif()

# Fake SDL3 targets so SDL_image's CMake configures without building SDL3.
add_library(SDL3::Headers INTERFACE IMPORTED)
set_target_properties(SDL3::Headers PROPERTIES
    INTERFACE_INCLUDE_DIRECTORIES "${SDL_SOURCE_DIR}/include")
add_library(SDL3::SDL3 INTERFACE IMPORTED)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_VENDORED ON CACHE BOOL "" FORCE)
set(SDLIMAGE_INSTALL OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_SAMPLES OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_TESTS OFF CACHE BOOL "" FORCE)
# AVIF (libavif/dav1d/aom) and JXL are large vendored dependencies; the other
# formats (PNG, JPG via stb_image, WEBP, TIF, GIF, BMP, CUR/ICO, LBM, PCX,
# PNM, QOI, SVG, TGA, XCF, XPM, XV, ANI) cover the common use cases.
set(SDLIMAGE_AVIF OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_JXL OFF CACHE BOOL "" FORCE)
# Link the vendored format backends statically (instead of dlopen()ing system
# libraries at runtime) so libsdl_image_jni is fully self-contained.
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

add_library(sdl_image_jni SHARED
    jni_image.cpp
)
set_target_properties(sdl_image_jni PROPERTIES OUTPUT_NAME sdl_image_jni)

# SDL_image.h redefines main on Windows (via SDL_main.h); this is a library,
# not an executable, so tell SDL the main() handling is the application's
# business.
target_compile_definitions(sdl_image_jni PRIVATE SDL_MAIN_HANDLED)

target_include_directories(sdl_image_jni PRIVATE
    ${SDL_SOURCE_DIR}/include
    ${SDLIMAGE_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}
)

if(JNI_INCLUDE_DIR)
    target_include_directories(sdl_image_jni PRIVATE ${JNI_INCLUDE_DIR})
else()
    find_package(JNI REQUIRED)
    target_include_directories(sdl_image_jni PRIVATE ${JNI_INCLUDE_DIRS})
endif()
if(JNI_INCLUDE_DIR_PLATFORM)
    target_include_directories(sdl_image_jni PRIVATE ${JNI_INCLUDE_DIR_PLATFORM})
endif()

# SDL3_image-static's link interface propagates the vendored zlib/libpng/
# libwebp/libtiff archives and the ImageIO frameworks on Apple. The archive
# is referenced by file path (not as a target) so SDL3 stays out of the link
# (see the top of this file); that also skips the interface libraries, so the
# vendored archives are merged in explicitly (merge_archives.cmake, globbed
# at build time) into a single archive that is linked by file path, keeping
# SDL3 out of the link.
set(SDLIMAGE_JNI_MERGED_ARCHIVE "${CMAKE_CURRENT_BINARY_DIR}/libSDL3_image_merged.a")
add_custom_command(
    OUTPUT ${SDLIMAGE_JNI_MERGED_ARCHIVE}
    COMMAND ${CMAKE_COMMAND}
        -DAR=${CMAKE_AR}
        -DRANLIB=${CMAKE_RANLIB}
        -DOUTPUT=${SDLIMAGE_JNI_MERGED_ARCHIVE}
        "-DINPUTS=$<TARGET_FILE:SDL3_image-static>"
        "-DINPUT_GLOB=${CMAKE_CURRENT_BINARY_DIR}/SDL_image/external/*/*.a"
        -P ${CMAKE_CURRENT_SOURCE_DIR}/../sdl-image-kmp/native/merge_archives.cmake
    DEPENDS SDL3_image-static
    COMMENT "Merging SDL3_image static library and vendored dependencies for JNI"
    COMMAND_EXPAND_LISTS
)
add_custom_target(sdl_image_jni_merged DEPENDS ${SDLIMAGE_JNI_MERGED_ARCHIVE})

target_link_libraries(sdl_image_jni PRIVATE ${SDLIMAGE_JNI_MERGED_ARCHIVE})
add_dependencies(sdl_image_jni sdl_image_jni_merged)

if(WIN32 AND DEFINED SDL_JNI_DLL AND EXISTS "${SDL_JNI_DLL}")
    # Linking the DLL file itself makes ld build import entries for its
    # exports (the SDL3 symbols), resolving the undefined references.
    target_link_libraries(sdl_image_jni PRIVATE "${SDL_JNI_DLL}")
elseif(APPLE)
    # Resolve the SDL3 symbols at runtime from libsdl_jni (loaded first, see
    # Jni.kt) instead of failing the link.
    target_link_options(sdl_image_jni PRIVATE -Wl,-undefined,dynamic_lookup)
    # The ImageIO backend (IMG_ImageIO.m) and the Objective-C runtime.
    target_link_libraries(sdl_image_jni PRIVATE
        "-framework ApplicationServices"
        "-framework Foundation"
        objc)
elseif(UNIX AND NOT APPLE)
    # GNU ld allows undefined symbols in shared libraries by default; the
    # SDL3 symbols are resolved from libsdl_jni at dlopen time.
    target_link_libraries(sdl_image_jni PRIVATE -lm -ldl -lpthread)
elseif(WIN32)
    # Fallback (no SDL_JNI_DLL provided): compile and link SDL3 itself.
    set(SDL_SHARED OFF CACHE BOOL "" FORCE)
    set(SDL_STATIC ON CACHE BOOL "" FORCE)
    set(SDL_TEST OFF CACHE BOOL "" FORCE)
    set(SDL_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(SDL_TESTS OFF CACHE BOOL "" FORCE)
    set(SDL_INSTALL OFF CACHE BOOL "" FORCE)
    add_subdirectory(${SDL_SOURCE_DIR} SDL)
    target_link_libraries(sdl_image_jni PRIVATE SDL3-static)
endif()

if(WIN32)
    set_target_properties(sdl_image_jni PROPERTIES PREFIX "")
endif()
