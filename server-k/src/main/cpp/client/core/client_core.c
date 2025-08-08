#include "client_core.h"
#include "../transport/client_shared_memory.h"
#include "../transport/client_unix.h"
#include "../transport/client_tcp.h"
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <stdio.h>
#include <sys/time.h>

// 全局客户端上下文
client_context_t g_client_ctx = {0};

// 获取当前时间戳（毫秒）
static long get_current_time_ms(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

// 获取客户端操作函数
const client_ops_t* get_client_ops(client_mode_t mode) {
    switch (mode) {
        case CLIENT_MODE_SHARED_MEMORY:
            return get_shared_memory_client_ops();
        case CLIENT_MODE_UNIX_SOCKET:
            return get_unix_client_ops();
        case CLIENT_MODE_TCP_SOCKET:
            return get_tcp_client_ops();
        default:
            LOG_ERROR(CLIENT_LOG_TAG, "不支持的客户端模式: %d", mode);
            return NULL;
    }
}

// 初始化客户端（参数配置）
int client_initialize_with_params(client_mode_t mode, int memory_size,
                                  const char* socket_name, const char* tcp_host, int tcp_port) {
    LOG_INFO(CLIENT_LOG_TAG, "使用参数配置初始化客户端");
    
    pthread_mutex_lock(&g_client_ctx.status_mutex);
    if (g_client_ctx.status != CLIENT_STATUS_DISCONNECTED) {
        LOG_WARN(CLIENT_LOG_TAG, "客户端已初始化");
        pthread_mutex_unlock(&g_client_ctx.status_mutex);
        return 0;
    }
    pthread_mutex_unlock(&g_client_ctx.status_mutex);
    
    // 设置配置
    memset(&g_client_ctx.config, 0, sizeof(client_config_t));
    g_client_ctx.config.mode = mode;
    g_client_ctx.config.memory_size = memory_size;
    g_client_ctx.config.tcp_port = tcp_port;
    
    if (socket_name) {
        strncpy(g_client_ctx.config.socket_name, socket_name, sizeof(g_client_ctx.config.socket_name) - 1);
    } else {
        strcpy(g_client_ctx.config.socket_name, "server-k");
    }
    if (tcp_host) {
        strncpy(g_client_ctx.config.tcp_host, tcp_host, sizeof(g_client_ctx.config.tcp_host) - 1);
    } else {
        strcpy(g_client_ctx.config.tcp_host, "127.0.0.1");
    }
    // 初始化互斥锁
    if (pthread_mutex_init(&g_client_ctx.status_mutex, NULL) != 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "初始化状态互斥锁失败");
        return -1;
    }
    
    pthread_mutex_lock(&g_client_ctx.status_mutex);
    g_client_ctx.status = CLIENT_STATUS_DISCONNECTED;
    pthread_mutex_unlock(&g_client_ctx.status_mutex);
    
    LOG_INFO(CLIENT_LOG_TAG, "使用参数配置的客户端初始化成功");
    return 0;
}

// 连接到服务器
int client_connect(void) {
    LOG_INFO(CLIENT_LOG_TAG, "连接到服务器");
    
    pthread_mutex_lock(&g_client_ctx.status_mutex);
    if (g_client_ctx.status == CLIENT_STATUS_CONNECTED) {
        LOG_WARN(CLIENT_LOG_TAG, "客户端已连接");
        pthread_mutex_unlock(&g_client_ctx.status_mutex);
        return 0;
    }
    g_client_ctx.status = CLIENT_STATUS_CONNECTING;
    pthread_mutex_unlock(&g_client_ctx.status_mutex);
    
    // 获取客户端操作函数并连接
    const client_ops_t* ops = get_client_ops(g_client_ctx.config.mode);
    if (!ops || ops->connect(&g_client_ctx.config) != 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "连接服务器失败");
        pthread_mutex_lock(&g_client_ctx.status_mutex);
        g_client_ctx.status = CLIENT_STATUS_ERROR;
        pthread_mutex_unlock(&g_client_ctx.status_mutex);
        return -1;
    }
    
    pthread_mutex_lock(&g_client_ctx.status_mutex);
    g_client_ctx.status = CLIENT_STATUS_CONNECTED;
    pthread_mutex_unlock(&g_client_ctx.status_mutex);
    
    LOG_INFO(CLIENT_LOG_TAG, "客户端连接成功");
    return 0;
}

// 断开连接
void client_disconnect(void) {
    LOG_INFO(CLIENT_LOG_TAG, "断开客户端连接");
    
    pthread_mutex_lock(&g_client_ctx.status_mutex);
    client_status_t status = g_client_ctx.status;
    pthread_mutex_unlock(&g_client_ctx.status_mutex);
    
    if (status == CLIENT_STATUS_DISCONNECTED) {
        LOG_WARN(CLIENT_LOG_TAG, "客户端未连接");
        return;
    }
    
    // 获取客户端操作函数并断开连接
    const client_ops_t* ops = get_client_ops(g_client_ctx.config.mode);
    if (ops && ops->disconnect) {
        ops->disconnect();
    }
    
    // 清理资源
    cleanup_client_resources();
    
    pthread_mutex_lock(&g_client_ctx.status_mutex);
    g_client_ctx.status = CLIENT_STATUS_DISCONNECTED;
    pthread_mutex_unlock(&g_client_ctx.status_mutex);
    
    LOG_INFO(CLIENT_LOG_TAG, "客户端断开连接成功");
}

// 截图
client_response_t* client_take_screenshot(void) {
    long start_time = get_current_time_ms();

    pthread_mutex_lock(&g_client_ctx.status_mutex);
    client_status_t status = g_client_ctx.status;
    pthread_mutex_unlock(&g_client_ctx.status_mutex);

    if (status != CLIENT_STATUS_CONNECTED) {
        LOG_ERROR(CLIENT_LOG_TAG, "客户端未连接");
        client_response_t* response = malloc(sizeof(client_response_t));
        if (response) {
            response->success = false;
            strcpy(response->message, "客户端未连接");
            response->data = NULL;
            response->data_size = 0;
            response->processing_time = get_current_time_ms() - start_time;
        }
        return response;
    }

    const client_ops_t* ops = get_client_ops(g_client_ctx.config.mode);
    if (!ops || !ops->take_screenshot) {
        LOG_ERROR(CLIENT_LOG_TAG, "客户端操作函数不可用");
        client_response_t* response = malloc(sizeof(client_response_t));
        if (response) {
            response->success = false;
            strcpy(response->message, "客户端操作函数不可用");
            response->data = NULL;
            response->data_size = 0;
            response->processing_time = get_current_time_ms() - start_time;
        }
        return response;
    }

    LOG_DEBUG(CLIENT_LOG_TAG, "开始截图");
    return ops->take_screenshot();
}

// 获取状态信息
const char* client_get_status_info(void) {
    static char status_buffer[512];

    pthread_mutex_lock(&g_client_ctx.status_mutex);
    client_status_t status = g_client_ctx.status;
    client_mode_t mode = g_client_ctx.config.mode;
    pthread_mutex_unlock(&g_client_ctx.status_mutex);

    const char* status_str;
    switch (status) {
        case CLIENT_STATUS_DISCONNECTED: status_str = "DISCONNECTED"; break;
        case CLIENT_STATUS_CONNECTING: status_str = "CONNECTING"; break;
        case CLIENT_STATUS_CONNECTED: status_str = "CONNECTED"; break;
        case CLIENT_STATUS_ERROR: status_str = "ERROR"; break;
        default: status_str = "UNKNOWN"; break;
    }

    const char* mode_str;
    switch (mode) {
        case CLIENT_MODE_SHARED_MEMORY: mode_str = "SHARED_MEMORY"; break;
        case CLIENT_MODE_UNIX_SOCKET: mode_str = "UNIX_SOCKET"; break;
        case CLIENT_MODE_TCP_SOCKET: mode_str = "TCP_SOCKET"; break;
        default: mode_str = "UNKNOWN"; break;
    }

    snprintf(status_buffer, sizeof(status_buffer),
             "NativeClient{status=%s, mode=%s}",
             status_str, mode_str);

    return status_buffer;
}

// 清理客户端资源
int cleanup_client_resources(void) {
    LOG_INFO(CLIENT_LOG_TAG, "清理客户端资源");

    const client_ops_t* ops = get_client_ops(g_client_ctx.config.mode);
    if (ops && ops->cleanup) {
        ops->cleanup();
    }

    // 清理互斥锁
    pthread_mutex_destroy(&g_client_ctx.status_mutex);

    // 重置上下文
    memset(&g_client_ctx, 0, sizeof(client_context_t));

    LOG_INFO(CLIENT_LOG_TAG, "客户端资源清理完成");
    return 0;
}

// 释放响应结构体
void free_client_response(client_response_t* response) {
    if (response) {
        if (response->data) {
            free(response->data);
        }
        free(response);
    }
}
