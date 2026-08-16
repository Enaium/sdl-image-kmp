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
# SDL3 is compiled statically into libsdl_image_jni on Linux and Windows,
# giving the process two SDL3 copies (sdl-kmp's libsdl_jni carries its own).
# This is required because the SDL3 symbols cannot be imported from
# libsdl_jni at runtime on those platforms:
#
#  - Windows: libsdl_jni.dll does not export the SDL3 symbols at all (SDL3
#    is compiled into it as a static library; only the Java_* JNI entry
#    points are exported), so linking against it or its import library
#    leaves every SDL3 reference undefined.
#  - Linux: the JVM dlopens libsdl_jni.so with RTLD_LOCAL, so its SDL3
#    exports are not in the global scope that a second library's undefined
#    symbols resolve against; dlopen/lazy binding fails at the first use.
#
# Everything within one copy is self-consistent (SDL_image surfaces are
# created and operated on by this library's own JNI functions), and
# SDL_IOStream objects have no magic validation, so sdl-kmp streams passed
# to IMG_Load_IO and friends still work across copies. Only sdl-kmp objects
# with magic validation (e.g. a renderer passed to IMG_LoadTexture, or a
# surface passed to IMG_Save*) are rejected, exactly like the TTF bindings'
# foreign surfaces.
#
# On macOS the SDL3 symbols stay undefined in libsdl_image_jni and are
# resolved at runtime from libsdl_jni: the dylib is linked with
# -undefined dynamic_lookup, and dyld's flat lookup finds the symbols in the
# already-loaded libsdl_jni (which exports them, since SDL3 is built with
# default visibility there).
if(NOT DEFINED SDL_SOURCE_DIR)
    set(SDL_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../includes/SDL")
endif()
if(NOT DEFINED SDLIMAGE_SOURCE_DIR)
    set(SDLIMAGE_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../includes/SDL_image")
endif()

# On Linux/Windows SDL3 is compiled first (below) so its SDL3::Headers/
# SDL3::SDL3 targets exist for SDL_image's CMake; on macOS fake targets are
# provided and SDL3 is resolved at runtime from libsdl_jni.
if(WIN32 OR (UNIX AND NOT APPLE))
    set(SDL_SHARED OFF CACHE BOOL "" FORCE)
    set(SDL_STATIC ON CACHE BOOL "" FORCE)
    set(SDL_TEST OFF CACHE BOOL "" FORCE)
    set(SDL_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(SDL_TESTS OFF CACHE BOOL "" FORCE)
    set(SDL_INSTALL OFF CACHE BOOL "" FORCE)
    add_subdirectory(${SDL_SOURCE_DIR} SDL)
else()
    # Fake SDL3 targets so SDL_image's CMake configures without building SDL3.
    add_library(SDL3::Headers INTERFACE IMPORTED)
    set_target_properties(SDL3::Headers PROPERTIES
        INTERFACE_INCLUDE_DIRECTORIES "${SDL_SOURCE_DIR}/include")
    add_library(SDL3::SDL3 INTERFACE IMPORTED)
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_VENDORED ON CACHE BOOL "" FORCE)
set(SDLIMAGE_INSTALL OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_SAMPLES OFF CACHE BOOL "" FORCE)
set(SDLIMAGE_TESTS OFF CACHE BOOL "" FORCE)
# AVIF (libavif/dav1d/aom) and JXL are large vendored dependencies; the other
# formats (PNG, JPG via stb_image, WEBP, TIF, GIF, BMP, CUR/ICO, LBM, PCX,
# PNM, QOI, SVG, TGA, XCF, XPM, XV, ANI) cover the common use cases.
set(SDLIMAGE_AVIF OFF CACHE BOOL "" FORCE)
# libtiff's FindCMath probes pow with check_symbol_exists, which cannot
# resolve the symbol at configure time on cross-compiled targets; pow lives
# either in libc (glibc 2.34+) or in libm, which libtiff links via
# CMath_LIBRARY anyway, so pre-seed the check's cache variable.
set(CMath_HAVE_LIBC_POW TRUE CACHE BOOL "pow is available in libc" FORCE)
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
# on macOS/Linux (see the top of this file); that also skips the interface
# libraries, so the vendored archives are merged in explicitly
# (merge_archives.cmake, globbed at build time) into a single archive that
# is linked by file path.
set(SDLIMAGE_JNI_MERGED_ARCHIVE "${CMAKE_CURRENT_BINARY_DIR}/libSDL3_image_merged.a")
add_custom_command(
    OUTPUT ${SDLIMAGE_JNI_MERGED_ARCHIVE}
    COMMAND ${CMAKE_COMMAND}
        -DAR=${CMAKE_AR}
        -DRANLIB=${CMAKE_RANLIB}
        -DOUTPUT=${SDLIMAGE_JNI_MERGED_ARCHIVE}
        "-DINPUTS=$<TARGET_FILE:SDL3_image-static>"
        "-DINPUT_GLOB_RECURSE=${CMAKE_CURRENT_BINARY_DIR}/SDL_image/external/*.a"
        -P ${CMAKE_CURRENT_SOURCE_DIR}/../sdl-image-kmp/native/merge_archives.cmake
    DEPENDS SDL3_image-static
    COMMENT "Merging SDL3_image static library and vendored dependencies for JNI"
    COMMAND_EXPAND_LISTS
)
add_custom_target(sdl_image_jni_merged DEPENDS ${SDLIMAGE_JNI_MERGED_ARCHIVE})

target_link_libraries(sdl_image_jni PRIVATE ${SDLIMAGE_JNI_MERGED_ARCHIVE})
add_dependencies(sdl_image_jni sdl_image_jni_merged)

if(WIN32 OR (UNIX AND NOT APPLE))
    # SDL3 was compiled above; link it statically (second copy, see the top
    # of this file). SDL3-static's interface pulls in the system libraries
    # it needs.
    target_link_libraries(sdl_image_jni PRIVATE SDL3-static)
elseif(APPLE)
    # Resolve the SDL3 symbols at runtime from libsdl_jni (loaded first, see
    # Jni.kt) instead of failing the link.
    target_link_options(sdl_image_jni PRIVATE -Wl,-undefined,dynamic_lookup)
    # The ImageIO backend (IMG_ImageIO.m) and the Objective-C runtime.
    target_link_libraries(sdl_image_jni PRIVATE
        "-framework ApplicationServices"
        "-framework Foundation"
        objc)
endif()

if(WIN32)
    set_target_properties(sdl_image_jni PROPERTIES PREFIX "")
endif()
