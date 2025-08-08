package com.github.kirer.server;

import android.annotation.SuppressLint;
import android.os.Looper;

import com.genymobile.scrcpy.util.Ln;

/**
 * 主入口类 - K Server 屏幕截图服务启动器
 * <p>
 * 功能：
 * 1. 解析命令行参数
 * 2. 初始化屏幕截图服务
 * 3. 启动持续的屏幕捕获循环
 * 4. 支持多种通信方式（共享内存、Unix套接字、TCP套接字）
 */
public class Launcher {
    private static final String VERSION = "1.0.0";
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
            Ln.i("[" + TAG + "] K Service v" + VERSION + " 正在启动...0");
            // 设置未捕获异常处理器
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable throwable) {
                    Ln.e("[" + TAG + "] 线程 " + thread.getName() + " 发生未捕获异常", throwable);
                    cleanup();
                    System.exit(1);
                }
            });
            Ln.i("[" + TAG + "] K Service v" + VERSION + " 正在启动...1");
            // 解析命令行参数
            if (!parseArguments(args)) {
                Ln.e("[" + TAG + "] 参数解析失败，退出程序");
                System.exit(1);
            }
            // 验证通信配置
            if (!validateConfig()) {
                Ln.e("[" + TAG + "] 通信配置验证失败，退出程序");
                System.exit(1);
            }
            // 准备主循环器
            Ln.d("[" + TAG + "] 准备主事件循环器");
            Looper.prepareMainLooper();
            initializeServer();
            // 创建并启动屏幕截图服务
            Ln.d("[" + TAG + "] 创建屏幕捕获服务");
            screenCaptureService = new ScreenCaptureService(CONFIG);
            screenCaptureService.start();
            // 运行主循环
            Ln.i("[" + TAG + "] 进入主事件循环");
            Looper.loop();
        } catch (Exception e) {
            Ln.e("[" + TAG + "] 启动过程中发生致命错误", e);
            cleanup();
            System.exit(1);
        }
    }

    /**
     * 解析命令行参数
     *
     * @param args 命令行参数数组
     * @return 解析是否成功
     */
    private static boolean parseArguments(String[] args) {
        for (int j = 0; j < args.length; j++) {
            Ln.i("[" + TAG + "]   args[" + j + "] = '" + args[j] + "'");
        }
        try {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                switch (arg) {
                    case "--version":
                    case "-v":
                        Ln.i("[" + TAG + "] K Service v" + VERSION);
                        System.exit(0);
                        break;
                    case "--debug":
                        Ln.initLogLevel(Ln.Level.DEBUG);
                        Ln.d("[" + TAG + "] 调试模式已启用");
                        break;
                    case "--help":
                    case "-h":
                        printUsage();
                        System.exit(0);
                        break;
                    // 通信方式参数
                    case "--mode":
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            String mode = args[++i].toUpperCase();
                            if (setMode(mode)) {
                                return false;
                            }
                        } else {
                            Ln.e("[" + TAG + "] --mode 参数缺失");
                            return false;
                        }
                        break;
                    // 共享内存相关参数
                    case "--libPath":
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            CONFIG.setLibPath(args[++i]);
                            Ln.d("[" + TAG + "] 设置库路径: " + CONFIG.getLibPath());
                        } else {
                            Ln.e("[" + TAG + "] --libPath 参数缺失");
                            return false;
                        }
                        break;

                    // Unix套接字相关参数
                    case "--socketName":
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            CONFIG.setSocketName(args[++i]);
                            Ln.d("[" + TAG + "] 设置套接字路径: " + CONFIG.getSocketName());
                        } else {
                            Ln.e("[" + TAG + "] --socketName 参数缺失");
                            return false;
                        }
                        break;

                    // TCP套接字相关参数
                    case "--tcpPort":
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            try {
                                int port = Integer.parseInt(args[++i]);
                                CONFIG.setTcpPort(port);
                                Ln.d("[" + TAG + "] 设置TCP端口: " + port);
                            } catch (NumberFormatException e) {
                                Ln.e("[" + TAG + "] TCP端口格式错误: " + args[i]);
                                return false;
                            }
                        } else {
                            Ln.e("[" + TAG + "] --tcpPort 参数缺失");
                            return false;
                        }
                        break;

                    case "--tcpHost":
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            CONFIG.setTcpHost(args[++i]);
                            Ln.d("[" + TAG + "] 设置TCP主机: " + CONFIG.getTcpHost());
                        } else {
                            Ln.e("[" + TAG + "] --tcpHost 参数缺失");
                            return false;
                        }
                        break;

                    default:
                        // 处理键值对格式的参数
                        if (arg.startsWith("--mode=")) {
                            String mode = arg.substring("--mode=".length()).toUpperCase();
                            if (setMode(mode)) {
                                return false;
                            }
                        } else if (arg.startsWith("--libPath=")) {
                            CONFIG.setLibPath(arg.substring("--libPath=".length()));
                            Ln.d("[" + TAG + "] 从键值对格式设置库路径: " + CONFIG.getLibPath());
                        } else if (arg.startsWith("--socketName=")) {
                            CONFIG.setSocketName(arg.substring("--socketName=".length()));
                            Ln.d("[" + TAG + "] 从键值对格式设置套接字路径: " + CONFIG.getSocketName());
                        } else if (arg.startsWith("--tcpPort=")) {
                            try {
                                int port = Integer.parseInt(arg.substring("--tcpPort=".length()));
                                CONFIG.setTcpPort(port);
                                Ln.d("[" + TAG + "] 从键值对格式设置TCP端口: " + port);
                            } catch (NumberFormatException e) {
                                Ln.e("[" + TAG + "] TCP端口格式错误: " + arg);
                                return false;
                            }
                        } else if (arg.startsWith("--tcpHost=")) {
                            CONFIG.setTcpHost(arg.substring("--tcpHost=".length()));
                            Ln.d("[" + TAG + "] 从键值对格式设置TCP主机: " + CONFIG.getTcpHost());
                        } else if (arg.startsWith("-")) {
                            Ln.w("[" + TAG + "] 未知参数: " + arg);
                        } else {
                            Ln.d("[" + TAG + "] 忽略非选项参数: " + arg);
                        }
                        break;
                }
            }
            Ln.i("[" + TAG + "] 参数解析完成");
            return true;

        } catch (Exception e) {
            Ln.e("[" + TAG + "] 处理参数时出错", e);
            return false;
        }
    }

    /**
     * 验证通信配置
     */
    private static boolean validateConfig() {
        try {
            switch (CONFIG.getMode()) {
                case SHARED_MEMORY:
                    if (CONFIG.getLibPath() == null || CONFIG.getLibPath().isEmpty()) {
                        Ln.e("[" + TAG + "] 共享内存模式需要指定库路径");
                        return false;
                    }
                    break;
                case UNIX_SOCKET:
                    if (CONFIG.getSocketName() == null || CONFIG.getSocketName().isEmpty()) {
                        Ln.e("[" + TAG + "] Unix套接字模式需要指定套接字路径");
                        return false;
                    }
                    break;
                case TCP_SOCKET:
                    if (CONFIG.getTcpPort() <= 0 || CONFIG.getTcpPort() > 65535) {
                        Ln.e("[" + TAG + "] TCP套接字模式需要指定有效端口号(1-65535)");
                        return false;
                    }
                    break;
            }
            return true;
        } catch (Exception e) {
            Ln.e("[" + TAG + "] 验证通信配置时出错", e);
            return false;
        }
    }

    /**
     * 设置通信方式
     *
     * @param mode 通信方式字符串
     * @return true表示设置成功，false表示设置失败
     */
    private static boolean setMode(String mode) {
        try {
            Mode commMode = Mode.valueOf(mode);
            CONFIG.setMode(commMode);
            Ln.d("[" + TAG + "] 设置通信方式: " + commMode);
            return false;
        } catch (IllegalArgumentException e) {
            Ln.e("[" + TAG + "] 无效的通信方式: " + mode + "，支持的方式: SHARED_MEMORY, UNIX_SOCKET, TCP_SOCKET");
            return true;
        }
    }

    /**
     * 初始化通信通道
     */
    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private static void initializeServer() {
        try {
            Ln.d("[" + TAG + "] 加载库文件: " + CONFIG.getLibPath() + "/libserver-k.so");
            System.load(CONFIG.getLibPath() + "/libserver-k.so");
            Ln.d("[" + TAG + "] 库文件加载完成");
        } catch (Exception e) {
            Ln.e("[" + TAG + "] 加载库文件失败", e);
            throw new RuntimeException(e);
        }
        Ln.d("[" + TAG + "] 初始化通信通道");
        int memorySize = ScreenCaptureService.getScreenshotMemorySize();
        CONFIG.setMemorySize(memorySize);
        Ln.i("[" + TAG + "] 设置共享内存大小: " + memorySize + " 字节");
        int result = Server.initializeWithParams(Mode.getModeValue(CONFIG.getMode()), CONFIG.getMemorySize(), CONFIG.getLibPath(), CONFIG.getSocketName(), CONFIG.getTcpHost(), CONFIG.getTcpPort());
        if (result != 0) {
            Ln.e("[" + TAG + "] 通信通道初始化失败: " + CONFIG.getMode());
            return;
        }
        Ln.i("[" + TAG + "] 通信通道初始化完成: " + CONFIG.getMode());
    }

    /**
     * 打印使用帮助
     */
    private static void printUsage() {
        System.out.println("K Service v" + VERSION + " - Android屏幕截图服务");
        System.out.println();
        System.out.println("用法: CLASSPATH=<apk路径> app_process /system/bin " + Launcher.class.getName() + " [选项]");
        System.out.println();
        System.out.println("通用选项:");
        System.out.println("  --version, -v          显示版本信息");
        System.out.println("  --help, -h             显示此帮助信息");
        System.out.println("  --debug                启用调试模式");
        System.out.println("  --mode <MODE>          设置通信方式 (SHARED_MEMORY|UNIX_SOCKET|TCP_SOCKET)");
        System.out.println();
        System.out.println("共享内存模式选项:");
        System.out.println("  --libPath <PATH>       指定native库路径");
        System.out.println();
        System.out.println("Unix套接字模式选项:");
        System.out.println("  --socketName <NAME>    指定套接字文件路径 (默认: k-server.socket)");
        System.out.println();
        System.out.println("TCP套接字模式选项:");
        System.out.println("  --tcpPort <PORT>       指定TCP端口号 (默认: 8888)");
        System.out.println("  --tcpHost <HOST>       指定TCP主机地址 (默认: 127.0.0.1)");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  # 使用共享内存模式");
        System.out.println("  CLASSPATH=/data/app/com.example.app/base.apk app_process /system/bin " + Launcher.class.getName() + " --mode=SHARED_MEMORY --libPath=/data/local/tmp");
        System.out.println();
        System.out.println("  # 使用Unix套接字模式");
        System.out.println("  CLASSPATH=/data/app/com.example.app/base.apk app_process /system/bin " + Launcher.class.getName() + " --mode=UNIX_SOCKET --socketPath=/data/local/tmp/kserver.sock");
        System.out.println();
        System.out.println("  # 使用TCP套接字模式");
        System.out.println("  CLASSPATH=/data/app/com.example.app/base.apk app_process /system/bin " + Launcher.class.getName() + " --mode=TCP_SOCKET --tcpPort=8888");
    }

    /**
     * 清理资源
     */
    private static void cleanup() {
        Ln.i("[" + TAG + "] 开始清理系统资源");
        if (screenCaptureService != null) {
            screenCaptureService.stop();
        }
        Ln.i("[" + TAG + "] 资源清理完成");
    }



}
