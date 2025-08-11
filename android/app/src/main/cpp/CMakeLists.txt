cmake_minimum_required(VERSION 3.22.1)
project(torch)
include(FetchContent)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_C_STANDARD 11)

# Optimization flags for release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG -flto")
    set(CMAKE_C_FLAGS_RELEASE "-O3 -DNDEBUG -flto")
    message(STATUS "Release build: Enabling O3 optimization and LTO")
elseif(CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_CXX_FLAGS_DEBUG "-O0 -g")
    set(CMAKE_C_FLAGS_DEBUG "-O0 -g")
    message(STATUS "Debug build: Disabling optimization, enabling debug symbols")
endif()

# Set build options for Android - NOT standalone
set(USE_STANDALONE OFF)
set(BUILD_STORMLIB OFF)
set(ENABLE_ASAN OFF)

# Game support options
set(BUILD_SM64 ON)
set(BUILD_MK64 ON)
set(BUILD_SF64 ON)
set(BUILD_FZERO ON)
set(BUILD_MARIO_ARTIST ON)
set(BUILD_NAUDIO ON)

# Add the main source directory
# Current path: android/app/src/main/cpp
# Need to go up 5 levels to reach project root
message(STATUS "CMAKE_CURRENT_SOURCE_DIR: ${CMAKE_CURRENT_SOURCE_DIR}")

# Try different approaches to find the root
set(TORCH_ROOT_CANDIDATES
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../.."
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../.."
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../.."
)

foreach(candidate ${TORCH_ROOT_CANDIDATES})
    get_filename_component(abs_path "${candidate}" ABSOLUTE)
    message(STATUS "Checking candidate: ${abs_path}")
    if(EXISTS "${abs_path}/lib" AND EXISTS "${abs_path}/src")
        set(TORCH_ROOT ${abs_path})
        message(STATUS "Found TORCH_ROOT at: ${TORCH_ROOT}")
        break()
    endif()
endforeach()

if(NOT DEFINED TORCH_ROOT)
    message(FATAL_ERROR "Could not find TORCH_ROOT. Checked paths: ${TORCH_ROOT_CANDIDATES}")
endif()

message(STATUS "Final TORCH_ROOT: ${TORCH_ROOT}")
message(STATUS "Looking for binarytools at: ${TORCH_ROOT}/lib/binarytools")

include_directories(${TORCH_ROOT})
include_directories(${TORCH_ROOT}/lib)
include_directories(${TORCH_ROOT}/src)

# Collect source files (excluding main.cpp since we'll have our own JNI entry point)
file(GLOB_RECURSE CXX_FILES 
    ${TORCH_ROOT}/src/*.cpp 
    ${TORCH_ROOT}/src/**/*.cpp 
    ${TORCH_ROOT}/lib/strhash64/*.cpp
)

# Make sure we include preprocessing code
file(GLOB PREPROCESS_FILES ${TORCH_ROOT}/src/preprocess/*.cpp)
list(APPEND CXX_FILES ${PREPROCESS_FILES})
file(GLOB C_FILES 
    ${TORCH_ROOT}/src/*.c 
    ${TORCH_ROOT}/src/**/*.c 
    ${TORCH_ROOT}/lib/libmio0/*.c 
    ${TORCH_ROOT}/lib/libyay0/*.c
)

# Remove the original main.cpp and any standalone-specific files
list(FILTER CXX_FILES EXCLUDE REGEX "${TORCH_ROOT}/src/main.cpp")

# Note: We don't include libgfxd since that's only for standalone builds

# Add our JNI wrapper
set(JNI_SOURCES
    torch_jni.cpp
)

# Build definitions for supported games
add_definitions(-DSM64_SUPPORT)
add_definitions(-DSF64_SUPPORT) 
add_definitions(-DMK64_SUPPORT)
add_definitions(-DFZERO_SUPPORT)
add_definitions(-DMARIO_ARTIST_SUPPORT)
add_definitions(-DNAUDIO_SUPPORT)

# NOT standalone mode
add_definitions(-USTANDALONE)

# Create the native library
add_library(torch SHARED
    ${JNI_SOURCES}
    ${CXX_FILES}
    ${C_FILES}
)

# Find required libraries
find_library(log-lib log)
find_library(android-lib android)

# Link libraries
target_link_libraries(torch
    ${log-lib}
    ${android-lib}
)

# Fetch yaml-cpp
set(YAML_CPP_BUILD_TOOLS OFF)
set(YAML_CPP_BUILD_TESTS OFF)
set(YAML_CPP_DISABLE_UNINSTALL ON)
FetchContent_Declare(
    yaml-cpp
    GIT_REPOSITORY https://github.com/jbeder/yaml-cpp.git
    GIT_TAG 2f86d13775d119edbb69af52e5f566fd65c6953b
)
FetchContent_MakeAvailable(yaml-cpp)

# Fetch spdlog
FetchContent_Declare(
    spdlog
    GIT_REPOSITORY https://github.com/gabime/spdlog.git
    GIT_TAG 7e635fca68d014934b4af8a1cf874f63989352b7
)
FetchContent_MakeAvailable(spdlog)

# Fetch tinyxml2
set(tinyxml2_BUILD_TESTING OFF)
FetchContent_Declare(
    tinyxml2
    GIT_REPOSITORY https://github.com/leethomason/tinyxml2.git
    GIT_TAG 10.0.0
)
FetchContent_MakeAvailable(tinyxml2)

# Initialize extra libraries list
set(EXTRA_LIBS "")

# Add bzip2 (critical dependency for ROM decompression)
set(BZIP2_DIR ${TORCH_ROOT}/lib/StormLib/src/bzip2)
if(EXISTS ${BZIP2_DIR})
    message(STATUS "Adding bzip2 library from StormLib")
    file(GLOB BZIP2_SOURCES 
        ${BZIP2_DIR}/blocksort.c
        ${BZIP2_DIR}/bzlib.c
        ${BZIP2_DIR}/compress.c
        ${BZIP2_DIR}/crctable.c
        ${BZIP2_DIR}/decompress.c
        ${BZIP2_DIR}/huffman.c
        ${BZIP2_DIR}/randtable.c
    )
    if(BZIP2_SOURCES)
        add_library(bzip2 STATIC ${BZIP2_SOURCES})
        target_include_directories(bzip2 PUBLIC ${BZIP2_DIR})
        list(APPEND EXTRA_LIBS bzip2)
        list(LENGTH BZIP2_SOURCES BZIP2_COUNT)
        message(STATUS "bzip2 library added with ${BZIP2_COUNT} source files")
    endif()
endif()

# Add miniz (compression library that might be needed)
if(EXISTS ${TORCH_ROOT}/lib/miniz)
    message(STATUS "Adding miniz library")
    file(GLOB MINIZ_SOURCES ${TORCH_ROOT}/lib/miniz/*.c)
    if(MINIZ_SOURCES)
        add_library(miniz STATIC ${MINIZ_SOURCES})
        target_include_directories(miniz PUBLIC ${TORCH_ROOT}/lib/miniz)
        list(APPEND EXTRA_LIBS miniz)
    endif()
endif()

# Fetch GSL (Guidelines Support Library)
FetchContent_Declare(
    GSL
    GIT_REPOSITORY https://github.com/Microsoft/GSL.git
    GIT_TAG a3534567187d2edc428efd3f13466ff75fe5805c
)
FetchContent_MakeAvailable(GSL)

# Build BinaryTools inline
set(BINARYTOOLS_DIR ${TORCH_ROOT}/lib/binarytools)
if(EXISTS ${BINARYTOOLS_DIR})
    message(STATUS "Building BinaryTools inline from: ${BINARYTOOLS_DIR}")
    file(GLOB BINARYTOOLS_SOURCES ${BINARYTOOLS_DIR}/*.cpp)
    if(BINARYTOOLS_SOURCES)
        add_library(BinaryTools STATIC ${BINARYTOOLS_SOURCES})
        target_include_directories(BinaryTools PUBLIC ${BINARYTOOLS_DIR})
        list(LENGTH BINARYTOOLS_SOURCES BINARYTOOLS_COUNT)
        message(STATUS "BinaryTools built with ${BINARYTOOLS_COUNT} source files")
    else()
        message(FATAL_ERROR "No source files found in BinaryTools directory")
    endif()
else()
    message(FATAL_ERROR "BinaryTools directory not found at: ${BINARYTOOLS_DIR}")
endif()

# Build N64Graphics inline  
set(N64GRAPHICS_DIR ${TORCH_ROOT}/lib/n64graphics)
if(EXISTS ${N64GRAPHICS_DIR})
    message(STATUS "Building N64Graphics inline from: ${N64GRAPHICS_DIR}")
    file(GLOB N64GRAPHICS_SOURCES ${N64GRAPHICS_DIR}/*.c)
    if(N64GRAPHICS_SOURCES)
        add_library(N64Graphics STATIC ${N64GRAPHICS_SOURCES})
        target_include_directories(N64Graphics PUBLIC ${N64GRAPHICS_DIR})
        list(LENGTH N64GRAPHICS_SOURCES N64GRAPHICS_COUNT)
        message(STATUS "N64Graphics built with ${N64GRAPHICS_COUNT} source files")
    else()
        message(FATAL_ERROR "No source files found in N64Graphics directory")
    endif()
else()
    message(FATAL_ERROR "N64Graphics directory not found at: ${N64GRAPHICS_DIR}")
endif()

# Add zlib (often needed with bzip2)
find_library(ZLIB_LIBRARY z)
if(ZLIB_LIBRARY)
    message(STATUS "Found system zlib: ${ZLIB_LIBRARY}")
    list(APPEND EXTRA_LIBS ${ZLIB_LIBRARY})
else()
    message(STATUS "System zlib not found")
endif()

target_link_libraries(torch 
    yaml-cpp
    spdlog::spdlog
    tinyxml2
    GSL
    BinaryTools 
    N64Graphics
    ${EXTRA_LIBS}
)