package com.github.kirer.adb.screenshot;

import com.genymobile.scrcpy.util.Ln;

/**
 * 屏幕截图服务主类
 * 通过adb shell app_process启动，提供持续的屏幕截图服务
 */
public class ScreenshotService {

    private ScreenCaptureManager captureManager;
    private volatile boolean running = false;
    private volatile boolean initialized = false;

    public ScreenshotService(ScreenshotConfig config){
        Ln.i("ScreenshotService Initializing...");
        if (initialized) {
            Ln.w("ScreenshotService already initialized");
            return;
        }
        if (config == null) {
            throw new IllegalArgumentException("ScreenshotService Config cannot be null");
        }
        try {
            // 创建屏幕捕获管理器
            captureManager = new ScreenCaptureManager(config);
            // 初始化屏幕捕获管理器
            captureManager.initialize();
            initialized = true;
            Ln.i("ScreenshotService initialized successfully");
            Ln.i("Display: " + captureManager.getDisplayInfo().getSize().getWidth() + "x" + captureManager.getDisplayInfo().getSize().getHeight());
        } catch (Exception e) {
            Ln.e("Failed to initialize ScreenshotService", e);
            cleanup();
            throw new RuntimeException("Service initialization failed", e);
        }
    }

    /**
     * 启动服务
     */
    public void start() {
        if (!initialized) {
            throw new IllegalStateException("Service not initialized");
        }
        Ln.i("Starting ScreenshotService...");
        try {
            // 启动屏幕捕获
            captureManager.startCapture();
            running = true;
            Ln.i("ScreenshotService started successfully");
        } catch (Exception e) {
            Ln.e("Failed to start ScreenshotService", e);
            throw new RuntimeException("Service start failed", e);
        }
    }

    // 删除了getLatestImageResult方法，简化版不需要

    /**
     * 获取处理结果（字节数组格式）- 兼容性方法
     */
    public byte[] getLatestImageBytes() {
        return captureManager.getLatestImageBytes();
    }

    /**
     * 停止服务
     */
    public void stop() {
        running = false;
        if (captureManager != null) {
            captureManager.stopCapture();
        }
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        stop();
        if (captureManager != null) {
            captureManager.cleanup();
        }
        initialized = false;
    }
    
    /**
     * 检查服务是否正在运行
     */
    public boolean isRunning() {
        return running && initialized;
    }
}