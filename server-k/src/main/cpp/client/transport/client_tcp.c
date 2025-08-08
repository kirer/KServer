#include "client_tcp.h"
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>
#include <netinet/tcp.h>

// TCP客户端操作函数表
static const client_ops_t tcp_client_ops = {
    .connect = tcp_client_connect,
    .disconnect = tcp_client_disconnect,
    .take_screenshot = tcp_client_take_screenshot,
    .cleanup = tcp_client_cleanup
};

// 获取当前时间戳（毫秒）
static long get_current_time_ms(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

// TCP客户端连接
int tcp_client_connect(client_config_t* config) {
    if (!config) {
        LOG_ERROR(TCP_CLIENT_LOG_TAG, "配置参数为空");
        return -1;
    }
    
    LOG_INFO(TCP_CLIENT_LOG_TAG, "连接TCP服务器");
    
    // 检查主机和端口
    if (strlen(config->tcp_host) == 0) {
        LOG_ERROR(TCP_CLIENT_LOG_TAG, "TCP主机地址为空");
        return -1;
    }
    
    if (config->tcp_port <= 0 || config->tcp_port > 65535) {
        LOG_ERROR(TCP_CLIENT_LOG_TAG, "无效的TCP端口: %d", config->tcp_port);
        return -1;
    }
    
    // 创建socket
    int client_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (client_fd < 0) {
        LOG_ERROR(TCP_CLIENT_LOG_TAG, "创建套接字失败: %s", strerror(errno));
        return -1;
    }
    
    // 设置socket选项
    int opt = 1;
    if (setsockopt(client_fd, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt)) < 0) {
        LOG_WARN(TCP_CLIENT_LOG_TAG, "设置TCP_NODELAY失败: %s", strerror(errno));
    }
    
    // 设置socket超时
    struct timeval timeout;
    timeout.tv_sec = READ_TIMEOUT / 1000;
    timeout.tv_usec = (READ_TIMEOUT % 1000) * 1000;
    if (setsockopt(client_fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout)) < 0) {
        LOG_WARN(TCP_CLIENT_LOG_TAG, "设置接收超时失败: %s", strerror(errno));
    }
    
    timeout.tv_sec = CONNECTION_TIMEOUT / 1000;
    timeout.tv_usec = (CONNECTION_TIMEOUT % 1000) * 1000;
    if (setsockopt(client_fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout)) < 0) {
        LOG_WARN(TCP_CLIENT_LOG_TAG, "设置发送超时失败: %s", strerror(errno));
    }
    
    // 连接服务器
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(config->tcp_port);
    
    if (inet_pton(AF_INET, config->tcp_host, &addr.sin_addr) <= 0) {
        LOG_ERROR(TCP_CLIENT_LOG_TAG, "无效的TCP主机地址: %s", config->tcp_host);
        close(client_fd);
        return -1;
    }
    
    if (connect(client_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        LOG_ERROR(TCP_CLIENT_LOG_TAG, "连接TCP服务器 %s:%d 失败: %s", 
                  config->tcp_host, config->tcp_port, strerror(errno));
        close(client_fd);
        return -1;
    }
    
    // 保存客户端文件描述符
    g_client_ctx.client_data.tcp_socket.socket_fd = client_fd;
    
    LOG_INFO(TCP_CLIENT_LOG_TAG, "TCP客户端连接成功，地址: %s:%d", 
             config->tcp_host, config->tcp_port);
    return 0;
}

// TCP客户端断开连接
void tcp_client_disconnect(void) {
    LOG_INFO(TCP_CLIENT_LOG_TAG, "断开TCP连接");
    
    // 关闭客户端socket
    if (g_client_ctx.client_data.tcp_socket.socket_fd >= 0) {
        close(g_client_ctx.client_data.tcp_socket.socket_fd);
        g_client_ctx.client_data.tcp_socket.socket_fd = -1;
    }
    
    LOG_INFO(TCP_CLIENT_LOG_TAG, "TCP连接已断开");
}

// TCP客户端截图
client_response_t* tcp_client_take_screenshot(void) {
    long start_time = get_current_time_ms();
    
    client_response_t* response = malloc(sizeof(client_response_t));
    if (!response) {
        LOG_ERROR(TCP_CLIENT_LOG_TAG, "分配响应结构体内存失败");
        return NULL;
    }
    
    // 初始化响应
    memset(response, 0, sizeof(client_response_t));
    response->success = false;
    response->data = NULL;
    response->data_size = 0;
    
    if (g_client_ctx.client_data.tcp_socket.socket_fd < 0) {
        strcpy(response->message, "套接字未连接");
        response->processing_time = get_current_time_ms() - start_time;
        LOG_ERROR(TCP_CLIENT_LOG_TAG, "套接字未连接");
        return response;
    }
    
    LOG_DEBUG(TCP_CLIENT_LOG_TAG, "从TCP套接字读取截图数据");
    
    // 读取数据
    uint8_t* data = NULL;
    size_t data_size = 0;
    int result = tcp_client_read_data(&data, &data_size);
    
    long processing_time = get_current_time_ms() - start_time;
    
    if (result == 0 && data && data_size > 0) {
        response->success = true;
        snprintf(response->message, sizeof(response->message), 
                "截图读取成功，耗时: %ld ms", processing_time);
        response->data = data;
        response->data_size = data_size;
        response->processing_time = processing_time;
        
        LOG_DEBUG(TCP_CLIENT_LOG_TAG, "成功从TCP套接字读取数据，大小: %zu 字节，耗时: %ld ms", 
                  data_size, processing_time);
    } else {
        snprintf(response->message, sizeof(response->message), 
                "读取失败，错误代码: %d", result);
        response->processing_time = processing_time;
        LOG_ERROR(TCP_CLIENT_LOG_TAG, "从TCP套接字读取数据失败，错误代码: %d", result);
        
        if (data) {
            free(data);
        }
    }
    
    return response;
}

// 读取数据
int tcp_client_read_data(uint8_t** data, size_t* size) {
    int client_fd = g_client_ctx.client_data.tcp_socket.socket_fd;
    
    // 读取4字节长度头
    uint8_t header[4];
    ssize_t bytes_read = 0;
    while (bytes_read < 4) {
        ssize_t read_result = recv(client_fd, header + bytes_read, 4 - bytes_read, 0);
        if (read_result <= 0) {
            if (read_result == 0) {
                LOG_ERROR(TCP_CLIENT_LOG_TAG, "连接已断开");
            } else {
                LOG_ERROR(TCP_CLIENT_LOG_TAG, "读取数据头失败: %s", strerror(errno));
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
    
    LOG_DEBUG(TCP_CLIENT_LOG_TAG, "准备读取数据，长度: %u 字节", data_length);
    
    if (data_length == 0) {
        *data = NULL;
        *size = 0;
        return 0;
    }
    
    if (data_length > MAX_DATA_SIZE) {
        LOG_ERROR(TCP_CLIENT_LOG_TAG, "数据长度过大: %u", data_length);
        return -1;
    }
    
    // 分配内存并读取数据
    uint8_t* buffer = malloc(data_length);
    if (!buffer) {
        LOG_ERROR(TCP_CLIENT_LOG_TAG, "分配数据缓冲区内存失败");
        return -1;
    }
    
    bytes_read = 0;
    while (bytes_read < data_length) {
        ssize_t read_result = recv(client_fd, buffer + bytes_read, data_length - bytes_read, 0);
        if (read_result <= 0) {
            if (read_result == 0) {
                LOG_ERROR(TCP_CLIENT_LOG_TAG, "连接已断开");
            } else {
                LOG_ERROR(TCP_CLIENT_LOG_TAG, "读取数据失败: %s", strerror(errno));
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

// TCP客户端清理
int tcp_client_cleanup(void) {
    LOG_INFO(TCP_CLIENT_LOG_TAG, "清理TCP客户端");
    
    // 断开连接
    tcp_client_disconnect();
    
    LOG_INFO(TCP_CLIENT_LOG_TAG, "TCP客户端清理完成");
    return 0;
}

// 获取TCP客户端操作函数
const client_ops_t* get_tcp_client_ops(void) {
    return &tcp_client_ops;
}
