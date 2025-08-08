#include "server_shared_memory.h"
#include <string.h>
#include <stdio.h>

// 共享内存服务器操作函数表
static const server_ops_t shm_server_ops = {
    .initialize = shm_server_initialize,
    .start = shm_server_start,
    .stop = shm_server_stop,
    .write_data = shm_server_write_data,
    .cleanup = shm_server_cleanup
};

// 共享内存服务器初始化
int shm_server_initialize(server_config_t* config) {
    if (!config) {
        LOG_ERROR(SHM_SERVER_LOG_TAG, "配置参数为空");
        return -1;
    }

    LOG_INFO(SHM_SERVER_LOG_TAG, "初始化共享内存服务器");

    // 检查内存大小
    if (config->memory_size <= 0) {
        LOG_ERROR(SHM_SERVER_LOG_TAG, "无效的内存大小: %d", config->memory_size);
        return -1;
    }

    LOG_INFO(SHM_SERVER_LOG_TAG, "共享内存服务器初始化成功，内存大小: %d 字节",
             config->memory_size);

    return 0;
}

// 共享内存服务器启动
int shm_server_start(void) {
    LOG_INFO(SHM_SERVER_LOG_TAG, "启动共享内存服务器");

    // 创建共享内存
    int result = asm_create(g_server_ctx.config.memory_size);
    if (result != 0) {
        LOG_ERROR(SHM_SERVER_LOG_TAG, "创建共享内存失败，错误代码: %d", result);
        return -1;
    }

    // 保存文件描述符到上下文
    g_server_ctx.server_data.shared_memory.shm_fd = g_asm_fd;
    g_server_ctx.server_data.shared_memory.shm_base = g_asm_base;

    LOG_INFO(SHM_SERVER_LOG_TAG, "共享内存服务器启动成功");
    return 0;
}

// 共享内存服务器停止
int shm_server_stop(void) {
    LOG_INFO(SHM_SERVER_LOG_TAG, "停止共享内存服务器");

    // 清理共享内存
    int result = asm_cleanup();
    if (result != 0) {
        LOG_WARN(SHM_SERVER_LOG_TAG, "清理共享内存时发生错误");
    }

    // 重置上下文数据
    g_server_ctx.server_data.shared_memory.shm_fd = -1;
    g_server_ctx.server_data.shared_memory.shm_base = NULL;

    LOG_INFO(SHM_SERVER_LOG_TAG, "共享内存服务器已停止");
    return 0;
}

// 共享内存服务器写入数据
int shm_server_write_data(const uint8_t* data, size_t size) {
    if (!data || size == 0) {
        LOG_WARN(SHM_SERVER_LOG_TAG, "数据参数无效");
        return -1;
    }

    if (!g_asm_base) {
        LOG_ERROR(SHM_SERVER_LOG_TAG, "共享内存未初始化");
        return -1;
    }

    LOG_DEBUG(SHM_SERVER_LOG_TAG, "向共享内存写入数据，大小: %zu 字节", size);

    int result = asm_write(data, size);
    if (result == 0) {
        LOG_DEBUG(SHM_SERVER_LOG_TAG, "数据成功写入共享内存");
    } else {
        LOG_ERROR(SHM_SERVER_LOG_TAG, "向共享内存写入数据失败，错误代码: %d", result);
    }

    return result;
}

// 共享内存服务器清理
int shm_server_cleanup(void) {
    LOG_INFO(SHM_SERVER_LOG_TAG, "清理共享内存服务器");

    // 清理共享内存
    int result = asm_cleanup();

    // 重置上下文数据
    g_server_ctx.server_data.shared_memory.shm_fd = -1;
    g_server_ctx.server_data.shared_memory.shm_base = NULL;

    LOG_INFO(SHM_SERVER_LOG_TAG, "共享内存服务器清理完成");
    return result;
}

// 获取共享内存服务器操作函数
const server_ops_t* get_shared_memory_server_ops(void) {
    return &shm_server_ops;
}
