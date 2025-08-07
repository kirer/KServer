package com.github.kirer.server;

import android.os.Looper;

import com.genymobile.scrcpy.util.Ln;

import java.io.File;
import java.util.Objects;

/**
 * 主入口类 - K Server 屏幕截图服务启动器
 * <p>
 * 功能：
 * 1. 解析命令行参数
 * 2. 初始化屏幕截图服务
 * 3. 启动持续的屏幕捕获循环
 */
public class Launcher {
    private static final String VERSION = "1.0.0";
    private static final String DEFAULT_LIB_NAME = "/libashmem.so";

    private static String libPath = new File(Objects.requireNonNull(System.getProperty("java.class.path"))).getParent();
    private static ScreenCaptureService screenCaptureService;

    /**
     * 程序主入口点
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        try {
            Ln.i("[启动] K Service v" + VERSION + " 正在启动...");
            // 设置未捕获异常处理器
            Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> {
                Ln.e("[异常] 线程 " + thread.getName() + " 发生未捕获异常", exception);
                cleanup();
                System.exit(1);
            });
            // 解析命令行参数
            parseArguments(args);
            // 准备主循环器
            Ln.d("[初始化] 准备主事件循环器");
            Looper.prepareMainLooper();
            // 创建并启动屏幕截图服务
            Ln.i("[初始化] 创建屏幕捕获服务，库路径: " + libPath);
            screenCaptureService = new ScreenCaptureService(libPath);
            screenCaptureService.start();
            // 运行主循环
            Ln.i("[运行] 进入主事件循环");
            Looper.loop();
        } catch (Exception e) {
            Ln.e("[错误] 启动过程中发生致命错误", e);
            cleanup();
            System.exit(1);
        }
    }

    /**
     * 清理资源
     */
    private static void cleanup() {
        Ln.i("[清理] 开始清理系统资源");
        if (screenCaptureService != null) {
            screenCaptureService.stop();
        }

        // 清理共享内存资源
        try {
            Ashmem.cleanup();
            Ln.d("[清理] 共享内存资源清理完成");
        } catch (Exception e) {
            Ln.w("[清理] 清理共享内存时出错", e);
        }

        Ln.i("[清理] 资源清理完成");
    }

    /**
     * 解析命令行参数
     *
     * @param args 命令行参数数组
     */
    private static void parseArguments(String[] args) {
        Ln.d("[参数] 解析启动参数，共 " + args.length + " 个:");
        for (int j = 0; j < args.length; j++) {
            Ln.d("[参数]   args[" + j + "] = '" + args[j] + "'");
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            try {
                switch (arg) {
                    case "--version":
                    case "-v":
                        Ln.i("[版本] K Service v" + VERSION);
                        System.exit(0);
                        break;
                    case "--debug":
                        Ln.initLogLevel(Ln.Level.DEBUG);
                        Ln.d("[调试] 调试模式已启用");
                        break;
                    case "--libPath":
                        if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                            libPath = args[++i];
                            Ln.d("[配置] 设置库路径: " + libPath);
                        } else {
                            Ln.w("[警告] --libPath 参数缺失或无效");
                        }
                        break;
                    // TCP端口参数已移除，使用直接文件访问

                    default:
                        if (arg.startsWith("--libPath=")) {
                            libPath = arg.substring("--libPath=".length());
                            Ln.d("[配置] 从键值对格式设置库路径: " + libPath);
                            // TCP端口参数已移除
                        } else if (arg.startsWith("-")) {
                            Ln.w("[警告] 未知参数: " + arg);
                        } else {
                            Ln.d("[参数] 忽略非选项参数: " + arg);
                        }
                        break;
                }
            } catch (Exception e) {
                Ln.e("[错误] 处理参数时出错: " + arg, e);
                System.exit(1);
            }
        }
        // 打印最终配置
        Ln.d("[配置] 最终配置 - 库路径: " + libPath);
    }

}
