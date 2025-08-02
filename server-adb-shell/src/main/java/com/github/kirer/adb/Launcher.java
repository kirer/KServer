package com.github.kirer.adb;

import android.os.Looper;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.net.CommandProcessor;
import com.github.kirer.adb.net.SocketServer;
import com.github.kirer.adb.screenshot.ScreenshotConfig;
import com.github.kirer.adb.screenshot.ScreenshotService;
import com.github.kirer.adb.commands.ScreenshotCommand;

public class Launcher {
    private static final String VERSION = "1.0.0";
    private static int socketPort = 8888;
    private static ScreenshotService screenshotService;

    /**
     * 主入口点，通过app_process调用
     */
    public static void main(String[] args) {
        Ln.i("K Shell ADB Service v" + VERSION + " starting...");
        // 设置未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable exception) {
                Ln.e("Uncaught exception in thread " + thread.getName(), exception);
                System.exit(1);
            }
        });
        try {
            // 准备主循环器
            prepareMainLooper();
            // 解析命令行参数
            ScreenshotConfig config = parseArguments(args);
            // 创建并初始化服务
            screenshotService = new ScreenshotService(config);
            // 启动服务
            screenshotService.start();
            startSocketServer(screenshotService);
            // 运行主循环
            Looper.loop();
        } catch (Exception e) {
            Ln.e("Fatal error during startup", e);
            System.exit(1);
        } finally {
            if (screenshotService != null) {
                screenshotService.cleanup();
            }
        }
    }

    /**
     * 准备主循环器
     */
    private static void prepareMainLooper() {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
    }

    /**
     * 解析命令行参数
     */
    private static ScreenshotConfig parseArguments(String[] args) {
        ScreenshotConfig config = new ScreenshotConfig();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            try {
                switch (arg) {
                    case "--help":
                    case "-h":
                        printUsage();
                        System.exit(0);
                        break;
                    case "--version":
                    case "-v":
                        System.out.println("Screenshot Service v" + VERSION);
                        System.exit(0);
                        break;
                    case "--debug":
                        Ln.initLogLevel(Ln.Level.DEBUG);
                        break;
                    case "--port":
                    case "-p":
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            socketPort = Integer.parseInt(args[++i]);
                        }
                        break;
                    case "--display-id":
                    case "-d":
                        if (i + 1 < args.length) {
                            config.setDisplayId(Integer.parseInt(args[++i]));
                        }
                        break;
                    case "--max-images":
                    case "-m":
                        if (i + 1 < args.length) {
                            config.setMaxImages(Integer.parseInt(args[++i]));
                        }
                        break;
                    case "--no-auto-rotate":
                        config.setAutoRotate(false);
                        break;
                    default:
                        if (arg.startsWith("-")) {
                            Ln.w("Unknown argument: " + arg);
                        }
                        break;
                }
            } catch (NumberFormatException e) {
                Ln.e("Invalid number format for argument " + arg);
                System.exit(1);
            }
        }
        Ln.i("Using config: " + config);
        return config;
    }

    /**
     * 打印使用说明
     */
    private static void printUsage() {
        System.out.println("Screenshot Service v" + VERSION);
        System.out.println("Usage: CLASSPATH=screenshot-service.jar app_process /system/bin com.screenshot.ScreenshotService [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -d, --display-id <id>     Display ID to capture (default: 0)");
        System.out.println("  -m, --max-images <num>    Maximum images in buffer (default: 2)");
        System.out.println("  -s, --socket [port]       Enable socket server mode (default port: 8888)");
        System.out.println("  --no-auto-rotate          Disable automatic rotation handling");
        System.out.println("  --debug                   Enable debug logging");
        System.out.println("  -h, --help                Show this help message");
        System.out.println("  -v, --version             Show version information");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # Start socket server on default port 8888");
        System.out.println("  adb shell CLASSPATH=/data/local/tmp/screenshot-service.jar app_process /system/bin com.screenshot.ScreenshotService --socket");
        System.out.println();
        System.out.println("  # Start socket server on custom port");
        System.out.println("  adb shell CLASSPATH=/data/local/tmp/screenshot-service.jar app_process /system/bin com.screenshot.ScreenshotService --socket 9999");
        System.out.println();
        System.out.println("Socket Commands:");
        System.out.println("  screenshot <path>         Take screenshot and save to path");
        System.out.println("  status                    Get service status");
        System.out.println("  help                      Show available commands");
    }

    private static void startSocketServer(ScreenshotService screenshotService) {
        try {
            // 创建命令处理器
            CommandProcessor commandProcessor = new CommandProcessor();
            // 注册截图命令
            commandProcessor.registerCommand(new ScreenshotCommand(screenshotService));
            // 创建并启动Socket服务器
            SocketServer socketServer = new SocketServer(socketPort, commandProcessor);
            socketServer.start();
            Ln.i("Socket server started on port " + socketPort);
        } catch (Exception e) {
            Ln.e("Failed to start socket server", e);
            throw new RuntimeException("Socket server start failed", e);
        }
    }


}
