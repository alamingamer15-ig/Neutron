cmake_minimum_required(VERSION 3.22.1)
project(neutron_native LANGUAGES C CXX)
include(FetchContent)
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# All native targets use the shared Android C++ runtime.
if(ANDROID AND NOT ANDROID_STL STREQUAL "c++_shared")
    message(FATAL_ERROR "Neutron requires ANDROID_STL=c++_shared")
endif()

set(ZYDIS_BUILD_SHARED_LIB OFF CACHE BOOL "" FORCE)
set(ZYDIS_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(ZYDIS_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(ZYDIS_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ZYDIS_BUILD_DOXYGEN OFF CACHE BOOL "" FORCE)
set(ZYDIS_BUILD_MAN OFF CACHE BOOL "" FORCE)
set(ZYDIS_FEATURE_DECODER ON CACHE BOOL "" FORCE)
set(ZYDIS_FEATURE_FORMATTER ON CACHE BOOL "" FORCE)
set(ZYDIS_FEATURE_ENCODER OFF CACHE BOOL "" FORCE)
set(ZYDIS_FEATURE_KNC OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    zydis
    GIT_REPOSITORY https://github.com/zyantific/zydis.git
    GIT_TAG v4.1.1
    GIT_SHALLOW TRUE
    GIT_SUBMODULES_RECURSE TRUE
)
FetchContent_MakeAvailable(zydis)

# Oboe supplies Android's low-latency native audio output.
set(OBOE_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(OBOE_BUILD_SAMPLES OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    oboe
    GIT_REPOSITORY https://github.com/google/oboe.git
    GIT_TAG 1.9.3
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(oboe)

add_library(neutron_native SHARED
    src/zydis_decoder.cpp
    src/audio_jni.cpp
    src/terminal_output_jni.c
    ../core/audio/src/audio_output.cpp
    ../core/elf/src/elf_parser.cpp
    ../core/elf/src/elf_validator.cpp
    ../core/elf/src/elf_loader.cpp
    ../core/memory/src/guest_memory.cpp
    ../core/process/src/initial_stack.cpp
    src/logging/logger.cpp
    src/neutron_jni.cpp
)
target_include_directories(neutron_native PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../core/audio/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../core/elf/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../core/memory/include
    ${CMAKE_CURRENT_SOURCE_DIR}/../core/process/include
)
target_link_libraries(neutron_native PRIVATE Zydis oboe android dl)
target_compile_options(neutron_native PRIVATE -Wall -Wextra)
