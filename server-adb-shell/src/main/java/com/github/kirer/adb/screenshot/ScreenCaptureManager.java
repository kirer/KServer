package com.github.kirer.adb.screenshot;

import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.os.Build;
import android.os.IBinder;
import android.view.Surface;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.device.DisplayInfo;
import com.genymobile.scrcpy.device.Size;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.wrappers.DisplayManager;
import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.wrappers.SurfaceControl;

/**
 * 屏幕捕获管理器，负责管理VirtualDisplay和ImageReader的集成
 */
public class ScreenCaptureManager {
    private final ScreenshotConfig config;
    private final int displayId;
    private DisplayInfo displayInfo;
    private ImageReaderHandler imageReaderHandler;
    private DisplayManager displayManager;
    // 主方案
    private VirtualDisplay virtualDisplay;
    // 状态标志
    private boolean initialized = false;
    private boolean capturing = false;

    /**
     * 创建屏幕捕获管理器
     *
     * @param config 截图配置
     */
    public ScreenCaptureManager(ScreenshotConfig config) {
        this.config = config;
        this.displayId = config.getDisplayId();
        Ln.i("ScreenCaptureManager created for display " + displayId);
    }

    /**
     * 初始化屏幕捕获组件
     *
     * @throws Exception 初始化失败时抛出异常
     */
    public void initialize() throws Exception {
        Ln.i("Initializing ScreenCaptureManager...");
        try {
            // 获取DisplayManager
            displayManager = ServiceManager.getDisplayManager();
            if (displayManager == null) {
                throw new Exception("Failed to get DisplayManager");
            }
            // 获取显示器信息
            displayInfo = displayManager.getDisplayInfo(displayId);
            if (displayInfo == null) {
                throw new Exception("Display " + displayId + " not found");
            }
            Ln.i("Display info: " + displayInfo.getSize().getWidth() + "x" + displayInfo.getSize().getHeight() + ", rotation=" + displayInfo.getRotation() + ", dpi=" + displayInfo.getDpi());
            // 创建ImageReaderHandler
            imageReaderHandler = new ImageReaderHandler(config);
            initialized = true;
            Ln.i("ScreenCaptureManager initialized successfully");
        } catch (Exception e) {
            Ln.e("Failed to initialize ScreenCaptureManager", e);
            cleanup();
            throw e;
        }
    }

    /**
     * 获取显示器信息
     *
     * @return 显示器信息
     */
    public DisplayInfo getDisplayInfo() {
        return displayInfo;
    }

    /**
     * 创建镜像VirtualDisplay
     *
     */
    private void createVirtualDisplay() {
        if (displayInfo == null) {
            throw new IllegalStateException("Display info not available");
        }
        if (imageReaderHandler == null || !imageReaderHandler.isCreated()) {
            throw new IllegalStateException("ImageReader not created");
        }
        int width = displayInfo.getSize().getWidth();
        int height = displayInfo.getSize().getHeight();
        Size inputSize = new Size(width, height);
        Ln.i("Creating VirtualDisplay: " + width + "x" + height + " for display " + displayId);
        try {
            virtualDisplay = displayManager.createVirtualDisplay(
                    "scrcpy",
                    inputSize.getWidth(),
                    inputSize.getHeight(),
                    displayId,  // 镜像现有显示器
                    imageReaderHandler.getSurface()
            );
            Ln.d("Display: using DisplayManager API:" + virtualDisplay.getDisplay().getDisplayId());
        } catch (Exception displayManagerException) {
            Ln.w("DisplayManager createVirtualDisplay failed, trying SurfaceControl API");
            try {
                // 降级方案
                IBinder display = createDisplay();
                Size deviceSize = displayInfo.getSize();
                int layerStack = displayInfo.getLayerStack();
                setDisplaySurface(display, imageReaderHandler.getSurface(), deviceSize.toRect(), inputSize.toRect(), layerStack);
                Ln.d("Display: using SurfaceControl API");
            } catch (Exception surfaceControlException) {
                Ln.e("Could not create display using DisplayManager", displayManagerException);
                Ln.e("Could not create display using SurfaceControl", surfaceControlException);
                throw new AssertionError("Could not create display");
            }
        }
    }

    /**
     * 降级方案 设置显示表面
     *
     * @param display     显示IBinder
     * @param surface     表面
     * @param deviceRect  设备矩形
     * @param displayRect 显示矩形
     * @param layerStack  图层栈
     */
    private static void setDisplaySurface(IBinder display, Surface surface, Rect deviceRect, Rect displayRect, int layerStack) {
        SurfaceControl.openTransaction();
        try {
            SurfaceControl.setDisplaySurface(display, surface);
            SurfaceControl.setDisplayProjection(display, 0, deviceRect, displayRect);
            SurfaceControl.setDisplayLayerStack(display, layerStack);
        } finally {
            SurfaceControl.closeTransaction();
        }
    }

    /**
     * 降级方案 创建显示
     */
    private static IBinder createDisplay() throws Exception {
        // Since Android 12 (preview), secure displays could not be created with shell permissions anymore.
        // On Android 12 preview, SDK_INT is still R (not S), but CODENAME is "S".
        boolean secure = Build.VERSION.SDK_INT < AndroidVersions.API_30_ANDROID_11 || (Build.VERSION.SDK_INT == AndroidVersions.API_30_ANDROID_11
                && !"S".equals(Build.VERSION.CODENAME));
        return SurfaceControl.createDisplay("scrcpy", secure);
    }

    /**
     * 开始屏幕捕获
     *
     */
    public void startCapture() {
        Ln.i("Starting screen capture...");
        if (!initialized) {
            throw new IllegalStateException("ScreenCaptureManager not initialized");
        }
        if (capturing) {
            Ln.w("Screen capture already started");
            return;
        }
        try {
            // 创建ImageReader
            int width = displayInfo.getSize().getWidth();
            int height = displayInfo.getSize().getHeight();
            imageReaderHandler.createImageReader(width, height);
            // 创建VirtualDisplay
            createVirtualDisplay();
            capturing = true;
            Ln.i("Screen capture started successfully");
        } catch (Exception e) {
            // 清理失败的资源
            stopCapture();
            throw e;
        }
    }

    /**
     * 停止屏幕捕获
     */
    public void stopCapture() {
        Ln.i("Stopping screen capture...");
        capturing = false;
        // 释放VirtualDisplay
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Exception e) {
                Ln.w("Failed to release VirtualDisplay", e);
            }
            virtualDisplay = null;
        }
        // 关闭ImageReaderHandler
        if (imageReaderHandler != null) {
            imageReaderHandler.close();
        }
        Ln.i("Screen capture stopped");
    }

    // 删除了getLatestImageResult方法，简化版不需要

    /**
     * 获取最新的图像数据（字节数组格式）- 兼容性方法
     */
    public byte[] getLatestImageBytes(){
        return imageReaderHandler.getLatestImageBytes();
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        Ln.i("Cleaning up ScreenCaptureManager...");
        // 打印性能统计
        if (imageReaderHandler != null) {
            imageReaderHandler.close();
            imageReaderHandler = null;
        }
        stopCapture();
        initialized = false;
        displayInfo = null;
        displayManager = null;
        Ln.i("ScreenCaptureManager cleanup completed");
    }
}