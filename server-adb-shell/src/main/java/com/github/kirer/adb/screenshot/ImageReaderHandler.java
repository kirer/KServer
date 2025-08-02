package com.github.kirer.adb.screenshot;

import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.genymobile.scrcpy.util.Ln;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ImageReader处理器，负责管理ImageReader的创建、配置和图像获取
 */
public class ImageReaderHandler {

    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final ScreenshotConfig config;

    // 用于同步图像获取
    private final AtomicReference<Image> latestImage = new AtomicReference<>();
    private CountDownLatch imageLatch;

    /**
     * 创建ImageReaderHandler实例
     *
     * @param config 截图配置
     */
    public ImageReaderHandler(ScreenshotConfig config) {
        this.config = config;
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
            // 设置图像可用监听器
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    handleImageAvailable(reader);
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
     * 获取最新的图像
     *
     * @return 最新的Image对象
     */
    public Image getLastImage() {
        return latestImage.get();
    }

    /**
     * 处理图像可用回调
     */
    private void handleImageAvailable(ImageReader reader) {
        try {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                // 关闭之前的图像
                Image oldImage = latestImage.getAndSet(image);
                if (oldImage != null) {
                    try {
                        oldImage.close();
                    } catch (Exception e) {
                        Ln.w("Failed to close old image", e);
                    }
                }
                // 通知等待的线程
                CountDownLatch latch = imageLatch;
                if (latch != null) {
                    latch.countDown();
                }
                Ln.d("New image available: " + image.getWidth() + "x" + image.getHeight());
            }
        } catch (Exception e) {
            Ln.e("Error handling image available", e);
            CountDownLatch latch = imageLatch;
            if (latch != null) {
                latch.countDown();
            }
        }
    }

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
        // 清理最新图像
        Image image = latestImage.getAndSet(null);
        if (image != null) {
            try {
                image.close();
            } catch (Exception e) {
                Ln.w("Failed to close latest image", e);
            }
        }
        // 释放等待的线程
        CountDownLatch latch = imageLatch;
        if (latch != null) {
            latch.countDown();
            imageLatch = null;
        }
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