# 消息类型设计方案

## 设计原则

C层只关心核心协议消息类型，业务消息类型与协议消息类型分离，避免冲突。

## 消息类型范围划分

### 协议消息类型 (1-99)
- **范围**: 1 ~ 99
- **用途**: 核心协议控制消息
- **处理**: C层直接处理

```c
typedef enum {
    MSG_TYPE_INIT_REQ = 1,      // 客户端请求初始化
    MSG_TYPE_INIT_RESP = 2,     // 服务端返回
    MSG_TYPE_HEARTBEAT = 3,     // 心跳消息
    MSG_TYPE_DISCONNECT = 4     // 断开连接
} message_type_t;
```

### 业务消息类型 (100-999)
- **范围**: 100 ~ 999
- **用途**: 应用层业务消息
- **处理**: 通过回调函数传递给Java层

```java
public static final int MSG_TYPE_INIT_SCREEN_CAPTURE = 111;
public static final int MSG_TYPE_NOTIFY_SHARE_MEMORY_SIZE = 112;
public static final int MSG_TYPE_NOTIFY_SCREEN_CAPTURE = 113;
```

## 消息处理流程

### 服务端处理逻辑
```c
switch (msg->type) {
    case MSG_TYPE_INIT_REQ:
    case MSG_TYPE_HEARTBEAT:
    case MSG_TYPE_DISCONNECT:
        // C层直接处理协议消息
        break;
    default:
        if (IS_BUSINESS_MESSAGE(msg->type)) {
            // 业务消息通过回调传递给Java层
            if (g_server.on_message) {
                g_server.on_message(msg->type, msg->data, msg->data_size);
            }
        } else {
            // 未知协议消息，记录警告
            LOG_WARN("收到未知协议消息类型: %d", msg->type);
        }
        break;
}
```

### 客户端处理逻辑
```c
switch (msg->type) {
    case MSG_TYPE_HEARTBEAT:
        // C层直接处理协议消息
        break;
    default:
        if (IS_BUSINESS_MESSAGE(msg->type)) {
            // 业务消息通过回调传递给Java层
            if (g_client.on_message) {
                g_client.on_message(msg->type, msg->data, msg->data_size);
            }
        } else {
            // 未知协议消息，记录警告
            LOG_WARN("收到未知协议消息类型: %d", msg->type);
        }
        break;
}
```

## 优势

1. **职责分离**: C层专注协议，Java层处理业务
2. **避免冲突**: 明确的范围划分防止消息类型冲突
3. **易于扩展**: 业务消息类型可以在Java层自由定义
4. **类型安全**: 使用宏进行类型检查
5. **向后兼容**: 不影响现有的协议消息处理

## 使用示例

### Java层定义新的业务消息类型
```java
public static final int MSG_TYPE_NEW_FEATURE = 200;  // 在100-999范围内
```

### C层自动处理
```c
// 无需修改C层代码，业务消息会自动通过回调传递给Java层
```

### Java层接收处理
```java
Server.setMessageCallback((type, data) -> {
    switch (type) {
        case MSG_TYPE_NOTIFY_SHARE_MEMORY_SIZE:
            // 处理共享内存大小通知
            break;
        case MSG_TYPE_NEW_FEATURE:
            // 处理新功能消息
            break;
    }
});
```

## 注意事项

1. 协议消息类型 (1-99) 需要在C层和Java层保持一致
2. 业务消息类型 (100-999) 只需要在Java层定义
3. 超出范围的消息类型会被记录为警告但不会导致错误
4. 建议为不同的业务模块预留不同的消息类型范围，如：
   - 屏幕捕获: 100-199
   - 文件传输: 200-299
   - 音频传输: 300-399
   - 等等...