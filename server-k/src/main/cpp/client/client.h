#ifndef CLIENT_H
#define CLIENT_H

#include "../common/protocol.h"
#include "../common/log.h"
#include <pthread.h>

#if defined(__ANDROID__) && !defined(DISABLE_JNI)

#include <jni.h>

#endif

#define CLIENT_LOG_TAG "SERVER-K-CLIENT"

// 客户端状态枚举
typedef enum {
    CLIENT_STATE_DISCONNECTED = 0,
    CLIENT_STATE_CONNECTING = 1,
    CLIENT_STATE_CONNECTED = 2,
    CLIENT_STATE_DISCONNECTING = 3
} client_state_t;

// 客户端配置
typedef struct {
    socket_type_t socket_type;
    char address[256];              // TCP: "host:port", Unix: "socket_name"
    int debug;
} client_config_t;

// 消息回调函数
typedef void (*client_message_callback_t)(message_type_t type, const uint8_t *data, uint32_t data_size);

// 客户端实例
typedef struct {
    client_config_t config;
    client_state_t state;
    int socket_fd;
    pthread_t receive_thread;
    int should_stop;
    // 回调函数
    client_message_callback_t on_message; // 消息回调
} client_t;

// 全局客户端实例
extern client_t g_client;

/**
 * 初始化客户端
 * @param config 客户端配置
 * @return 0成功，-1失败
 */
int client_init(const client_config_t *config);

/**
 * 连接到服务器
 * @return 0成功，-1失败
 */
int client_connect(void);

/**
 * 断开与服务器的连接
 * @return 0成功，-1失败
 */
int client_disconnect(void);

/**
 * 获取客户端状态
 * @return 客户端状态
 */
client_state_t client_get_state(void);

/**
 * 发送心跳消息
 * @return 0成功，-1失败
 */
int client_send_heartbeat(void);

/**
 * 清理客户端资源
 * @return 0成功，-1失败
 */
int client_cleanup(void);

/**
 * 解析地址字符串
 * @param address 地址字符串
 * @param host 输出主机名（TCP模式）
 * @param port 输出端口（TCP模式）
 * @param socket_name 输出socket名称（Unix模式）
 * @param socket_type socket类型
 * @return 0成功，-1失败
 */
int client_parse_address(const char *address, char *host, int *port, char *socket_name,
                         socket_type_t socket_type);

// 发送消息
int client_send_message(message_type_t type, const void *data, uint32_t data_size);

// 设置消息回调函数
void client_set_message_callback(client_message_callback_t callback);

static int client_create_tcp_connection(const char *host, int port);

static int client_create_unix_connection(const char *socket_name);

static int client_connect_socket(void);

static int client_send_init_request(void);

static int client_handle_init_response(const control_message_t *msg);

static void *client_receive_thread(void *arg);

static int client_recv_message(control_message_t **message);

#endif // CLIENT_H