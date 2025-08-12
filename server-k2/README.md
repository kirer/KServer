# Server-K2

**Server-K2 是对 Server-K 模块的重构版本**，采用全新的架构设计，显著提升性能并简化代码结构。

## 📋 重构概述

Server-K2 重新设计了传输层架构，将数据传输和控制通信分离：
- **数据层**：共享内存永远存在，负责高性能的截图数据传输
- **控制层**：Socket通信负责控制信令和通知机制

### 🚀 核心改进

| 方面 | Server-K | Server-K2 | 改进 |
|------|----------|-----------|------|
| **TCP模式性能** | 1.4秒 | ~25ms | **56倍提升** |
| **Unix模式性能** | 5秒 | ~25ms | **200倍提升** |
| **代码复杂度** | 3套独立实现 | 统一架构 | **显著简化** |
| **维护成本** | 高 | 低 | **大幅降低** |

## 🏗️ 新架构设计

### 架构对比

**Server-K (旧架构)**：
```
三种互斥模式：
├── SHARED_MEMORY: 共享内存传输数据 (25ms)
├── TCP_SOCKET: TCP传输数据 (1.4s)
└── UNIX_SOCKET: Unix Socket传输数据 (5s)
```

**Server-K2 (新架构)**：
```
分层设计：
├── 数据层: 共享内存 (永远存在，25ms性能)
└── 控制层: Socket通信 (TCP/Unix可选，仅传输控制信令)
```

### 工作流程

#### 1. 初始化阶段
```
服务端启动:
├── 获取屏幕分辨率
├── 创建共享内存 (width × height × 4 × 1.2)
├── 启动Socket控制服务器 (TCP或Unix)
└── 开始截图服务

客户端连接:
├── 连接Socket控制服务器
├── 发送初始化请求
├── 接收共享内存配置信息
└── 连接共享内存
```

#### 2. 运行阶段
```
截图传输:
├── 服务端截图 → 写入共享内存
├── 服务端通过Socket发送"新截图可用"通知
├── 客户端收到通知 → 从共享内存读取截图
└── 客户端发送确认（可选）
```

## 📁 重构后的目录结构

```
src/main/cpp/
├── common/
│   ├── shared_memory.c       # 共享内存实现（统一接口）
│   ├── shared_memory.h
│   ├── protocol.h            # 新增：控制协议定义
│   └── log.c                 # 日志系统
├── client/
│   ├── client.c              # 客户端完整逻辑（Socket + 共享内存）
│   ├── client.h
│   ├── jni/                  # 客户端JNI接口（支持Android Library）
│   │   ├── client_jni.c
│   │   └── client_jni.h
│   └── main.c                # 独立客户端主程序
└── server/
    ├── server.c              # 服务端完整逻辑（Socket + 共享内存）
    ├── server.h
    └── jni/                  # 服务端JNI接口
        ├── server_jni.c
        └── server_jni.h
```

### 设计原则
- **极简化**：避免过度抽象，最少的文件数量
- **职责集中**：相关功能在同一文件中，便于理解和维护
- **双重支持**：既支持Android Library，也支持独立程序

## 📡 控制协议设计

### 消息类型
```c
typedef enum {
    MSG_TYPE_INIT_REQ = 1,          // 客户端请求初始化
    MSG_TYPE_INIT_RESP = 2,         // 服务端返回共享内存配置
    MSG_TYPE_SCREENSHOT_NOTIFY = 3, // 服务端通知新截图可用
    ...                             // 其他消息类型
} message_type_t;
```

### 数据结构
```c
// 控制消息格式
typedef struct {
    uint32_t type;                  // 消息类型
    uint32_t data_size;             // 数据大小
    uint8_t data[];                 // 可变长度数据
} control_message_t;

// 初始化响应数据
typedef struct {
    uint32_t shm_size;              // 共享内存大小
    uint32_t screen_width;          // 屏幕宽度
    uint32_t screen_height;         // 屏幕高度
    uint32_t pixel_format;          // 像素格式
} init_response_data_t;

.... // 其他消息结构类型
```

## 🚀 共享内存逻辑

参考 /Users/kirer/projects/android/KServer/server-k/src/main/cpp/common/ashmem.c

## 🔧 截屏服务逻辑

参考 /Users/kirer/projects/android/KServer/server-k/src/main/java/com/github/kirer/server/ScreenCaptureService.java

## 🎯 TCP和Unix Socket的处理

在 client.c 和 server.c 中，可以通过简单的条件分支处理：

```
// 在client.c中
int client_connect_socket(socket_type_t type, const char* address) {
    switch(type) {
        case SOCKET_TYPE_TCP:
            return connect_tcp_socket(host, port);
        case SOCKET_TYPE_UNIX:
            return connect_unix_socket(name);
        default:
            return -1;
    }
}

// 在server.c中
int server_start_socket(socket_type_t type, const char* address) {
    switch(type) {
        case SOCKET_TYPE_TCP:
            return start_tcp_server(host, port);
        case SOCKET_TYPE_UNIX:
            return start_unix_server(name);
        default:
            return -1;
    }
}
```

## 🔄 使用场景对比

### 1. 作为Android Library使用

```
// 在Android应用中
Client client = new Client();
client.initialize(Client.SOCKET_TYPE_TCP, "127.0.0.1:7777");
client.connect();
```

### 2. 作为独立客户端使用

```
# 命令行使用
./k-client --socket-type=TCP --address=127.0.0.1:7777 --auto-start-server
```

## 🔧 Gradle构建重构

### 新的构建任务

```kotlin
参考 server-k 项目 build.gradle.kts 中的任务
// 1. 独立服务器模式（重构）
tasks.register("buildServer") {
    description = "构建独立服务器程序（仅服务端 + 共享内存）"
    doLast {
        println("🚀 独立服务器构建完成")
        println("📁 输出目录: build/k-server-output/")
        println("📋 使用方式:")
        println("adb shell 'chmod +x /data/local/tmp/k-server.dex && chmod +x /data/local/tmp/lib/arm64-v8a/libserver-k.so && CLASSPATH=/data/local/tmp/k-server.dex app_process /system/bin com.github.kirer.server.Launcher --libPath=/data/local/tmp/lib/arm64-v8a --socket-type=UNIX --address=k.socket --debug > /data/local/tmp/k-server.log 2>&1 &'")
        println("adb shell 'chmod +x /data/local/tmp/k-server.dex && chmod +x /data/local/tmp/lib/arm64-v8a/libserver-k.so && CLASSPATH=/data/local/tmp/k-server.dex app_process /system/bin com.github.kirer.server.Launcher --libPath=/data/local/tmp/lib/arm64-v8a --socket-type=TCP --address=127.0.0.1:7777 --debug > /data/local/tmp/k-server.log 2>&1 &'")
    }
}

// 2. 独立客户端模式（重构）
tasks.register("buildClient") {
    description = "构建独立客户端程序（包含嵌入式服务器）"
    dependsOn("buildServer", "generateEmbeddedFiles")
    doLast {
        println("📱 独立客户端构建完成")
        println("📁 输出目录: build/k-client-output/")
        println("📋 使用方式:")
        println("adb shell 'cd /data/local/tmp && chmod +x k-client && ./k-client --socket-type=UNIX --address=k.socket --auto-start-server --debug'")
        println("adb shell 'cd /data/local/tmp && chmod +x k-client && ./k-client --socket-type=TCP --address=127.0.0.1:7777 --auto-start-server --debug'")
    }
}
```
