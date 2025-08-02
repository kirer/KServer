package com.github.kirer.adb.screenshot;

import android.graphics.Bitmap;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.image.OptimizedImageProcessor;

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
     * 直接获取PNG字节数组
     */
    public byte[] takePngBytes() {
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

            // 直接获取PNG字节数组
            byte[] pngBytes = captureManager.capturePngBytes();

            long duration = System.currentTimeMillis() - startTime;
            if (pngBytes != null) {
                Ln.i("PNG bytes captured in " + duration + "ms, size: " + pngBytes.length + " bytes");
            } else {
                Ln.w("PNG bytes capture failed after " + duration + "ms");
            }
            return pngBytes;
        } catch (Exception e) {
            Ln.e("Error taking PNG bytes", e);
            return null;
        }
    }

    /**
     * 获取图像字节数组（当前格式）
     */
    public byte[] takeImageBytes() {
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

            // 获取当前格式的图像字节数组
            byte[] imageBytes = captureManager.captureImageBytes();

            long duration = System.currentTimeMillis() - startTime;
            if (imageBytes != null) {
                Ln.i("Image bytes captured in " + duration + "ms, size: " + imageBytes.length + " bytes");
            } else {
                Ln.w("Image bytes capture failed after " + duration + "ms");
            }
            return imageBytes;
        } catch (Exception e) {
            Ln.e("Error taking image bytes", e);
            return null;
        }
    }

    /**
     * 设置输出格式
     */
    public void setOutputFormat(OptimizedImageProcessor.OutputFormat format, int quality) {
        if (!running || !initialized) {
            Ln.w("Service not running or not initialized");
            return;
        }
        if (captureManager == null) {
            Ln.e("CaptureManager not available");
            return;
        }

        captureManager.setOutputFormat(format, quality);
    }

    /**
     * 获取处理结果（包含元数据）
     */
    public OptimizedImageProcessor.ProcessResult getProcessResult() {
        if (!running || !initialized) {
            Ln.w("Service not running or not initialized");
            return null;
        }
        if (captureManager == null) {
            Ln.e("CaptureManager not available");
            return null;
        }

        return captureManager.captureProcessResult();
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