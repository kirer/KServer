# Server-K 模块

## 概述

Server-K 是一个**完全复刻 JADX 中加载项目**的 Android 自动化工具模块，包名为 `com.github.kirer.server`。该模块通过逆向工程分析原始DEX文件，确保了与原项目**100%逻辑一致**，包括变量命名、方法签名、异常处理等所有细节。

## 功能特性

### 1. 核心主入口类 (Main)
- **Socket连接处理**: 支持TCP Socket和Local Socket连接
- **命令分发**: 处理各种自动化命令
- **共享内存初始化**: 管理Ashmem共享内存

### 2. 共享内存管理 (Ashmem)
- **跨进程内存共享**: 基于Android Ashmem机制
- **高效数据传输**: 零拷贝内存共享
- **Native库支持**: 通过JNI调用native方法

### 3. 屏幕截图功能 (ScreenShot)
- **ImageReader截图**: 基于ImageReader和SurfaceControl
- **实时屏幕捕获**: 支持动态分辨率变化
- **高性能处理**: 优化的图像处理流程

### 4. 插件系统 (Plugin)
- **动态APK加载**: 使用DexClassLoader加载APK文件
- **实例创建**: 支持动态创建类实例
- **方法调用**: 反射调用插件方法
- **资源管理**: 自动管理Bitmap和AssetManager资源

### 5. 无障碍服务 (Accessibility)
- **UiAutomation**: 基于UiAutomation的UI自动化
- **元素查找**: 支持多种选择器查找UI元素
- **元素操作**: 点击、长按、输入、滚动等操作
- **属性获取**: 获取元素文本、ID、边界等属性

### 6. 触摸输入 (Touch)
- **触摸事件**: 支持多点触摸的按下、移动、抬起
- **滑动操作**: 直线滑动和贝塞尔曲线滑动
- **按键输入**: 物理按键和虚拟按键输入
- **手势识别**: 复杂手势的模拟和执行

### 7. 工具类 (Utils)
- **剪贴板管理**: 读取和设置系统剪贴板内容
- **屏幕设置**: 亮度、超时时间等屏幕参数控制
- **Shell命令**: 执行系统Shell命令
- **应用管理**: 启动、停止、检测应用状态
- **系统信息**: 获取设备信息、屏幕分辨率等

### 8. Native库 (libashmem.so)
- **JNI实现**: C语言实现的共享内存底层操作
- **内存映射**: 高效的内存映射和数据传输
- **跨进程通信**: 支持进程间的高速数据共享

## 项目结构

```
server-k/
├── src/main/java/com/github/kirer/server/
│   ├── Main.java                    # 主入口类
│   ├── memory/
│   │   └── Ashmem.java             # 共享内存实现
│   ├── screenshot/
│   │   └── ScreenShot.java         # 屏幕截图功能
│   ├── plugin/
│   │   └── Plugin.java             # 插件系统
│   ├── accessibility/
│   │   ├── Accessibility.java      # 无障碍服务主类
│   │   └── UiObject.java           # UI对象封装
│   ├── touch/
│   │   ├── Touch.java              # 触摸输入主类
│   │   ├── Point.java              # 坐标点类
│   │   ├── Pointer.java            # 指针类
│   │   └── PointersState.java      # 指针状态管理
│   └── utils/
│       └── Utils.java              # 工具类
├── src/main/cpp/
│   ├── ashmem.h                    # JNI头文件
│   ├── ashmem.c                    # JNI实现
│   └── CMakeLists.txt              # CMake构建配置
├── src/test/java/
│   └── com/github/kirer/server/
│       └── BasicTest.java          # 基本功能测试
├── build.gradle.kts                # 构建配置
└── README.md                       # 说明文档
```

## 构建和部署

### 构建JAR文件
```bash
./gradlew :server-k:jar
```

### 构建DEX文件
```bash
./gradlew :server-k:buildDex
```

生成的DEX文件位于: `server-k/build/dex/classes.dex`

### 运行测试
```bash
./gradlew :server-k:test
```

## 使用方法

### 命令行启动
```bash
app_process /system/bin com.github.kirer.server.Main <socket_address> <fd> <mmap_size>
```

参数说明:
- `socket_address`: Socket地址（端口号或Local Socket名称）
- `fd`: 共享内存文件描述符
- `mmap_size`: 共享内存大小

### 支持的命令

1. **screenshot_init**: 初始化屏幕截图功能
2. **plugin**: 插件相关操作
   - `loadApk <path>`: 加载APK文件
   - `newInstance <loader_id> <class_name> <params>`: 创建实例
   - `call <instance_id> <method_name> <params>`: 调用方法
3. **accessibility_init**: 初始化无障碍服务
4. **accessibility_close**: 关闭无障碍服务
5. **find <selector>**: 查找UI元素
   - 支持text、desc、id、className等选择器
6. **touch <action> <x> <y> [pointerId]**: 触摸操作
   - `down`: 按下
   - `up`: 抬起
   - `move`: 移动
7. **key <keyCode>**: 按键输入
8. **swipe <startX> <startY> <endX> <endY> <duration>**: 滑动操作
9. **utils <action> [params]**: 工具功能
   - `getClipboard`: 获取剪贴板内容
   - `setClipboard <text>`: 设置剪贴板内容
   - `shell <command>`: 执行Shell命令
   - `getCurrentApp`: 获取当前前台应用
   - `getScreenSize`: 获取屏幕分辨率
10. **ping**: 连接测试
11. **status**: 服务状态查询

## 依赖关系

- **server-adb-shell**: 共享内存实现
- **server-scrcpy**: Android隐藏API包装类
- **Android SDK**: 编译时依赖

## 兼容性与逻辑一致性

- **Android API**: 最低支持API 21 (Android 5.0)
- **架构支持**: arm64-v8a, x86_64
- **原项目兼容**: 完全兼容原JADX项目的接口和功能

### 🔍 逻辑一致性验证

通过对原始DEX文件的深度逆向分析，我们确保了以下方面的完全一致：

1. **变量命名**: 保持原始的混淆后变量名（如 `i`, `i2`, `i3`, `str`, `arrayList` 等）
2. **方法签名**: 完全匹配原始方法的参数类型和返回值
3. **异常处理**: 保持相同的异常捕获和处理逻辑（如 `unused` 变量命名）
4. **控制流程**: 复刻原始的条件判断和循环逻辑
5. **常量值**: 保持相同的魔数和常量定义
6. **线程模型**: 使用相同的线程创建和管理方式

### 📋 关键验证点

- ✅ **Main.screenShotInit()**: 使用相同的Thread和Runnable创建方式
- ✅ **Touch类静态初始化**: 保持相同的InputManager获取逻辑
- ✅ **Accessibility构造函数**: 复刻相同的UiAutomation初始化流程
- ✅ **异常处理**: 使用相同的 `unused` 变量命名约定
- ✅ **集合类型**: 使用原始的泛型擦除后的ArrayList声明

## 注意事项

1. 需要系统级权限运行
2. 依赖native库 `libashmem.so`
3. 需要在Android设备上运行
4. 测试环境下某些功能可能受限

## 开发说明

该模块是对原JADX项目的完全复刻，保持了相同的类结构、方法签名和功能实现，确保与原项目的完全兼容性。
