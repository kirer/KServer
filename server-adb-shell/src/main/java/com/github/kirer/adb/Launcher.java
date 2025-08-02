package com.github.kirer.adb;

import android.os.Looper;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.net.CommandProcessor;
import com.github.kirer.adb.net.CommandResult;
import com.github.kirer.adb.net.SocketServer;
import com.github.kirer.adb.screenshot.ScreenshotConfig;
import com.github.kirer.adb.screenshot.ScreenshotService;
import com.github.kirer.adb.commands.ScreenshotCommand;

public class Launcher {
    private static final String VERSION = "1.0.0";
    private static int socketPort = 8888;
    private static ScreenshotService screenshotService;
    private static boolean socketMode = false;
    private static String directCommand = null;

    /**
     * 主入口点，通过app_process调用
     */
    public static void main(String[] args) {
        Ln.i("K Shell ADB Service v" + VERSION + " starting...");
        // 设置未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
            Ln.e("Uncaught exception in thread " + thread.getName(), exception);
            System.exit(1);
        });
        try {
            // 解析命令行参数
            ScreenshotConfig config = parseArguments(args);
            if (socketMode) {
                // Socket服务模式：启动持续服务
                // 准备主循环器
                prepareMainLooper();
                // 创建并初始化服务
                screenshotService = new ScreenshotService(config);
                // 启动服务
                screenshotService.start();
                startSocketServer(screenshotService);
                // 运行主循环
                Looper.loop();
            } else {
                // 直接模式：执行命令并退出（默认模式）
                // 在直接模式下，如果是无参数的screenshot命令，禁用日志输出
                if (directCommand != null && directCommand.trim().equals("screenshot")) {
                    Ln.initLogLevel(Ln.Level.ERROR); // 只显示错误日志
                }
                executeDirectCommand(config, directCommand);
            }
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
                    case "--socket":
                    case "-s":
                        socketMode = true;
                        break;
                    default:
                        if (arg.startsWith("-")) {
                            Ln.w("Unknown argument: " + arg);
                        } else {
                            // 非选项参数作为直接命令
                            if (directCommand == null) {
                                directCommand = arg;
                            } else {
                                directCommand += " " + arg;
                            }
                        }
                        break;
                }
            } catch (NumberFormatException e) {
                Ln.e("Invalid number format for argument " + arg);
                System.exit(1);
            }
        }

        Ln.i("Using config: " + config);
        if (socketMode) {
            Ln.i("Socket mode enabled on port: " + socketPort);
        } else {
            Ln.i("Direct mode enabled with command: " + (directCommand != null ? directCommand : "none"));
        }

        return config;
    }

    /**
     * 打印使用说明
     */
    private static void printUsage() {
        System.out.println("K Shell ADB Service v" + VERSION);
        System.out.println("Usage: CLASSPATH=k-server.dex app_process /system/bin com.github.kirer.adb.Launcher [options] [command]");
        System.out.println();
        System.out.println("Modes:");
        System.out.println("  Direct Mode (default)     Execute command and exit");
        System.out.println("  Socket Mode (-s/--socket) Start persistent socket server");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -d, --display-id <id>     Display ID to capture (default: 0)");
        System.out.println("  -m, --max-images <num>    Maximum images in buffer (default: 2)");
        System.out.println("  -s, --socket              Enable socket server mode");
        System.out.println("  -p, --port <port>         Socket server port (default: 8888)");
        System.out.println("  --no-auto-rotate          Disable automatic rotation handling");
        System.out.println("  --debug                   Enable debug logging");
        System.out.println("  -h, --help                Show this help message");
        System.out.println("  -v, --version             Show version information");
        System.out.println();
        System.out.println("Direct Mode Examples:");
        System.out.println("  # Take screenshot directly");
        System.out.println("  adb shell CLASSPATH=/data/local/tmp/k-server.dex app_process /system/bin com.github.kirer.adb.Launcher screenshot /sdcard/test.png");
        System.out.println();
        System.out.println("  # Get status");
        System.out.println("  adb shell CLASSPATH=/data/local/tmp/k-server.dex app_process /system/bin com.github.kirer.adb.Launcher status");
        System.out.println();
        System.out.println("Socket Mode Examples:");
        System.out.println("  # Start socket server on default port 8888");
        System.out.println("  adb shell CLASSPATH=/data/local/tmp/k-server.dex app_process /system/bin com.github.kirer.adb.Launcher --socket");
        System.out.println();
        System.out.println("  # Start socket server on custom port");
        System.out.println("  adb shell CLASSPATH=/data/local/tmp/k-server.dex app_process /system/bin com.github.kirer.adb.Launcher --socket --port 9999");
        System.out.println();
        System.out.println("Available Commands:");
        System.out.println("  screenshot <path>         Take screenshot and save to path");
        System.out.println("  status                    Get service status");
        System.out.println("  help                      Show available commands");
    }

    private static void startSocketServer(ScreenshotService screenshotService) {
        try {
            // 创建命令处理器
            CommandProcessor commandProcessor = new CommandProcessor();
            // 注册命令
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

    /**
     * 执行直接命令模式
     */
    private static void executeDirectCommand(ScreenshotConfig config, String command) {
        try {
            // 创建并初始化服务
            screenshotService = new ScreenshotService(config);
            screenshotService.start();

            // 创建命令处理器
            CommandProcessor commandProcessor = new CommandProcessor();
            commandProcessor.registerCommand(new ScreenshotCommand(screenshotService));

            // 处理命令
            CommandResult result;
            if (command == null || command.trim().isEmpty()) {
                // 如果没有提供命令，显示状态信息
                result = CommandResult.success("Service initialized successfully. Available commands: screenshot [path], status, help");
            } else {
                // 执行指定的命令
                result = commandProcessor.execute(command.trim());
            }

            // 处理结果
            if (result.getType() == CommandResult.Type.BYTES) {
                // 如果是字节数组结果，直接输出字节数组
                byte[] imageBytes = (byte[]) result.getData();
                if (imageBytes != null) {
                    try {
                        // 直接将字节数组写入stdout，不输出任何文本
                        System.out.write(imageBytes);
                        System.out.flush();

                        // 不输出任何文本信息，直接退出
                        System.exit(0);

                    } catch (Exception e) {
                        System.exit(1);
                    }
                } else {
                    System.exit(1);
                }

            } else if (result.getType() == CommandResult.Type.BOOLEAN) {
                // 如果是布尔结果，输出true/false
                boolean success = (Boolean) result.getData();
                System.out.println(success ? "OK " + result.getMessage() : "ERROR " + result.getMessage());
                System.exit(success ? 0 : 1);

            } else {
                // 普通字符串结果
                System.out.println(result.toStringResponse());
            }

            // 根据结果设置退出码
            if (!result.isSuccess()) {
                System.exit(1);
            } else {
                System.exit(0);
            }

        } catch (Exception e) {
            Ln.e("Error in direct command execution", e);
            System.err.println("ERROR " + e.getMessage());
            System.exit(1);
        }
    }

}
