#ifndef PROTOCOL_H
#define PROTOCOL_H

#include <stdint.h>

/**
 * Server-K 控制协议定义
 * 用于Socket通信的控制信令和通知机制
 */

// 消息类型枚举 - 只包含核心协议消息
typedef enum {
    MSG_TYPE_INIT_REQ = 1,                  // 客户端请求初始化
    MSG_TYPE_INIT_RESP = 2,                 // 服务端返回
    MSG_TYPE_HEARTBEAT = 3,                 // 心跳消息
    MSG_TYPE_DISCONNECT = 4                 // 断开连接
} message_type_t;

// 消息类型范围定义
#define MSG_TYPE_PROTOCOL_MIN    1          // 协议消息最小值
#define MSG_TYPE_PROTOCOL_MAX    99         // 协议消息最大值
#define MSG_TYPE_BUSINESS_MIN    100        // 业务消息最小值
#define MSG_TYPE_BUSINESS_MAX    999        // 业务消息最大值

// 消息类型判断宏
#define IS_PROTOCOL_MESSAGE(type) ((type) >= MSG_TYPE_PROTOCOL_MIN && (type) <= MSG_TYPE_PROTOCOL_MAX)
#define IS_BUSINESS_MESSAGE(type) ((type) >= MSG_TYPE_BUSINESS_MIN && (type) <= MSG_TYPE_BUSINESS_MAX)

// Socket类型枚举
typedef enum {
    SOCKET_TYPE_UNIX = 1,
    SOCKET_TYPE_TCP = 2,
} socket_type_t;

// 控制消息格式
typedef struct {
    uint32_t type;                  // 消息类型
    uint32_t data_size;             // 数据大小
    uint8_t data[];                 // 可变长度数据
} control_message_t;

// 初始化请求数据
typedef struct {
    uint32_t client_version;        // 客户端版本
    uint32_t reserved;              // 保留字段
} init_request_data_t;

// 初始化响应数据
typedef struct {
    uint32_t server_version;        // 服务端版本
    uint32_t reserved;              // 保留字段
} init_response_data_t;

// 协议版本
#define PROTOCOL_VERSION 1

#endif // PROTOCOL_H