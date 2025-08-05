package com.github.kirer.server;

import android.annotation.SuppressLint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.view.Surface;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.device.DisplayInfo;
import com.genymobile.scrcpy.device.Size;
import com.genymobile.scrcpy.util.Ln;
import com.genymobile.scrcpy.wrappers.DisplayManager;
import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.wrappers.SurfaceControl;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

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

    // 配置常量
    private static final int BUFFER_PADDING_BYTES = 40; // 缓冲区额外填充字节
    private static final int BYTES_PER_PIXEL = 4; // RGBA_8888 每像素4字节
    private static final int HEADER_SIZE_BYTES = 8; // 图像数据头部大小
    private static final long CAPTURE_INTERVAL_MS = 20L; // 截图间隔毫秒
    private static final int MAX_IMAGE_BUFFERS = 3; // ImageReader最大缓冲区数量

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

    /**
     * 屏幕截图服务类
     * 负责管理屏幕捕获的整个生命周期
     */
    public static class ScreenCaptureService {
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
        private byte[] reusableByteArray;

        // 控制标志
        private final AtomicBoolean isRunning = new AtomicBoolean(false);
        private final AtomicBoolean shouldStop = new AtomicBoolean(false);

        // 统计信息
        private volatile long lastFrameTime = 0;

        /**
         * 构造函数 - 初始化屏幕截图服务
         *
         * @param libPath native库路径
         * @throws Exception 初始化失败时抛出异常
         */
        @SuppressLint("UnsafeDynamicallyLoadedCode")
        public ScreenCaptureService(String libPath) throws Exception {
            Ln.d("[初始化] 正在初始化屏幕捕获服务...");

            // 加载native库
            try {
                String fullLibPath = libPath + DEFAULT_LIB_NAME;
                Ln.d("[库加载] 尝试加载本地库: " + fullLibPath);
                System.load(fullLibPath);
                Ln.i("[库加载] 本地库加载成功: " + fullLibPath);
            } catch (UnsatisfiedLinkError e) {
                throw new Exception("[库加载] 本地库加载失败: " + e.getMessage(), e);
            }

            // 初始化显示管理器
            Ln.d("[显示] 初始化显示管理器");
            displayManager = ServiceManager.getDisplayManager();
            if (displayManager == null) {
                throw new Exception("[显示] 显示管理器初始化失败");
            }
            Ln.d("[显示] 显示管理器初始化成功");

            // 获取显示器信息
            Ln.d("[显示] 获取显示器信息，显示器ID: " + DISPLAY_ID);
            displayInfo = displayManager.getDisplayInfo(DISPLAY_ID);
            if (displayInfo == null) {
                throw new Exception("[显示] 无法获取显示器 " + DISPLAY_ID + " 的信息");
            }

            screenSize = displayInfo.getSize();
            Ln.i("[显示] 屏幕尺寸: " + screenSize.getWidth() + "x" + screenSize.getHeight());

            // 创建共享内存文件
            int memorySize = calculateMemorySize(screenSize);
            Ln.i("[内存] 创建共享内存文件，大小: " + memorySize + " 字节");
            int result = Ashmem.create(memorySize);
            if (result == -1) {
                throw new Exception("[内存] 共享内存文件创建失败，大小: " + memorySize);
            }
            Ln.i("[内存] 共享内存文件创建成功");

            Ln.d("[初始化] 屏幕捕获服务初始化完成");
        }

        /**
         * 计算所需的共享内存大小
         *
         * @param size 屏幕尺寸
         * @return 内存大小（字节）
         */
        private int calculateMemorySize(Size size) {
            // 计算：(宽度 + 填充) * (高度 + 填充) * 每像素字节数
            int width = size.getWidth() + BUFFER_PADDING_BYTES;
            int height = size.getHeight() + BUFFER_PADDING_BYTES;
            return width * height * BYTES_PER_PIXEL;
        }

        /**
         * 启动屏幕截图服务
         * 首先尝试使用VirtualDisplay API，失败时降级到SurfaceControl API
         */
        public void start() throws Exception {
            if (isRunning.get()) {
                Ln.w("[服务] 屏幕捕获服务已在运行中");
                return;
            }

            Ln.i("[服务] 正在启动屏幕捕获服务...");
            shouldStop.set(false);

            // 初始化缓冲区
            initializeBuffers();

            try {
                // 优先尝试使用VirtualDisplay API（更稳定）
                Ln.d("[API] 尝试使用 VirtualDisplay API");
                startWithVirtualDisplay();
                Ln.i("[API] VirtualDisplay API 启动成功");
            } catch (Exception e) {
                Ln.d("[API] VirtualDisplay API 失败，切换到 SurfaceControl API");
                try {
                    startWithSurfaceControl();
                    Ln.i("[API] SurfaceControl API 启动成功");
                } catch (Exception fallbackException) {
                    Ln.e("[API] VirtualDisplay 和 SurfaceControl API 都失败了", fallbackException);
                    stop();
                    throw new Exception("[服务] 屏幕捕获服务启动失败", fallbackException);
                }
            }

            isRunning.set(true);
            Ln.i("[服务] 屏幕捕获服务启动完成");
        }

        /**
         * 初始化可重用的缓冲区以避免频繁内存分配
         */
        private void initializeBuffers() {
            int maxBufferSize = calculateMemorySize(screenSize) + HEADER_SIZE_BYTES;
            reusableByteArray = new byte[maxBufferSize];
            Ln.d("[缓冲区] 初始化可重用缓冲区，大小: " + maxBufferSize + " 字节");
        }

        /**
         * 使用VirtualDisplay API启动截图服务
         */
        private void startWithVirtualDisplay() throws Exception {
            Ln.d("[VirtualDisplay] 尝试使用 VirtualDisplay API 启动");

            // 创建ImageReader
            Ln.d("[VirtualDisplay] 创建 ImageReader");
            imageReader = createImageReader(screenSize.getWidth(), screenSize.getHeight(), PixelFormat.RGBA_8888, MAX_IMAGE_BUFFERS);
            if (imageReader == null) {
                throw new Exception("[VirtualDisplay] ImageReader 创建失败");
            }

            // 创建后台线程处理图像回调
            Ln.d("[VirtualDisplay] 创建后台处理线程");
            backgroundThread = new HandlerThread("ScreenCaptureHandler");
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());

            // 设置图像可用监听器
            Ln.d("[VirtualDisplay] 设置图像回调监听器");
            imageReader.setOnImageAvailableListener(reader -> {
                if (!shouldStop.get()) {
                    processImage();
                }
            }, backgroundHandler);

            // 创建VirtualDisplay
            Ln.d("[VirtualDisplay] 创建虚拟显示器");
            virtualDisplay = displayManager.createVirtualDisplay("k-server-capture", screenSize.getWidth(), screenSize.getHeight(), DISPLAY_ID, imageReader.getSurface());

            if (virtualDisplay == null) {
                throw new Exception("[VirtualDisplay] 虚拟显示器创建失败");
            }

            Ln.d("[VirtualDisplay] 虚拟显示器创建成功");
        }

        /**
         * 使用SurfaceControl API启动截图服务（降级方案）
         * 这种方式会持续循环捕获屏幕
         */
        private void startWithSurfaceControl() {
            Ln.d("[SurfaceControl] 使用 SurfaceControl API 启动（降级模式）");

            // 创建后台线程用于持续截图
            Ln.d("[SurfaceControl] 创建后台捕获线程");
            backgroundThread = new HandlerThread("SurfaceControlCaptureThread");
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());

            // 在后台线程中启动持续截图循环
            Ln.d("[SurfaceControl] 启动持续捕获循环");
            backgroundHandler.post(this::runSurfaceControlCaptureLoop);
        }

        /**
         * SurfaceControl持续截图循环
         * 这是一个无限循环，会持续捕获屏幕直到停止信号
         */
        private void runSurfaceControlCaptureLoop() {
            Ln.d("[SurfaceControl] 开始 SurfaceControl 捕获循环");
            while (!shouldStop.get()) {
                try {
                    // 清理之前的资源
                    cleanupSurfaceControlResources();
                    // 创建新的ImageReader
                    imageReader = createImageReader(screenSize.getWidth(), screenSize.getHeight(), PixelFormat.RGBA_8888, MAX_IMAGE_BUFFERS);
                    if (imageReader == null) {
                        Ln.e("[SurfaceControl] SurfaceControl 模式下 ImageReader 创建失败");
                        break;
                    }
                    // 创建虚拟显示
                    virtualDisplayToken = createVirtualDisplay();
                    if (virtualDisplayToken == null) {
                        Ln.e("[SurfaceControl] 虚拟显示器创建失败");
                        break;
                    }
                    // 设置显示表面
                    Rect screenRect = screenSize.toRect();
                    setDisplaySurface(virtualDisplayToken, imageReader.getSurface(), screenRect, screenRect, displayInfo.getLayerStack());
                    // 处理当前帧
                    while (true) {
                        if (processImage()) {
                            break;
                        }
                    }
                    // 短暂休眠避免过度消耗CPU
                    Thread.sleep(CAPTURE_INTERVAL_MS);

                } catch (InterruptedException e) {
                    Ln.d("[SurfaceControl] SurfaceControl 捕获循环被中断");
                    break;
                } catch (Exception e) {
                    Ln.w("[SurfaceControl] SurfaceControl 捕获循环出错", e);
                }
            }
            // 清理资源
            cleanupSurfaceControlResources();
            Ln.d("[SurfaceControl] SurfaceControl 捕获循环结束");
        }

        /**
         * 清理SurfaceControl相关资源
         */
        private void cleanupSurfaceControlResources() {
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
                Ln.d("[服务] 屏幕捕获服务未在运行");
                return;
            }

            Ln.i("[服务] 正在停止屏幕捕获服务...");
            shouldStop.set(true);

            // 清理VirtualDisplay资源
            if (virtualDisplay != null) {
                try {
                    virtualDisplay.release();
                    Ln.d("[清理] VirtualDisplay 已释放");
                } catch (Exception e) {
                    Ln.w("[清理] 释放 VirtualDisplay 时出错", e);
                }
                virtualDisplay = null;
            }

            // 清理SurfaceControl资源
            cleanupSurfaceControlResources();

            // 停止后台线程
            if (backgroundThread != null) {
                try {
                    Ln.d("[线程] 正在停止后台线程");
                    backgroundThread.quitSafely();
                    backgroundThread.join(1000); // 等待最多1秒
                    Ln.d("[线程] 后台线程已停止");
                } catch (InterruptedException e) {
                    Ln.w("[线程] 等待后台线程停止时被中断");
                }
                backgroundThread = null;
                backgroundHandler = null;
            }

            // TCP服务器已移除，使用直接文件访问

            isRunning.set(false);
            Ln.i("[服务] 屏幕捕获服务已停止");
        }

        /**
         * 处理ImageReader
         */
        private boolean processImage() {
            long startTime = System.currentTimeMillis();
            synchronized (imageLock) {
                Image image = null;
                try {
                    // 获取最新图像
                    image = imageReader.acquireLatestImage();
                    if (image == null) {
                        return false;
                    }
                    // 处理图像数据
                    processImageData(image);
                    lastFrameTime = System.currentTimeMillis();
                    long processingTime = lastFrameTime - startTime;
                    Ln.d("[帧处理] 耗时 " + processingTime + "ms");
                    return true;
                } catch (Exception e) {
                    Ln.w("[帧处理] 回调中处理图像时出错", e);
                    return false;
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

                // 计算实际缓冲区尺寸
                int bufferWidth = imageWidth + rowPadding / pixelStride;
                int bufferHeight = imageHeight;
                int dataSize = buffer.remaining();
                // 检查缓冲区大小
                int requiredSize = dataSize + HEADER_SIZE_BYTES;
                if (reusableByteArray.length < requiredSize) {
                    Ln.w("[缓冲区] 可重用缓冲区太小，重新分配: " + requiredSize + " 字节");
                    reusableByteArray = new byte[requiredSize];
                }
                // 写入头部信息（宽度和高度，小端格式）
                ByteBuffer headerBuffer = ByteBuffer.wrap(reusableByteArray, 0, HEADER_SIZE_BYTES);
                headerBuffer.order(ByteOrder.LITTLE_ENDIAN);
                headerBuffer.putInt(bufferWidth);
                headerBuffer.putInt(bufferHeight);
                // 写入像素数据
                buffer.get(reusableByteArray, HEADER_SIZE_BYTES, dataSize);
                // 写入共享内存
                int result = Ashmem.write(reusableByteArray);
                if (result != 0) {
                    Ln.e("[内存写入] 写入共享内存失败，错误代码: " + result);
                } else {
                    Ln.d("[内存写入] 成功写入到共享内存 (" + imageWidth + "x" + imageHeight + ")，数据大小: " + dataSize + " 字节");
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
    }
}
