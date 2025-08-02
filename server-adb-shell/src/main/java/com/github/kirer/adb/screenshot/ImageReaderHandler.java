package com.github.kirer.adb.screenshot;

import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.image.OptimizedImageProcessor;
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
    private OptimizedImageProcessor imageProcessor;

    // 缓存处理结果而不是原始字节数组
    private final AtomicReference<OptimizedImageProcessor.ProcessResult> cachedResult = new AtomicReference<>();

    // 降级模式的PNG字节缓存
    private final AtomicReference<byte[]> cachedPngBytes = new AtomicReference<>();

    // 当前输出格式配置
    private OptimizedImageProcessor.CompressionConfig compressionConfig;

    /**
     * 创建ImageReaderHandler实例
     *
     * @param config 截图配置
     */
    public ImageReaderHandler(ScreenshotConfig config) {
        this.config = config;

        // 初始化图像处理器，估算最大缓冲区大小
        int maxBufferSize = estimateMaxBufferSize();
        try {
            this.imageProcessor = new OptimizedImageProcessor(maxBufferSize, 4);
            // 默认使用PNG格式
            this.compressionConfig = OptimizedImageProcessor.CompressionConfig.png();
            Ln.i("OptimizedImageProcessor初始化成功");
        } catch (Exception e) {
            Ln.e("OptimizedImageProcessor初始化失败，使用降级模式: " + e.getMessage());
            this.imageProcessor = null;
            this.compressionConfig = null;
        }

        Ln.d("ImageReaderHandler创建，maxBufferSize=" + maxBufferSize);
    }

    /**
     * 估算最大缓冲区大小
     */
    private int estimateMaxBufferSize() {
        // 假设最大分辨率为4K (3840x2160)，RGBA格式
        int maxWidth = 3840;
        int maxHeight = 2160;
        int bytesPerPixel = 4; // RGBA
        // 原始数据大小
        int rawSize = maxWidth * maxHeight * bytesPerPixel;
        // 考虑压缩后的大小，PNG通常能压缩到原始大小的20-50%
        // 为了安全起见，分配原始大小的80%
        return (int) (rawSize * 0.8);
    }

    /**
     * 设置输出格式
     */
    public void setCompressionConfig(OptimizedImageProcessor.CompressionConfig config) {
        if (config != null) {
            this.compressionConfig = config;
            Ln.d("输出格式已设置: " + config.format);
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
            imageReader.setOnImageAvailableListener(reader -> {
                try (Image image = reader.acquireLatestImage()) {
                    if (image != null) {
                        if (imageProcessor != null && compressionConfig != null) {
                            // 使用优化的图像处理器
                            OptimizedImageProcessor.ProcessResult result = imageProcessor.processImage(image, compressionConfig);
                            if (result != null) {
                                // 释放之前的结果
                                OptimizedImageProcessor.ProcessResult oldResult = cachedResult.getAndSet(result);
                                if (oldResult != null) {
                                    imageProcessor.releaseResult(oldResult);
                                }
                            }
                        } else {
                            // 降级到原始方法
                            byte[] pngBytes = imageToBytes(image);
                            if (pngBytes != null) {
                                // 缓存PNG字节
                                cachedPngBytes.set(pngBytes);
                                Ln.d("使用降级模式处理图像，大小: " + pngBytes.length + " bytes");
                            }
                        }
                    }
                } catch (Exception e) {
                    Ln.w("Error processing new image", e);
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
     * 获取最新的图像数据（字节数组格式）
     *
     * @return 图像字节数组，如果没有则返回null
     */
    public byte[] getLatestImageBytes() {
        if (imageProcessor != null && compressionConfig != null) {
            // 优化模式
            OptimizedImageProcessor.ProcessResult result = cachedResult.get();
            if (result != null) {
                return AshmemManager.readFromBuffer(result.buffer, result.dataSize);
            }

            if (imageReader == null) {
                Ln.e("ImageReader not available");
                return null;
            }

            try (Image image = imageReader.acquireLatestImage()) {
                if (image != null) {
                    OptimizedImageProcessor.ProcessResult newResult =
                        imageProcessor.processImage(image, compressionConfig);
                    if (newResult != null) {
                        // 更新缓存
                        OptimizedImageProcessor.ProcessResult oldResult = cachedResult.getAndSet(newResult);
                        if (oldResult != null) {
                            imageProcessor.releaseResult(oldResult);
                        }
                        return AshmemManager.readFromBuffer(newResult.buffer, newResult.dataSize);
                    }
                }
            } catch (Exception e) {
                Ln.w("Failed to acquire image", e);
            }
        } else {
            // 降级模式
            Ln.d("使用降级模式获取图像数据");
            return getLatestPngBytes();
        }
        return null;
    }

    /**
     * 获取最新的PNG字节数组（向后兼容）
     *
     * @return PNG字节数组，如果没有则返回null
     */
    public byte[] getLatestPngBytes() {
        if (imageProcessor != null && compressionConfig != null) {
            // 优化模式：临时设置为PNG格式
            OptimizedImageProcessor.CompressionConfig oldConfig = compressionConfig;
            compressionConfig = OptimizedImageProcessor.CompressionConfig.png();

            try {
                return getLatestImageBytes();
            } finally {
                compressionConfig = oldConfig;
            }
        } else {
            // 降级模式：首先尝试使用缓存的PNG字节
            byte[] cached = cachedPngBytes.get();
            if (cached != null) {
                Ln.d("使用缓存的PNG字节，大小: " + cached.length);
                return cached;
            }

            // 如果没有缓存，尝试获取新的图像
            if (imageReader == null) {
                Ln.e("ImageReader not available");
                return null;
            }

            try (Image image = imageReader.acquireLatestImage()) {
                if (image != null) {
                    byte[] pngBytes = imageToBytes(image);
                    if (pngBytes != null) {
                        cachedPngBytes.set(pngBytes);
                    }
                    return pngBytes;
                }
            } catch (Exception e) {
                Ln.w("Failed to acquire image", e);
            }
            return null;
        }
    }

    /**
     * 获取最新的处理结果（包含元数据）
     *
     * @return 处理结果，如果没有则返回null
     */
    public OptimizedImageProcessor.ProcessResult getLatestProcessResult() {
        return cachedResult.get();
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

        // 释放缓存的处理结果
        OptimizedImageProcessor.ProcessResult result = cachedResult.getAndSet(null);
        if (result != null) {
            imageProcessor.releaseResult(result);
        }

        // 关闭图像处理器
        if (imageProcessor != null) {
            imageProcessor.close();
            imageProcessor = null;
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

    /**
     * 获取性能统计信息
     */
    public void logStatistics() {
        if (imageProcessor != null) {
            imageProcessor.logStatistics();
        }
    }
}