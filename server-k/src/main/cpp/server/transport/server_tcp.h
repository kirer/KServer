#ifndef TCP_SERVER_H
#define TCP_SERVER_H

#include "../core/server_core.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define TCP_SERVER_LOG_TAG "SERVER-K-TCP"

// TCP服务器函数声明
int tcp_server_initialize(server_config_t* config);
int tcp_server_start(void);
int tcp_server_stop(void);
int tcp_server_write_data(const uint8_t* data, size_t size);
int tcp_server_cleanup(void);

// 内部辅助函数
int tcp_server_accept_clients(void);
int tcp_server_send_to_clients(const uint8_t* data, size_t size);
void tcp_server_close_client(int client_index);

// 获取TCP服务器操作函数
const server_ops_t* get_tcp_server_ops(void);

#endif // TCP_SERVER_H
