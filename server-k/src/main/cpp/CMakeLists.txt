cmake_minimum_required(VERSION 3.18.1)

# 项目名称
project("server-k")

# 设置C标准
set(CMAKE_C_STANDARD 99)
# 设置C++标准（用于OpenCV支持）
set(CMAKE_CXX_STANDARD 17)

# 添加编译选项
add_compile_options(-Wall -Wextra -O2)

# 检查OpenCV
find_package(OpenCV QUIET)
if(OpenCV_FOUND)
    message(STATUS "Found OpenCV: ${OpenCV_VERSION}")
    add_definitions(-DHAVE_OPENCV)
else()
    message(STATUS "OpenCV not found, using fallback image saving")
endif()

# 包含目录
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/common
)

# 通用源文件
set(COMMON_SOURCES
    common/log.c
    common/shared_memory.c
        common/common_jni.c
)

# 服务端源文件
set(SERVER_SOURCES
    ${COMMON_SOURCES}
    server/server.c
    client/client.c
)

# 客户端源文件
set(CLIENT_SOURCES
    ${COMMON_SOURCES}
    client/client.c
    client/main.c
)

# 创建服务端共享库
add_library(server-k SHARED ${SERVER_SOURCES})

# Android平台特定配置
if(ANDROID)
    find_library(log-lib log)
    target_link_libraries(server-k ${log-lib})
endif ()

# 在Android上，pthread功能内置在libc中，不需要单独链接
# 只在非Android平台上链接pthread库
if (NOT ANDROID)
    find_library(pthread-lib pthread)
    if (pthread-lib)
        target_link_libraries(server-k ${pthread-lib})
    else ()
        target_link_libraries(server-k pthread)
    endif ()
endif ()

# 设置输出目录
set_target_properties(server-k PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/libs"
)

# 如果不是Android平台，或者定义了BUILD_CLIENT，则构建客户端可执行文件
if(NOT ANDROID OR DEFINED BUILD_CLIENT)
    # 创建客户端可执行文件
    add_executable(k-client ${CLIENT_SOURCES})
    
    # 链接OpenCV（如果可用）
    if(OpenCV_FOUND)
        target_link_libraries(k-client ${OpenCV_LIBS})
    endif()
    
    # 链接pthread库
    if(NOT ANDROID)
        find_library(pthread-lib pthread)
        if(pthread-lib)
            target_link_libraries(k-client ${pthread-lib})
        else()
            target_link_libraries(k-client pthread)
        endif()
    endif()
    
    # Android平台特定配置
    if(ANDROID)
        find_library(log-lib log)
        target_link_libraries(k-client ${log-lib})
    endif()
    
    # 设置输出目录
    set_target_properties(k-client PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/bin"
    )
endif()

