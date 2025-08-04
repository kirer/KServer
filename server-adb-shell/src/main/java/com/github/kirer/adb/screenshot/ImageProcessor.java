package com.github.kirer.adb.screenshot;

import android.media.Image;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.memory.AshmemManager;
// import com.github.kirer.adb.memory.BufferPool; // 不再需要

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简化的图像处理器
 * 只支持RAW格式和零拷贝传输
 */
public class ImageProcessor {
    // 处理结果（超级简化版：直接存储字节数组）
    public static class ProcessResult {
        public final byte[] data;
        public final int width;
        public final int height;
        public final long processingTime;

        public ProcessResult(byte[] data, int width, int height, long processingTime) {
            this.data = data;
            this.width = width;
            this.height = height;
            this.processingTime = processingTime;
        }

        // 计算数据大小：width * height * 4 (RGBA)
        public int getDataSize() {
            return data != null ? data.length : 0;
        }

        // 直接获取字节数组数据
        public byte[] getBytes() {
            return data;
        }
    }

    // private final BufferPool bufferPool; // 不再需要
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    /**
     * 创建图像处理器（简化版）
     */
    public ImageProcessor() {
        // 简化版：不再需要BufferPool
        Ln.i("简化版ImageProcessor创建成功");
    }

    /**
     * 处理Image对象
     *
     * @param image  输入图像
     * @return 处理结果，失败时返回null
     */
    public ProcessResult processImage(Image image) {
        if (image == null) {
            return null;
        }
        long startTime = System.currentTimeMillis();
        try {
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

                // 超级简化版：直接创建字节数组
                byte[] imageData = new byte[dataSize];
                ByteBuffer targetBuffer = ByteBuffer.wrap(imageData);

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

                long processingTime = System.currentTimeMillis() - startTime;
                Ln.d("原始处理完成: " + width + "x" + height + ", " + dataSize + " bytes, " + processingTime + "ms");
                return new ProcessResult(imageData, width, height, processingTime);
            } catch (Exception e) {
                Ln.e("处理原始RGBA失败", e);
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
}
