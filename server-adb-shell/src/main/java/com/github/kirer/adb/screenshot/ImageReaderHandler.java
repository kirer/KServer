package com.github.kirer.adb.screenshot;

import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.genymobile.scrcpy.util.Ln;

import java.util.concurrent.atomic.AtomicReference;


/**
 * ImageReader处理器，负责管理ImageReader的创建、配置和图像获取
 */
public class ImageReaderHandler {

    private ImageReader imageReader;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private final ScreenshotConfig config;

    // 缓存PNG字节数组而不是Image对象
    private final AtomicReference<byte[]> cachedPngBytes = new AtomicReference<>();

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
                    try (Image image = reader.acquireLatestImage()) {
                        if (image != null) {
                            byte[] pngBytes = imageToBytes(image);
                            if (pngBytes != null) {
                                cachedPngBytes.set(pngBytes);
                            }
                        }
                    } catch (Exception e) {
                        Ln.w("Error processing new image", e);
                    }
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
     * 获取最新的PNG字节数组
     *
     * @return PNG字节数组，如果没有则返回null
     */
    public byte[] getLatestPngBytes() {
        byte[] cached = cachedPngBytes.get();
        if (cached != null) {
            return cached;
        }

        if (imageReader == null) {
            Ln.e("ImageReader not available");
            return null;
        }

        try (Image image = imageReader.acquireLatestImage()) {
            if (image != null) {
                byte[] pngBytes = imageToBytes(image);
                if (pngBytes != null) {
                    cachedPngBytes.set(pngBytes);
                    return pngBytes;
                }
            }
        } catch (Exception e) {
            Ln.w("Failed to acquire image", e);
        }
        return null;
    }

    /**
     * 直接从Image转换为PNG字节数组
     */
    private byte[] imageToBytes(Image image) {
        try {
            // 获取Image的像素数据
            Image.Plane[] planes = image.getPlanes();
            if (planes.length == 0) {
                Ln.e("Image has no planes");
                return null;
            }

            Image.Plane plane = planes[0];
            java.nio.ByteBuffer buffer = plane.getBuffer();

            int width = image.getWidth();
            int height = image.getHeight();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            // 创建Bitmap
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height, android.graphics.Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);

            // 如果有padding，需要裁剪
            if (rowPadding != 0) {
                bitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, width, height);
            }

            // 压缩为PNG
            try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos);
                return baos.toByteArray();
            } finally {
                bitmap.recycle();
            }

        } catch (Exception e) {
            Ln.e("Failed to convert image to bytes", e);
            return null;
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