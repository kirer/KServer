#include "server_unix.h"
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/select.h>
#include <stdio.h>

// Unix套接字服务器操作函数表
static const server_ops_t unix_server_ops = {
    .initialize = unix_server_initialize,
    .start = unix_server_start,
    .stop = unix_server_stop,
    .write_data = unix_server_write_data,
    .cleanup = unix_server_cleanup
};

// Unix套接字服务器初始化
int unix_server_initialize(server_config_t* config) {
    if (!config) {
        LOG_ERROR(UNIX_SERVER_LOG_TAG, "配置参数为空");
        return -1;
    }

    LOG_INFO(UNIX_SERVER_LOG_TAG, "初始化Unix套接字服务器");

    // 检查socket名称
    if (strlen(config->socket_name) == 0) {
        LOG_ERROR(UNIX_SERVER_LOG_TAG, "套接字名称为空");
        return -1;
    }

    // 初始化客户端连接数组
    for (int i = 0; i < MAX_CLIENTS; i++) {
        g_server_ctx.server_data.unix_socket.client_fds[i] = -1;
    }
    g_server_ctx.server_data.unix_socket.client_count = 0;
    g_server_ctx.server_data.unix_socket.server_fd = -1;

    LOG_INFO(UNIX_SERVER_LOG_TAG, "Unix套接字服务器初始化成功，套接字名称: %s",
             config->socket_name);

    return 0;
}

// Unix套接字服务器启动
int unix_server_start(void) {
    LOG_INFO(UNIX_SERVER_LOG_TAG, "启动Unix套接字服务器");

    // 创建socket
    LOG_DEBUG(UNIX_SERVER_LOG_TAG, "创建Unix套接字...");
    int server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd < 0) {
        LOG_ERROR(UNIX_SERVER_LOG_TAG, "创建套接字失败: %s", strerror(errno));
        return -1;
    }
    LOG_DEBUG(UNIX_SERVER_LOG_TAG, "Unix套接字创建成功，fd: %d", server_fd);

    // 设置socket为非阻塞模式
    LOG_DEBUG(UNIX_SERVER_LOG_TAG, "设置套接字为非阻塞模式...");
    int flags = fcntl(server_fd, F_GETFL, 0);
    if (flags < 0 || fcntl(server_fd, F_SETFL, flags | O_NONBLOCK) < 0) {
        LOG_WARN(UNIX_SERVER_LOG_TAG, "设置套接字非阻塞模式失败: %s", strerror(errno));
    } else {
        LOG_DEBUG(UNIX_SERVER_LOG_TAG, "套接字非阻塞模式设置成功");
    }

    // 使用Abstract Unix Socket（不需要文件系统权限）
    LOG_DEBUG(UNIX_SERVER_LOG_TAG, "使用Abstract Unix Socket: @%s", g_server_ctx.config.socket_name);

    // 绑定socket
    LOG_DEBUG(UNIX_SERVER_LOG_TAG, "绑定Abstract Unix套接字...");
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    // Abstract socket: 第一个字符为'\0'，后面跟socket名称
    addr.sun_path[0] = '\0';
    strncpy(addr.sun_path + 1, g_server_ctx.config.socket_name, sizeof(addr.sun_path) - 2);

    // Abstract socket的长度计算
    int addr_len = sizeof(addr.sun_family) + 1 + strlen(g_server_ctx.config.socket_name);

    if (bind(server_fd, (struct sockaddr*)&addr, addr_len) < 0) {
        LOG_ERROR(UNIX_SERVER_LOG_TAG, "绑定Abstract套接字 @%s 失败: %s",
                  g_server_ctx.config.socket_name, strerror(errno));
        close(server_fd);
        return -1;
    }
    LOG_DEBUG(UNIX_SERVER_LOG_TAG, "Unix套接字绑定成功");

    // 监听连接
    LOG_DEBUG(UNIX_SERVER_LOG_TAG, "开始监听连接，最大客户端数: %d", MAX_CLIENTS);
    if (listen(server_fd, MAX_CLIENTS) < 0) {
        LOG_ERROR(UNIX_SERVER_LOG_TAG, "监听套接字失败: %s", strerror(errno));
        close(server_fd);
        // Abstract socket不需要unlink
        return -1;
    }
    LOG_DEBUG(UNIX_SERVER_LOG_TAG, "Unix套接字监听成功");

    // 保存服务器文件描述符
    g_server_ctx.server_data.unix_socket.server_fd = server_fd;

    LOG_INFO(UNIX_SERVER_LOG_TAG, "Abstract Unix套接字服务器启动成功，名称: @%s", g_server_ctx.config.socket_name);
    return 0;
}

// Unix套接字服务器停止
int unix_server_stop(void) {
    LOG_INFO(UNIX_SERVER_LOG_TAG, "停止Unix套接字服务器");

    // 关闭所有客户端连接
    for (int i = 0; i < MAX_CLIENTS; i++) {
        if (g_server_ctx.server_data.unix_socket.client_fds[i] >= 0) {
            unix_server_close_client(i);
        }
    }

    // 关闭服务器socket
    if (g_server_ctx.server_data.unix_socket.server_fd >= 0) {
        close(g_server_ctx.server_data.unix_socket.server_fd);
        g_server_ctx.server_data.unix_socket.server_fd = -1;
    }

    // 删除socket文件
    char socket_path[256];
    snprintf(socket_path, sizeof(socket_path), "%s%s",
             UNIX_SOCKET_PATH_PREFIX, g_server_ctx.config.socket_name);
    unlink(socket_path);

    g_server_ctx.server_data.unix_socket.client_count = 0;

    LOG_INFO(UNIX_SERVER_LOG_TAG, "Unix套接字服务器已停止");
    return 0;
}

// Unix套接字服务器写入数据
int unix_server_write_data(const uint8_t* data, size_t size) {
    if (!data || size == 0) {
        LOG_WARN(UNIX_SERVER_LOG_TAG, "数据参数无效");
        return -1;
    }

    // 接受新的客户端连接
    unix_server_accept_clients();

    // 发送数据到所有客户端
    return unix_server_send_to_clients(data, size);
}

// Unix套接字服务器清理
int unix_server_cleanup(void) {
    LOG_INFO(UNIX_SERVER_LOG_TAG, "清理Unix套接字服务器");

    // 停止服务器
    unix_server_stop();

    LOG_INFO(UNIX_SERVER_LOG_TAG, "Unix套接字服务器清理完成");
    return 0;
}

// 接受客户端连接
int unix_server_accept_clients(void) {
    if (g_server_ctx.server_data.unix_socket.server_fd < 0) {
        return -1;
    }
    
    // 使用select检查是否有新连接
    fd_set read_fds;
    struct timeval timeout = {0, 0}; // 非阻塞
    
    FD_ZERO(&read_fds);
    FD_SET(g_server_ctx.server_data.unix_socket.server_fd, &read_fds);
    
    int result = select(g_server_ctx.server_data.unix_socket.server_fd + 1, &read_fds, NULL, NULL, &timeout);
    if (result <= 0) {
        return 0; // 没有新连接或出错
    }
    
    if (FD_ISSET(g_server_ctx.server_data.unix_socket.server_fd, &read_fds)) {
        // 接受新连接
        int client_fd = accept(g_server_ctx.server_data.unix_socket.server_fd, NULL, NULL);
        if (client_fd < 0) {
            if (errno != EAGAIN && errno != EWOULDBLOCK) {
                LOG_WARN(UNIX_SERVER_LOG_TAG, "接受客户端连接失败: %s", strerror(errno));
            }
            return -1;
        }

        // 查找空闲的客户端槽位
        for (int i = 0; i < MAX_CLIENTS; i++) {
            if (g_server_ctx.server_data.unix_socket.client_fds[i] < 0) {
                g_server_ctx.server_data.unix_socket.client_fds[i] = client_fd;
                g_server_ctx.server_data.unix_socket.client_count++;
                LOG_INFO(UNIX_SERVER_LOG_TAG, "新客户端连接，fd: %d，总客户端数: %d",
                         client_fd, g_server_ctx.server_data.unix_socket.client_count);
                return 0;
            }
        }

        // 没有空闲槽位，关闭连接
        LOG_WARN(UNIX_SERVER_LOG_TAG, "已达到最大客户端数量，拒绝连接");
        close(client_fd);
    }
    
    return 0;
}

// 发送数据到所有客户端
int unix_server_send_to_clients(const uint8_t* data, size_t size) {
    if (g_server_ctx.server_data.unix_socket.client_count == 0) {
        LOG_DEBUG(UNIX_SERVER_LOG_TAG, "没有客户端连接，跳过数据发送");
        return 0;
    }

    LOG_DEBUG(UNIX_SERVER_LOG_TAG, "向 %d 个客户端发送数据，大小: %zu 字节",
              g_server_ctx.server_data.unix_socket.client_count, size);

    // 准备数据包（4字节长度头 + 数据）
    uint32_t data_length = (uint32_t)size;
    uint8_t header[4];
    header[0] = (uint8_t)(data_length & 0xFF);
    header[1] = (uint8_t)((data_length >> 8) & 0xFF);
    header[2] = (uint8_t)((data_length >> 16) & 0xFF);
    header[3] = (uint8_t)((data_length >> 24) & 0xFF);

    int success_count = 0;

    for (int i = 0; i < MAX_CLIENTS; i++) {
        int client_fd = g_server_ctx.server_data.unix_socket.client_fds[i];
        if (client_fd < 0) {
            continue;
        }

        // 发送长度头
        ssize_t sent = send(client_fd, header, 4, MSG_NOSIGNAL);
        if (sent != 4) {
            LOG_WARN(UNIX_SERVER_LOG_TAG, "向客户端 %d 发送数据头失败: %s",
                     client_fd, strerror(errno));
            unix_server_close_client(i);
            continue;
        }

        // 发送数据
        sent = send(client_fd, data, size, MSG_NOSIGNAL);
        if (sent != (ssize_t)size) {
            LOG_WARN(UNIX_SERVER_LOG_TAG, "向客户端 %d 发送数据失败: %s",
                     client_fd, strerror(errno));
            unix_server_close_client(i);
            continue;
        }

        success_count++;
    }

    LOG_DEBUG(UNIX_SERVER_LOG_TAG, "成功向 %d 个客户端发送数据", success_count);
    return success_count > 0 ? 0 : -1;
}

// 关闭客户端连接
void unix_server_close_client(int client_index) {
    if (client_index < 0 || client_index >= MAX_CLIENTS) {
        return;
    }

    int client_fd = g_server_ctx.server_data.unix_socket.client_fds[client_index];
    if (client_fd >= 0) {
        LOG_INFO(UNIX_SERVER_LOG_TAG, "关闭客户端连接，fd: %d", client_fd);
        close(client_fd);
        g_server_ctx.server_data.unix_socket.client_fds[client_index] = -1;
        g_server_ctx.server_data.unix_socket.client_count--;
    }
}

// 获取Unix套接字服务器操作函数
const server_ops_t* get_unix_server_ops(void) {
    return &unix_server_ops;
}
