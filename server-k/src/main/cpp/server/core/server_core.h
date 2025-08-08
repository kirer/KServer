#ifndef SERVER_CORE_H
#define SERVER_CORE_H

#include <stdint.h>
#include <stdbool.h>
#include <pthread.h>
#include "../../common/log.h"

#ifdef __ANDROID__
#include <jni.h>
#endif

#define SERVER_LOG_TAG "SERVER-K-CORE"
#define MAX_CLIENTS 10
#define MAX_DATA_SIZE (50 * 1024 * 1024) // 50MB

// 通信模式枚举
typedef enum {
    MODE_SHARED_MEMORY = 0,
    MODE_UNIX_SOCKET = 1,
    MODE_TCP_SOCKET = 2
} server_mode_t;

// 服务器状态枚举
typedef enum {
    SERVER_STATE_UNINITIALIZED = 0,
    SERVER_STATE_INITIALIZED = 1,
    SERVER_STATE_STARTED = 2,
    SERVER_STATE_STOPPED = 3,
    SERVER_STATE_ERROR = 4
} server_state_t;

// 配置结构体
typedef struct {
    server_mode_t mode;
    
    // 共享内存配置
    int memory_size;
    char lib_path[256];
    
    // Unix套接字配置
    char socket_name[256];
    
    // TCP套接字配置
    char tcp_host[256];
    int tcp_port;
} server_config_t;

// 服务器上下文结构体
typedef struct {
    server_config_t config;
    server_state_t state;
    pthread_mutex_t state_mutex;
    pthread_t server_thread;
    bool thread_running;
    
    // 服务器特定数据
    union {
        struct {
            // 共享内存相关
            int shm_fd;
            void* shm_base;
        } shared_memory;
        
        struct {
            // Unix套接字相关
            int server_fd;
            int client_fds[MAX_CLIENTS];
            int client_count;
        } unix_socket;
        
        struct {
            // TCP套接字相关
            int server_fd;
            int client_fds[MAX_CLIENTS];
            int client_count;
        } tcp_socket;
    } server_data;
} server_context_t;

// 全局服务器上下文
extern server_context_t g_server_ctx;

// 核心函数声明
int server_initialize(const char* config_json);
int server_initialize_with_params(server_mode_t mode, int memory_size, const char* lib_path,
                                  const char* socket_name, const char* tcp_host, int tcp_port);
int server_start(void);
int server_stop(void);
int server_write_data(const uint8_t* data, size_t size);
server_state_t server_get_state(void);
const char* server_get_status_info(void);

// 内部辅助函数
int parse_config_json(const char* json, server_config_t* config);
void* server_thread_func(void* arg);
int cleanup_server_resources(void);

// 各种服务器实现的函数指针
typedef struct {
    int (*initialize)(server_config_t* config);
    int (*start)(void);
    int (*stop)(void);
    int (*write_data)(const uint8_t* data, size_t size);
    int (*cleanup)(void);
} server_ops_t;

// 获取服务器操作函数
const server_ops_t* get_server_ops(server_mode_t mode);

#ifdef __ANDROID__
// JNI函数声明

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_initializeWithParams(JNIEnv *env, jclass clazz,
                                                         jint mode, jint memory_size, jstring lib_path,
                                                         jstring socket_name, jstring tcp_host, jint tcp_port);

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_start(JNIEnv *env, jclass clazz);

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_stop(JNIEnv *env, jclass clazz);

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_writeData(JNIEnv *env, jclass clazz, jbyteArray data);

#endif

#endif // SERVER_CORE_H
