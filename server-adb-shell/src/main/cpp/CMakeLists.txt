cmake_minimum_required(VERSION 3.22.1)

project("ashmem")

# 设置C标准
set(CMAKE_C_STANDARD 99)

# 添加编译选项
add_compile_options(-Wall -Wextra -O2)

# 创建共享库
add_library(ashmem SHARED ashmem.c)

# 链接Android日志库
find_library(log-lib log)
target_link_libraries(ashmem ${log-lib})

# 设置输出目录
set_target_properties(ashmem PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/libs"
)
