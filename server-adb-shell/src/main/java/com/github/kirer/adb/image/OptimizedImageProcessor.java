package com.github.kirer.adb.image;

import android.graphics.Bitmap;
import android.media.Image;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.memory.AshmemManager;
import com.github.kirer.adb.memory.BufferPool;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 优化的图像处理器
 * 支持多种数据格式和零拷贝传输
 */
public class OptimizedImageProcessor {

    private static final String TAG = "OptimizedImageProcessor";

    // 支持的输出格式
    public enum OutputFormat {
        RAW_RGBA,    // 原始RGBA数据
        PNG,         // PNG压缩
        JPEG,        // JPEG压缩
        WEBP         // WebP压缩
    }

    // 压缩质量配置
    public static class CompressionConfig {
        public final OutputFormat format;
        public final int quality;  // 0-100，仅对JPEG和WebP有效

        public CompressionConfig(OutputFormat format, int quality) {
            this.format = format;
            this.quality = Math.max(0, Math.min(100, quality));
        }

        public static CompressionConfig rawRgba() {
            return new CompressionConfig(OutputFormat.RAW_RGBA, 100);
        }

        public static CompressionConfig png() {
            return new CompressionConfig(OutputFormat.PNG, 100);
        }

        public static CompressionConfig jpeg(int quality) {
            return new CompressionConfig(OutputFormat.JPEG, quality);
        }

        public static CompressionConfig webp(int quality) {
            return new CompressionConfig(OutputFormat.WEBP, quality);
        }
    }

    // 处理结果
    public static class ProcessResult {
        public final AshmemManager.AshmemBuffer buffer;
        public final int dataSize;
        public final OutputFormat format;
        public final int width;
        public final int height;
        public final long processingTime;

        public ProcessResult(AshmemManager.AshmemBuffer buffer, int dataSize,
                             OutputFormat format, int width, int height, long processingTime) {
            this.buffer = buffer;
            this.dataSize = dataSize;
            this.format = format;
            this.width = width;
            this.height = height;
            this.processingTime = processingTime;
        }
    }

    private final BufferPool bufferPool;
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    /**
     * 创建图像处理器
     *
     * @param maxBufferSize 最大缓冲区大小
     * @param poolSize      缓冲池大小
     */
    public OptimizedImageProcessor(int maxBufferSize, int poolSize) {
        this.bufferPool = new BufferPool("ImageProcessor", maxBufferSize, poolSize / 2, poolSize);
        Ln.i("OptimizedImageProcessor创建成功，maxBufferSize=" + maxBufferSize + ", poolSize=" + poolSize);
    }

    /**
     * 处理Image对象
     *
     * @param image  输入图像
     * @param config 压缩配置
     * @return 处理结果，失败时返回null
     */
    public ProcessResult processImage(Image image, CompressionConfig config) {
        if (image == null || config == null) {
            return null;
        }

        long startTime = System.currentTimeMillis();

        try {
            switch (config.format) {
                case RAW_RGBA:
                    return processRawRgba(image, startTime);
                case PNG:
                    return processPng(image, startTime);
                case JPEG:
                    return processJpeg(image, config.quality, startTime);
                case WEBP:
                    return processWebp(image, config.quality, startTime);
                default:
                    Ln.e("不支持的输出格式: " + config.format);
                    return null;
            }
        } catch (Exception e) {
            Ln.e("处理图像失败", e);
            return null;
        } finally {
            long processingTime = System.currentTimeMillis() - startTime;
            totalProcessed.incrementAndGet();
            totalProcessingTime.addAndGet(processingTime);
        }
    }

    /**
     * 处理原始RGBA数据
     */
    private ProcessResult processRawRgba(Image image, long startTime) {
        try {
            int width = image.getWidth();
            int height = image.getHeight();

            // 获取像素数据
            Image.Plane[] planes = image.getPlanes();
            if (planes.length == 0) {
                Ln.e("Image没有planes");
                return null;
            }

            Image.Plane plane = planes[0];
            ByteBuffer buffer = plane.getBuffer();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            // 计算实际数据大小
            int dataSize = width * height * 4; // RGBA = 4 bytes per pixel

            // 从缓冲池获取缓冲区
            AshmemManager.AshmemBuffer ashmemBuffer = bufferPool.acquire();
            if (ashmemBuffer == null) {
                Ln.e("无法获取缓冲区");
                return null;
            }

            // 检查缓冲区大小
            if (dataSize > ashmemBuffer.getSize()) {
                Ln.e("数据大小超过缓冲区: " + dataSize + " > " + ashmemBuffer.getSize());
                bufferPool.release(ashmemBuffer);
                return null;
            }

            // 复制像素数据到Ashmem缓冲区
            ByteBuffer targetBuffer = ashmemBuffer.getBuffer();
            targetBuffer.clear();

            if (rowPadding == 0) {
                // 没有padding，直接复制
                targetBuffer.put(buffer);
            } else {
                // 有padding，逐行复制
                buffer.rewind();
                for (int row = 0; row < height; row++) {
                    for (int col = 0; col < width; col++) {
                        // 复制RGBA像素
                        for (int component = 0; component < pixelStride; component++) {
                            targetBuffer.put(buffer.get());
                        }
                    }
                    // 跳过padding
                    buffer.position(buffer.position() + rowPadding);
                }
            }

            targetBuffer.flip();

            long processingTime = System.currentTimeMillis() - startTime;
            Ln.d("原始RGBA处理完成: " + width + "x" + height + ", " + dataSize + " bytes, " + processingTime + "ms");

            return new ProcessResult(ashmemBuffer, dataSize, OutputFormat.RAW_RGBA, width, height, processingTime);

        } catch (Exception e) {
            Ln.e("处理原始RGBA失败", e);
            return null;
        }
    }

    /**
     * 处理PNG格式
     */
    private ProcessResult processPng(Image image, long startTime) {
        return processCompressedFormat(image, Bitmap.CompressFormat.PNG, 100, OutputFormat.PNG, startTime);
    }

    /**
     * 处理JPEG格式
     */
    private ProcessResult processJpeg(Image image, int quality, long startTime) {
        return processCompressedFormat(image, Bitmap.CompressFormat.JPEG, quality, OutputFormat.JPEG, startTime);
    }

    /**
     * 处理WebP格式
     */
    private ProcessResult processWebp(Image image, int quality, long startTime) {
        return processCompressedFormat(image, Bitmap.CompressFormat.WEBP, quality, OutputFormat.WEBP, startTime);
    }

    /**
     * 处理压缩格式的通用方法
     */
    private ProcessResult processCompressedFormat(Image image, Bitmap.CompressFormat format,
                                                  int quality, OutputFormat outputFormat, long startTime) {
        try {
            int width = image.getWidth();
            int height = image.getHeight();

            // 转换为Bitmap
            Bitmap bitmap = convertImageToBitmap(image);
            if (bitmap == null) {
                return null;
            }

            try {
                // 压缩为字节数组
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(format, quality, baos);
                byte[] compressedData = baos.toByteArray();
                baos.close();

                // 获取缓冲区
                AshmemManager.AshmemBuffer ashmemBuffer = bufferPool.acquire();
                if (ashmemBuffer == null) {
                    Ln.e("无法获取缓冲区");
                    return null;
                }

                // 检查缓冲区大小
                if (compressedData.length > ashmemBuffer.getSize()) {
                    Ln.e("压缩数据大小超过缓冲区: " + compressedData.length + " > " + ashmemBuffer.getSize());
                    bufferPool.release(ashmemBuffer);
                    return null;
                }

                // 写入压缩数据
                if (!AshmemManager.writeToBuffer(ashmemBuffer, compressedData, 0, compressedData.length)) {
                    Ln.e("写入压缩数据失败");
                    bufferPool.release(ashmemBuffer);
                    return null;
                }

                long processingTime = System.currentTimeMillis() - startTime;
                Ln.d(outputFormat + "处理完成: " + width + "x" + height + ", " + compressedData.length + " bytes, " + processingTime + "ms");

                return new ProcessResult(ashmemBuffer, compressedData.length, outputFormat, width, height, processingTime);

            } finally {
                bitmap.recycle();
            }

        } catch (Exception e) {
            Ln.e("处理" + outputFormat + "失败", e);
            return null;
        }
    }

    /**
     * 将Image转换为Bitmap
     */
    private Bitmap convertImageToBitmap(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            if (planes.length == 0) {
                return null;
            }

            Image.Plane plane = planes[0];
            ByteBuffer buffer = plane.getBuffer();
            int width = image.getWidth();
            int height = image.getHeight();
            int pixelStride = plane.getPixelStride();
            int rowStride = plane.getRowStride();
            int rowPadding = rowStride - pixelStride * width;

            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);

            if (rowPadding != 0) {
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
            }

            return bitmap;
        } catch (Exception e) {
            Ln.e("转换Image为Bitmap失败", e);
            return null;
        }
    }

    /**
     * 释放处理结果
     */
    public void releaseResult(ProcessResult result) {
        if (result != null && result.buffer != null) {
            bufferPool.release(result.buffer);
        }
    }

    /**
     * 获取统计信息
     */
    public void logStatistics() {
        long processed = totalProcessed.get();
        long totalTime = totalProcessingTime.get();

        Ln.i("OptimizedImageProcessor统计:");
        Ln.i("  总处理数: " + processed);
        Ln.i("  总处理时间: " + totalTime + "ms");
        if (processed > 0) {
            Ln.i("  平均处理时间: " + (totalTime / processed) + "ms");
        }

        bufferPool.logStatistics();
    }

    /**
     * 关闭处理器
     */
    public void close() {
        bufferPool.close();
        Ln.i("OptimizedImageProcessor已关闭");
    }
}
