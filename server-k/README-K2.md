# Server-K 重构项目

本项目是对原 server-k 项目的完全重构，严格遵循 `server-k2/README.md` 中的设计要求。

## 项目结构

```
src/main/cpp/
├── protocol.h          # 统一协议定义
├── shared_memory.h     # 共享内存接口
├── shared_memory.c     # 共享内存实现
├── server.h            # 服务端接口
├── server.c            # 服务端实现
├── client.h            # 客户端接口
├── client.c            # 客户端实现
├── client/
│   └── main.c          # 客户端主程序
├── common/
│   └── log.c           # 日志功能
└── CMakeLists.txt      # 构建配置
```

## 核心特性

### 1. 统一协议设计
- 定义了完整的控制协议消息类型
- 支持初始化、截图通知、心跳、错误处理等
- 清晰的消息格式和数据结构

### 2. 分层架构
- **控制层**: 使用 Socket (TCP/Unix) 进行命令和状态通信
- **数据层**: 使用共享内存进行高效的截图数据传输
- **接口层**: 提供统一的客户端和服务端 API

### 3. 简化的 Socket 设计
- 支持 TCP 和 Unix Socket 两种模式
- 简单的连接管理和消息收发
- 避免复杂的多路复用和异步处理

### 4. 统一的共享内存接口
- 封装了原有的 ashmem 实现
- 提供简洁的创建、连接、读写、清理接口
- 支持时间戳和数据有效性检查

## 构建说明

### 使用 CMake 构建

```bash
# 进入 cpp 目录
cd src/main/cpp

# 创建构建目录
mkdir build && cd build

# 配置项目
cmake ..

# 编译
make

# 安装（可选）
make install
```

### 输出文件
- `lib/libserver-k2-server.so` - 服务端共享库
- `bin/k2-client` - 客户端可执行文件

## 使用方法

### 客户端使用

```bash
# TCP 模式连接
./k2-client --socket-type=TCP --address=127.0.0.1:7777

# Unix Socket 模式连接
./k2-client --socket-type=UNIX --address=k.socket

# 启用调试模式
./k2-client --socket-type=TCP --address=127.0.0.1:7777 --debug

# 自动启动服务器
./k2-client --socket-type=TCP --address=127.0.0.1:7777 --auto-start-server

# 查看帮助
./k2-client --help
```

### 编程接口

#### 客户端 API

```c
#include "client.h"

// 配置客户端
client_config_t config = {
    .socket_type = SOCKET_TYPE_TCP,
    .address = "127.0.0.1:7777",
    .auto_start_server = 0,
    .debug = 1
};

// 初始化
client_init(&config);

// 设置回调
client_set_screenshot_callback(on_screenshot, NULL);
client_set_error_callback(on_error, NULL);

// 连接服务器
client_connect();

// 主循环
while (running) {
    client_send_heartbeat();
    usleep(20000);
}

// 清理
client_disconnect();
client_cleanup();
```

#### 服务端 API

```c
#include "server.h"

// 配置服务器
server_config_t config = {
    .socket_type = SOCKET_TYPE_TCP,
    .address = "127.0.0.1:7777",
    .max_clients = 10,
    .debug = 1
};

// 初始化并启动
server_init(&config);
server_start();

// 写入截图数据
server_write_screenshot(data, size);
server_notify_screenshot();

// 清理
server_stop();
server_cleanup();
```

## 协议说明

### 消息类型
- `MSG_TYPE_INIT_REQUEST` - 初始化请求
- `MSG_TYPE_INIT_RESPONSE` - 初始化响应
- `MSG_TYPE_SCREENSHOT_NOTIFY` - 截图通知
- `MSG_TYPE_SCREENSHOT_ACK` - 截图确认
- `MSG_TYPE_HEARTBEAT` - 心跳消息
- `MSG_TYPE_ERROR` - 错误消息

### 数据流程
1. 客户端通过 Socket 发送初始化请求
2. 服务端响应并提供共享内存信息
3. 客户端连接共享内存
4. 服务端写入截图数据到共享内存
5. 服务端通过 Socket 通知客户端
6. 客户端从共享内存读取数据
7. 客户端发送确认消息

## 与原项目的差异

### 简化的设计
- 移除了复杂的多模式支持
- 统一了接口设计
- 简化了 Socket 通信逻辑

### 改进的架构
- 清晰的分层设计
- 统一的协议定义
- 更好的错误处理

### 更好的可维护性
- 模块化的代码结构
- 统一的编码风格
- 完整的文档说明

## 注意事项

1. 本重构版本与原 server-k 项目不兼容
2. 需要 Android API 21+ 支持
3. 建议在测试环境中充分验证后再用于生产
4. Socket 设计保持简单，避免过度复杂化

## 开发状态

- [x] 协议定义
- [x] 共享内存接口
- [x] 服务端核心逻辑
- [x] 客户端核心逻辑
- [x] 客户端主程序
- [x] CMake 构建配置
- [ ] 服务端 JNI 集成
- [ ] 完整测试验证