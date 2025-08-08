#include "server_tcp.h"
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/select.h>
#include <stdio.h>

// TCP服务器操作函数表
static const server_ops_t tcp_server_ops = {
    .initialize = tcp_server_initialize,
    .start = tcp_server_start,
    .stop = tcp_server_stop,
    .write_data = tcp_server_write_data,
    .cleanup = tcp_server_cleanup
};

// TCP服务器初始化
int tcp_server_initialize(server_config_t* config) {
    if (!config) {
        LOG_ERROR(TCP_SERVER_LOG_TAG, "配置参数为空");
        return -1;
    }

    LOG_INFO(TCP_SERVER_LOG_TAG, "初始化TCP服务器");

    // 检查主机和端口
    if (strlen(config->tcp_host) == 0) {
        LOG_ERROR(TCP_SERVER_LOG_TAG, "TCP主机地址为空");
        return -1;
    }

    if (config->tcp_port <= 0 || config->tcp_port > 65535) {
        LOG_ERROR(TCP_SERVER_LOG_TAG, "无效的TCP端口: %d", config->tcp_port);
        return -1;
    }

    // 初始化客户端连接数组
    for (int i = 0; i < MAX_CLIENTS; i++) {
        g_server_ctx.server_data.tcp_socket.client_fds[i] = -1;
    }
    g_server_ctx.server_data.tcp_socket.client_count = 0;
    g_server_ctx.server_data.tcp_socket.server_fd = -1;

    LOG_INFO(TCP_SERVER_LOG_TAG, "TCP服务器初始化成功，主机: %s，端口: %d",
             config->tcp_host, config->tcp_port);

    return 0;
}

// TCP服务器启动
int tcp_server_start(void) {
    LOG_INFO(TCP_SERVER_LOG_TAG, "启动TCP服务器");

    // 创建socket
    int server_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (server_fd < 0) {
        LOG_ERROR(TCP_SERVER_LOG_TAG, "创建套接字失败: %s", strerror(errno));
        return -1;
    }

    // 设置socket选项
    int opt = 1;
    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        LOG_WARN(TCP_SERVER_LOG_TAG, "设置SO_REUSEADDR失败: %s", strerror(errno));
    }

    // 设置socket为非阻塞模式
    int flags = fcntl(server_fd, F_GETFL, 0);
    if (flags < 0 || fcntl(server_fd, F_SETFL, flags | O_NONBLOCK) < 0) {
        LOG_WARN(TCP_SERVER_LOG_TAG, "设置套接字非阻塞模式失败: %s", strerror(errno));
    }

    // 绑定socket
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(g_server_ctx.config.tcp_port);

    if (inet_pton(AF_INET, g_server_ctx.config.tcp_host, &addr.sin_addr) <= 0) {
        LOG_ERROR(TCP_SERVER_LOG_TAG, "无效的TCP主机地址: %s", g_server_ctx.config.tcp_host);
        close(server_fd);
        return -1;
    }

    if (bind(server_fd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        LOG_ERROR(TCP_SERVER_LOG_TAG, "绑定套接字到 %s:%d 失败: %s",
                  g_server_ctx.config.tcp_host, g_server_ctx.config.tcp_port, strerror(errno));
        close(server_fd);
        return -1;
    }

    // 监听连接
    if (listen(server_fd, MAX_CLIENTS) < 0) {
        LOG_ERROR(TCP_SERVER_LOG_TAG, "监听套接字失败: %s", strerror(errno));
        close(server_fd);
        return -1;
    }

    // 保存服务器文件描述符
    g_server_ctx.server_data.tcp_socket.server_fd = server_fd;

    LOG_INFO(TCP_SERVER_LOG_TAG, "TCP服务器启动成功，地址: %s:%d",
             g_server_ctx.config.tcp_host, g_server_ctx.config.tcp_port);
    return 0;
}

// TCP服务器停止
int tcp_server_stop(void) {
    LOG_INFO(TCP_SERVER_LOG_TAG, "停止TCP服务器");

    // 关闭所有客户端连接
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (g_server_ctx.server_data.tcp_socket.client_fds[i] >= 0) {
            tcp_server_close_client(i);
        }
    }

    // 关闭服务器socket
    if (g_server_ctx.server_data.tcp_socket.server_fd >= 0) {
        close(g_server_ctx.server_data.tcp_socket.server_fd);
        g_server_ctx.server_data.tcp_socket.server_fd = -1;
    }

    g_server_ctx.server_data.tcp_socket.client_count = 0;

    LOG_INFO(TCP_SERVER_LOG_TAG, "TCP服务器已停止");
    return 0;
}

// TCP服务器写入数据
int tcp_server_write_data(const uint8_t* data, size_t size) {
    if (!data || size == 0) {
        LOG_WARN(TCP_SERVER_LOG_TAG, "数据参数无效");
        return -1;
    }

    // 接受新的客户端连接
    tcp_server_accept_clients();

    // 发送数据到所有客户端
    return tcp_server_send_to_clients(data, size);
}

// TCP服务器清理
int tcp_server_cleanup(void) {
    LOG_INFO(TCP_SERVER_LOG_TAG, "清理TCP服务器");

    // 停止服务器
    tcp_server_stop();

    LOG_INFO(TCP_SERVER_LOG_TAG, "TCP服务器清理完成");
    return 0;
}

// 接受客户端连接
int tcp_server_accept_clients(void) {
    if (g_server_ctx.server_data.tcp_socket.server_fd < 0) {
        return -1;
    }
    
    // 使用select检查是否有新连接
    fd_set read_fds;
    struct timeval timeout = {0, 0}; // 非阻塞
    
    FD_ZERO(&read_fds);
    FD_SET(g_server_ctx.server_data.tcp_socket.server_fd, &read_fds);
    
    int result = select(g_server_ctx.server_data.tcp_socket.server_fd + 1, &read_fds, NULL, NULL, &timeout);
    if (result <= 0) {
        return 0; // 没有新连接或出错
    }
    
    if (FD_ISSET(g_server_ctx.server_data.tcp_socket.server_fd, &read_fds)) {
        // 接受新连接
        struct sockaddr_in client_addr;
        socklen_t client_len = sizeof(client_addr);
        int client_fd = accept(g_server_ctx.server_data.tcp_socket.server_fd, 
                              (struct sockaddr*)&client_addr, &client_len);
        if (client_fd < 0) {
            if (errno != EAGAIN && errno != EWOULDBLOCK) {
                LOG_WARN(TCP_SERVER_LOG_TAG, "Failed to accept client: %s", strerror(errno));
            }
            return -1;
        }
        
        // 查找空闲的客户端槽位
        for (int i = 0; i < MAX_CLIENTS; i++) {
            if (g_server_ctx.server_data.tcp_socket.client_fds[i] < 0) {
                g_server_ctx.server_data.tcp_socket.client_fds[i] = client_fd;
                g_server_ctx.server_data.tcp_socket.client_count++;
                
                char client_ip[INET_ADDRSTRLEN];
                inet_ntop(AF_INET, &client_addr.sin_addr, client_ip, INET_ADDRSTRLEN);
                LOG_INFO(TCP_SERVER_LOG_TAG, "新客户端连接来自 %s:%d，fd: %d，总客户端数: %d",
                         client_ip, ntohs(client_addr.sin_port), client_fd,
                         g_server_ctx.server_data.tcp_socket.client_count);
                return 0;
            }
        }

        // 没有空闲槽位，关闭连接
        LOG_WARN(TCP_SERVER_LOG_TAG, "已达到最大客户端数量，拒绝连接");
        close(client_fd);
    }
    
    return 0;
}

// 发送数据到所有客户端
int tcp_server_send_to_clients(const uint8_t* data, size_t size) {
    if (g_server_ctx.server_data.tcp_socket.client_count == 0) {
        LOG_DEBUG(TCP_SERVER_LOG_TAG, "没有客户端连接，跳过数据发送");
        return 0;
    }

    LOG_DEBUG(TCP_SERVER_LOG_TAG, "向 %d 个客户端发送数据，大小: %zu 字节",
              g_server_ctx.server_data.tcp_socket.client_count, size);

    // 准备数据包（4字节长度头 + 数据）
    uint32_t data_length = (uint32_t)size;
    uint8_t header[4];
    header[0] = (uint8_t)(data_length & 0xFF);
    header[1] = (uint8_t)((data_length >> 8) & 0xFF);
    header[2] = (uint8_t)((data_length >> 16) & 0xFF);
    header[3] = (uint8_t)((data_length >> 24) & 0xFF);

    int success_count = 0;

    for (int i = 0; i < MAX_CLIENTS; i++) {
        int client_fd = g_server_ctx.server_data.tcp_socket.client_fds[i];
        if (client_fd < 0) {
            continue;
        }

        // 发送长度头
        ssize_t sent = send(client_fd, header, 4, MSG_NOSIGNAL);
        if (sent != 4) {
            LOG_WARN(TCP_SERVER_LOG_TAG, "向客户端 %d 发送数据头失败: %s",
                     client_fd, strerror(errno));
            tcp_server_close_client(i);
            continue;
        }

        // 发送数据
        sent = send(client_fd, data, size, MSG_NOSIGNAL);
        if (sent != (ssize_t)size) {
            LOG_WARN(TCP_SERVER_LOG_TAG, "向客户端 %d 发送数据失败: %s",
                     client_fd, strerror(errno));
            tcp_server_close_client(i);
            continue;
        }

        success_count++;
    }

    LOG_DEBUG(TCP_SERVER_LOG_TAG, "成功向 %d 个客户端发送数据", success_count);
    return success_count > 0 ? 0 : -1;
}

// 关闭客户端连接
void tcp_server_close_client(int client_index) {
    if (client_index < 0 || client_index >= MAX_CLIENTS) {
        return;
    }

    int client_fd = g_server_ctx.server_data.tcp_socket.client_fds[client_index];
    if (client_fd >= 0) {
        LOG_INFO(TCP_SERVER_LOG_TAG, "关闭客户端连接，fd: %d", client_fd);
        close(client_fd);
        g_server_ctx.server_data.tcp_socket.client_fds[client_index] = -1;
        g_server_ctx.server_data.tcp_socket.client_count--;
    }
}

// 获取TCP服务器操作函数
const server_ops_t* get_tcp_server_ops(void) {
    return &tcp_server_ops;
}
