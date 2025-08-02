package com.github.kirer.adb.screenshot;

import android.graphics.ImageFormat;
import android.graphics.PixelFormat;

/**
 * 截图配置类，封装截图相关的参数设置
 */
public class ScreenshotConfig {

    // 默认配置值
    public static final int DEFAULT_DISPLAY_ID = 0;
    public static final int DEFAULT_IMAGE_FORMAT = PixelFormat.RGBA_8888;
    public static final int DEFAULT_MAX_IMAGES = 4; // 增加缓冲区数量
    public static final boolean DEFAULT_AUTO_ROTATE = true;

    // 性能优化配置
    public static final int OPTIMIZED_MAX_IMAGES = 6; // 高性能模式的缓冲区数量
    public static final int BUFFER_POOL_SIZE = 8; // 缓冲池大小

    private int displayId;           // 显示器ID，默认主显示器
    private int imageFormat;         // 图像格式
    private int maxImages;           // ImageReader缓冲区大小
    private boolean autoRotate;      // 自动处理旋转

    /**
     * 使用默认配置创建实例
     */
    public ScreenshotConfig() {
        this.displayId = DEFAULT_DISPLAY_ID;
        this.imageFormat = DEFAULT_IMAGE_FORMAT;
        this.maxImages = DEFAULT_MAX_IMAGES;
        this.autoRotate = DEFAULT_AUTO_ROTATE;
    }

    /**
     * 创建自定义配置实例
     */
    public ScreenshotConfig(int displayId, int imageFormat, int maxImages, boolean autoRotate) {
        this.displayId = displayId;
        this.imageFormat = imageFormat;
        this.maxImages = maxImages;
        this.autoRotate = autoRotate;
        validate();
    }

    /**
     * 验证配置参数的有效性
     */
    private void validate() {
        if (displayId < 0) {
            throw new IllegalArgumentException("Display ID must be non-negative: " + displayId);
        }
        if (maxImages <= 0) {
            throw new IllegalArgumentException("Max images must be positive: " + maxImages);
        }
        // 验证图像格式
        if (imageFormat != ImageFormat.JPEG &&
                imageFormat != ImageFormat.YUV_420_888 &&
                imageFormat != ImageFormat.NV21 &&
                imageFormat != PixelFormat.RGBA_8888) {
            throw new IllegalArgumentException("Unsupported image format: " + imageFormat);
        }
    }

    // Getters
    public int getDisplayId() {
        return displayId;
    }

    public int getImageFormat() {
        return imageFormat;
    }

    public int getMaxImages() {
        return maxImages;
    }

    public boolean isAutoRotate() {
        return autoRotate;
    }

    // Setters with validation
    public void setDisplayId(int displayId) {
        if (displayId < 0) {
            throw new IllegalArgumentException("Display ID must be non-negative: " + displayId);
        }
        this.displayId = displayId;
    }

    public void setImageFormat(int imageFormat) {
        if (imageFormat != ImageFormat.JPEG &&
                imageFormat != ImageFormat.YUV_420_888 &&
                imageFormat != ImageFormat.NV21 &&
                imageFormat != PixelFormat.RGBA_8888) {
            throw new IllegalArgumentException("Unsupported image format: " + imageFormat);
        }
        this.imageFormat = imageFormat;
    }

    public void setMaxImages(int maxImages) {
        if (maxImages <= 0) {
            throw new IllegalArgumentException("Max images must be positive: " + maxImages);
        }
        this.maxImages = maxImages;
    }

    public void setAutoRotate(boolean autoRotate) {
        this.autoRotate = autoRotate;
    }

    /**
     * 创建默认配置的便捷方法
     */
    public static ScreenshotConfig createDefault() {
        return new ScreenshotConfig();
    }

    /**
     * 创建指定显示器的配置
     */
    public static ScreenshotConfig forDisplay(int displayId) {
        ScreenshotConfig config = new ScreenshotConfig();
        config.setDisplayId(displayId);
        return config;
    }

    /**
     * 创建高性能优化配置
     */
    public static ScreenshotConfig createOptimized() {
        ScreenshotConfig config = new ScreenshotConfig();
        config.setMaxImages(OPTIMIZED_MAX_IMAGES);
        return config;
    }

    /**
     * 创建指定显示器的高性能配置
     */
    public static ScreenshotConfig createOptimizedForDisplay(int displayId) {
        ScreenshotConfig config = createOptimized();
        config.setDisplayId(displayId);
        return config;
    }

    @Override
    public String toString() {
        return "ScreenshotConfig{" +
                "displayId=" + displayId +
                ", imageFormat=" + imageFormat +
                ", maxImages=" + maxImages +
                ", autoRotate=" + autoRotate +
                '}';
    }
}