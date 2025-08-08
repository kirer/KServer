#ifndef TCP_CLIENT_H
#define TCP_CLIENT_H

#include "../core/client_core.h"
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define TCP_CLIENT_LOG_TAG "SERVER-K-TCP-CLIENT"

// TCP客户端函数声明
int tcp_client_connect(client_config_t* config);
void tcp_client_disconnect(void);
client_response_t* tcp_client_take_screenshot(void);
int tcp_client_cleanup(void);

// 内部辅助函数
int tcp_client_read_data(uint8_t** data, size_t* size);

// 获取TCP客户端操作函数
const client_ops_t* get_tcp_client_ops(void);

#endif // TCP_CLIENT_H
