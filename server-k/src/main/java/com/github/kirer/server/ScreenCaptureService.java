package com.github.kirer.server;

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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server-K 屏幕截图服务类 (v2.0.0)
 * 负责管理屏幕捕获的整个生命周期
 * <p>
 * 新架构特点:
 * - 数据层: 将截图数据写入共享内存，实现高性能数据传输
 * - 控制层: 通过Socket通信进行服务控制和状态管理
 * - 分层设计: 数据传输与控制逻辑分离，提升性能和可维护性
 */
public class ScreenCaptureService {

    private static final String TAG = "ScreenCaptureService";

    // 配置常量
    private static final int BUFFER_PADDING_BYTES = 40; // 缓冲区额外填充字节
    private static final int BYTES_PER_PIXEL = 4; // RGBA_8888 每像素4字节
    private static final int HEADER_SIZE_BYTES = 8; // 图像数据头部大小
    private static final long CAPTURE_INTERVAL_MS = 20L; // 截图间隔毫秒
    private static final int MAX_IMAGE_BUFFERS = 3; // ImageReader最大缓冲区数量

    // 同步锁 - 保护图像处理过程
    private final Object imageLock = new Object();

    // 显示相关
    private static final int DISPLAY_ID = 0;
    private final DisplayManager displayManager;
    private final DisplayInfo displayInfo;
    private final Size screenSize;

    // 截图相关
    private IBinder virtualDisplayToken;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    // 缓冲区池 - 避免频繁内存分配
    private byte[] reusableByteArray = new byte[1];

    // 控制标志
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean shouldStop = new AtomicBoolean(false);

    // 统计信息
    private volatile long lastFrameTime = 0;

    /**
     * 构造函数 - 初始化屏幕截图服务
     *
     * @throws Exception 初始化失败时抛出异常
     */
    public ScreenCaptureService() throws Exception {
        Ln.d("[" + TAG + "] 初始化显示管理器");
        displayManager = ServiceManager.getDisplayManager();
        if (displayManager == null) {
            throw new Exception("[" + TAG + "] 显示管理器初始化失败");
        }
        Ln.d("[" + TAG + "] 获取显示器信息，显示器ID: " + DISPLAY_ID);
        displayInfo = displayManager.getDisplayInfo(DISPLAY_ID);
        if (displayInfo == null) {
            throw new Exception("[" + TAG + "] 无法获取显示器 " + DISPLAY_ID + " 的信息");
        }
        screenSize = displayInfo.getSize();
        Ln.i("[" + TAG + "] 屏幕尺寸: " + screenSize.getWidth() + "x" + screenSize.getHeight());
        Ln.d("[" + TAG + "] 屏幕捕获服务初始化完成");
        Ln.d("[" + TAG + "] 初始化共享内存数据层");
        int width = screenSize.getWidth() + BUFFER_PADDING_BYTES;
        int height = screenSize.getHeight() + BUFFER_PADDING_BYTES;
        int memorySize = width * height * BYTES_PER_PIXEL;
        int result = ShareMemory.create(memorySize);
        if (result != 0) {
            throw new RuntimeException("共享内存创建失败，错误码: " + result);
        }
        Ln.i("[" + TAG + "] 数据层：共享内存已就绪, 大小: " + memorySize + " 字节");
        Server.sendMessage(Server.MSG_TYPE_NOTIFY_SHARE_MEMORY_SIZE, ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(memorySize).array());
    }

    /**
     * 启动屏幕截图服务
     * 首先尝试使用VirtualDisplay API，失败时降级到SurfaceControl API
     */
    public void start() throws Exception {
        if (isRunning.get()) {
            Ln.w("[" + TAG + "] 屏幕捕获服务已在运行中");
            return;
        }
        Ln.i("[" + TAG + "] 正在启动屏幕捕获服务...");
        shouldStop.set(false);
        try {
            Ln.d("[" + TAG + "] 尝试使用 VirtualDisplay API");
            startWithVirtualDisplay();
            Ln.i("[" + TAG + "] VirtualDisplay API 启动成功");
        } catch (Exception e) {
            Ln.d("[" + TAG + "] VirtualDisplay API 失败，切换到 SurfaceControl API");
            try {
                startWithSurfaceControl();
                Ln.i("[" + TAG + "] SurfaceControl API 启动成功");
            } catch (Exception fallbackException) {
                Ln.e("[" + TAG + "] VirtualDisplay 和 SurfaceControl API 都失败了", fallbackException);
                stop();
                throw new Exception("[" + TAG + "] 屏幕捕获服务启动失败", fallbackException);
            }
        }
        isRunning.set(true);
        Ln.i("[" + TAG + "] 屏幕捕获服务启动完成");
    }

    /**
     * 使用VirtualDisplay API启动截图服务
     */
    private void startWithVirtualDisplay() throws Exception {
        Ln.d("[" + TAG + "] 尝试使用 VirtualDisplay API 启动");
        // 创建ImageReader
        imageReader = createImageReader(screenSize.getWidth(), screenSize.getHeight(), PixelFormat.RGBA_8888, MAX_IMAGE_BUFFERS);
        if (imageReader == null) {
            throw new Exception("[" + TAG + "] ImageReader 创建失败");
        }
        // 创建后台线程处理图像回调
        Ln.d("[" + TAG + "] 创建后台处理线程");
        backgroundThread = new HandlerThread("ScreenCaptureHandler");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        // 设置图像可用监听器
        Ln.d("[" + TAG + "] 设置图像回调监听器");
        imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                if (!shouldStop.get()) {
                    processImage();
                }
            }
        }, backgroundHandler);
        // 创建VirtualDisplay
        Ln.d("[" + TAG + "] 创建虚拟显示器");
        virtualDisplay = displayManager.createVirtualDisplay("k-server-capture", screenSize.getWidth(), screenSize.getHeight(), DISPLAY_ID, imageReader.getSurface());
        if (virtualDisplay == null) {
            throw new Exception("[" + TAG + "] 虚拟显示器创建失败");
        }
        Ln.d("[" + TAG + "] 虚拟显示器创建成功");
    }

    /**
     * 使用SurfaceControl API启动截图服务（降级方案）
     * 这种方式会持续循环捕获屏幕
     */
    private void startWithSurfaceControl() {
        Ln.d("[" + TAG + "] 使用 SurfaceControl API 启动（降级模式）");
        Ln.d("[" + TAG + "] 创建后台捕获线程");
        backgroundThread = new HandlerThread("SurfaceControlCaptureThread");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
        // 在后台线程中启动持续截图循环
        Ln.d("[" + TAG + "] 启动持续捕获循环");
        backgroundHandler.post(new Runnable() {
            @Override
            public void run() {
                runSurfaceControlCaptureLoop();
            }
        });
    }

    /**
     * SurfaceControl持续截图循环
     * 这是一个无限循环，会持续捕获屏幕直到停止信号
     */
    private void runSurfaceControlCaptureLoop() {
        Ln.d("[SurfaceControl] 开始优化的 SurfaceControl 捕获循环");
        // 在循环外创建一次 ImageReader，复用整个生命周期
        imageReader = createImageReader(screenSize.getWidth(), screenSize.getHeight(), PixelFormat.RGBA_8888, MAX_IMAGE_BUFFERS);
        if (imageReader == null) {
            Ln.e("[SurfaceControl] ImageReader 创建失败");
            return;
        }
        Ln.d("[SurfaceControl] ImageReader 创建成功，开始复用模式");
        while (!shouldStop.get()) {
            try {
                // 只清理虚拟显示，保留 ImageReader
                cleanupVirtualDisplay();
                // 重新创建虚拟显示
                virtualDisplayToken = createVirtualDisplay();
                if (virtualDisplayToken == null) {
                    Ln.e("[SurfaceControl] 虚拟显示器创建失败");
                    break;
                }
                // 重新设置显示表面
                Rect screenRect = screenSize.toRect();
                setDisplaySurface(virtualDisplayToken, imageReader.getSurface(), screenRect, screenRect, displayInfo.getLayerStack());
                Ln.d("[SurfaceControl] 虚拟显示重新设置完成");
                // 处理图像
                processImage();
                Utils.sleep(CAPTURE_INTERVAL_MS);
            } catch (Exception e) {
                Ln.w("[SurfaceControl] SurfaceControl 捕获循环出错", e);
            }
        }
        // 最终清理所有资源
        cleanupSurfaceControlResources();
        Ln.d("[SurfaceControl] SurfaceControl 捕获循环结束");
    }

    /**
     * 只清理虚拟显示，保留 ImageReader
     */
    private void cleanupVirtualDisplay() {
        if (virtualDisplayToken != null) {
            try {
                SurfaceControl.destroyDisplay(virtualDisplayToken);
                Ln.d("[清理] 虚拟显示已销毁");
            } catch (Exception e) {
                Ln.w("[清理] 销毁虚拟显示时出错", e);
            }
            virtualDisplayToken = null;
        }
    }

    /**
     * 清理SurfaceControl相关资源
     */
    private void cleanupSurfaceControlResources() {
        // 先清理虚拟显示
        cleanupVirtualDisplay();
        // 再清理 ImageReader
        if (imageReader != null) {
            try {
                imageReader.close();
                Ln.d("[清理] ImageReader 已关闭");
            } catch (Exception e) {
                Ln.w("[清理] 关闭 ImageReader 时出错", e);
            }
            imageReader = null;
        }
        if (virtualDisplayToken != null) {
            try {
                SurfaceControl.destroyDisplay(virtualDisplayToken);
                Ln.d("[清理] 虚拟显示器已销毁");
            } catch (Exception e) {
                Ln.w("[清理] 销毁虚拟显示器时出错", e);
            }
            virtualDisplayToken = null;
        }
    }

    /**
     * 创建虚拟显示（SurfaceControl方式）
     *
     * @return 显示token，失败时返回null
     */
    private IBinder createVirtualDisplay() {
        try {
            // Android 12+不允许shell权限创建安全显示
            boolean secure = Build.VERSION.SDK_INT < AndroidVersions.API_30_ANDROID_11 || (Build.VERSION.SDK_INT == AndroidVersions.API_30_ANDROID_11 && !"S".equals(Build.VERSION.CODENAME));
            Ln.d("[显示] 创建虚拟显示器，安全模式: " + secure + ", SDK版本: " + Build.VERSION.SDK_INT);
            IBinder token = SurfaceControl.createDisplay("k-server-surface", secure);
            if (token != null) {
                Ln.d("[显示] 虚拟显示器创建成功");
            } else {
                Ln.w("[显示] SurfaceControl.createDisplay 返回 null");
            }
            return token;
        } catch (Exception e) {
            Ln.e("[显示] 虚拟显示器创建失败", e);
            return null;
        }
    }

    /**
     * 设置显示表面属性
     *
     * @param displayToken 显示token
     * @param surface      表面
     * @param deviceRect   设备矩形区域
     * @param displayRect  显示矩形区域
     * @param layerStack   图层栈ID
     */
    private void setDisplaySurface(IBinder displayToken, Surface surface, Rect deviceRect, Rect displayRect, int layerStack) {
        SurfaceControl.openTransaction();
        try {
            SurfaceControl.setDisplaySurface(displayToken, surface);
            SurfaceControl.setDisplayProjection(displayToken, 0, deviceRect, displayRect);
            SurfaceControl.setDisplayLayerStack(displayToken, layerStack);
        } finally {
            SurfaceControl.closeTransaction();
        }
    }

    /**
     * 创建ImageReader实例
     *
     * @param width     图像宽度
     * @param height    图像高度
     * @param format    像素格式
     * @param maxImages 最大缓冲图像数
     * @return ImageReader实例，失败时返回null
     */
    private ImageReader createImageReader(int width, int height, int format, int maxImages) {
        Ln.d("[ImageReader] 创建 ImageReader: " + width + "x" + height + ", 格式=" + format + ", 最大缓冲=" + maxImages);
        try {
            ImageReader reader = ImageReader.newInstance(width, height, format, maxImages);
            Ln.d("[ImageReader] ImageReader 创建成功");
            return reader;
        } catch (Exception e) {
            Ln.e("[ImageReader] ImageReader 创建失败", e);
            return null;
        }
    }

    /**
     * 停止屏幕截图服务
     */
    public void stop() {
        if (!isRunning.get()) {
            Ln.d("[" + TAG + "] 屏幕捕获服务未在运行");
            return;
        }
        Ln.i("[" + TAG + "] 正在停止屏幕捕获服务...");
        shouldStop.set(true);
        // 清理VirtualDisplay资源
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
                Ln.d("[" + TAG + "] VirtualDisplay 已释放");
            } catch (Exception e) {
                Ln.w("[" + TAG + "] 释放 VirtualDisplay 时出错", e);
            }
            virtualDisplay = null;
        }
        // 清理SurfaceControl资源
        cleanupSurfaceControlResources();
        // 停止后台线程
        if (backgroundThread != null) {
            try {
                Ln.d("[" + TAG + "] 正在停止后台线程");
                backgroundThread.quitSafely();
                backgroundThread.join(1000); // 等待最多1秒
                Ln.d("[" + TAG + "] 后台线程已停止");
            } catch (InterruptedException e) {
                Ln.w("[" + TAG + "] 等待后台线程停止时被中断");
            }
            backgroundThread = null;
            backgroundHandler = null;
        }
        // 停止通信通道
        try {
            Ln.d("[" + TAG + "] 正在停止通信通道");
            Server.stop();
            Ln.d("[" + TAG + "] 通信通道已停止");
        } catch (Exception e) {
            Ln.w("[" + TAG + "] 停止通信通道时出错", e);
        }
        isRunning.set(false);
        Ln.i("[" + TAG + "] 屏幕捕获服务已停止");
    }

    /**
     * 处理ImageReader
     */
    private void processImage() {
        long startTime = System.currentTimeMillis();
        synchronized (imageLock) {
            Image image = null;
            try {
                // 获取最新图像
                image = imageReader.acquireLatestImage();
                if (image == null) {
                    return;
                }
                // 处理图像数据
                processImageData(image);
                lastFrameTime = System.currentTimeMillis();
                long processingTime = lastFrameTime - startTime;
                Ln.d("[帧处理] 耗时 " + processingTime + "ms");
            } catch (Exception e) {
                Ln.w("[帧处理] 回调中处理图像时出错", e);
            } finally {
                if (image != null) {
                    try {
                        image.close();
                    } catch (Exception e) {
                        Ln.w("[帧处理] 关闭图像时出错", e);
                    }
                }
            }
        }
    }

    /**
     * 处理图像数据并写入共享内存
     *
     * @param image 要处理的图像
     */
    private void processImageData(Image image) {
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            if (buffer == null) {
                Ln.w("[数据处理] 图像缓冲区为空");
                return;
            }
            // 获取图像参数
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            int pixelStride = plane.getPixelStride(); // RGBA_8888通常是4字节
            int rowStride = plane.getRowStride(); // 每行实际字节数
            int rowPadding = rowStride - pixelStride * imageWidth; // 行填充字节数
            // 计算清理后的数据大小
            int cleanDataSize = imageWidth * imageHeight * 4;
            int totalSize = cleanDataSize + HEADER_SIZE_BYTES;
            if (reusableByteArray.length < totalSize) {
                reusableByteArray = new byte[totalSize];
            }
            // 写入正确的头部信息（实际图像尺寸）
            ByteBuffer headerBuffer = ByteBuffer.wrap(reusableByteArray, 0, HEADER_SIZE_BYTES);
            headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
            headerBuffer.putInt(imageWidth);  // 实际宽度
            headerBuffer.putInt(imageHeight); // 实际高度
            // 去除行填充，只复制实际像素数据
            if (rowPadding == 0) {
                // 没有行填充，直接复制
                buffer.get(reusableByteArray, HEADER_SIZE_BYTES, cleanDataSize);
            } else {
                // 有行填充，逐行复制
                byte[] rowBuffer = new byte[rowStride];
                int destOffset = HEADER_SIZE_BYTES;
                for (int y = 0; y < imageHeight; y++) {
                    buffer.get(rowBuffer, 0, rowStride);
                    System.arraycopy(rowBuffer, 0, reusableByteArray, destOffset, imageWidth * 4);
                    destOffset += imageWidth * 4;
                }
            }
            // 写入共享内存数据层
            try {
                int result = ShareMemory.write(reusableByteArray);
                if (result != 0) {
                    Ln.e("[" + TAG + "] 屏幕截图写入共享内存失败，错误码: " + result);
                    return;
                }
                Ln.d("[" + TAG + "] 屏幕截图已写入共享内存 (" + imageWidth + "x" + imageHeight + ")，数据大小: " + totalSize + " 字节");
                result = Server.sendMessage(Server.MSG_TYPE_NOTIFY_SCREEN_CAPTURE, null);
                if (result == 0) {
                    Ln.d("[" + TAG + "] 成功通知 (" + imageWidth + "x" + imageHeight + ")，数据大小: " + totalSize + " 字节");
                } else {
                    Ln.e("[" + TAG + "] 通知失败，错误码: " + result);
                }
            } catch (Exception e) {
                Ln.e("[" + TAG + "] 写入共享内存时出错", e);
            }
        } catch (Exception e) {
            Ln.e("[数据处理] 处理图像数据时出错", e);
        }
    }

    /**
     * 获取服务运行状态
     *
     * @return true如果服务正在运行
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    public static int getScreenshotMemorySize() {
        DisplayManager displayManager = ServiceManager.getDisplayManager();
        DisplayInfo displayInfo = displayManager.getDisplayInfo(0);
        Size size = displayInfo.getSize();
        int width = size.getWidth() + BUFFER_PADDING_BYTES;
        int height = size.getHeight() + BUFFER_PADDING_BYTES;
        return width * height * BYTES_PER_PIXEL;
    }
}