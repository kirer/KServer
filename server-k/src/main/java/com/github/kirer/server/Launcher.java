package com.github.kirer.server;

import android.annotation.SuppressLint;
import android.os.Looper;

import com.genymobile.scrcpy.util.Ln;

/**
 * Server-K 主入口类 - 重构版屏幕截图服务启动器
 * <p>
 * 新架构特点：
 * 1. 数据层：共享内存永远存在，负责高性能截图数据传输
 * 2. 控制层：Socket通信负责控制信令和通知机制
 * 3. 统一架构：TCP和Unix Socket仅用于控制通信，数据传输统一使用共享内存
 * <p>
 * 功能：
 * 1. 解析命令行参数（简化为socket-type和address）
 * 2. 初始化共享内存数据层
 * 3. 启动Socket控制层服务器
 * 4. 启动屏幕截图服务
 */
public class Launcher {
    private static final String VERSION = "2.0.0";
    private static final String TAG = "Launcher";
    private static ScreenCaptureService screenCaptureService;
    private static final Config CONFIG = new Config();

    /**
     * 程序主入口点
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        try {
            Ln.i("[" + TAG + "] Server-K v" + VERSION + " 正在启动...");
            // 设置未捕获异常处理器
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable throwable) {
                    Ln.e("[" + TAG + "] 线程 " + thread.getName() + " 发生未捕获异常", throwable);
                    clean();
                    System.exit(1);
                }
            });
            // 解析命令行参数
            if (!parseArguments(args)) {
                Ln.e("[" + TAG + "] 参数解析失败，退出程序");
                System.exit(1);
            }
            // 验证配置
            if (!validateConfig()) {
                Ln.e("[" + TAG + "] 配置验证失败，退出程序");
                System.exit(1);
            }
            // 准备主循环器
            Ln.d("[" + TAG + "] 准备主事件循环器");
            Looper.prepareMainLooper();
            // 初始化服务器（共享内存 + Socket控制层）
            initializeServer();
            // 运行主循环
            Ln.i("[" + TAG + "] 进入主事件循环");
            Looper.loop();
        } catch (Exception e) {
            Ln.e("[" + TAG + "] 启动过程中发生致命错误", e);
            clean();
            System.exit(1);
        }
    }

    /**
     * 解析命令行参数（新架构简化版）
     * 支持参数：
     * --socket-type=tcp|unix (默认: tcp)
     * --address=<地址> (TCP: host:port, Unix: socket_name)
     * --lib-path=<路径> (共享内存库路径)
     *
     * @param args 命令行参数数组
     * @return 解析是否成功
     */
    private static boolean parseArguments(String[] args) {
        try {
            Ln.d("[" + TAG + "] 开始解析命令行参数");
            // 设置默认值
            CONFIG.setSocketType(Mode.TCP_SOCKET);
            CONFIG.setAddress("127.0.0.1:7777");
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                Ln.d("[" + TAG + "] 处理参数: " + arg);
                if (!arg.startsWith("--")) {
                    Ln.w("[" + TAG + "] 忽略无效参数: " + arg);
                    continue;
                }
                if (arg.contains("=")) {
                    // 键值对格式：--key=value
                    String[] parts = arg.split("=", 2);
                    String key = parts[0];
                    String value = parts[1];
                    switch (key) {
                        case "--socket-type":
                            if ("tcp".equals(value)) {
                                CONFIG.setSocketType(Mode.TCP_SOCKET);
                                Ln.i("[" + TAG + "] 设置Socket类型: TCP");
                            } else if ("unix".equals(value)) {
                                CONFIG.setSocketType(Mode.UNIX_SOCKET);
                                Ln.i("[" + TAG + "] 设置Socket类型: Unix");
                            } else {
                                Ln.e("[" + TAG + "] 无效的Socket类型: " + value + " (支持: tcp, unix)");
                                return false;
                            }
                            break;
                        case "--address":
                            CONFIG.setAddress(value);
                            Ln.i("[" + TAG + "] 设置地址: " + value);
                            break;
                        case "--lib-path":
                            CONFIG.setLibPath(value);
                            Ln.i("[" + TAG + "] 设置库路径: " + value);
                            break;
                        default:
                            Ln.w("[" + TAG + "] 未知参数: " + key);
                            break;
                    }
                } else {
                    // 单独的长选项
                    switch (arg) {
                        case "--debug":
                            CONFIG.setDebug(true);
                            Ln.d("[" + TAG + "] 开启调试模式");
                            break;
                        case "--help":
                            printUsage();
                            return false;
                        case "--version":
                            Ln.i("[" + TAG + "] Server-K 版本: " + VERSION);
                            return false;
                        default:
                            Ln.w("[" + TAG + "] 未知选项: " + arg);
                            break;
                    }
                }
            }
            Ln.i("[" + TAG + "] 参数解析完成");
            Ln.i("[" + TAG + "] 当前配置: " + CONFIG);
            return true;
        } catch (Exception e) {
            Ln.e("[" + TAG + "] 解析命令行参数时发生错误", e);
            return false;
        }
    }

    /**
     * 验证配置是否有效
     *
     * @return true表示配置有效
     */
    private static boolean validateConfig() {
        try {
            Ln.d("[" + TAG + "] 验证Server-K配置");
            // 验证Socket类型
            if (CONFIG.getSocketType() == null) {
                Ln.e("[" + TAG + "] Socket类型未设置");
                return false;
            }
            // 验证地址配置
            if (CONFIG.getAddress() == null || CONFIG.getAddress().trim().isEmpty()) {
                Ln.e("[" + TAG + "] 地址未设置");
                return false;
            }
            // 根据Socket类型验证地址格式
            switch (CONFIG.getSocketType()) {
                case UNIX_SOCKET:
                    // Unix Socket: 直接使用地址作为socket名称
                    Ln.d("[" + TAG + "] Unix Socket名称: " + CONFIG.getAddress());
                    break;
                case TCP_SOCKET:
                    // TCP Socket: 验证host:port格式
                    if (!CONFIG.getAddress().contains(":")) {
                        Ln.e("[" + TAG + "] TCP地址格式错误，应为 host:port");
                        return false;
                    }
                    String[] parts = CONFIG.getAddress().split(":", 2);
                    try {
                        int port = Integer.parseInt(parts[1]);
                        if (port <= 0 || port > 65535) {
                            Ln.e("[" + TAG + "] TCP端口号无效: " + port);
                            return false;
                        }
                    } catch (NumberFormatException e) {
                        Ln.e("[" + TAG + "] TCP端口号格式错误: " + parts[1]);
                        return false;
                    }
                    break;
                default:
                    Ln.e("[" + TAG + "] 不支持的Socket类型: " + CONFIG.getSocketType());
                    return false;
            }
            // 验证库路径（可选）
            if (CONFIG.getLibPath() != null && !CONFIG.getLibPath().trim().isEmpty()) {
                Ln.d("[" + TAG + "] 使用自定义库路径: " + CONFIG.getLibPath());
            }
            Ln.d("[" + TAG + "] Server-K配置验证通过");
            return true;
        } catch (Exception e) {
            Ln.e("[" + TAG + "] 验证配置时发生错误", e);
            return false;
        }
    }

    /**
     * 初始化服务器（新架构：共享内存数据层 + Socket控制层）
     */
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private static void initializeServer() {
        try {
            Ln.i("[" + TAG + "] 开始初始化Server-K服务器");
            // 1. 加载native库
            Ln.d("[" + TAG + "] 加载库文件: " + CONFIG.getLibPath() + "/lib.so");
            System.load(CONFIG.getLibPath() + "/lib.so");
            Ln.i("[" + TAG + "] 库文件加载完成");
            // 2. 准备初始化Socket控制层服务器
            Ln.d("[" + TAG + "] 准备初始化" + CONFIG.getSocketType() + "控制层服务器");
            int result = Server.initialize(Mode.getModeValue(CONFIG.getSocketType()), CONFIG.getAddress(), CONFIG.isDebug());
            if (result != 0) {
                throw new RuntimeException("服务器初始化失败，错误码: " + result);
            }
            Ln.i("[" + TAG + "] 控制层：" + CONFIG.getSocketType() + " Socket服务器已就绪");
            // 3. 启动服务器
            Ln.d("[" + TAG + "] 启动" + CONFIG.getSocketType() + "控制层服务器");
            result = Server.start();
            if (result != 0) {
                throw new RuntimeException("服务器启动失败，错误码: " + result);
            }
            Server.setMessageCallback(new Server.ServerMessageListener() {
                @Override
                public void onMessage(int type, byte[] data) {
                    Ln.d("[" + TAG + "] 收到控制层消息: " + type);
                    switch (type) {
                        case Server.MSG_TYPE_INIT_SCREEN_CAPTURE:
                            Ln.d("[" + TAG + "] 启动屏幕捕获服务");
                            try {
                                screenCaptureService = new ScreenCaptureService();
                                screenCaptureService.start();
                            } catch (Exception e) {
                                Ln.e("[" + TAG + "] 启动屏幕捕获服务失败", e);
                            }
                            break;
                    }
                }
            });
            Ln.i("[" + TAG + "] 控制层：" + CONFIG.getSocketType() + " Socket服务器已启动");
            Ln.i("[" + TAG + "] Server-K服务器初始化完成");
        } catch (Exception e) {
            throw new RuntimeException("服务器初始化失败", e);
        }

    }

    /**
     * 打印使用帮助（新架构版本）
     */
    private static void printUsage() {
        System.out.println("Server-K v" + VERSION + " - Android屏幕截图服务（重构版）");
        System.out.println();
        System.out.println("新架构特点:");
        System.out.println("  • 数据层：共享内存永远存在，负责高性能截图数据传输");
        System.out.println("  • 控制层：Socket通信负责控制信令和通知机制");
        System.out.println("  • 统一架构：TCP和Unix Socket仅用于控制通信");
        System.out.println();
        System.out.println("用法: CLASSPATH=<apk路径> app_process /system/bin " + Launcher.class.getName() + " [选项]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  --help                  显示此帮助信息");
        System.out.println("  --version               显示版本信息");
        System.out.println();
        System.out.println("Socket控制层配置:");
        System.out.println("  --socket-type=<类型>    设置Socket类型（默认: tcp）");
        System.out.println("                          tcp: TCP Socket控制层");
        System.out.println("                          unix: Unix Socket控制层");
        System.out.println();
        System.out.println("  --address=<地址>        设置Socket地址");
        System.out.println("                          TCP格式: host:port（默认: 127.0.0.1:8888）");
        System.out.println("                          Unix格式: socket_name（默认: server-k2）");
        System.out.println();
        System.out.println("共享内存数据层配置:");
        System.out.println("  --lib-path=<路径>       设置native库路径（可选）");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  # 使用TCP Socket控制层");
        System.out.println("  CLASSPATH=/data/app/com.example.app/base.apk app_process /system/bin " + Launcher.class.getName() + " --socket-type=tcp --address=127.0.0.1:9999");
        System.out.println();
        System.out.println("  # 使用Unix Socket控制层");
        System.out.println("  CLASSPATH=/data/app/com.example.app/base.apk app_process /system/bin " + Launcher.class.getName() + " --socket-type=unix --address=my-socket");
        System.out.println();
        System.out.println("  # 指定库路径");
        System.out.println("  CLASSPATH=/data/app/com.example.app/base.apk app_process /system/bin " + Launcher.class.getName() + " --lib-path=/data/local/tmp");
        System.out.println();
        System.out.println("注意: 数据传输始终使用共享内存，Socket仅用于控制通信");
    }

    /**
     * 清理资源（新架构版本）
     */
    private static void clean() {
        try {
            Ln.i("[" + TAG + "] 开始清理Server-K资源");
            // 停止屏幕截图服务
            if (screenCaptureService != null) {
                Ln.d("[" + TAG + "] 停止屏幕截图服务");
                screenCaptureService.stop();
                screenCaptureService = null;
            }
            // 停止Socket控制层服务器（通过native方法）
            Ln.d("[" + TAG + "] 停止Socket控制层服务器");
            Server.stop();
            // 清理共享内存数据层
            Ln.d("[" + TAG + "] 清理共享内存数据层");
            ShareMemory.clean();
            // 清理native资源
            Ln.d("[" + TAG + "] 清理native资源");
            Server.clean();
            Ln.i("[" + TAG + "] Server-K资源清理完成");
        } catch (Exception e) {
            Ln.e("[" + TAG + "] 清理资源时发生错误", e);
        }
    }


}
