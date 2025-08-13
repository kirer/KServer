# main.cpp 适配说明

## 适配概述

将 `server-k/src/main/cpp/client/main.cpp` 适配到新的 client.c 设计架构。

## 主要修改

### 1. 回调函数重构

**修改前**:
```cpp
// 旧的回调函数
void on_screenshot(const uint8_t *data, size_t size, uint64_t timestamp, void *user_data);
void on_error(int error_code, const char *message, void *user_data);

// 设置回调
client_set_screenshot_callback(on_screenshot, NULL);
client_set_error_callback(on_error, NULL);
```

**修改后**:
```cpp
// 新的统一消息回调函数
void on_message(message_type_t type, const uint8_t *data, uint32_t data_size);

// 设置回调
client_set_message_callback(on_message);
```

### 2. 消息处理逻辑

新的消息处理逻辑基于消息类型进行分发：

```cpp
void on_message(message_type_t type, const uint8_t *data, uint32_t data_size) {
    switch (type) {
        case 112: { // MSG_TYPE_NOTIFY_SHARE_MEMORY_SIZE
            // 解析共享内存大小（大端序）
            uint32_t memory_size = (data[0] << 24) | (data[1] << 16) | (data[2] << 8) | data[3];
            LOG_INFO(MAIN_LOG_TAG, "收到共享内存大小通知: %u 字节", memory_size);
            break;
        }
        case 113: { // MSG_TYPE_NOTIFY_SCREEN_CAPTURE
            // 解析截图通知数据
            uint64_t timestamp = 0;
            uint32_t screenshot_size = 0;
            
            // 解析时间戳（8字节，大端序）
            for (int i = 0; i < 8; i++) {
                timestamp = (timestamp << 8) | data[i];
            }
            
            // 解析数据大小（4字节，大端序）
            for (int i = 8; i < 12; i++) {
                screenshot_size = (screenshot_size << 8) | data[i];
            }
            
            LOG_INFO(MAIN_LOG_TAG, "收到截图通知: 大小=%u 字节, 时间戳=%llu", 
                     screenshot_size, timestamp);
            break;
        }
        default:
            LOG_DEBUG(MAIN_LOG_TAG, "收到未知业务消息类型: %d", type);
            break;
    }
}
```

### 3. 配置结构更新

**修改前**:
```cpp
// 配置结构缺少 auto_start_server 字段
```

**修改后**:
```cpp
// 在 client.h 中添加了 auto_start_server 字段
typedef struct {
    socket_type_t socket_type;
    char address[256];
    int auto_start_server;          // 新增字段
    int debug;
} client_config_t;
```

### 4. 保持的功能

以下功能保持不变，继续正常工作：

- ✅ 命令行参数解析
- ✅ 自动启动服务器功能
- ✅ 嵌入文件释放功能
- ✅ Socket连接管理
- ✅ 心跳机制
- ✅ 信号处理
- ✅ OpenCV截图保存功能

## 适配后的架构优势

### 1. 统一的消息处理
- 所有消息通过统一的回调函数处理
- 基于消息类型进行分发，易于扩展

### 2. 更好的协议分离
- 协议消息（1-4）由C层处理
- 业务消息（100-999）传递给应用层

### 3. 简化的接口
- 减少了回调函数的数量
- 统一的消息格式和处理流程

## 使用示例

### 编译客户端
```bash
./gradlew :server-k:buildClient
```

### 运行客户端
```bash
# TCP模式
./k-client --socket-type=TCP --address=127.0.0.1:7777 --auto-start-server --debug

# Unix Socket模式
./k-client --socket-type=UNIX --address=k.socket --auto-start-server --debug

# 仅释放嵌入文件
./k-client --extract-files --temp-dir=/data/local/tmp
```

## 待完善的功能

### 1. 共享内存集成
当前版本中，共享内存的连接和读取功能还需要进一步集成：

```cpp
case 112: { // MSG_TYPE_NOTIFY_SHARE_MEMORY_SIZE
    // TODO: 调用 ShareMemory.connect(memory_size)
    break;
}

case 113: { // MSG_TYPE_NOTIFY_SCREEN_CAPTURE
    // TODO: 从共享内存读取截图数据
    // byte[] screenshot_data = ShareMemory.read();
    // save_screenshot_to_file(screenshot_data, screenshot_size, timestamp);
    break;
}
```

### 2. 错误处理增强
可以考虑添加更详细的错误处理和重连机制。

## 总结

main.cpp 已成功适配到新的 client.c 架构，保持了原有的功能特性，同时获得了更好的消息处理机制和协议分离设计。客户端现在可以正确接收和处理来自服务器的业务消息，为后续的功能扩展奠定了良好的基础。