cmake_minimum_required(VERSION 3.22.1)

project("server-k")

# 设置C标准
set(CMAKE_C_STANDARD 99)

# 添加编译选项
add_compile_options(-Wall -Wextra -Werror -Wno-unused-parameter -Wno-unused-function -O2)

# 创建server-k共享库（包含所有功能）
add_library(server-k SHARED
        server/core/server_core.c
        server/transport/server_shared_memory.c
        server/transport/server_unix.c
        server/transport/server_tcp.c
        client/core/client_core.c
        client/transport/client_shared_memory.c
        client/transport/client_unix.c
        client/transport/client_tcp.c
        common/ashmem.c
        common/log.c
        client/jni/client_jni_bridge.c
        server/jni/server_jni_bridge.c
)

# 链接Android日志库
if (ANDROID)
    find_library(log-lib log)
    if (log-lib)
        target_link_libraries(server-k ${log-lib})
    endif ()
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
