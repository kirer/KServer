#ifndef CLIENT_H
#define CLIENT_H

#include "../common/protocol.h"
#include "../common/shared_memory.h"
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

// 服务器信息（从初始化响应获取）
typedef struct {
    uint32_t shm_size;
    uint32_t screen_width;
    uint32_t screen_height;
    uint32_t pixel_format;
    uint32_t server_version;
} server_info_t;

// 客户端实例
typedef struct {
    client_config_t config;
    client_state_t state;
    int socket_fd;
    server_info_t server_info;
    pthread_t receive_thread;
    int should_stop;
    
    // 回调函数
    void (*on_screenshot)(const uint8_t *data, size_t size, uint64_t timestamp, void *user_data);
    void (*on_error)(int error_code, const char *message, void *user_data);
    void *user_data;
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
 * 获取服务器信息
 * @return 服务器信息指针，未连接时返回NULL
 */
const server_info_t* client_get_server_info(void);

/**
 * 设置截图回调函数
 * @param callback 回调函数
 * @param user_data 用户数据
 */
void client_set_screenshot_callback(void (*callback)(const uint8_t *data, size_t size, uint64_t timestamp, void *user_data), void *user_data);

/**
 * 设置错误回调函数
 * @param callback 回调函数
 * @param user_data 用户数据
 */
void client_set_error_callback(void (*callback)(int error_code, const char *message, void *user_data), void *user_data);

/**
 * 手动读取一次截图数据
 * @param data 输出数据指针（调用者负责释放）
 * @param size 输出数据大小
 * @param timestamp 输出时间戳
 * @return 0成功，1无新数据，-1失败
 */
int client_read_screenshot(uint8_t **data, size_t *size, uint64_t *timestamp);

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
int client_parse_address(const char *address, char *host, int *port, char *socket_name, socket_type_t socket_type);

// 内部函数声明
static int client_connect_socket(void);
static int client_send_init_request(void);
static int client_handle_init_response(const control_message_t *msg);
static void* client_receive_thread(void *arg);
static int client_send_message(message_type_t type, const void *data, uint32_t data_size);
static int client_recv_message(control_message_t **message);
static int client_create_tcp_connection(const char *host, int port);
static int client_create_unix_connection(const char *socket_name);
static void client_handle_screenshot_notify(const control_message_t *msg);
static void client_handle_error_message(const control_message_t *msg);

#if defined(__ANDROID__) && !defined(DISABLE_JNI)
// JNI接口

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_initialize(JNIEnv *env, jclass clazz, jint socket_type, jstring address, jboolean debug);

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_connect(JNIEnv *env, jclass clazz);

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_disconnect(JNIEnv *env, jclass clazz);

JNIEXPORT jbyteArray JNICALL
Java_com_github_kirer_server_Client_readScreenshot(JNIEnv *env, jclass clazz);

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_getState(JNIEnv *env, jclass clazz);

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_clean(JNIEnv *env, jclass clazz);

#endif

#endif // CLIENT_H

