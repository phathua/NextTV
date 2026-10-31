cmake_minimum_required(VERSION 3.22.1)

project("vault-jni")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -Os -flto -fvisibility=hidden -fdata-sections -ffunction-sections")
set(CMAKE_SHARED_LINKER_FLAGS_RELEASE "${CMAKE_SHARED_LINKER_FLAGS_RELEASE} -Wl,--gc-sections -Wl,--exclude-libs,ALL -s -Wl,-z,max-page-size=16384")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

add_library(
    vault-jni
    SHARED
    vault-jni.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    vault-jni
    ${log-lib}
)
