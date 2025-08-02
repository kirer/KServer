package com.github.kirer.adb.utils;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.media.Image;

import com.genymobile.scrcpy.util.Ln;

import java.nio.ByteBuffer;

/**
 * 位图转换工具类，负责将Image对象转换为Bitmap
 */
public class BitmapConverter {

    private BitmapConverter() {
        // 工具类，不允许实例化
    }

    /**
     * 将Image转换为Bitmap
     *
     * @param image 要转换的Image对象
     * @return 转换后的Bitmap，失败时返回null
     */
    public static Bitmap imageToBitmap(Image image) {
        if (image == null) {
            Ln.w("Image is null, cannot convert to bitmap");
            return null;
        }

        try {
            int format = image.getFormat();
            switch (format) {
                case ImageFormat.YUV_420_888:
                    Ln.d("Converting YUV420 image to bitmap");
                    return convertYUV420ToBitmap(image);
                case ImageFormat.NV21:
                    Ln.d("Converting NV21 image to bitmap");
                    return convertNV21ToBitmap(image);
                case ImageFormat.JPEG:
                    Ln.d("Converting JPEG image to bitmap");
                    return convertJPEGToBitmap(image);
                case PixelFormat.RGBA_8888:
                    Ln.d("Converting RGBA8888 image to bitmap");
                    return convertRGBA8888ToBitmap(image);
                default:
                    Ln.e("Unsupported image format: " + format);
                    return null;
            }
        } catch (Exception e) {
            Ln.e("Failed to convert image to bitmap", e);
            return null;
        } finally {
            // 确保Image资源被释放
            try {
                image.close();
            } catch (Exception e) {
                Ln.w("Failed to close image", e);
            }
        }
    }

    /**
     * 转换YUV_420_888格式的图像
     */
    private static Bitmap convertYUV420ToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes.length != 3) {
            Ln.e("Invalid YUV420 image: expected 3 planes, got " + planes.length);
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        // 获取Y、U、V平面的数据
        Image.Plane yPlane = planes[0];
        Image.Plane uPlane = planes[1];
        Image.Plane vPlane = planes[2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();

        byte[] nv21 = new byte[ySize + uSize + vSize];

        // 复制Y数据
        yBuffer.get(nv21, 0, ySize);

        // 交错复制U和V数据到NV21格式
        byte[] uvPixels = new byte[uSize];
        uBuffer.get(uvPixels);

        byte[] vvPixels = new byte[vSize];
        vBuffer.get(vvPixels);

        // 将UV数据交错排列
        for (int i = 0; i < uSize; i++) {
            nv21[ySize + i * 2] = vvPixels[i];
            nv21[ySize + i * 2 + 1] = uvPixels[i];
        }

        return convertNV21ToRGB(nv21, width, height);
    }

    /**
     * 转换NV21格式的图像
     */
    private static Bitmap convertNV21ToBitmap(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        return convertNV21ToRGB(bytes, image.getWidth(), image.getHeight());
    }

    /**
     * 转换JPEG格式的图像
     */
    private static Bitmap convertJPEGToBitmap(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);

        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    /**
     * 转换RGBA8888格式的图像
     */
    private static Bitmap convertRGBA8888ToBitmap(Image image) {
        if (image.getFormat() != PixelFormat.RGBA_8888) {
            throw new IllegalArgumentException("Unsupported format, expected RGBA_8888");
        }
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();   // 通常为4
        int rowStride = planes[0].getRowStride();       // 可能大于 width * 4
        int rowPadding = rowStride - pixelStride * image.getWidth();
        int width = image.getWidth();
        int height = image.getHeight();
        // 创建比实际内容宽的 Bitmap，包含 row padding
        Bitmap bitmap = Bitmap.createBitmap(
                rowStride / pixelStride,  // padded width
                height,
                Bitmap.Config.ARGB_8888
        );
        bitmap.copyPixelsFromBuffer(buffer);
        // 剪裁去掉 padding，返回最终 Bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, width, height);
    }

    /**
     * 将NV21字节数组转换为RGB Bitmap
     */
    private static Bitmap convertNV21ToRGB(byte[] nv21, int width, int height) {
        try {
            // 创建YuvImage
            android.graphics.YuvImage yuvImage = new android.graphics.YuvImage(
                    nv21, ImageFormat.NV21, width, height, null);

            // 转换为JPEG字节流
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            yuvImage.compressToJpeg(new android.graphics.Rect(0, 0, width, height), 100, out);
            byte[] jpegBytes = out.toByteArray();

            // 解码JPEG为Bitmap
            return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);
        } catch (Exception e) {
            Ln.e("Failed to convert NV21 to RGB", e);
            return null;
        }
    }

    /**
     * 旋转Bitmap
     *
     * @param bitmap   原始Bitmap
     * @param rotation 旋转角度（0, 90, 180, 270）
     * @return 旋转后的Bitmap
     */
    public static Bitmap rotateBitmap(Bitmap bitmap, int rotation) {
        if (bitmap == null) {
            return null;
        }

        if (rotation == 0) {
            return bitmap;
        }

        try {
            Matrix matrix = new Matrix();
            matrix.postRotate(rotation);

            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

            // 如果创建了新的Bitmap，回收原始Bitmap
            if (rotatedBitmap != bitmap) {
                bitmap.recycle();
            }

            return rotatedBitmap;
        } catch (Exception e) {
            Ln.e("Failed to rotate bitmap", e);
            return bitmap; // 返回原始Bitmap
        }
    }

    /**
     * 根据显示器旋转角度计算需要的Bitmap旋转角度
     *
     * @param displayRotation 显示器旋转角度（Surface.ROTATION_*）
     * @return Bitmap旋转角度
     */
    public static int getRotationAngle(int displayRotation) {
        switch (displayRotation) {
            case 0: // Surface.ROTATION_0
                return 0;
            case 1: // Surface.ROTATION_90
                return 90;
            case 2: // Surface.ROTATION_180
                return 180;
            case 3: // Surface.ROTATION_270
                return 270;
            default:
                Ln.w("Unknown display rotation: " + displayRotation);
                return 0;
        }
    }

    /**
     * 安全地回收Bitmap
     *
     * @param bitmap 要回收的Bitmap
     */
    public static void recycleBitmap(Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            try {
                bitmap.recycle();
            } catch (Exception e) {
                Ln.w("Failed to recycle bitmap", e);
            }
        }
    }
}