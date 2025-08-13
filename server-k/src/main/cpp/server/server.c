#include "server.h"
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

// 全局服务器实例
server_t g_server = {0};

// 解析地址字符串
int server_parse_address(const char *address, char *host, int *port, char *socket_name,
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

// 创建TCP socket
static int server_create_tcp_socket(const char *host, int port) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd < 0) {
        LOG_ERROR(SERVER_LOG_TAG, "创建TCP socket失败: %s", strerror(errno));
        return -1;
    }
    // 设置地址重用
    int opt = 1;
    if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        LOG_WARN(SERVER_LOG_TAG, "设置SO_REUSEADDR失败: %s", strerror(errno));
    }
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    if (inet_pton(AF_INET, host, &addr.sin_addr) <= 0) {
        LOG_ERROR(SERVER_LOG_TAG, "无效的IP地址: %s", host);
        close(fd);
        return -1;
    }
    if (bind(fd, (struct sockaddr *) &addr, sizeof(addr)) < 0) {
        LOG_ERROR(SERVER_LOG_TAG, "绑定TCP地址失败 %s:%d: %s", host, port, strerror(errno));
        close(fd);
        return -1;
    }
    if (listen(fd, 8) < 0) {
        LOG_ERROR(SERVER_LOG_TAG, "监听TCP端口失败: %s", strerror(errno));
        close(fd);
        return -1;
    }
    LOG_INFO(SERVER_LOG_TAG, "TCP服务器监听 %s:%d", host, port);
    return fd;
}

// 创建Unix socket
static int server_create_unix_socket(const char *socket_name) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) {
        LOG_ERROR(SERVER_LOG_TAG, "创建Unix socket失败: %s", strerror(errno));
        return -1;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;

    // 构建socket路径
    snprintf(addr.sun_path, sizeof(addr.sun_path), "/data/local/tmp/%s", socket_name);

    // 删除可能存在的socket文件
    unlink(addr.sun_path);

    if (bind(fd, (struct sockaddr *) &addr, sizeof(addr)) < 0) {
        LOG_ERROR(SERVER_LOG_TAG, "绑定Unix socket失败 %s: %s", addr.sun_path, strerror(errno));
        close(fd);
        return -1;
    }

    if (listen(fd, 8) < 0) {
        LOG_ERROR(SERVER_LOG_TAG, "监听Unix socket失败: %s", strerror(errno));
        close(fd);
        unlink(addr.sun_path);
        return -1;
    }

    LOG_INFO(SERVER_LOG_TAG, "Unix服务器监听 %s", addr.sun_path);
    return fd;
}

// 设置消息回调
void server_set_message_callback(server_message_callback_t callback) {
    g_server.on_message = callback;
}

// 发送控制消息
static int server_send_message(int fd, message_type_t type, const void *data, uint32_t data_size) {
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
    ssize_t sent = send(fd, msg, total_size, 0);
    free(msg);
    if (sent != (ssize_t) total_size) {
        LOG_ERROR(SERVER_LOG_TAG, "发送消息失败: %s", strerror(errno));
        return -1;
    }
    return 0;
}

// 接收控制消息
static int server_recv_message(int fd, control_message_t **message) {
    if (!message) {
        return -1;
    }
    // 先接收消息头
    control_message_t header;
    ssize_t received = recv(fd, &header, sizeof(header), 0);
    if (received != sizeof(header)) {
        if (received == 0) {
            LOG_DEBUG(SERVER_LOG_TAG, "客户端断开连接");
        } else {
            LOG_ERROR(SERVER_LOG_TAG, "接收消息头失败: %s", strerror(errno));
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
        received = recv(fd, msg->data, header.data_size, 0);
        if (received != (ssize_t) header.data_size) {
            LOG_ERROR(SERVER_LOG_TAG, "接收消息数据失败: %s", strerror(errno));
            free(msg);
            return -1;
        }
    }
    *message = msg;
    return 0;
}

// 处理客户端连接
static int server_handle_client(int client_fd) {
    LOG_INFO(SERVER_LOG_TAG, "处理客户端连接 fd=%d", client_fd);
    while (!g_server.should_stop) {
        control_message_t *msg = NULL;
        if (server_recv_message(client_fd, &msg) < 0) {
            break;
        }
        switch (msg->type) {
            case MSG_TYPE_INIT_REQ: {
                LOG_INFO(SERVER_LOG_TAG, "收到初始化请求");
                // 准备响应数据
                init_response_data_t response = {
                        .server_version = PROTOCOL_VERSION,
                        .reserved = 0
                };
                if (server_send_message(client_fd, MSG_TYPE_INIT_RESP, &response,
                                        sizeof(response)) < 0) {
                    LOG_ERROR(SERVER_LOG_TAG, "发送初始化响应失败");
                    free(msg);
                    return -1;
                }
                LOG_INFO(SERVER_LOG_TAG, "发送初始化响应成功");
                break;
            }
            case MSG_TYPE_HEARTBEAT: {
                LOG_DEBUG(SERVER_LOG_TAG, "收到心跳消息");
                server_send_message(client_fd, MSG_TYPE_HEARTBEAT, NULL, 0);
                break;
            }
            case MSG_TYPE_DISCONNECT: {
                LOG_INFO(SERVER_LOG_TAG, "客户端请求断开连接");
                free(msg);
                return 0;
            }
            default:
                LOG_WARN(SERVER_LOG_TAG, "收到消息类型: %d", msg->type);
                if (g_server.on_message) {
                    g_server.on_message(msg->type, msg->data, msg->data_size);
                }
                break;
        }
        free(msg);
    }
    return 0;
}

// 客户端处理线程
static void *server_client_thread(void *arg) {
    client_connection_t *client = (client_connection_t *) arg;
    LOG_INFO(SERVER_LOG_TAG, "客户端线程启动 fd=%d", client->socket_fd);
    server_handle_client(client->socket_fd);
    LOG_INFO(SERVER_LOG_TAG, "客户端线程结束 fd=%d", client->socket_fd);
    // 标记客户端为非活跃状态
    pthread_mutex_lock(&g_server.clients_mutex);
    client->active = 0;
    pthread_mutex_unlock(&g_server.clients_mutex);
    return NULL;
}

// 关闭客户端连接
static void server_close_client(client_connection_t *client) {
    if (client->active) {
        if (client->socket_fd >= 0) {
            close(client->socket_fd);
            client->socket_fd = -1;
        }
        client->should_stop = 1; // 通知线程退出
        if (client->thread != 0) {
            pthread_join(client->thread, NULL); // 等待线程退出
            client->thread = 0;
        }
        client->active = 0;
    }
}

// 关闭所有客户端连接
static void server_close_all_clients(void) {
    pthread_mutex_lock(&g_server.clients_mutex);

    for (int i = 0; i < g_server.client_count; i++) {
        server_close_client(&g_server.clients[i]);
    }

    g_server.client_count = 0;
    pthread_mutex_unlock(&g_server.clients_mutex);
}

// 监听线程
static void *server_listen_thread(void *arg) {
    LOG_INFO(SERVER_LOG_TAG, "监听线程启动");

    while (!g_server.should_stop) {
        int client_fd = accept(g_server.listen_fd, NULL, NULL);
        if (client_fd < 0) {
            if (!g_server.should_stop) {
                LOG_ERROR(SERVER_LOG_TAG, "接受连接失败: %s", strerror(errno));
            }
            continue;
        }

        LOG_INFO(SERVER_LOG_TAG, "接受新客户端连接 fd=%d", client_fd);

        pthread_mutex_lock(&g_server.clients_mutex);

        // 查找空闲的客户端槽位
        int slot = -1;
        for (int i = 0; i < 8; i++) {
            if (!g_server.clients[i].active) {
                slot = i;
                break;
            }
        }

        if (slot == -1) {
            LOG_WARN(SERVER_LOG_TAG, "客户端连接数已满，拒绝连接");
            close(client_fd);
            pthread_mutex_unlock(&g_server.clients_mutex);
            continue;
        }

        // 初始化客户端连接
        client_connection_t *client = &g_server.clients[slot];
        client->socket_fd = client_fd;
        client->active = 1;

        // 创建客户端处理线程
        if (pthread_create(&client->thread, NULL, server_client_thread, client) != 0) {
            LOG_ERROR(SERVER_LOG_TAG, "创建客户端线程失败: %s", strerror(errno));
            close(client_fd);
            client->active = 0;
            pthread_mutex_unlock(&g_server.clients_mutex);
            continue;
        }

        if (slot >= g_server.client_count) {
            g_server.client_count = slot + 1;
        }

        pthread_mutex_unlock(&g_server.clients_mutex);

        LOG_INFO(SERVER_LOG_TAG, "客户端连接已建立，当前连接数: %d", g_server.client_count);
    }

    LOG_INFO(SERVER_LOG_TAG, "监听线程结束");
    return NULL;
}

// 初始化服务器
int server_init(const server_config_t *config) {
    if (!config) {
        return -1;
    }
    LOG_INFO(SERVER_LOG_TAG, "初始化服务器: %d，%s", config->socket_type, config->address);
    // 清理现有状态
    server_cleanup();
    // 复制配置
    memcpy(&g_server.config, config, sizeof(server_config_t));
    // 初始化状态
    g_server.state = SERVER_STATE_STOPPED;
    g_server.listen_fd = -1;
    g_server.listen_thread = 0;
    g_server.client_count = 0;
    g_server.should_stop = 0;
    // 初始化客户端数组
    memset(g_server.clients, 0, sizeof(g_server.clients));
    // 初始化互斥锁
    if (pthread_mutex_init(&g_server.clients_mutex, NULL) != 0) {
        LOG_ERROR(SERVER_LOG_TAG, "初始化互斥锁失败: %s", strerror(errno));
        return -1;
    }
    LOG_INFO(SERVER_LOG_TAG, "服务器初始化成功");
    return 0;
}

// 启动服务器
int server_start(void) {
    if (g_server.state != SERVER_STATE_STOPPED) {
        LOG_WARN(SERVER_LOG_TAG, "服务器已在运行中");
        return -1;
    }
    g_server.state = SERVER_STATE_STARTING;
    // 解析地址
    char host[256] = {0};
    int port = 0;
    char socket_name[256] = {0};
    if (server_parse_address(g_server.config.address, host, &port, socket_name,
                             g_server.config.socket_type) < 0) {
        LOG_ERROR(SERVER_LOG_TAG, "解析地址失败: %s", g_server.config.address);
        g_server.state = SERVER_STATE_STOPPED;
        return -1;
    }
    // 创建监听socket
    if (g_server.config.socket_type == SOCKET_TYPE_UNIX) {
        LOG_INFO(SERVER_LOG_TAG, "启动服务器: %d，%s", g_server.config.socket_type, socket_name);
        g_server.listen_fd = server_create_unix_socket(socket_name);
    } else if (g_server.config.socket_type == SOCKET_TYPE_TCP) {
        LOG_INFO(SERVER_LOG_TAG, "启动服务器: %d，%s:%d", g_server.config.socket_type, host, port);
        g_server.listen_fd = server_create_tcp_socket(host, port);
    } else {
        LOG_ERROR(SERVER_LOG_TAG, "不支持的socket类型: %d", g_server.config.socket_type);
        g_server.state = SERVER_STATE_STOPPED;
        return -1;
    }
    if (g_server.listen_fd < 0) {
        LOG_ERROR(SERVER_LOG_TAG, "创建监听socket失败");
        g_server.state = SERVER_STATE_STOPPED;
        return -1;
    }
    // 启动监听线程
    g_server.should_stop = 0;
    if (pthread_create(&g_server.listen_thread, NULL, server_listen_thread, NULL) != 0) {
        LOG_ERROR(SERVER_LOG_TAG, "创建监听线程失败: %s", strerror(errno));
        close(g_server.listen_fd);
        g_server.listen_fd = -1;
        g_server.state = SERVER_STATE_STOPPED;
        return -1;
    }

    g_server.state = SERVER_STATE_RUNNING;
    LOG_INFO(SERVER_LOG_TAG, "服务器启动成功");
    return 0;
}

// 停止服务器
int server_stop(void) {
    if (g_server.state != SERVER_STATE_RUNNING) {
        LOG_WARN(SERVER_LOG_TAG, "服务器未在运行中");
        return -1;
    }
    LOG_INFO(SERVER_LOG_TAG, "停止服务器");
    g_server.state = SERVER_STATE_STOPPING;
    // 设置停止标志
    g_server.should_stop = 1;
    // 关闭监听socket
    if (g_server.listen_fd >= 0) {
        close(g_server.listen_fd);
        g_server.listen_fd = -1;
    }
    // 等待监听线程结束
    if (g_server.listen_thread != 0) {
        pthread_join(g_server.listen_thread, NULL);
        g_server.listen_thread = 0;
    }
    // 关闭所有客户端连接
    server_close_all_clients();
    g_server.state = SERVER_STATE_STOPPED;
    LOG_INFO(SERVER_LOG_TAG, "服务器停止成功");
    return 0;
}

// 获取服务器状态
server_state_t server_get_state(void) {
    return g_server.state;
}

// 清理服务器资源
int server_cleanup(void) {
    LOG_INFO(SERVER_LOG_TAG, "清理服务器资源");
    // 停止服务器
    if (g_server.state == SERVER_STATE_RUNNING) {
        server_stop();
    }
    // 销毁互斥锁
    pthread_mutex_destroy(&g_server.clients_mutex);
    // 重置状态
    memset(&g_server, 0, sizeof(g_server));
    g_server.listen_fd = -1;
    LOG_INFO(SERVER_LOG_TAG, "服务器资源清理完成");
    return 0;
}

#if defined(__ANDROID__) && !defined(DISABLE_JNI)
// JNI接口实现
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

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_initialize(JNIEnv *env, jclass clazz,
                                               jint socket_type, jstring address,
                                               jboolean debug) {
    const char *addr_str = (*env)->GetStringUTFChars(env, address, NULL);
    if (!addr_str) {
        return -1;
    }
    server_config_t config = {
            .socket_type = (socket_type_t) socket_type,
            .debug = debug ? 1 : 0
    };
    strncpy(config.address, addr_str, sizeof(config.address) - 1);
    config.address[sizeof(config.address) - 1] = '\0';
    (*env)->ReleaseStringUTFChars(env, address, addr_str);
    return server_init(&config);
}

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_start(JNIEnv *env, jclass clazz) {
    return server_start();
}

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_stop(JNIEnv *env, jclass clazz) {
    return server_stop();
}

JNIEXPORT void JNICALL
Java_com_github_kirer_server_Server_setMessageCallback(JNIEnv *env, jclass clazz,
                                                       jobject callback) {
    if (g_java_callback) {
        (*env)->DeleteGlobalRef(env, g_java_callback);
        g_java_callback = NULL;
    }
    g_java_callback = (*env)->NewGlobalRef(env, callback);
    server_set_message_callback(c_message_callback);
}

// JNI 调用 C 层发送消息
JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_sendMessage(JNIEnv *env, jclass clazz, jint type,
                                                jbyteArray data) {
    if (g_server.state != SERVER_STATE_RUNNING) {
        return -1;
    }
    pthread_mutex_lock(&g_server.clients_mutex);
    int success_count = 0;
    for (int i = 0; i < g_server.client_count; i++) {
        if (g_server.clients[i].active) {
            if (server_send_message(g_server.clients[i].socket_fd, type, &data, sizeof(data)) ==
                0) {
                success_count++;
            } else {
                LOG_WARN(SERVER_LOG_TAG, "通知客户端 %d 失败", i);
            }
        }
    }
    pthread_mutex_unlock(&g_server.clients_mutex);
    LOG_DEBUG(SERVER_LOG_TAG, "通知 %d 个客户端", success_count);
    return success_count > 0 ? 0 : -1;
}

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_getState(JNIEnv *env, jclass clazz) {
    return (jint) server_get_state();
}

JNIEXPORT jint JNICALL
Java_com_github_kirer_server_Server_clean(JNIEnv *env, jclass clazz) {
    return server_cleanup();
}

#endif