#ifndef UNIX_CLIENT_H
#define UNIX_CLIENT_H

#include "../core/client_core.h"
#include <sys/socket.h>
#include <sys/un.h>

#define UNIX_CLIENT_LOG_TAG "SERVER-K-UNIX-CLIENT"
#define UNIX_SOCKET_PATH_PREFIX "/data/local/tmp/socket_"

// Unix套接字客户端函数声明
int unix_client_connect(client_config_t* config);
void unix_client_disconnect(void);
client_response_t* unix_client_take_screenshot(void);
int unix_client_cleanup(void);

// 内部辅助函数
int unix_client_read_data(uint8_t** data, size_t* size);

// 获取Unix套接字客户端操作函数
const client_ops_t* get_unix_client_ops(void);

#endif // UNIX_CLIENT_H
