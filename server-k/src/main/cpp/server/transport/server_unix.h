#ifndef UNIX_SERVER_H
#define UNIX_SERVER_H

#include "../core/server_core.h"
#include <sys/socket.h>
#include <sys/un.h>

#define UNIX_SERVER_LOG_TAG "SERVER-K-UNIX"
#define UNIX_SOCKET_PATH_PREFIX "/data/local/tmp/socket_"

// Unix套接字服务器函数声明
int unix_server_initialize(server_config_t* config);
int unix_server_start(void);
int unix_server_stop(void);
int unix_server_write_data(const uint8_t* data, size_t size);
int unix_server_cleanup(void);

// 内部辅助函数
int unix_server_accept_clients(void);
int unix_server_send_to_clients(const uint8_t* data, size_t size);
void unix_server_close_client(int client_index);

// 获取Unix套接字服务器操作函数
const server_ops_t* get_unix_server_ops(void);

#endif // UNIX_SERVER_H
