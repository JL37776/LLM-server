cmake_minimum_required(VERSION 3.22.1)
project(llm_server_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# GPU backend: Vulkan runs on the widest range of Android GPUs (no vendor-specific
# OpenCL driver needed). Flip this off (and pass useGpu=false from Kotlin) to force CPU-only.
option(GGML_VULKAN "llama.cpp: enable Vulkan backend" ON)
option(GGML_OPENCL "llama.cpp: enable OpenCL backend" OFF)

if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/CMakeLists.txt")
    message(FATAL_ERROR
        "llama.cpp submodule is missing. Run: git submodule update --init --recursive")
endif()

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp llama_cpp_build)

add_library(llama_bridge SHARED llama_bridge.cpp)

target_include_directories(llama_bridge PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(llama_bridge
    llama
    ggml
    ${log-lib}
    ${android-lib}
)
