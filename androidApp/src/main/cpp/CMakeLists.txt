cmake_minimum_required(VERSION 3.22.1)
project(llm_server_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# GPU backend: Vulkan runs on the widest range of Android GPUs (no vendor-specific
# OpenCL driver needed). Building it requires the LunarG Vulkan SDK on the HOST machine
# (glslc + SPIRV-Headers, to compile the compute shaders at build time) - set
# -DGGML_VULKAN=ON (and point CMAKE_PREFIX_PATH / Vulkan_INCLUDE_DIR at the SDK) once
# that's installed. Left off by default so a plain NDK install can build CPU-only.
option(GGML_VULKAN "llama.cpp: enable Vulkan backend (needs Vulkan SDK on host)" OFF)
option(GGML_OPENCL "llama.cpp: enable OpenCL backend" OFF)

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp submodule is missing. Run: git submodule update --init --recursive")
endif()

# Enable multimodal (mtmd) library for vision/audio support.
set(LLAMA_BUILD_MTMD  ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)

# Disable video support (requires ffmpeg, not available on Android NDK)
set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp llama_cpp_build)

add_library(llama_bridge SHARED llama_bridge.cpp)

target_include_directories(llama_bridge PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/tools/mtmd
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama_bridge
    llama
    ggml
    mtmd
    ${log-lib}
    ${android-lib}
)
