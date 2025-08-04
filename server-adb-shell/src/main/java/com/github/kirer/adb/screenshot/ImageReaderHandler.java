package com.github.kirer.adb.screenshot;

import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.memory.AshmemManager;

import java.util.concurrent.atomic.AtomicReference;


/**
 * ImageReader处理器，负责管理ImageReader的创建、配置和图像获取
 * 集成Ashmem优化和多格式支持
 */
public class ImageReaderHandler {

    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final ScreenshotConfig config;

    // 优化的图像处理器
    private final ImageProcessor imageProcessor;

    // 共享内存管理器
    private final AshmemManager ashmemManager;
    private volatile boolean hasImageData = false;

    /**
     * 创建ImageReaderHandler实例
     *
     * @param config 截图配置
     */
    public ImageReaderHandler(ScreenshotConfig config) {
        this.config = config;
        // 初始化图像处理器（简化版）
        this.imageProcessor = new ImageProcessor();
        // 初始化共享内存管理器
        this.ashmemManager = new AshmemManager();
        // 初始化共享内存（假设最大4K分辨率，RGBA格式）
        int maxSize = 3840 * 2160 * 4; // 4K RGBA
        int result = ashmemManager.init(0, maxSize); // fd=0是占位符
        if (result != 0) {
            Ln.w("共享内存初始化失败: " + result);
        }
        Ln.d("ImageReaderHandler创建（简化版），共享内存大小: " + maxSize);
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
        Ln.d("ImageReader creating: " + width + "x" + height + ", format=" + format + ", maxImages=" + maxImages);
        // 清理之前的资源
        close();
        try {
            // 创建ImageReader
            imageReader = ImageReader.newInstance(width, height, format, maxImages);
            // 创建后台线程处理图像回调
            backgroundThread = new HandlerThread("ImageReaderHandler");
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
            // 设置图像可用监听器（超级简化版：直接保存到共享内存）
            imageReader.setOnImageAvailableListener(reader -> {
                Ln.d("🔥 ImageReader回调被触发！");
                try (Image image = reader.acquireLatestImage()) {
                    if (image == null) {
                        Ln.w("🔥 ImageReader回调触发但image为null！");
                        return;
                    }
                    Ln.d("🔥 获取到新图像: " + image.getWidth() + "x" + image.getHeight() + ", format=" + image.getFormat());

                    // 处理图像并保存到共享内存
                    ImageProcessor.ProcessResult result = imageProcessor.processImage(image);
                    if (result != null) {
                        // 获取字节数组数据
                        byte[] imageData = result.getBytes();
                        if (imageData != null) {
                            // 写入到共享内存
                            int writeResult = ashmemManager.writeData(imageData);
                            if (writeResult == 0) {
                                hasImageData = true;
                                Ln.d("🔥 图片数据已存入共享内存，大小: " + imageData.length + " bytes");
                            } else {
                                Ln.e("🔥 写入共享内存失败: " + writeResult);
                            }
                        }
                    }
                } catch (Exception e) {
                    Ln.w("🔥 ImageReader回调处理异常", e);
                }
            }, backgroundHandler);
            Ln.i("ImageReader created successfully");
        } catch (Exception e) {
            Ln.e("Failed to create ImageReader", e);
            close();
            throw new RuntimeException("Failed to create ImageReader", e);
        }
    }

    /**
     * 使用配置创建ImageReader
     *
     * @param width  图像宽度
     * @param height 图像高度
     */
    public void createImageReader(int width, int height) {
        createImageReader(width, height, config.getImageFormat(), config.getMaxImages());
    }

    /**
     * 获取Surface供VirtualDisplay使用
     *
     * @return ImageReader的Surface
     */
    public Surface getSurface() {
        if (imageReader == null) {
            throw new IllegalStateException("ImageReader not created");
        }
        return imageReader.getSurface();
    }

    /**
     * 获取最新的图像数据（字节数组格式）- 超级简化版
     * 从共享内存读取数据
     *
     * @return 图像字节数组，如果没有则返回null
     */
    public byte[] getLatestImageBytes() {
        Ln.d("📸 从共享内存获取最新图像数据...");

        if (!hasImageData) {
            Ln.w("📸 共享内存中没有图像数据");
            return null;
        }

        // 从共享内存读取数据
        byte[] data = ashmemManager.readData();
        if (data != null) {
            Ln.d("📸 从共享内存读取成功，大小: " + data.length + " bytes");
            return data;
        } else {
            Ln.w("📸 从共享内存读取失败");
            return null;
        }
    }

    // 删除了releaseResult方法，简化版不需要

    /**
     * 检查ImageReader是否已创建
     *
     * @return true如果已创建
     */
    public boolean isCreated() {
        return imageReader != null;
    }

    /**
     * 清理资源
     */
    public void close() {
        Ln.d("Closing ImageReaderHandler");
        // 关闭ImageReader
        if (imageReader != null) {
            try {
                imageReader.close();
            } catch (Exception e) {
                Ln.w("Failed to close ImageReader", e);
            }
            imageReader = null;
        }
        // 停止后台线程
        if (backgroundThread != null) {
            try {
                backgroundThread.quitSafely();
                backgroundThread.join(1000); // 等待最多1秒
            } catch (Exception e) {
                Ln.w("Failed to stop background thread", e);
            }
            backgroundThread = null;
            backgroundHandler = null;
        }
        Ln.d("ImageReaderHandler closed");
    }

}