cmake_minimum_required(VERSION 3.20)
project(stella_client LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(stella_client
    src/main.cpp
)

target_compile_definitions(stella_client PRIVATE UNICODE _UNICODE NOMINMAX WIN32_LEAN_AND_MEAN)

if(MSVC)
    target_compile_options(stella_client PRIVATE /W4 /permissive- /utf-8)
else()
    target_compile_options(stella_client PRIVATE -Wall -Wextra)
endif()
