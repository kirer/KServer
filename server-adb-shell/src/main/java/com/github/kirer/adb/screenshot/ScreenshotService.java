package com.github.kirer.adb.screenshot;

import android.graphics.Bitmap;

import com.genymobile.scrcpy.util.Ln;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

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

    /**
     * 获取屏幕截图的公共接口
     */
    public Bitmap takeScreenshot() {
        if (!running || !initialized) {
            Ln.w("Service not running or not initialized");
            return null;
        }
        if (captureManager == null) {
            Ln.e("CaptureManager not available");
            return null;
        }
        try {
            long startTime = System.currentTimeMillis();
            // 使用带重试机制的截图方法
            Bitmap bitmap = captureManager.captureScreen();
            long duration = System.currentTimeMillis() - startTime;
            if (bitmap != null) {
                Ln.d("Screenshot taken in " + duration + "ms, size: " + bitmap.getWidth() + "x" + bitmap.getHeight());
            } else {
                Ln.w("Screenshot failed after " + duration + "ms");
            }
            return bitmap;
        } catch (Exception e) {
            Ln.e("Error taking screenshot", e);
            return null;
        }
    }
    
    /**
     * 保存截图到文件（用于测试和调试）
     */
    public void saveScreenshot(String filePath) {
        Bitmap bitmap = takeScreenshot();
        if (bitmap == null) {
            Ln.e("No bitmap to save");
            return;
        }
        try {
            File file = new File(filePath);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Ln.i("Screenshot saved to: " + filePath);
        } catch (IOException e) {
            Ln.e("Failed to save screenshot to " + filePath, e);
        } finally {
            // 回收Bitmap
            if (!bitmap.isRecycled()) {
                bitmap.recycle();
            }
        }
    }

    /**
     * 停止服务
     */
    public void stop() {
        Ln.i("Stopping ScreenshotService...");
        running = false;
        if (captureManager != null) {
            captureManager.stopCapture();
        }
        Ln.i("ScreenshotService stopped");
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        Ln.i("Cleaning up service");
        stop();
        if (captureManager != null) {
            captureManager.cleanup();
        }
        initialized = false;
        Ln.i("Service cleanup completed");
    }
    
    /**
     * 检查服务是否正在运行
     */
    public boolean isRunning() {
        return running && initialized;
    }
}