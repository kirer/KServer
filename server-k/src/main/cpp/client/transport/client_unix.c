#include "client_unix.h"
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>

// Unix套接字客户端操作函数表
static const client_ops_t unix_client_ops = {
    .connect = unix_client_connect,
    .disconnect = unix_client_disconnect,
    .take_screenshot = unix_client_take_screenshot,
    .cleanup = unix_client_cleanup
};

// 获取当前时间戳（毫秒）
static long get_current_time_ms(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

// Unix套接字客户端连接
int unix_client_connect(client_config_t* config) {
    if (!config) {
        LOG_ERROR(UNIX_CLIENT_LOG_TAG, "配置参数为空");
        return -1;
    }
    
    LOG_INFO(UNIX_CLIENT_LOG_TAG, "连接Unix套接字");
    
    // 检查socket名称
    if (strlen(config->socket_name) == 0) {
        LOG_ERROR(UNIX_CLIENT_LOG_TAG, "套接字名称为空");
        return -1;
    }
    
    // 创建socket
    int client_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (client_fd < 0) {
        LOG_ERROR(UNIX_CLIENT_LOG_TAG, "创建套接字失败: %s", strerror(errno));
        return -1;
    }
    
    // 设置socket超时
    struct timeval timeout;
    timeout.tv_sec = READ_TIMEOUT / 1000;
    timeout.tv_usec = (READ_TIMEOUT % 1000) * 1000;
    if (setsockopt(client_fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout)) < 0) {
        LOG_WARN(UNIX_CLIENT_LOG_TAG, "设置接收超时失败: %s", strerror(errno));
    }
    
    // 使用Abstract Unix Socket连接
    LOG_DEBUG(UNIX_CLIENT_LOG_TAG, "连接到Abstract Unix Socket: @%s", config->socket_name);

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    // Abstract socket: 第一个字符为'\0'，后面跟socket名称
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, config->socket_name, sizeof(addr.sun_path) - 2);

    // Abstract socket的长度计算
    int addr_len = sizeof(addr.sun_family) + 1 + strlen(config->socket_name);

    if (connect(client_fd, (struct sockaddr*)&addr, addr_len) < 0) {
        LOG_ERROR(UNIX_CLIENT_LOG_TAG, "连接Abstract套接字 @%s 失败: %s",
                  config->socket_name, strerror(errno));
        close(client_fd);
        return -1;
    }
    
    // 保存客户端文件描述符
    g_client_ctx.client_data.unix_socket.socket_fd = client_fd;

    LOG_INFO(UNIX_CLIENT_LOG_TAG, "Abstract Unix套接字连接成功，名称: @%s", config->socket_name);
    return 0;
}

// Unix套接字客户端断开连接
void unix_client_disconnect(void) {
    LOG_INFO(UNIX_CLIENT_LOG_TAG, "断开Unix套接字连接");
    
    // 关闭客户端socket
    if (g_client_ctx.client_data.unix_socket.socket_fd >= 0) {
        close(g_client_ctx.client_data.unix_socket.socket_fd);
        g_client_ctx.client_data.unix_socket.socket_fd = -1;
    }
    
    LOG_INFO(UNIX_CLIENT_LOG_TAG, "Unix套接字连接已断开");
}

// Unix套接字客户端截图
client_response_t* unix_client_take_screenshot(void) {
    long start_time = get_current_time_ms();
    
    client_response_t* response = malloc(sizeof(client_response_t));
    if (!response) {
        LOG_ERROR(UNIX_CLIENT_LOG_TAG, "分配响应结构体内存失败");
        return NULL;
    }
    
    // 初始化响应
    memset(response, 0, sizeof(client_response_t));
    response->success = false;
    response->data = NULL;
    response->data_size = 0;
    
    if (g_client_ctx.client_data.unix_socket.socket_fd < 0) {
        strcpy(response->message, "套接字未连接");
        response->processing_time = get_current_time_ms() - start_time;
        LOG_ERROR(UNIX_CLIENT_LOG_TAG, "套接字未连接");
        return response;
    }
    
    LOG_DEBUG(UNIX_CLIENT_LOG_TAG, "从Unix套接字读取截图数据");
    
    // 读取数据
    uint8_t* data = NULL;
    size_t data_size = 0;
    int result = unix_client_read_data(&data, &data_size);
    
    long processing_time = get_current_time_ms() - start_time;
    
    if (result == 0 && data && data_size > 0) {
        response->success = true;
        snprintf(response->message, sizeof(response->message), 
                "截图读取成功，耗时: %ld ms", processing_time);
        response->data = data;
        response->data_size = data_size;
        response->processing_time = processing_time;
        
        LOG_DEBUG(UNIX_CLIENT_LOG_TAG, "成功从Unix套接字读取数据，大小: %zu 字节，耗时: %ld ms", 
                  data_size, processing_time);
    } else {
        snprintf(response->message, sizeof(response->message), 
                "读取失败，错误代码: %d", result);
        response->processing_time = processing_time;
        LOG_ERROR(UNIX_CLIENT_LOG_TAG, "从Unix套接字读取数据失败，错误代码: %d", result);
        
        if (data) {
            free(data);
        }
    }
    
    return response;
}

// 读取数据
int unix_client_read_data(uint8_t** data, size_t* size) {
    int client_fd = g_client_ctx.client_data.unix_socket.socket_fd;
    
    // 读取4字节长度头
    uint8_t header[4];
    ssize_t bytes_read = 0;
    while (bytes_read < 4) {
        ssize_t read_result = recv(client_fd, header + bytes_read, 4 - bytes_read, 0);
        if (read_result <= 0) {
            if (read_result == 0) {
                LOG_ERROR(UNIX_CLIENT_LOG_TAG, "连接已断开");
            } else {
                LOG_ERROR(UNIX_CLIENT_LOG_TAG, "读取数据头失败: %s", strerror(errno));
            }
            return -1;
        }
        bytes_read += read_result;
    }
    
    // 解析数据长度（小端序）
    uint32_t data_length = (uint32_t)header[0] | 
                          ((uint32_t)header[1] << 8) | 
                          ((uint32_t)header[2] << 16) | 
                          ((uint32_t)header[3] << 24);
    
    LOG_DEBUG(UNIX_CLIENT_LOG_TAG, "准备读取数据，长度: %u 字节", data_length);
    
    if (data_length == 0) {
        *data = NULL;
        *size = 0;
        return 0;
    }
    
    if (data_length > MAX_DATA_SIZE) {
        LOG_ERROR(UNIX_CLIENT_LOG_TAG, "数据长度过大: %u", data_length);
        return -1;
    }
    
    // 分配内存并读取数据
    uint8_t* buffer = malloc(data_length);
    if (!buffer) {
        LOG_ERROR(UNIX_CLIENT_LOG_TAG, "分配数据缓冲区内存失败");
        return -1;
    }
    
    bytes_read = 0;
    while (bytes_read < data_length) {
        ssize_t read_result = recv(client_fd, buffer + bytes_read, data_length - bytes_read, 0);
        if (read_result <= 0) {
            if (read_result == 0) {
                LOG_ERROR(UNIX_CLIENT_LOG_TAG, "连接已断开");
            } else {
                LOG_ERROR(UNIX_CLIENT_LOG_TAG, "读取数据失败: %s", strerror(errno));
            }
            free(buffer);
            return -1;
        }
        bytes_read += read_result;
    }
    
    *data = buffer;
    *size = data_length;
    return 0;
}

// Unix套接字客户端清理
int unix_client_cleanup(void) {
    LOG_INFO(UNIX_CLIENT_LOG_TAG, "清理Unix套接字客户端");
    
    // 断开连接
    unix_client_disconnect();
    
    LOG_INFO(UNIX_CLIENT_LOG_TAG, "Unix套接字客户端清理完成");
    return 0;
}

// 获取Unix套接字客户端操作函数
const client_ops_t* get_unix_client_ops(void) {
    return &unix_client_ops;
}
