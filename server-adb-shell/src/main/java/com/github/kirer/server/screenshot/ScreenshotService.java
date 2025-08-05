package com.github.kirer.server.screenshot;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.view.Surface;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.device.DisplayInfo;
import com.genymobile.scrcpy.device.Size;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.wrappers.DisplayManager;
import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.wrappers.SurfaceControl;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 屏幕捕获管理器，负责管理VirtualDisplay和ImageReader的集成
 */
public class ScreenshotService {
    private final int displayId = 0;
    private DisplayInfo displayInfo;
    private DisplayManager displayManager;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private byte[] bitmapBytes;

    private static final Object lock = new Object();

    /**
     * 创建屏幕捕获管理器
     */
    public ScreenshotService() {
        Ln.i("初始化 ScreenshotService...");
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
            Ln.i("初始化成功 ScreenshotService");
        } catch (Exception e) {
            Ln.e("Failed to initialize ScreenshotService", e);
            cleanup();
        }
    }

    /**
     * 创建ImageReader
     *
     * @param width     图像宽度
     * @param height    图像高度
     * @param format    图像格式
     * @param maxImages 最大图像缓冲数量
     */
    public void createImageReader(int width, int height, int format, int maxImages) {
        Ln.d("创建 ImageReader: " + width + "x" + height + ", format=" + format + ", maxImages=" + maxImages);
        try {
            // 创建ImageReader
            imageReader = ImageReader.newInstance(width, height, format, maxImages);
            Ln.i("创建成功 ImageReader");
        } catch (Exception e) {
            Ln.e("Failed to create ImageReader", e);
            throw new RuntimeException("Failed to create ImageReader", e);
        }
    }

    /**
     * 创建镜像VirtualDisplay
     */
    private void createVirtualDisplay() {
        if (displayInfo == null) {
            throw new IllegalStateException("Display info not available");
        }
        if (imageReader == null) {
            throw new IllegalStateException("ImageReader not created");
        }
        int width = displayInfo.getSize().getWidth();
        int height = displayInfo.getSize().getHeight();
        Size inputSize = new Size(width, height);
        Ln.i("创建 VirtualDisplay: " + width + "x" + height + " for display " + displayId);
        try {
            virtualDisplay = displayManager.createVirtualDisplay("k-server", inputSize.getWidth(), inputSize.getHeight(), displayId, imageReader.getSurface());
            if (virtualDisplay != null) {
                // 创建后台线程处理图像回调
                HandlerThread backgroundThread = new HandlerThread("ImageReaderHandler");
                backgroundThread.start();
                Handler backgroundHandler = new Handler(backgroundThread.getLooper());
                // 设置图像可用监听器（超级简化版：直接保存到共享内存）
                imageReader.setOnImageAvailableListener(reader -> {
                    Ln.d("🔥 ImageReader回调被触发！");
                    byte[] bytes = processImage();
                    if (bytes != null) {
                        bitmapBytes = bytes;
                    }
                }, backgroundHandler);
                Ln.d("使用 DisplayManager API:" + virtualDisplay.getDisplay().getDisplayId());
            }
        } catch (Exception displayManagerException) {
            Ln.w("不可用 DisplayManager API");
            createDisplaySurface();
        }
    }

    private void createDisplaySurface(){
        try {
            int width = displayInfo.getSize().getWidth();
            int height = displayInfo.getSize().getHeight();
            Size inputSize = new Size(width, height);
            IBinder display = createDisplay();
            Size deviceSize = displayInfo.getSize();
            int layerStack = displayInfo.getLayerStack();
            setDisplaySurface(display, imageReader.getSurface(), deviceSize.toRect(), inputSize.toRect(), layerStack);
            Ln.d("使用 SurfaceControl API");
        } catch (Exception surfaceControlException) {
            Ln.e("不可用 SurfaceControl API");
            throw new AssertionError("Could not create display");
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
        boolean secure = Build.VERSION.SDK_INT < AndroidVersions.API_30_ANDROID_11 || (Build.VERSION.SDK_INT == AndroidVersions.API_30_ANDROID_11 && !"S".equals(Build.VERSION.CODENAME));
        return SurfaceControl.createDisplay("k-server", secure);
    }

    /**
     * 开始屏幕捕获
     */
    public void start() {
        Ln.i("开始截图服务...");
        try {
            // 创建ImageReader
            int width = displayInfo.getSize().getWidth();
            int height = displayInfo.getSize().getHeight();
            createImageReader(width, height, PixelFormat.RGBA_8888, 3);
            createVirtualDisplay();
            Ln.i("启动成功 截图服务");
        } catch (Exception e) {
            // 清理失败的资源
            stop();
            throw e;
        }
    }

    /**
     * 停止屏幕捕获
     */
    public void stop() {
        Ln.i("Stopping screen capture...");
        // 释放VirtualDisplay
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (Exception e) {
                Ln.w("Failed to release VirtualDisplay", e);
            }
            virtualDisplay = null;
        }
        // 关闭imageReader
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        Ln.i("Screen capture stopped");
    }

    private byte[] processImage() {
        Image image = null;
        try {
            image = imageReader.acquireLatestImage();
            if (image == null) {
                Ln.w("🔥 ImageReader acquireLatestImage 为null！");
                return null;
            }
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            Ln.d("🔥 获取到新图像: " + imageWidth + "x" + imageHeight);
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            if (buffer == null) {
                return null;
            }
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * imageWidth;
            Bitmap bitmap = Bitmap.createBitmap(imageWidth + rowPadding / pixelStride, imageHeight, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            if (rowPadding != 0) {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, imageWidth, imageHeight);
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
            Ln.d("🔥 压缩后图像大小: " + outputStream.size() + " bytes");
            byte[] bytes = outputStream.toByteArray();
            ByteBuffer finalBuffer = ByteBuffer.allocate(bytes.length);
            finalBuffer.order(ByteOrder.LITTLE_ENDIAN);
            finalBuffer.put(bytes);
            bitmap.recycle();
            return finalBuffer.array();
        } catch (Exception e) {
            Ln.w("🔥 ImageReader回调处理异常", e);
            return null;
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    /**
     * 获取最新的图像数据
     */
    public byte[] getBitmapBytes() {
        synchronized (lock) {
            if (virtualDisplay == null) {
                createDisplaySurface();
                byte[] bytes;
                long startTime = System.currentTimeMillis();
                do {
                    if (System.currentTimeMillis() - startTime >= 5000) {
                        return null;
                    }
                    bytes = processImage();
                } while (bytes == null);
                this.bitmapBytes = bytes;
            }
            return this.bitmapBytes;
        }
    }

    /**
     * 清理资源
     */
    public void cleanup() {
        Ln.i("Cleaning up ScreenshotService...");
        stop();
        displayInfo = null;
        displayManager = null;
        Ln.i("ScreenshotService cleanup completed");
    }
}