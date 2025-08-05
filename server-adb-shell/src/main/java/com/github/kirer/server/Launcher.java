package com.github.kirer.server;

import android.os.Looper;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.server.commands.ScreenshotCommand;
import com.github.kirer.server.commands.CommandProcessor;
import com.github.kirer.server.net.SocketServer;
import com.github.kirer.server.screenshot.ScreenshotService;

import java.io.File;

public class Launcher {
    private static final String VERSION = "1.0.0";
    private static int socketPort = 8888;
    private static ScreenshotService screenshotService;

    private static String libPath = new File(System.getProperty("java.class.path")).getParent();

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
            initAshmen();
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

    private static void initAshmen(){
        try {
            Ln.d("Initializing Ashmem...");
            try {
                System.load(libPath + "/libashmem.so");
                Ln.d("Successfully loaded libashmem using System.loadLibrary");
            } catch (UnsatisfiedLinkError e) {
                Ln.e("Failed to load libashmem from library path: " + e.getMessage());
            }
            Ashmem ashmem = new Ashmem();
            // 尝试初始化共享内存
            int result = ashmem.init(1000, 1000 * 1000);
            if (result != 0) {
                Ln.e("Shared memory init failed with code: " + result);
                // 不要直接退出，让程序继续运行，但记录错误
                Ln.w("Continuing without shared memory support");
            } else {
                Ln.d("Shared memory initialized successfully");
            }
        } catch (Exception e) {
            Ln.e("Failed to initialize Ashmem", e);
            Ln.w("Continuing without shared memory support");
            // 不要直接退出，让程序继续运行
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
        // 添加调试信息，打印所有接收到的参数
        Ln.d("Received " + args.length + " arguments:");
        for (int j = 0; j < args.length; j++) {
            Ln.d("  args[" + j + "] = '" + args[j] + "'");
        }

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
                            Ln.d("Set socketPort: " + socketPort);
                        } else {
                            Ln.w("--port parameter missing or invalid");
                        }
                        break;
                    case "--libPath":
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            libPath = args[++i];
                            Ln.d("Set libPath: " + libPath);
                        } else {
                            Ln.w("--libPath parameter missing or invalid");
                        }
                        break;
                    default:
                        Ln.d("Unknown argument: " + arg);
                        break;
                }
            } catch (NumberFormatException e) {
                Ln.e("Invalid number format for argument:" + arg);
                System.exit(1);
            }
        }

        // 打印最终的配置
        Ln.d("Final configuration:");
        Ln.d("  socketPort: " + socketPort);
        Ln.d("  libPath: " + libPath);
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
