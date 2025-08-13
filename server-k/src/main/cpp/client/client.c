#include "client.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <signal.h>

// 全局客户端实例
client_t g_client = {0};

// 解析地址字符串
int client_parse_address(const char *address, char *host, int *port, char *socket_name,
                         socket_type_t socket_type) {
    if (!address) {
        return -1;
    }
    if (socket_type == SOCKET_TYPE_TCP) {
        // TCP格式: "host:port"
        const char *colon = strchr(address, ':');
        if (!colon) {
            return -1;
        }
        size_t host_len = colon - address;
        if (host_len >= 256) {
            return -1;
        }
        strncpy(host, address, host_len);
        host[host_len] = '\0';
        *port = atoi(colon + 1);
        if (*port <= 0 || *port > 65535) {
            return -1;
        }
    } else if (socket_type == SOCKET_TYPE_UNIX) {
        // Unix格式: "socket_name"
        if (strlen(address) >= 256) {
            return -1;
        }
        strcpy(socket_name, address);
    } else {
        return -1;
    }
    return 0;
}

// 创建TCP连接
static int client_create_tcp_connection(const char *host, int port) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "创建TCP socket失败: %s", strerror(errno));
        return -1;
    }

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);

    if (inet_pton(AF_INET, host, &addr.sin_addr) <= 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "无效的IP地址: %s", host);
        close(fd);
        return -1;
    }

    if (connect(fd, (struct sockaddr *) &addr, sizeof(addr)) < 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "连接TCP服务器失败 %s:%d: %s", host, port, strerror(errno));
        close(fd);
        return -1;
    }

    LOG_INFO(CLIENT_LOG_TAG, "TCP连接成功 %s:%d", host, port);
    return fd;
}

// 创建Unix连接
static int client_create_unix_connection(const char *socket_name) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "创建Unix socket失败: %s", strerror(errno));
        return -1;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    // 构建socket路径
    snprintf(addr.sun_path, sizeof(addr.sun_path), "/data/local/tmp/%s", socket_name);

    if (connect(fd, (struct sockaddr *) &addr, sizeof(addr)) < 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "连接Unix socket失败 %s: %s", addr.sun_path, strerror(errno));
        close(fd);
        return -1;
    }

    LOG_INFO(CLIENT_LOG_TAG, "Unix连接成功 %s", addr.sun_path);
    return fd;
}

// 发送控制消息
int client_send_message(message_type_t type, const void *data, uint32_t data_size) {
    if (g_client.socket_fd < 0) {
        return -1;
    }
    size_t total_size = sizeof(control_message_t) + data_size;
    control_message_t *msg = (control_message_t *) malloc(total_size);
    if (!msg) {
        return -1;
    }
    msg->type = type;
    msg->data_size = data_size;
    if (data && data_size > 0) {
        memcpy(msg->data, data, data_size);
    }
    ssize_t sent = send(g_client.socket_fd, msg, total_size, 0);
    free(msg);
    if (sent != (ssize_t) total_size) {
        LOG_ERROR(CLIENT_LOG_TAG, "发送消息失败: %s", strerror(errno));
        return -1;
    }
    return 0;
}

// 接收控制消息
static int client_recv_message(control_message_t **message) {
    if (!message || g_client.socket_fd < 0) {
        return -1;
    }
    // 先接收消息头
    control_message_t header;
    ssize_t received = recv(g_client.socket_fd, &header, sizeof(header), 0);
    if (received != sizeof(header)) {
        if (received == 0) {
            LOG_DEBUG(CLIENT_LOG_TAG, "服务器断开连接");
        } else {
            LOG_ERROR(CLIENT_LOG_TAG, "接收消息头失败: %s", strerror(errno));
        }
        return -1;
    }
    // 分配完整消息内存
    size_t total_size = sizeof(control_message_t) + header.data_size;
    control_message_t *msg = (control_message_t *) malloc(total_size);
    if (!msg) {
        return -1;
    }
    // 复制头部
    memcpy(msg, &header, sizeof(header));
    // 接收数据部分
    if (header.data_size > 0) {
        received = recv(g_client.socket_fd, msg->data, header.data_size, 0);
        if (received != (ssize_t) header.data_size) {
            LOG_ERROR(CLIENT_LOG_TAG, "接收消息数据失败: %s", strerror(errno));
            free(msg);
            return -1;
        }
    }
    *message = msg;
    return 0;
}

// 发送初始化请求
static int client_send_init_request(void) {
    init_request_data_t request = {
            .client_version = PROTOCOL_VERSION,
            .reserved = 0
    };
    return client_send_message(MSG_TYPE_INIT_REQ, &request, sizeof(request));
}

// 处理初始化响应
static int client_handle_init_response(const control_message_t *msg) {
    if (!msg || msg->data_size != sizeof(init_response_data_t)) {
        LOG_ERROR(CLIENT_LOG_TAG, "初始化响应格式错误");
        return -1;
    }
    const init_response_data_t *response = (const init_response_data_t *) msg->data;
    LOG_INFO(CLIENT_LOG_TAG, "收到服务器初始化：%d", response->server_version);
    LOG_INFO(CLIENT_LOG_TAG, "初始化完成");
    return 0;
}

// 接收线程
static void *client_receive_thread(void *arg) {
    LOG_INFO(CLIENT_LOG_TAG, "接收线程启动");
    while (!g_client.should_stop && g_client.state == CLIENT_STATE_CONNECTED) {
        control_message_t *msg = NULL;
        if (client_recv_message(&msg) < 0) {
            if (!g_client.should_stop) {
                LOG_ERROR(CLIENT_LOG_TAG, "接收消息失败，连接可能已断开");
            }
            break;
        }
        switch (msg->type) {
            case MSG_TYPE_HEARTBEAT:
                LOG_DEBUG(CLIENT_LOG_TAG, "收到心跳响应");
                break;
            default:
                LOG_WARN(CLIENT_LOG_TAG, "收到消息类型: %d", msg->type);
                if (g_client.on_message) {
                    g_client.on_message(msg->type, msg->data, msg->data_size);
                }
                break;
        }
        free(msg);
    }
    LOG_INFO(CLIENT_LOG_TAG, "接收线程结束");
    return NULL;
}

// 连接socket
static int client_connect_socket(void) {
    // 解析地址
    char host[256] = {0};
    int port = 0;
    char socket_name[256] = {0};
    if (client_parse_address(g_client.config.address, host, &port, socket_name,
                             g_client.config.socket_type) < 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "解析地址失败: %s", g_client.config.address);
        return -1;
    }
    // 创建连接
    if (g_client.config.socket_type == SOCKET_TYPE_TCP) {
        g_client.socket_fd = client_create_tcp_connection(host, port);
    } else if (g_client.config.socket_type == SOCKET_TYPE_UNIX) {
        g_client.socket_fd = client_create_unix_connection(socket_name);
    } else {
        LOG_ERROR(CLIENT_LOG_TAG, "不支持的socket类型: %d", g_client.config.socket_type);
        return -1;
    }
    return g_client.socket_fd >= 0 ? 0 : -1;
}

// 初始化客户端
int client_init(const client_config_t *config) {
    if (!config) {
        return -1;
    }
    LOG_INFO(CLIENT_LOG_TAG, "初始化客户端: %d，%s", config->socket_type, config->address);
    // 清理现有状态
    client_cleanup();
    // 复制配置
    memcpy(&g_client.config, config, sizeof(client_config_t));
    // 初始化状态
    g_client.state = CLIENT_STATE_DISCONNECTED;
    g_client.socket_fd = -1;
    g_client.receive_thread = 0;
    g_client.should_stop = 0;
    g_client.on_message = NULL;
    LOG_INFO(CLIENT_LOG_TAG, "客户端初始化成功");
    return 0;
}

// 连接到服务器
int client_connect(void) {
    if (g_client.state != CLIENT_STATE_DISCONNECTED) {
        LOG_WARN(CLIENT_LOG_TAG, "客户端已连接或正在连接中");
        return -1;
    }
    LOG_INFO(CLIENT_LOG_TAG, "连接到服务器");
    g_client.state = CLIENT_STATE_CONNECTING;
    // 连接socket
    if (client_connect_socket() < 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "连接socket失败");
        g_client.state = CLIENT_STATE_DISCONNECTED;
        return -1;
    }
    // 发送初始化请求
    if (client_send_init_request() < 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "发送初始化请求失败");
        close(g_client.socket_fd);
        g_client.socket_fd = -1;
        g_client.state = CLIENT_STATE_DISCONNECTED;
        return -1;
    }
    // 等待初始化响应
    control_message_t *msg = NULL;
    if (client_recv_message(&msg) < 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "接收初始化响应失败");
        close(g_client.socket_fd);
        g_client.socket_fd = -1;
        g_client.state = CLIENT_STATE_DISCONNECTED;
        return -1;
    }
    if (msg->type != MSG_TYPE_INIT_RESP) {
        LOG_ERROR(CLIENT_LOG_TAG, "收到非初始化响应消息: %d", msg->type);
        free(msg);
        close(g_client.socket_fd);
        g_client.socket_fd = -1;
        g_client.state = CLIENT_STATE_DISCONNECTED;
        return -1;
    }
    // 处理初始化响应
    if (client_handle_init_response(msg) < 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "处理初始化响应失败");
        free(msg);
        close(g_client.socket_fd);
        g_client.socket_fd = -1;
        g_client.state = CLIENT_STATE_DISCONNECTED;
        return -1;
    }
    free(msg);
    // 启动接收线程
    g_client.should_stop = 0;
    if (pthread_create(&g_client.receive_thread, NULL, client_receive_thread, NULL) != 0) {
        LOG_ERROR(CLIENT_LOG_TAG, "创建接收线程失败: %s", strerror(errno));
        close(g_client.socket_fd);
        g_client.socket_fd = -1;
        g_client.state = CLIENT_STATE_DISCONNECTED;
        return -1;
    }
    g_client.state = CLIENT_STATE_CONNECTED;
    LOG_INFO(CLIENT_LOG_TAG, "连接成功");
    return 0;
}

// 断开与服务器的连接
int client_disconnect(void) {
    if (g_client.state != CLIENT_STATE_CONNECTED) {
        LOG_WARN(CLIENT_LOG_TAG, "客户端未连接");
        return -1;
    }
    LOG_INFO(CLIENT_LOG_TAG, "断开连接");
    g_client.state = CLIENT_STATE_DISCONNECTING;
    // 发送断开连接消息
    client_send_message(MSG_TYPE_DISCONNECT, NULL, 0);
    // 停止接收线程
    g_client.should_stop = 1;
    // 关闭socket
    if (g_client.socket_fd >= 0) {
        close(g_client.socket_fd);
        g_client.socket_fd = -1;
    }
    // 等待接收线程结束
    if (g_client.receive_thread != 0) {
        pthread_join(g_client.receive_thread, NULL);
        g_client.receive_thread = 0;
    }
    g_client.state = CLIENT_STATE_DISCONNECTED;
    LOG_INFO(CLIENT_LOG_TAG, "断开连接成功");
    return 0;
}

// 设置消息回调
void client_set_message_callback(client_message_callback_t callback) {
    g_client.on_message = callback;
}

// 获取客户端状态
client_state_t client_get_state(void) {
    return g_client.state;
}

// 发送心跳消息
int client_send_heartbeat(void) {
    if (g_client.state != CLIENT_STATE_CONNECTED) {
        return -1;
    }
    return client_send_message(MSG_TYPE_HEARTBEAT, NULL, 0);
}

// 清理客户端资源
int client_cleanup(void) {
    LOG_INFO(CLIENT_LOG_TAG, "清理客户端资源");
    // 断开连接
    if (g_client.state == CLIENT_STATE_CONNECTED) {
        client_disconnect();
    }
    // 重置状态
    memset(&g_client, 0, sizeof(g_client));
    g_client.socket_fd = -1;
    LOG_INFO(CLIENT_LOG_TAG, "客户端资源清理完成");
    return 0;
}

#if defined(__ANDROID__) && !defined(DISABLE_JNI)
static jobject g_java_callback = NULL;
static JavaVM *g_java_vm = NULL;

// 全局回调函数指针
static void
c_message_callback(message_type_t type, const uint8_t *data, uint32_t size) {
    if (!g_java_callback) return;
    JNIEnv *env;
    (*g_java_vm)->AttachCurrentThread(g_java_vm, (void **) &env, NULL);
    jclass cls = (*env)->GetObjectClass(env, g_java_callback);
    jmethodID mid = (*env)->GetMethodID(env, cls, "onMessage", "(I[B)V");
    if (!mid) return;
    jbyteArray array = (*env)->NewByteArray(env, size);
    if (array) {
        (*env)->SetByteArrayRegion(env, array, 0, size, (jbyte *) data);
        (*env)->CallVoidMethod(env, g_java_callback, mid, (jint) type, array);
        (*env)->DeleteLocalRef(env, array);
    }
    (*env)->DeleteLocalRef(env, cls);
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_java_vm = vm;
    return JNI_VERSION_1_6;
}

// JNI接口实现
JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_initialize(JNIEnv *env, jclass clazz, jint socket_type,
                                               jstring address, jboolean debug) {
    const char *addr_str = (*env)->GetStringUTFChars(env, address, NULL);
    if (!addr_str) {
        return -1;
    }
    client_config_t config = {
            .socket_type = (socket_type_t) socket_type,
            .debug = debug ? 1 : 0
    };
    strncpy(config.address, addr_str, sizeof(config.address) - 1);
    config.address[sizeof(config.address) - 1] = '\0';
    (*env)->ReleaseStringUTFChars(env, address, addr_str);
    return client_init(&config);
}

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_connect(JNIEnv *env, jclass clazz) {
    return client_connect();
}

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_disconnect(JNIEnv *env, jclass clazz) {
    return client_disconnect();
}

JNIEXPORT void JNICALL
Java_com_github_kirer_server_Client_setMessageCallback(JNIEnv *env, jclass clazz,
                                                       jobject callback) {
    if (g_java_callback) {
        (*env)->DeleteGlobalRef(env, g_java_callback);
        g_java_callback = NULL;
    }
    g_java_callback = (*env)->NewGlobalRef(env, callback);
    // C 层注册函数指针回调
    client_set_message_callback(c_message_callback);
}

// JNI 调用 C 层发送消息
JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_sendMessage(JNIEnv *env, jclass clazz, jint type,
                                                jbyteArray data) {
    jbyte *bytes = NULL;
    jsize size = 0;
    if (data) {
        size = (*env)->GetArrayLength(env, data);
        bytes = (*env)->GetByteArrayElements(env, data, NULL);
    }
    int ret = client_send_message((message_type_t) type, bytes, (uint32_t) size);
    if (bytes) {
        (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    }
    return ret;
}

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_getState(JNIEnv *env, jclass clazz) {
    return (jint) client_get_state();
}

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Client_clean(JNIEnv *env, jclass clazz) {
    return client_cleanup();
}

#endif
