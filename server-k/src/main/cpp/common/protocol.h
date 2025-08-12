#ifndef PROTOCOL_H
#define PROTOCOL_H

#include <stdint.h>

/**
 * Server-K 控制协议定义
 * 用于Socket通信的控制信令和通知机制
 */

// 消息类型枚举
typedef enum {
    MSG_TYPE_INIT_REQ = 1,          // 客户端请求初始化
    MSG_TYPE_INIT_RESP = 2,         // 服务端返回共享内存配置
    MSG_TYPE_SCREENSHOT_NOTIFY = 3, // 服务端通知新截图可用
    MSG_TYPE_SCREENSHOT_ACK = 4,    // 客户端确认收到截图（可选）
    MSG_TYPE_ERROR = 5,             // 错误消息
    MSG_TYPE_HEARTBEAT = 6,         // 心跳消息
    MSG_TYPE_DISCONNECT = 7         // 断开连接
} message_type_t;

// Socket类型枚举
typedef enum {
    SOCKET_TYPE_TCP = 1,
    SOCKET_TYPE_UNIX = 2
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
    uint32_t shm_size;              // 共享内存大小
    uint32_t pixel_format;          // 像素格式 (RGBA_8888 = 1)
    uint32_t server_version;        // 服务端版本
    uint32_t reserved;              // 保留字段
} init_response_data_t;

// 截图通知数据
typedef struct {
    uint64_t timestamp;             // 截图时间戳
    uint32_t data_size;             // 截图数据大小
    uint32_t reserved;              // 保留字段
} screenshot_notify_data_t;

// 错误消息数据
typedef struct {
    uint32_t error_code;            // 错误代码
    uint32_t message_len;           // 错误消息长度
    char message[];                 // 错误消息内容
} error_message_data_t;

// 协议版本
#define PROTOCOL_VERSION 1

// 像素格式定义
#define PIXEL_FORMAT_RGBA_8888 1

// 错误代码定义
#define ERROR_CODE_SUCCESS 0
#define ERROR_CODE_INVALID_REQUEST 1
#define ERROR_CODE_MEMORY_ERROR 2
#define ERROR_CODE_DISPLAY_ERROR 3
#define ERROR_CODE_SOCKET_ERROR 4
#define ERROR_CODE_UNKNOWN 999

// 消息大小计算宏
#define CONTROL_MESSAGE_SIZE(data_size) (sizeof(control_message_t) + (data_size))
#define INIT_REQUEST_MESSAGE_SIZE CONTROL_MESSAGE_SIZE(sizeof(init_request_data_t))
#define INIT_RESPONSE_MESSAGE_SIZE CONTROL_MESSAGE_SIZE(sizeof(init_response_data_t))
#define SCREENSHOT_NOTIFY_MESSAGE_SIZE CONTROL_MESSAGE_SIZE(sizeof(screenshot_notify_data_t))

// 默认配置
#define DEFAULT_TCP_PORT 7777
#define DEFAULT_UNIX_SOCKET_NAME "k.socket"
#define DEFAULT_SHM_SIZE (1920 * 1080 * 4 * 1.2)  // 默认共享内存大小

#endif // PROTOCOL_H