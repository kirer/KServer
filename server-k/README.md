# Server-K

一个高性能的Android屏幕截图服务，支持多种通信方式和部署模式。

## 📋 项目概述

Server-K是一个基于Android Native开发的高性能屏幕截图服务，采用模块化的客户端-服务器架构设计。项目支持多种通信协议、部署方式和截图方案，既可以作为Android库集成到应用中，也可以作为独立的可执行程序运行。

### 核心特性

- 🚀 **高性能**：共享内存模式下25ms完成12.8MB数据传输
- 🔄 **多协议支持**：TCP Socket、Unix Socket、Shared Memory
- 📦 **灵活部署**：Android库、独立服务器、嵌入式客户端
- 🖼️ **图像处理**：集成OpenCV 4.8.0，支持多种格式
- 🔧 **易于集成**：完整的JNI桥接和Java API
- 🛠️ **开发友好**：详细日志、调试模式、错误处理

### 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                    K-Client (嵌入式模式)                      │
├─────────────────────────────────────────────────────────────┤
│  Client Process (Native C/C++)                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │   OpenCV    │  │ Embedded    │  │   Client Core       │  │
│  │ Image Proc. │  │ Files       │  │                     │  │
│  │             │  │ ┌─────────┐ │  │ ┌─────────────────┐ │  │
│  │ - PNG Save  │  │ │ DEX     │ │  │ │ Transport Layer │ │  │
│  │ - Format    │  │ │ SO Lib  │ │  │ │ - TCP Socket    │ │  │
│  │   Convert   │  │ └─────────┘ │  │ │ - Unix Socket   │ │  │
│  └─────────────┘  └─────────────┘  │ │ - Shared Memory │ │  │
│                                    │ └─────────────────┘ │  │
│                                    └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ 1. Extract & Launch
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              K-Server (app_process)                         │
├─────────────────────────────────────────────────────────────┤
│  Java Layer                                                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │  Launcher   │  │ScreenCapture│  │      Server         │  │
│  │             │  │   Service   │  │                     │  │
│  │ - Args      │  │             │  │ - Config            │  │
│  │   Parse     │  │ ┌─────────┐ │  │ - Lifecycle         │  │
│  │ - Service   │  │ │Virtual  │ │  │ - JNI Bridge        │  │
│  │   Start     │  │ │Display  │ │  │                     │  │
│  └─────────────┘  │ │Surface  │ │  └─────────────────────┘  │
│                   │ │Control  │ │                           │
│                   │ └─────────┘ │                           │
│                   └─────────────┘                           │
├─────────────────────────────────────────────────────────────┤
│  Native Layer (JNI)                                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ JNI Bridge  │  │   Server    │  │   Transport Layer   │  │
│  │             │  │    Core     │  │                     │  │
│  │ - Method    │  │             │  │ ┌─────────────────┐ │  │
│  │   Export    │  │ - Thread    │  │ │ TCP Server      │ │  │
│  │ - Data      │  │   Mgmt      │  │ │ Unix Server     │ │  │
│  │   Bridge    │  │ - State     │  │ │ SharedMem Server│ │  │
│  └─────────────┘  │   Control   │  │ └─────────────────┘ │  │
│                   └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              │ 2. Screenshot Data
                              ▼
                    ┌─────────────────┐
                    │  Communication  │
                    │     Channel     │
                    │                 │
                    │ TCP: 127.0.0.1  │
                    │ Unix: @socket   │
                    │ SHM: /dev/ashmem│
                    └─────────────────┘
```

### 工作流程

```
启动阶段:
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│ 1. k-client │───►│ 2. Extract  │───►│ 3. Launch   │
│   启动      │    │   Files     │    │   Server    │
└─────────────┘    └─────────────┘    └─────────────┘
                          │                   │
                          ▼                   ▼
                   /tmp/k-server-xxx/    app_process
                   ├── k-server.dex      k-server.dex
                   └── libserver-k.so

运行阶段:
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│ 4. Connect  │───►│ 5. Request  │───►│ 6. Process  │
│   Channel   │    │ Screenshot  │    │   & Send    │
└─────────────┘    └─────────────┘    └─────────────┘
       │                   │                   │
       ▼                   ▼                   ▼
  TCP/Unix/SHM      Client Request      VirtualDisplay
                                       ┌─────────────┐
                                       │ ImageReader │
                                       │ ┌─────────┐ │
                                       │ │ RGBA    │ │
                                       │ │ Data    │ │
                                       │ └─────────┘ │
                                       └─────────────┘
```

## 🏗️ 项目结构

```
server-k/
├── src/main/
│   ├── cpp/                    # C/C++源代码
│   │   ├── client/            # 客户端代码
│   │   │   ├── core/          # 客户端核心逻辑
│   │   │   ├── transport/     # 通信层实现
│   │   │   ├── opencv/        # OpenCV集成
│   │   │   ├── embedded/      # 嵌入文件（自动生成）
│   │   │   └── main.c         # 客户端主程序
│   │   ├── server/            # 服务器端代码
│   │   │   ├── core/          # 服务器核心逻辑
│   │   │   ├── transport/     # 通信层实现
│   │   │   └── jni/           # JNI桥接
│   │   └── common/            # 共享代码
│   │       ├── log.c          # 日志系统
│   │       └── ashmem.c       # 共享内存实现
│   ├── java/                  # Java源代码
│   │   └── com/github/kirer/server/
│   │       ├── Launcher.java  # 服务器启动器
│   │       ├── Server.java    # 服务器主类
│   │       └── ScreenCaptureService.java # 截图服务
│   └── AndroidManifest.xml    # Android清单文件
├── build.gradle.kts           # 构建脚本
└── README.md                  # 本文档
```

## 🔧 环境要求

### 开发环境

- **Android Studio**: 2022.3或更高版本
- **Android NDK**: 29.0.13113456（项目指定版本）
- **CMake**: 3.18或更高版本
- **Gradle**: 8.0或更高版本
- **Java**: JDK 8或更高版本
- **Kotlin**: 1.8或更高版本

### 运行环境

- **Android版本**: 7.0 (API 24) 或更高（项目minSdk=24）
- **编译目标**: API 35（项目compileSdk=35）
- **CPU架构**: arm64-v8a（仅支持64位）
- **内存**: 至少64MB可用内存
- **存储**: 至少100MB可用空间
- **权限**: 系统级权限（通过adb shell运行）

### 依赖库

- **OpenCV**: 4.8.0 (静态链接)
- **Android NDK**: libc++, liblog, libandroid
- **系统库**: pthread, dl, m

## 🚀 构建方式

### 1. Android库模式

作为Android库集成到应用中：

```bash
# 构建AAR库文件
./gradlew :server-k:assembleRelease

# 输出位置
server-k/build/outputs/aar/server-k-release.aar
```

**使用方式**：
```kotlin
// 在应用的build.gradle中添加依赖
dependencies {
    implementation project(':server-k')
}

// 在代码中使用
val server = Server()
server.start()
```

### 2. 独立服务器模式

构建独立的服务器程序：

```bash
# 构建服务器
./gradlew :server-k:buildServer

# 输出文件
server-k/build/k-server-output/
├── k-server.dex              # Java字节码
└── lib/arm64-v8a/
    └── libserver-k.so        # Native库
```

**部署和运行**：
```bash
# 部署到设备
adb push server-k/build/k-server-output/* /data/local/tmp/

# 启动服务器
adb shell 'cd /data/local/tmp && \
  CLASSPATH=k-server.dex app_process /system/bin \
  com.github.kirer.server.Launcher \
  --mode=TCP_SOCKET --tcpHost=127.0.0.1 --tcpPort=7777 --debug'
```

### 3. 独立客户端模式（嵌入式服务器）

构建包含嵌入式服务器的客户端程序：

```bash
# 构建客户端（自动包含服务器文件）
./gradlew :server-k:buildClient

# 输出文件
server-k/build/k-client-output/
└── k-client                  # 单一可执行文件（约27MB）
```

**嵌入式服务器原理**：

1. **文件嵌入过程**：
   ```bash
   # 1. 构建服务器文件
   ./gradlew :server-k:buildServer

   # 2. 生成嵌入头文件
   ./gradlew :server-k:generateEmbeddedFiles

   # 3. 编译客户端（包含嵌入文件）
   ./gradlew :server-k:buildClient
   ```

2. **嵌入文件转换**：
   - `k-server.dex` → `k_server_dex.h` (43KB)
   - `libserver-k.so` → `libserver_k_so.h` (110KB)
   - 转换为C字节数组，编译时嵌入到客户端

3. **运行时文件释放**：
   ```c
   // 创建临时目录
   char temp_dir[] = "/data/local/tmp/k-server-XXXXXX";
   mkdtemp(temp_dir);

   // 释放嵌入的文件
   write_temp_file("k-server.dex", k_server_dex_data, k_server_dex_len);
   write_temp_file("libserver-k.so", libserver_k_so_data, libserver_k_so_len);
   ```

4. **自动启动服务器**：
   ```c
   // 根据通信模式生成启动命令
   snprintf(command, sizeof(command),
       "CLASSPATH=%s app_process /system/bin com.github.kirer.server.Launcher "
       "--mode=%s --libPath=%s --memorySize=%d --debug",
       dex_path, mode_str, lib_path, memory_size);

   // 执行启动命令
   system(command);
   ```

**部署和运行**：
```bash
# 部署到设备（只需一个文件）
adb push server-k/build/k-client-output/k-client /data/local/tmp/

# 运行（自动启动服务器）
adb shell 'cd /data/local/tmp && chmod +x k-client && \
  ./k-client --auto-start-server --mode=TCP_SOCKET --debug'
```

**优势**：
- ✅ **单文件部署**：无需手动管理多个文件
- ✅ **自动启动**：客户端自动释放并启动服务器
- ✅ **参数同步**：确保服务器和客户端使用相同参数
- ✅ **简化运维**：一个命令完成所有操作

## 🔗 通信方式

Server-K支持三种通信协议，每种都有其特定的使用场景和性能特点：

### 1. TCP Socket（推荐）

**特点**：
- ✅ 跨进程、跨网络通信
- ✅ 稳定可靠，兼容性好
- ✅ 支持远程连接
- ⚠️ 网络传输开销

**使用场景**：
- 客户端和服务器在不同进程
- 需要远程访问
- 对稳定性要求高

**参数**：
```bash
--mode=TCP_SOCKET --tcpHost=127.0.0.1 --tcpPort=7777
```

**性能**：约1.4秒传输12.8MB数据

### 2. Shared Memory（最快）

**特点**：
- 🚀 极高性能，内存直接共享
- ✅ 零拷贝数据传输
- ⚠️ 仅限同设备进程间通信
- ⚠️ 需要合适的内存大小

**使用场景**：
- 对性能要求极高
- 客户端和服务器在同一设备
- 大量数据传输

**参数**：
```bash
--mode=SHARED_MEMORY --memorySize=16777216  # 16MB
```

**性能**：约25毫秒传输12.8MB数据（快59倍）

**注意事项**：
- 内存大小必须足够容纳截图数据
- 服务器和客户端必须使用相同的内存大小
- 推荐16MB以上（1200x2670 RGBA需要12.8MB）

### 3. Unix Socket

**特点**：
- ✅ 进程间通信，性能中等
- ✅ 使用Abstract Socket，无文件系统依赖
- ⚠️ 仅限本地通信

**使用场景**：
- 本地进程间通信
- 不需要网络功能
- 中等性能需求

**参数**：
```bash
--mode=UNIX_SOCKET --socketName=my-server
```

**性能**：约5秒传输12.8MB数据

## 📸 截图方案

Server-K提供两种截图实现方案：

### 1. VirtualDisplay方案（优先）

**技术栈**：
- 基于Android VirtualDisplay API
- 使用ImageReader进行图像捕获
- 高效的屏幕镜像机制

**特点**：
- ✅ 高性能，低延迟
- ✅ 稳定的图像流
- ✅ 支持RGBA_8888格式
- ✅ 自动图像回调处理

**工作原理**：
1. 创建VirtualDisplay镜像主屏幕
2. 通过ImageReader获取图像数据
3. 在后台线程处理图像回调
4. 自动去除行填充，输出标准RGBA数据

### 2. SurfaceControl方案（降级）

**技术栈**：
- 基于Android SurfaceControl API
- 循环创建虚拟显示器进行截图
- 系统级底层截图机制

**特点**：
- ✅ 系统级截图能力
- ✅ 兼容性更广
- ⚠️ 需要系统权限（shell用户）
- ⚠️ 性能相对较低（循环模式）

**工作原理**：
1. 当VirtualDisplay方案失败时自动启用
2. 循环创建和销毁虚拟显示器
3. 每次截图间隔20ms
4. 复用ImageReader减少资源开销

**输出格式**：RGBA原始数据（宽度×高度×4字节）

## 🖼️ 图像处理

### OpenCV集成

客户端集成了OpenCV 4.8.0，提供强大的图像处理能力：

**功能**：
- 图像格式转换（RGBA → RGB）
- 多种输出格式（PNG、JPEG等）
- 图像尺寸调整
- 图像质量优化

**使用示例**：
```bash
# 保存为PNG格式
./k-client --output=screenshot --debug

# 输出文件：screenshot.png
```

## 📋 命令行参数

### 客户端参数

```bash
k-client [选项]

通信配置：
  --mode=MODE              通信模式 (TCP_SOCKET|UNIX_SOCKET|SHARED_MEMORY)
  --tcpHost=HOST           TCP主机地址 (默认: 127.0.0.1)
  --tcpPort=PORT           TCP端口 (默认: 7777)
  --socketName=NAME        Unix套接字名称 (默认: server-k)
  --memorySize=SIZE        共享内存大小 (默认: 16777216)

截图配置：
  --continuous             连续截图模式
  --interval=SECONDS       截图间隔秒数 (默认: 1)
  --output=FILE            输出文件前缀 (默认: screenshot)

服务器配置：
  --auto-start-server      自动启动服务器（如果未运行）

调试配置：
  --debug                  调试模式
  --help                   显示帮助信息
  --version                显示版本信息
```

### 服务器参数

```bash
java -cp k-server.dex com.github.kirer.server.Launcher [选项]

  --mode=MODE              通信模式
  --libPath=PATH           Native库路径
  --tcpHost=HOST           TCP主机地址
  --tcpPort=PORT           TCP端口
  --socketName=NAME        Unix套接字名称
  --memorySize=SIZE        共享内存大小
  --debug                  调试模式
```

## 🎯 使用示例

### 基本截图

```bash
# TCP模式（推荐）
./k-client --auto-start-server --mode=TCP_SOCKET --debug

# 共享内存模式（最快）
./k-client --auto-start-server --mode=SHARED_MEMORY --debug

# Unix Socket模式
./k-client --auto-start-server --mode=UNIX_SOCKET --socketName=test --debug
```

### 连续截图

```bash
# 每2秒截图一次
./k-client --auto-start-server --continuous --interval=2 --output=capture --debug
```

### 自定义配置

```bash
# 自定义TCP端口和共享内存大小
./k-client --auto-start-server \
  --mode=TCP_SOCKET --tcpPort=8888 \
  --memorySize=33554432 --debug  # 32MB
```

## 📚 API文档

### Java API

#### Server类

```java
public class Server {
    // 模式常量
    public static final int MODE_SHARED_MEMORY = 0;
    public static final int MODE_UNIX_SOCKET = 1;
    public static final int MODE_TCP_SOCKET = 2;

    // 初始化服务器
    public int initialize(int mode, int memorySize, String host, int port);

    // 启动服务器
    public int start();

    // 停止服务器
    public int stop();

    // 清理资源
    public int cleanup();

    // 获取服务器状态
    public boolean isRunning();
}
```

#### ScreenCaptureService类

```java
public class ScreenCaptureService {
    // 开始截图服务
    public void startCapture();

    // 停止截图服务
    public void stopCapture();

    // 获取截图数据
    public byte[] getScreenshot();

    // 设置截图参数
    public void setScreenshotParams(int width, int height, int format);
}
```

### Native API

#### 客户端API

```c
// 初始化客户端
int client_initialize_with_params(client_mode_t mode, int memory_size,
                                 const char* socket_name,
                                 const char* tcp_host, int tcp_port);

// 连接服务器
int client_connect(void);

// 截图
client_response_t* client_take_screenshot(void);

// 断开连接
void client_disconnect(void);

// 清理资源
int client_cleanup(void);
```

#### 服务器API

```c
// 初始化服务器
int server_initialize_with_params(server_mode_t mode, int memory_size,
                                 const char* lib_path,
                                 const char* socket_name,
                                 const char* tcp_host, int tcp_port);

// 启动服务器
int server_start(void);

// 停止服务器
int server_stop(void);

// 清理资源
int server_cleanup(void);
```

### 数据结构

```c
// 客户端响应结构
typedef struct {
    bool success;           // 操作是否成功
    char message[512];      // 错误或状态消息
    uint8_t *data;         // 截图数据
    size_t data_size;      // 数据大小
    long processing_time;   // 处理时间（毫秒）
} client_response_t;

// 客户端配置结构
typedef struct {
    client_mode_t mode;     // 通信模式
    int memory_size;        // 共享内存大小
    char socket_name[256];  // Unix套接字名称
    char tcp_host[256];     // TCP主机地址
    int tcp_port;          // TCP端口
} client_config_t;
```

## 🔧 开发指南

### 添加新的通信协议

1. 在 `src/main/cpp/server/transport/` 创建 `server_newprotocol.c`
2. 在 `src/main/cpp/client/transport/` 创建 `client_newprotocol.c`
3. 实现相应的操作函数接口
4. 在核心模块中注册新协议

### 添加新的截图方案

1. 在Java层实现新的截图服务
2. 在JNI层添加相应的桥接函数
3. 在服务器核心中集成新方案
4. 更新配置和参数解析

### 调试技巧

```bash
# 启用详细日志
./k-client --debug

# 查看服务器日志
adb shell 'cat /data/local/tmp/k-server.log'

# 查看系统日志
adb logcat | grep -E "(SERVER-K|k-server)"

# 检查进程状态
adb shell 'ps | grep -E "(k-client|Launcher)"'
```

## 📊 性能对比

| 通信模式 | 传输时间 | 内存使用 | CPU使用 | 适用场景 |
|---------|---------|---------|---------|---------|
| **Shared Memory** | 25ms | 低 | 极低 | 高性能本地通信 |
| **TCP Socket** | 1.4s | 中等 | 中等 | 通用场景，远程访问 |
| **Unix Socket** | 5s | 中等 | 中等 | 本地进程间通信 |

*测试环境：1200x2670分辨率，12.8MB RGBA数据*

## ⚠️ 注意事项

### 权限要求

- **系统权限**：需要通过adb shell以shell用户身份运行
- **显示访问**：需要访问系统显示服务（DisplayManager）
- **虚拟显示**：需要创建VirtualDisplay或SurfaceControl的权限
- **网络权限**：TCP模式需要网络访问权限（本地回环）
- **存储权限**：保存截图文件需要临时目录写入权限

### 系统要求

- **Android版本**：7.0 (API 24) 或更高
- **架构支持**：arm64-v8a
- **内存要求**：至少32MB可用内存

### 已知限制

- 共享内存模式仅支持本地通信
- Unix Socket在某些Android版本可能受限
- 大分辨率屏幕需要更大的内存配置

## 🤝 贡献指南

1. Fork项目
2. 创建功能分支
3. 提交更改
4. 创建Pull Request

## 📄 许可证

本项目采用MIT许可证 - 查看[LICENSE](LICENSE)文件了解详情。

## 🛠️ 故障排除

### 常见问题

**Q: 共享内存模式出现段错误**
```
A: 检查内存大小是否足够：
   - 1200x2670屏幕需要至少13MB
   - 推荐使用16MB或更大
   - 确保服务器和客户端使用相同大小
```

**Q: TCP连接失败**
```
A: 检查网络配置：
   - 确认端口未被占用：netstat -an | grep 7777
   - 检查防火墙设置
   - 验证IP地址和端口配置
```

**Q: Unix Socket连接失败**
```
A: 检查权限和路径：
   - 确认使用Abstract Socket模式
   - 检查SELinux策略
   - 验证socket名称正确
```

**Q: 服务器启动失败**
```
A: 检查依赖和权限：
   - 确认libserver-k.so存在且有执行权限
   - 检查Android版本兼容性
   - 查看详细错误日志
```

### 调试步骤

1. **启用调试模式**
   ```bash
   ./k-client --debug
   ```

2. **检查服务器日志**
   ```bash
   adb shell 'cat /data/local/tmp/k-server.log'
   ```

3. **查看系统日志**
   ```bash
   adb logcat -s "SERVER-K*"
   ```

4. **验证文件完整性**
   ```bash
   adb shell 'ls -la /data/local/tmp/k-*'
   ```

## 🔧 高级配置

### 自定义构建

**修改默认参数**：
```kotlin
// 在build.gradle.kts中修改
val defaultMemorySize = 32 * 1024 * 1024  // 32MB
val defaultTcpPort = 8888
```

**添加编译选项**：
```cmake
# 在CMakeLists.txt中添加
target_compile_definitions(k-client PRIVATE
    DEFAULT_MEMORY_SIZE=33554432
    ENABLE_EXTRA_LOGGING=1
)
```

### 性能优化

**共享内存优化**：
```bash
# 根据屏幕分辨率计算最优内存大小
# 公式：width × height × 4 × 1.2（预留20%）
# 例如：1200×2670×4×1.2 = 15,379,200 ≈ 16MB
```

**网络优化**：
```bash
# 使用本地回环地址减少延迟
--tcpHost=127.0.0.1

# 选择未占用的端口
--tcpPort=7777
```

### 集成到Android应用

**Gradle配置**：
```kotlin
android {
    defaultConfig {
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
}

dependencies {
    implementation project(':server-k')
}
```

**Java代码示例**：
```java
public class ScreenshotService {
    private Server server;

    public void startServer() {
        server = new Server();
        server.initialize(Server.MODE_TCP_SOCKET,
                         1024*1024*16, // 16MB
                         "127.0.0.1",
                         7777);
        server.start();
    }

    public void stopServer() {
        if (server != null) {
            server.stop();
            server.cleanup();
        }
    }
}
```

## 📈 版本历史

### v1.0.0 (当前版本)
- ✅ 支持三种通信协议
- ✅ 嵌入式服务器部署
- ✅ OpenCV图像处理
- ✅ 自动启动功能
- ✅ 详细调试日志

### 计划功能
- 🔄 支持更多图像格式
- 🔄 添加图像压缩选项
- 🔄 支持多客户端连接
- 🔄 添加认证机制
- 🔄 性能监控和统计

## 🔒 安全考虑

### 权限管理

- **系统级运行**：通过adb shell获得必要的系统权限
- **权限检查**：在使用前验证显示服务可用性
- **安全运行**：在受控环境中运行，避免权限滥用

### 数据安全

- **内存清理**：及时释放敏感数据
- **传输加密**：考虑对网络传输进行加密
- **访问控制**：限制服务器访问来源

### 最佳实践

```java
// 服务可用性检查示例
public boolean checkSystemServices() {
    try {
        // 检查显示管理器是否可用
        DisplayManager displayManager = ServiceManager.getDisplayManager();
        if (displayManager == null) {
            Log.e(TAG, "DisplayManager不可用");
            return false;
        }

        // 检查显示信息是否可获取
        DisplayInfo displayInfo = displayManager.getDisplayInfo(0);
        if (displayInfo == null) {
            Log.e(TAG, "无法获取显示信息");
            return false;
        }

        return true;
    } catch (Exception e) {
        Log.e(TAG, "系统服务检查失败", e);
        return false;
    }
}

// 资源管理示例
public void cleanup() {
    try {
        if (server != null) {
            server.stop();
            server.cleanup();
        }
    } catch (Exception e) {
        Log.e(TAG, "Cleanup failed", e);
    } finally {
        server = null;
    }
}
```

## 🎯 最佳实践

### 性能优化

1. **选择合适的通信模式**
   - 本地高性能：Shared Memory
   - 通用场景：TCP Socket
   - 轻量级本地：Unix Socket

2. **内存管理**
   - 及时释放截图数据
   - 使用合适的内存大小
   - 避免内存泄漏

3. **错误处理**
   - 检查所有返回值
   - 实现重试机制
   - 记录详细错误日志

### 集成建议

1. **模块化设计**
   ```java
   public class ScreenshotManager {
       private Server server;
       private boolean isInitialized = false;

       public synchronized boolean initialize() {
           if (isInitialized) return true;

           server = new Server();
           int result = server.initialize(
               Server.MODE_SHARED_MEMORY,
               16 * 1024 * 1024,
               "127.0.0.1",
               7777
           );

           isInitialized = (result == 0);
           return isInitialized;
       }
   }
   ```

2. **异步操作**
   ```java
   public void takeScreenshotAsync(Callback callback) {
       new Thread(() -> {
           try {
               byte[] data = takeScreenshot();
               callback.onSuccess(data);
           } catch (Exception e) {
               callback.onError(e);
           }
       }).start();
   }
   ```

3. **配置管理**
   ```java
   public class ServerConfig {
       public static final int DEFAULT_MEMORY_SIZE = 16 * 1024 * 1024;
       public static final int DEFAULT_TCP_PORT = 7777;
       public static final String DEFAULT_TCP_HOST = "127.0.0.1";

       // 根据屏幕分辨率计算内存大小
       public static int calculateMemorySize(int width, int height) {
           return width * height * 4 * 120 / 100; // 预留20%
       }
   }
   ```

### 调试技巧

1. **启用详细日志**
   ```bash
   # 设置日志级别
   adb shell setprop log.tag.SERVER-K VERBOSE

   # 过滤相关日志
   adb logcat -s "SERVER-K*:V"
   ```

2. **性能监控**
   ```java
   long startTime = System.currentTimeMillis();
   byte[] screenshot = takeScreenshot();
   long duration = System.currentTimeMillis() - startTime;
   Log.d(TAG, "Screenshot took " + duration + "ms");
   ```

3. **内存监控**
   ```java
   Runtime runtime = Runtime.getRuntime();
   long usedMemory = runtime.totalMemory() - runtime.freeMemory();
   Log.d(TAG, "Memory usage: " + usedMemory / 1024 / 1024 + "MB");
   ```

## 🔗 相关链接

- [Android VirtualDisplay API](https://developer.android.com/reference/android/hardware/display/VirtualDisplay)
- [Android SurfaceControl](https://developer.android.com/reference/android/view/SurfaceControl)
- [Android ImageReader](https://developer.android.com/reference/android/media/ImageReader)
- [OpenCV Android](https://opencv.org/android/)
- [Scrcpy项目](https://github.com/Genymobile/scrcpy)
- [Android NDK文档](https://developer.android.com/ndk)
- [Gradle构建工具](https://gradle.org/)
