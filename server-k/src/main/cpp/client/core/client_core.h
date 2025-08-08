#ifndef CLIENT_CORE_H
#define CLIENT_CORE_H

#include <stdint.h>
#include <stdbool.h>
#include <pthread.h>
#include "../../common/log.h"

#if defined(__ANDROID__) && !defined(DISABLE_JNI)

#include <jni.h>

#endif

#define CLIENT_LOG_TAG "SERVER-K-CLIENT"
#define MAX_DATA_SIZE (50 * 1024 * 1024) // 50MB
#define CONNECTION_TIMEOUT 5000 // 5秒连接超时
#define READ_TIMEOUT 10000 // 10秒读取超时

// 通信模式枚举
typedef enum {
    CLIENT_MODE_SHARED_MEMORY = 0,
    CLIENT_MODE_UNIX_SOCKET = 1,
    CLIENT_MODE_TCP_SOCKET = 2
} client_mode_t;

// 客户端状态枚举
typedef enum {
    CLIENT_STATUS_DISCONNECTED = 0,
    CLIENT_STATUS_CONNECTING = 1,
    CLIENT_STATUS_CONNECTED = 2,
    CLIENT_STATUS_ERROR = 3
} client_status_t;

// 客户端配置结构体
typedef struct {
    client_mode_t mode;

    // 共享内存配置
    int memory_size;

    // Unix套接字配置
    char socket_name[256];

    // TCP套接字配置
    char tcp_host[256];
    int tcp_port;
} client_config_t;

// 响应结构体
typedef struct {
    bool success;
    char message[512];
    uint8_t *data;
    size_t data_size;
    long processing_time;
} client_response_t;

// 客户端上下文结构体
typedef struct {
    client_config_t config;
    client_status_t status;
    pthread_mutex_t status_mutex;

    // 客户端特定数据
    union {
        struct {
            // 共享内存相关
            int shm_fd;
            void *shm_base;
            bool initialized;
        } shared_memory;

        struct {
            // Unix套接字相关
            int socket_fd;
        } unix_socket;

        struct {
            // TCP套接字相关
            int socket_fd;
        } tcp_socket;
    } client_data;
} client_context_t;

// 全局客户端上下文
extern client_context_t g_client_ctx;

// 核心函数声明
int client_initialize(const char *config_json);

int client_initialize_with_params(client_mode_t mode, int memory_size,
                                  const char *socket_name, const char *tcp_host, int tcp_port);

int client_connect(void);

void client_disconnect(void);

client_response_t *client_take_screenshot(void);

const char *client_get_status_info(void);

int cleanup_client_resources(void);

void free_client_response(client_response_t *response);

// 各种客户端实现的函数指针
typedef struct {
    int (*connect)(client_config_t *config);

    void (*disconnect)(void);

    client_response_t *(*take_screenshot)(void);

    int (*cleanup)(void);
} client_ops_t;

// 获取客户端操作函数
const client_ops_t *get_client_ops(client_mode_t mode);

#if defined(__ANDROID__) && !defined(DISABLE_JNI)
// JNI函数声明
JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_initializeWithParams(JNIEnv *env, jclass clazz,
                                                         jint mode, jint memory_size,
                                                         jstring socket_name, jstring tcp_host,
                                                         jint tcp_port);

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_connect(JNIEnv *env, jclass clazz);

JNIEXPORT void JNICALL
Java_com_github_kirer_server_Client_disconnect(JNIEnv *env, jclass clazz);

JNIEXPORT jobject JNICALL
Java_com_github_kirer_server_Client_takeScreenshot(JNIEnv *env, jclass clazz);

JNIEXPORT jstring JNICALL
Java_com_github_kirer_server_Client_getStatusInfo(JNIEnv *env, jclass clazz);

#endif

#endif // CLIENT_CORE_H
