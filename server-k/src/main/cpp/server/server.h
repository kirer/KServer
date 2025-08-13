#ifndef SERVER_H
#define SERVER_H

#include "../common/protocol.h"
#include "../common/shared_memory.h"
#include "../common/log.h"
#include <pthread.h>

#ifdef __ANDROID__

#include <jni.h>

#endif

#define SERVER_LOG_TAG "SERVER-K-SERVER"

// 服务器状态枚举
typedef enum {
    SERVER_STATE_STOPPED = 0,
    SERVER_STATE_STARTING = 1,
    SERVER_STATE_RUNNING = 2,
    SERVER_STATE_STOPPING = 3
} server_state_t;

// 客户端连接信息
typedef struct {
    int socket_fd;
    pthread_t thread;
    int active;
    int should_stop; // 通知线程退出
} client_connection_t;

// 服务器配置
typedef struct {
    socket_type_t socket_type;
    char address[256];              // TCP: "host:port", Unix: "socket_name"
    int debug;
} server_config_t;

// 消息回调函数
typedef void (*server_message_callback_t)(message_type_t type, const uint8_t *data, uint32_t data_size);

// 服务器实例
typedef struct {
    server_config_t config;
    server_state_t state;
    int listen_fd;
    pthread_t listen_thread;
    client_connection_t clients[8]; // 最多支持8个客户端
    int client_count;
    pthread_mutex_t clients_mutex;
    int should_stop;
    server_message_callback_t on_message; // 消息回调
} server_t;

// 全局服务器实例
extern server_t g_server;

/**
 * 初始化服务器
 * @param config 服务器配置
 * @return 0成功，-1失败
 */
int server_init(const server_config_t *config);

/**
 * 启动服务器
 * @return 0成功，-1失败
 */
int server_start(void);

/**
 * 停止服务器
 * @return 0成功，-1失败
 */
int server_stop(void);

/**
 * 获取服务器状态
 * @return 服务器状态
 */
server_state_t server_get_state(void);

/**
 * 通知所有客户端有新截图可用
 * @param timestamp 截图时间戳
 * @param data_size 截图数据大小
 * @return 0成功，-1失败
 */
int server_notify_screenshot(uint64_t timestamp, uint32_t data_size);

/**
 * 写入截图数据到共享内存并通知客户端
 * @param data 截图数据
 * @param size 数据大小
 * @return 0成功，-1失败
 */
int server_write_screenshot(const void *data, size_t size);

/**
 * 清理服务器资源
 * @return 0成功，-1失败
 */
int server_cleanup(void);

/**
 * 解析地址字符串
 * @param address 地址字符串
 * @param host 输出主机名（TCP模式）
 * @param port 输出端口（TCP模式）
 * @param socket_name 输出socket名称（Unix模式）
 * @param socket_type socket类型
 * @return 0成功，-1失败
 */
int server_parse_address(const char *address, char *host, int *port, char *socket_name,
                         socket_type_t socket_type);

// 内部函数声明
static void *server_listen_thread(void *arg);

static void *server_client_thread(void *arg);

static int server_handle_client(int client_fd);

static int server_send_message(int fd, message_type_t type, const void *data, uint32_t data_size);

static int server_recv_message(int fd, control_message_t **message);

static int server_create_tcp_socket(const char *host, int port);

static int server_create_unix_socket(const char *socket_name);

static void server_close_client(client_connection_t *client);

static void server_close_all_clients(void);

#endif // SERVER_H