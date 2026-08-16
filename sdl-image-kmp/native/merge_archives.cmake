# Copyright (c) 2026 Enaium
#
# Merges a list of static libraries into a single archive, so the Kotlin/Native
# cinterop task can embed one self-contained library (libSDL3_image.a) into the
# published klib. SDL_image itself only merges in-repo sources; the vendored
# zlib/libpng/libwebp/libtiff archives are linked via the target interface and
# would otherwise be lost (the cinterop -staticLibrary option embeds exactly
# one archive file).
#
# Usage: cmake -DAR=<ar> -DRANLIB=<ranlib> -DOUTPUT=<out.a>
#              [-DINPUTS="a.a;b.a;..."] [-DINPUT_GLOB="<glob pattern>"]
#              [-DINPUT_GLOB_RECURSE="<recursive glob pattern>"]
#              -P merge_archives.cmake
#
# INPUTS are explicit archives; INPUT_GLOB/INPUT_GLOB_RECURSE (CMake glob
# patterns, expanded recursively for the latter) are expanded at BUILD time —
# this runs inside a build-time custom command, so archives produced by other
# targets of the same build are already present.
#
# Object files are renamed with a per-archive prefix so identically named
# objects from different libraries never collide during extraction.

if(NOT DEFINED AR)
    set(AR ar)
endif()
if(NOT DEFINED RANLIB)
    set(RANLIB ranlib)
endif()
if(NOT DEFINED OUTPUT OR (NOT DEFINED INPUTS AND NOT DEFINED INPUT_GLOB AND NOT DEFINED INPUT_GLOB_RECURSE))
    message(FATAL_ERROR "OUTPUT and INPUTS/INPUT_GLOB are required")
endif()

foreach(PATTERN IN LISTS INPUT_GLOB)
    file(GLOB GLOB_MATCHES "${PATTERN}")
    list(APPEND INPUTS ${GLOB_MATCHES})
endforeach()
if(DEFINED INPUT_GLOB_RECURSE)
    file(GLOB_RECURSE RECURSE_MATCHES "${INPUT_GLOB_RECURSE}")
    list(APPEND INPUTS ${RECURSE_MATCHES})
endif()

# Deduplicate inputs by content: some vendored libraries install a
# compatibility copy alongside the real archive (e.g. libpng.a is a copy of
# libpng16.a); merging both would duplicate every global symbol.
set(SEEN_HASHES "")
set(DEDUPED_INPUTS "")
foreach(INPUT IN LISTS INPUTS)
    file(SHA256 "${INPUT}" INPUT_HASH)
    set(DUPLICATE FALSE)
    foreach(SEEN IN LISTS SEEN_HASHES)
        if(SEEN STREQUAL INPUT_HASH)
            set(DUPLICATE TRUE)
        endif()
    endforeach()
    if(NOT DUPLICATE)
        list(APPEND SEEN_HASHES "${INPUT_HASH}")
        list(APPEND DEDUPED_INPUTS "${INPUT}")
    endif()
endforeach()
set(INPUTS "${DEDUPED_INPUTS}")

set(TMP "${OUTPUT}.merge")
file(REMOVE_RECURSE "${TMP}")
file(MAKE_DIRECTORY "${TMP}")

set(ALL_OBJECTS "")
foreach(INPUT IN LISTS INPUTS)
    if(NOT EXISTS "${INPUT}")
        message(FATAL_ERROR "archive not found: ${INPUT}")
    endif()
    get_filename_component(ARCH_NAME "${INPUT}" NAME_WE)
    execute_process(
        COMMAND "${AR}" t "${INPUT}"
        OUTPUT_VARIABLE OBJS
        RESULT_VARIABLE RES
        ERROR_VARIABLE ERR
    )
    if(NOT RES EQUAL 0)
        message(FATAL_ERROR "ar t failed for ${INPUT}: ${ERR}")
    endif()
    string(REPLACE "\n" ";" OBJ_LIST "${OBJS}")
    foreach(OBJ IN LISTS OBJ_LIST)
        if(OBJ STREQUAL "" OR OBJ STREQUAL "__.SYMDEF SORTED")
            continue()
        endif()
        set(RENAMED "${ARCH_NAME}__${OBJ}")
        execute_process(
            COMMAND "${AR}" x "${INPUT}" "${OBJ}"
            WORKING_DIRECTORY "${TMP}"
            RESULT_VARIABLE RES
            ERROR_VARIABLE ERR
        )
        if(NOT RES EQUAL 0)
            message(FATAL_ERROR "ar x failed for ${OBJ} from ${INPUT}: ${ERR}")
        endif()
        if(NOT "${RENAMED}" STREQUAL "${OBJ}")
            file(RENAME "${TMP}/${OBJ}" "${TMP}/${RENAMED}")
        endif()
        list(APPEND ALL_OBJECTS "${RENAMED}")
    endforeach()
endforeach()

execute_process(
    COMMAND "${AR}" qc "${OUTPUT}" ${ALL_OBJECTS}
    WORKING_DIRECTORY "${TMP}"
    RESULT_VARIABLE RES
    ERROR_VARIABLE ERR
)
if(NOT RES EQUAL 0)
    message(FATAL_ERROR "ar qc failed: ${ERR}")
endif()

execute_process(
    COMMAND "${RANLIB}" "${OUTPUT}"
    RESULT_VARIABLE RES
    ERROR_VARIABLE ERR
)
if(NOT RES EQUAL 0)
    message(FATAL_ERROR "ranlib failed: ${ERR}")
endif()

file(REMOVE_RECURSE "${TMP}")
