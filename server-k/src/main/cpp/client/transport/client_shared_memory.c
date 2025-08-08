#include "client_shared_memory.h"
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>

// 共享内存客户端操作函数表
static const client_ops_t shm_client_ops = {
    .connect = shm_client_connect,
    .disconnect = shm_client_disconnect,
    .take_screenshot = shm_client_take_screenshot,
    .cleanup = shm_client_cleanup
};

// 获取当前时间戳（毫秒）
static long get_current_time_ms(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

// 共享内存客户端连接
int shm_client_connect(client_config_t* config) {
    if (!config) {
        LOG_ERROR(SHM_CLIENT_LOG_TAG, "配置参数为空");
        return -1;
    }
    
    LOG_INFO(SHM_CLIENT_LOG_TAG, "连接共享内存");
    
    // 检查内存大小
    if (config->memory_size <= 0) {
        LOG_ERROR(SHM_CLIENT_LOG_TAG, "无效的内存大小: %d", config->memory_size);
        return -1;
    }
    
    // 连接共享内存
    int result = asm_connect(config->memory_size);
    if (result != 0) {
        LOG_ERROR(SHM_CLIENT_LOG_TAG, "连接共享内存失败，错误代码: %d", result);
        return -1;
    }
    
    // 保存文件描述符到上下文
    g_client_ctx.client_data.shared_memory.shm_fd = g_asm_fd;
    g_client_ctx.client_data.shared_memory.shm_base = g_asm_base;
    g_client_ctx.client_data.shared_memory.initialized = true;
    
    LOG_INFO(SHM_CLIENT_LOG_TAG, "共享内存连接成功，内存大小: %d 字节", config->memory_size);
    return 0;
}

// 共享内存客户端断开连接
void shm_client_disconnect(void) {
    LOG_INFO(SHM_CLIENT_LOG_TAG, "断开共享内存连接");
    
    // 清理共享内存
    int result = asm_cleanup();
    if (result != 0) {
        LOG_WARN(SHM_CLIENT_LOG_TAG, "清理共享内存时发生错误");
    }
    
    // 重置上下文数据
    g_client_ctx.client_data.shared_memory.shm_fd = -1;
    g_client_ctx.client_data.shared_memory.shm_base = NULL;
    g_client_ctx.client_data.shared_memory.initialized = false;
    
    LOG_INFO(SHM_CLIENT_LOG_TAG, "共享内存连接已断开");
}

// 共享内存客户端截图
client_response_t* shm_client_take_screenshot(void) {
    long start_time = get_current_time_ms();
    
    client_response_t* response = malloc(sizeof(client_response_t));
    if (!response) {
        LOG_ERROR(SHM_CLIENT_LOG_TAG, "分配响应结构体内存失败");
        return NULL;
    }
    
    // 初始化响应
    memset(response, 0, sizeof(client_response_t));
    response->success = false;
    response->data = NULL;
    response->data_size = 0;
    
    if (!g_client_ctx.client_data.shared_memory.initialized) {
        strcpy(response->message, "共享内存未初始化");
        response->processing_time = get_current_time_ms() - start_time;
        LOG_ERROR(SHM_CLIENT_LOG_TAG, "共享内存未初始化");
        return response;
    }
    
    if (!g_asm_base) {
        strcpy(response->message, "共享内存基地址无效");
        response->processing_time = get_current_time_ms() - start_time;
        LOG_ERROR(SHM_CLIENT_LOG_TAG, "共享内存基地址无效");
        return response;
    }
    
    LOG_DEBUG(SHM_CLIENT_LOG_TAG, "从共享内存读取截图数据");
    
    // 读取共享内存数据
    uint8_t* data = NULL;
    size_t data_size = 0;
    int result = asm_read(&data, &data_size);
    
    long processing_time = get_current_time_ms() - start_time;
    
    if (result == 0 && data && data_size > 0) {
        response->success = true;
        snprintf(response->message, sizeof(response->message), 
                "截图读取成功，耗时: %ld ms", processing_time);
        response->data = data;
        response->data_size = data_size;
        response->processing_time = processing_time;
        
        LOG_DEBUG(SHM_CLIENT_LOG_TAG, "成功从共享内存读取数据，大小: %zu 字节，耗时: %ld ms", 
                  data_size, processing_time);
    } else if (result == 1) {
        // 没有新数据
        strcpy(response->message, "暂无新数据");
        response->processing_time = processing_time;
        LOG_DEBUG(SHM_CLIENT_LOG_TAG, "共享内存中暂无新数据");
    } else {
        snprintf(response->message, sizeof(response->message), 
                "读取失败，错误代码: %d", result);
        response->processing_time = processing_time;
        LOG_ERROR(SHM_CLIENT_LOG_TAG, "从共享内存读取数据失败，错误代码: %d", result);
    }
    
    return response;
}

// 共享内存客户端清理
int shm_client_cleanup(void) {
    LOG_INFO(SHM_CLIENT_LOG_TAG, "清理共享内存客户端");
    
    // 断开连接
    shm_client_disconnect();
    
    LOG_INFO(SHM_CLIENT_LOG_TAG, "共享内存客户端清理完成");
    return 0;
}

// 获取共享内存客户端操作函数
const client_ops_t* get_shared_memory_client_ops(void) {
    return &shm_client_ops;
}
