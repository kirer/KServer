cmake_minimum_required(VERSION 3.18.1)

# 项目名称
project("server-k")

# 设置C标准
set(CMAKE_C_STANDARD 99)
# 设置C++标准（用于OpenCV支持）
set(CMAKE_CXX_STANDARD 17)

# 添加编译选项
add_compile_options(-Wall -Wextra -O2)

# =============================================================================
# 构建选项定义
# =============================================================================
option(BUILD_ANDROID_LIBRARY "Build Android Library" ON)
option(BUILD_SERVER_ONLY "Build Server Only" OFF)
option(BUILD_CLIENT "Build Client" OFF)

# =============================================================================
# 构建选项验证 - 确保模式之间的互斥性
# =============================================================================
message(STATUS "🔍 Validating build configuration...")

# 计算激活的构建选项数量
set(ACTIVE_BUILD_MODES 0)
if(BUILD_ANDROID_LIBRARY)
    math(EXPR ACTIVE_BUILD_MODES "${ACTIVE_BUILD_MODES} + 1")
endif()
if(BUILD_SERVER_ONLY)
    math(EXPR ACTIVE_BUILD_MODES "${ACTIVE_BUILD_MODES} + 1")
endif()
if(BUILD_CLIENT)
    math(EXPR ACTIVE_BUILD_MODES "${ACTIVE_BUILD_MODES} + 1")
endif()

# 验证互斥性
if(ACTIVE_BUILD_MODES GREATER 1)
    message(WARNING "⚠️  Multiple build modes specified! Using priority order:")
    message(WARNING "   Priority 1: BUILD_CLIENT")
    message(WARNING "   Priority 2: BUILD_SERVER_ONLY") 
    message(WARNING "   Priority 3: BUILD_ANDROID_LIBRARY")
    
    # 应用优先级顺序
    if(BUILD_CLIENT)
        set(BUILD_ANDROID_LIBRARY OFF)
        set(BUILD_SERVER_ONLY OFF)
        message(STATUS "✅ Using CLIENT mode (highest priority)")
    elseif(BUILD_SERVER_ONLY)
        set(BUILD_ANDROID_LIBRARY OFF)
        message(STATUS "✅ Using SERVER_ONLY mode (medium priority)")
    endif()
elseif(ACTIVE_BUILD_MODES EQUAL 0)
    message(STATUS "📋 No build mode specified, defaulting to Android Library")
    set(BUILD_ANDROID_LIBRARY ON)
endif()

# 显示最终构建配置
message(STATUS "📋 Final Build Configuration:")
message(STATUS "  ├─ Android Library: ${BUILD_ANDROID_LIBRARY}")
message(STATUS "  ├─ Server Only: ${BUILD_SERVER_ONLY}")
message(STATUS "  ├─ Client: ${BUILD_CLIENT}")
message(STATUS "  └─ Platform: ${CMAKE_SYSTEM_NAME}")

# =============================================================================
# 模块化源文件组织
# =============================================================================
message(STATUS "📁 Organizing source files into logical groups...")

# 通用源文件 - 所有构建模式共享的核心功能
set(COMMON_SOURCES
    common/log.c
    common/shared_memory.c
)
message(STATUS "  ├─ COMMON_SOURCES: ${COMMON_SOURCES}")

# JNI源文件 - Android特定的JNI桥接代码
set(JNI_SOURCES
    common/common_jni.c
)
message(STATUS "  ├─ JNI_SOURCES: ${JNI_SOURCES}")

# 客户端通信源文件 - 客户端通信层
set(CLIENT_COMM_SOURCES
    client/client.c
)
message(STATUS "  ├─ CLIENT_COMM_SOURCES: ${CLIENT_COMM_SOURCES}")

# 服务器通信源文件 - 服务器通信层
set(SERVER_COMM_SOURCES
    server/server.c
)
message(STATUS "  ├─ SERVER_COMM_SOURCES: ${SERVER_COMM_SOURCES}")

# 客户端主程序源文件 - 客户端应用程序入口点
set(CLIENT_MAIN_SOURCES
    client/main.c
)
message(STATUS "  ├─ CLIENT_MAIN_SOURCES: ${CLIENT_MAIN_SOURCES}")

# 嵌入式源文件 - 客户端模式的嵌入式服务器文件
set(EMBEDDED_SOURCES)
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/client/embedded/k_server_dex.h")
    list(APPEND EMBEDDED_SOURCES client/embedded/k_server_dex.h)
endif()
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/client/embedded/libserver_k_so.h")
    list(APPEND EMBEDDED_SOURCES client/embedded/libserver_k_so.h)
endif()

if(EMBEDDED_SOURCES)
    message(STATUS "  ├─ EMBEDDED_SOURCES: ${EMBEDDED_SOURCES}")
    add_definitions(-DHAVE_EMBEDDED_FILES)
else()
    message(STATUS "  ├─ EMBEDDED_SOURCES: (none found)")
endif()

# =============================================================================
# 依赖检测和配置
# =============================================================================
message(STATUS "🔍 Detecting dependencies...")

# 检查OpenCV
find_package(OpenCV QUIET)
if(OpenCV_FOUND)
    message(STATUS "  ├─ OpenCV: Found version ${OpenCV_VERSION}")
    add_definitions(-DHAVE_OPENCV)
else()
    message(STATUS "  ├─ OpenCV: Not found, using fallback implementation")
endif()

# 包含目录
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/common
    ${CMAKE_CURRENT_SOURCE_DIR}/client
    ${CMAKE_CURRENT_SOURCE_DIR}/server
)

# =============================================================================
# 1. Android Library 目标 (默认)
# =============================================================================
if(BUILD_ANDROID_LIBRARY)
    message(STATUS "🏗️ Configuring Android Library build...")
    
    # 组合源文件
    set(ANDROID_LIB_SOURCES 
        ${COMMON_SOURCES}
        ${JNI_SOURCES}
        ${CLIENT_COMM_SOURCES}
        ${SERVER_COMM_SOURCES}
    )
    
    message(STATUS "  ├─ Target: server-k (shared library)")
    message(STATUS "  ├─ Sources included:")
    foreach(source ${ANDROID_LIB_SOURCES})
        message(STATUS "  │  └─ ${source}")
    endforeach()
    
    add_library(server-k SHARED ${ANDROID_LIB_SOURCES})
    
    # Android平台特定配置
    if(ANDROID)
        find_library(log-lib log)
        if(log-lib)
            target_link_libraries(server-k ${log-lib})
            message(STATUS "  ├─ Linked Android log library")
        else()
            message(WARNING "  ⚠️  Android log library not found")
        endif()
    endif()
    
    # 链接pthread库
    if(NOT ANDROID)
        find_library(pthread-lib pthread)
        if(pthread-lib)
            target_link_libraries(server-k ${pthread-lib})
            message(STATUS "  ├─ Linked pthread library")
        else()
            target_link_libraries(server-k pthread)
            message(STATUS "  ├─ Linked pthread library (fallback)")
        endif()
    endif()
    
    # 设置输出目录
    set(OUTPUT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/libs")
    set_target_properties(server-k PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY "${OUTPUT_DIR}"
    )
    
    message(STATUS "  ├─ Output directory: ${OUTPUT_DIR}")
    message(STATUS "  └─ Expected output: libserver-k.so")
    message(STATUS "✅ Android Library target configured successfully")
endif()

# =============================================================================
# 2. 独立Server端目标
# =============================================================================
if(BUILD_SERVER_ONLY)
    message(STATUS "🏗️ Configuring Server Only build...")
    
    # 组合源文件 (排除客户端通信源文件)
    set(SERVER_ONLY_SOURCES
        ${COMMON_SOURCES}
        ${JNI_SOURCES}
        ${SERVER_COMM_SOURCES}
    )
    
    message(STATUS "  ├─ Target: server-k-server (shared library)")
    message(STATUS "  ├─ Sources included:")
    foreach(source ${SERVER_ONLY_SOURCES})
        message(STATUS "  │  └─ ${source}")
    endforeach()
    message(STATUS "  ├─ Sources excluded: ${CLIENT_COMM_SOURCES}")
    
    add_library(server-k-server SHARED ${SERVER_ONLY_SOURCES})
    
    # Android平台特定配置
    if(ANDROID)
        find_library(log-lib log)
        if(log-lib)
            target_link_libraries(server-k-server ${log-lib})
            message(STATUS "  ├─ Linked Android log library")
        else()
            message(WARNING "  ⚠️  Android log library not found")
        endif()
    endif()
    
    # 链接pthread库
    if(NOT ANDROID)
        find_library(pthread-lib pthread)
        if(pthread-lib)
            target_link_libraries(server-k-server ${pthread-lib})
            message(STATUS "  ├─ Linked pthread library")
        else()
            target_link_libraries(server-k-server pthread)
            message(STATUS "  ├─ Linked pthread library (fallback)")
        endif()
    endif()
    
    # 设置输出目录
    set(OUTPUT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/server-libs")
    set_target_properties(server-k-server PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY "${OUTPUT_DIR}"
    )
    
    message(STATUS "  ├─ Output directory: ${OUTPUT_DIR}")
    message(STATUS "  └─ Expected output: libserver-k.so")
    message(STATUS "✅ Server Only target configured successfully")
endif()

# =============================================================================
# 3. 客户端目标
# =============================================================================
if(BUILD_CLIENT)
    message(STATUS "🏗️ Configuring Client build...")
    
    # 禁用JNI相关代码
    add_definitions(-DDISABLE_JNI)
    
    # 组合源文件 (排除JNI和服务器通信源文件)
    set(CLIENT_SOURCES
        ${COMMON_SOURCES}
        ${CLIENT_COMM_SOURCES}
        ${CLIENT_MAIN_SOURCES}
    )
    
    message(STATUS "  ├─ Target: k-client (executable)")
    message(STATUS "  ├─ Sources included:")
    foreach(source ${CLIENT_SOURCES})
        message(STATUS "  │  └─ ${source}")
    endforeach()
    message(STATUS "  ├─ Sources excluded: ${JNI_SOURCES}, ${SERVER_COMM_SOURCES}")
    
    # 检查嵌入文件状态
    if(EMBEDDED_SOURCES)
        message(STATUS "  ├─ Embedded files: Available (${EMBEDDED_SOURCES})")
        message(STATUS "  │  └─ HAVE_EMBEDDED_FILES defined")
    else()
        message(WARNING "  ⚠️  Embedded files not found - client cannot auto-start server")
        message(STATUS "  │  └─ Run 'gradle generateEmbeddedFiles' to create embedded files")
    endif()
    
    add_executable(k-client ${CLIENT_SOURCES})
    
    # 链接OpenCV（如果可用）
    if(OpenCV_FOUND)
        target_link_libraries(k-client ${OpenCV_LIBS})
        target_include_directories(k-client PRIVATE ${OpenCV_INCLUDE_DIRS})
        message(STATUS "  ├─ OpenCV: Linked version ${OpenCV_VERSION}")
    else()
        message(STATUS "  ├─ OpenCV: Not available, using fallback implementation")
    endif()
    
    # Android平台特定配置
    if(ANDROID)
        find_library(log-lib log)
        if(log-lib)
            target_link_libraries(k-client ${log-lib})
            message(STATUS "  ├─ Linked Android log library")
        else()
            message(WARNING "  ⚠️  Android log library not found")
        endif()
    endif()
    
    # 链接pthread库
    if(NOT ANDROID)
        find_library(pthread-lib pthread)
        if(pthread-lib)
            target_link_libraries(k-client ${pthread-lib})
            message(STATUS "  ├─ Linked pthread library")
        else()
            target_link_libraries(k-client pthread)
            message(STATUS "  ├─ Linked pthread library (fallback)")
        endif()
    endif()
    
    # 设置输出目录
    set(OUTPUT_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/client-bin")
    set_target_properties(k-client PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY "${OUTPUT_DIR}"
    )
    
    message(STATUS "  ├─ Output directory: ${OUTPUT_DIR}")
    message(STATUS "  └─ Expected output: k-client")
    message(STATUS "✅ Client target configured successfully")
endif()

# =============================================================================
# 构建完成状态报告
# =============================================================================
message(STATUS "")
message(STATUS "🎯 Build Configuration Summary:")
message(STATUS "═══════════════════════════════════════════════════════════════")

# 显示活动的构建模式
if(BUILD_ANDROID_LIBRARY)
    message(STATUS "📱 Active Mode: Android Library")
    message(STATUS "   └─ Output: build/libs/libserver-k.so")
elseif(BUILD_SERVER_ONLY)
    message(STATUS "🖥️  Active Mode: Server Only")
    message(STATUS "   └─ Output: build/server-libs/libserver-k.so + k-server.dex")
elseif(BUILD_CLIENT)
    message(STATUS "💻 Active Mode: Client")
    message(STATUS "   └─ Output: build/client-bin/k-client")
endif()

# 显示依赖状态
message(STATUS "📦 Dependencies:")
message(STATUS "   ├─ OpenCV: ${OpenCV_FOUND}")
if(OpenCV_FOUND)
    message(STATUS "   │  └─ Version: ${OpenCV_VERSION}")
endif()
message(STATUS "   ├─ Platform: ${CMAKE_SYSTEM_NAME}")
if(ANDROID)
    message(STATUS "   └─ Android Log: Available")
else()
    message(STATUS "   └─ pthread: Available")
endif()

# 显示源文件统计
list(LENGTH COMMON_SOURCES COMMON_COUNT)
list(LENGTH JNI_SOURCES JNI_COUNT)
list(LENGTH CLIENT_COMM_SOURCES CLIENT_COMM_COUNT)
list(LENGTH SERVER_COMM_SOURCES SERVER_COMM_COUNT)
list(LENGTH CLIENT_MAIN_SOURCES CLIENT_MAIN_COUNT)

message(STATUS "📁 Source File Statistics:")
message(STATUS "   ├─ Common Sources: ${COMMON_COUNT}")
message(STATUS "   ├─ JNI Sources: ${JNI_COUNT}")
message(STATUS "   ├─ Client Comm Sources: ${CLIENT_COMM_COUNT}")
message(STATUS "   ├─ Server Comm Sources: ${SERVER_COMM_COUNT}")
message(STATUS "   └─ Client Main Sources: ${CLIENT_MAIN_COUNT}")

if(BUILD_CLIENT AND EMBEDDED_SOURCES)
    list(LENGTH EMBEDDED_SOURCES EMBEDDED_COUNT)
    message(STATUS "   └─ Embedded Sources: ${EMBEDDED_COUNT}")
endif()

message(STATUS "═══════════════════════════════════════════════════════════════")
message(STATUS "✅ CMake configuration completed successfully!")
message(STATUS "")