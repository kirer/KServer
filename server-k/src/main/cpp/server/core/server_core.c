#include "server_core.h"
#include "../transport/server_shared_memory.h"
#include "../transport/server_unix.h"
#include "../transport/server_tcp.h"
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <errno.h>
#include <stdio.h>

// 全局服务器上下文
server_context_t g_server_ctx = {0};

// 获取服务器操作函数
const server_ops_t* get_server_ops(server_mode_t mode) {
    switch (mode) {
        case MODE_SHARED_MEMORY:
            return get_shared_memory_server_ops();
        case MODE_UNIX_SOCKET:
            return get_unix_server_ops();
        case MODE_TCP_SOCKET:
            return get_tcp_server_ops();
        default:
            LOG_ERROR(SERVER_LOG_TAG, "不支持的服务器模式: %d", mode);
            return NULL;
    }
}

// 服务器线程函数
void* server_thread_func(void* arg) {
    (void)arg; // 抑制未使用参数警告

    LOG_INFO(SERVER_LOG_TAG, "服务器线程已启动");

    const server_ops_t* ops = get_server_ops(g_server_ctx.config.mode);
    if (!ops) {
        LOG_ERROR(SERVER_LOG_TAG, "获取服务器操作函数失败");
        pthread_mutex_lock(&g_server_ctx.state_mutex);
        g_server_ctx.state = SERVER_STATE_ERROR;
        pthread_mutex_unlock(&g_server_ctx.state_mutex);
        return NULL;
    }

    // 启动服务器
    if (ops->start() != 0) {
        LOG_ERROR(SERVER_LOG_TAG, "启动服务器失败");
        pthread_mutex_lock(&g_server_ctx.state_mutex);
        g_server_ctx.state = SERVER_STATE_ERROR;
        pthread_mutex_unlock(&g_server_ctx.state_mutex);
        return NULL;
    }

    pthread_mutex_lock(&g_server_ctx.state_mutex);
    g_server_ctx.state = SERVER_STATE_STARTED;
    pthread_mutex_unlock(&g_server_ctx.state_mutex);

    LOG_INFO(SERVER_LOG_TAG, "服务器启动成功");

    // 保持线程运行
    while (g_server_ctx.thread_running) {
        usleep(100000); // 100ms
    }

    LOG_INFO(SERVER_LOG_TAG, "服务器线程正在停止");

    // 停止服务器
    if (ops->stop() != 0) {
        LOG_WARN(SERVER_LOG_TAG, "停止服务器时发生错误");
    }

    pthread_mutex_lock(&g_server_ctx.state_mutex);
    g_server_ctx.state = SERVER_STATE_STOPPED;
    pthread_mutex_unlock(&g_server_ctx.state_mutex);

    LOG_INFO(SERVER_LOG_TAG, "服务器线程已停止");
    return NULL;
}

// 初始化服务器（参数配置）
int server_initialize_with_params(server_mode_t mode, int memory_size, const char* lib_path,
                                  const char* socket_name, const char* tcp_host, int tcp_port) {
    LOG_INFO(SERVER_LOG_TAG, "使用参数配置初始化服务器");

    pthread_mutex_lock(&g_server_ctx.state_mutex);
    if (g_server_ctx.state != SERVER_STATE_UNINITIALIZED) {
        LOG_WARN(SERVER_LOG_TAG, "服务器已初始化");
        pthread_mutex_unlock(&g_server_ctx.state_mutex);
        return 0;
    }
    pthread_mutex_unlock(&g_server_ctx.state_mutex);

    // 设置配置
    memset(&g_server_ctx.config, 0, sizeof(server_config_t));
    g_server_ctx.config.mode = mode;
    g_server_ctx.config.memory_size = memory_size;
    g_server_ctx.config.tcp_port = tcp_port;

    if (lib_path) {
        strncpy(g_server_ctx.config.lib_path, lib_path, sizeof(g_server_ctx.config.lib_path) - 1);
    }
    if (socket_name) {
        strncpy(g_server_ctx.config.socket_name, socket_name, sizeof(g_server_ctx.config.socket_name) - 1);
    } else {
        strcpy(g_server_ctx.config.socket_name, "server-k");
    }
    if (tcp_host) {
        strncpy(g_server_ctx.config.tcp_host, tcp_host, sizeof(g_server_ctx.config.tcp_host) - 1);
    } else {
        strcpy(g_server_ctx.config.tcp_host, "127.0.0.1");
    }

    // 初始化互斥锁
    if (pthread_mutex_init(&g_server_ctx.state_mutex, NULL) != 0) {
        LOG_ERROR(SERVER_LOG_TAG, "初始化状态互斥锁失败");
        return -1;
    }

    // 获取服务器操作函数并初始化
    const server_ops_t* ops = get_server_ops(g_server_ctx.config.mode);
    if (!ops || ops->initialize(&g_server_ctx.config) != 0) {
        LOG_ERROR(SERVER_LOG_TAG, "初始化服务器实现失败");
        pthread_mutex_destroy(&g_server_ctx.state_mutex);
        return -1;
    }

    pthread_mutex_lock(&g_server_ctx.state_mutex);
    g_server_ctx.state = SERVER_STATE_INITIALIZED;
    g_server_ctx.thread_running = false;
    pthread_mutex_unlock(&g_server_ctx.state_mutex);

    LOG_INFO(SERVER_LOG_TAG, "使用参数配置的服务器初始化成功");
    return 0;
}

// 启动服务器
int server_start(void) {
    LOG_INFO(SERVER_LOG_TAG, "启动服务器");

    pthread_mutex_lock(&g_server_ctx.state_mutex);
    if (g_server_ctx.state != SERVER_STATE_INITIALIZED) {
        LOG_ERROR(SERVER_LOG_TAG, "服务器未初始化或已启动");
        pthread_mutex_unlock(&g_server_ctx.state_mutex);
        return -1;
    }
    pthread_mutex_unlock(&g_server_ctx.state_mutex);

    // 启动服务器线程
    g_server_ctx.thread_running = true;
    if (pthread_create(&g_server_ctx.server_thread, NULL, server_thread_func, NULL) != 0) {
        LOG_ERROR(SERVER_LOG_TAG, "创建服务器线程失败");
        g_server_ctx.thread_running = false;
        return -1;
    }

    // 等待服务器启动
    int timeout = 150; // 15秒超时（从5秒增加到15秒）
    LOG_DEBUG(SERVER_LOG_TAG, "等待服务器启动，超时时间: %d 秒", timeout / 10);

    while (timeout > 0) {
        pthread_mutex_lock(&g_server_ctx.state_mutex);
        server_state_t state = g_server_ctx.state;
        pthread_mutex_unlock(&g_server_ctx.state_mutex);

        if (state == SERVER_STATE_STARTED) {
            LOG_INFO(SERVER_LOG_TAG, "服务器启动成功");
            return 0;
        } else if (state == SERVER_STATE_ERROR) {
            LOG_ERROR(SERVER_LOG_TAG, "服务器启动失败");
            g_server_ctx.thread_running = false;
            pthread_join(g_server_ctx.server_thread, NULL);
            return -1;
        }

        // 每秒打印一次等待状态（仅在Unix Socket模式下）
        if (g_server_ctx.config.mode == MODE_UNIX_SOCKET && timeout % 10 == 0) {
            LOG_DEBUG(SERVER_LOG_TAG, "等待Unix Socket服务器启动... 剩余 %d 秒", timeout / 10);
        }

        usleep(100000); // 100ms
        timeout--;
    }

    LOG_ERROR(SERVER_LOG_TAG, "服务器启动超时（15秒）");
    g_server_ctx.thread_running = false;
    pthread_join(g_server_ctx.server_thread, NULL);
    return -1;
}

// 停止服务器
int server_stop(void) {
    LOG_INFO(SERVER_LOG_TAG, "停止服务器");

    pthread_mutex_lock(&g_server_ctx.state_mutex);
    server_state_t state = g_server_ctx.state;
    pthread_mutex_unlock(&g_server_ctx.state_mutex);

    if (state != SERVER_STATE_STARTED) {
        LOG_WARN(SERVER_LOG_TAG, "服务器未启动");
        return 0;
    }

    // 停止服务器线程
    g_server_ctx.thread_running = false;
    if (pthread_join(g_server_ctx.server_thread, NULL) != 0) {
        LOG_WARN(SERVER_LOG_TAG, "等待服务器线程结束失败");
    }

    // 清理资源
    cleanup_server_resources();

    pthread_mutex_lock(&g_server_ctx.state_mutex);
    g_server_ctx.state = SERVER_STATE_STOPPED;
    pthread_mutex_unlock(&g_server_ctx.state_mutex);

    LOG_INFO(SERVER_LOG_TAG, "服务器停止成功");
    return 0;
}

// 写入数据
int server_write_data(const uint8_t* data, size_t size) {
    if (!data || size == 0) {
        LOG_WARN(SERVER_LOG_TAG, "数据参数无效");
        return -1;
    }

    if (size > MAX_DATA_SIZE) {
        LOG_ERROR(SERVER_LOG_TAG, "数据大小过大: %zu", size);
        return -1;
    }

    pthread_mutex_lock(&g_server_ctx.state_mutex);
    server_state_t state = g_server_ctx.state;
    pthread_mutex_unlock(&g_server_ctx.state_mutex);

    if (state != SERVER_STATE_STARTED) {
        LOG_ERROR(SERVER_LOG_TAG, "服务器未启动");
        return -1;
    }

    const server_ops_t* ops = get_server_ops(g_server_ctx.config.mode);
    if (!ops || !ops->write_data) {
        LOG_ERROR(SERVER_LOG_TAG, "服务器操作函数不可用");
        return -1;
    }

    LOG_DEBUG(SERVER_LOG_TAG, "写入数据，大小: %zu 字节", size);
    return ops->write_data(data, size);
}

// 清理服务器资源
int cleanup_server_resources(void) {
    LOG_INFO(SERVER_LOG_TAG, "清理服务器资源");

    const server_ops_t* ops = get_server_ops(g_server_ctx.config.mode);
    if (ops && ops->cleanup) {
        ops->cleanup();
    }

    // 清理互斥锁
    pthread_mutex_destroy(&g_server_ctx.state_mutex);

    // 重置上下文
    memset(&g_server_ctx, 0, sizeof(server_context_t));

    LOG_INFO(SERVER_LOG_TAG, "服务器资源清理完成");
    return 0;
}
