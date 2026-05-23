cmake_minimum_required(VERSION 3.20)
project(OpenZenNative LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

if(NOT DEFINED ENV{JAVA_HOME})
    message(FATAL_ERROR "JAVA_HOME must be set so we can locate jni.h / jvmti.h")
endif()

set(JDK_INCLUDE "$ENV{JAVA_HOME}/include")
include_directories(${JDK_INCLUDE} ${JDK_INCLUDE}/win32)

if(NOT WIN32)
    message(FATAL_ERROR "OpenZen native build only supports Windows")
endif()

add_compile_definitions(
    UNICODE
    _UNICODE
    WIN32_LEAN_AND_MEAN
    NOMINMAX
)

if(MSVC)
    add_compile_options(/W3 /permissive- /utf-8 /EHsc)
endif()

add_subdirectory(dll)
add_subdirectory(loader)
