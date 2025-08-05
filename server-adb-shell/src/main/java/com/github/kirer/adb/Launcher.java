package com.github.kirer.adb;

import android.os.Looper;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.commands.ScreenshotCommand;
import com.github.kirer.adb.commands.CommandProcessor;
import com.github.kirer.adb.net.SocketServer;
import com.github.kirer.adb.screenshot.ScreenshotService;

public class Launcher {
    private static final String VERSION = "1.0.0";
    private static int socketPort = 8888;
    private static ScreenshotService screenshotService;

    /**
     * 主入口点，通过app_process调用
     */
    public static void main(String[] args) {
        Ln.i("KServer v" + VERSION + " starting...");
        // 设置未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
            Ln.e("Uncaught exception in thread " + thread.getName(), exception);
            System.exit(1);
        });
        try {
            // 解析命令行参数
            parseArguments(args);
            // Socket服务模式：启动持续服务
            // 准备主循环器
            prepareMainLooper();
            // 创建并初始化服务
            screenshotService = new ScreenshotService();
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
    private static void parseArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            try {
                switch (arg) {
                    case "--version":
                    case "-v":
                        System.out.println("K Service v" + VERSION);
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
                    default:
                        break;
                }
            } catch (NumberFormatException e) {
                Ln.e("Invalid number format for argument:" + arg);
                System.exit(1);
            }
        }
    }

    private static void startSocketServer(ScreenshotService screenshotService) {
        try {
            CommandProcessor commandProcessor = new CommandProcessor();
            commandProcessor.registerCommand(new ScreenshotCommand(screenshotService));
            SocketServer socketServer = new SocketServer(socketPort, commandProcessor);
            socketServer.start();
            Ln.i("KServer socket started on port:" + socketPort);
        } catch (Exception e) {
            Ln.e("Failed to start socket:", e);
            throw new RuntimeException("KServer socket start failed:", e);
        }
    }
}
