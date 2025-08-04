package com.github.kirer.server.screenshot;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import com.github.kirer.server.memory.Ashmem;
import com.genymobile.scrcpy.wrappers.DisplayManager;
import com.genymobile.scrcpy.wrappers.ServiceManager;
import com.genymobile.scrcpy.wrappers.SurfaceControl;
import com.genymobile.scrcpy.device.DisplayInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 屏幕截图功能实现
 * 复刻自 com.autogo.ScreenShot.ScreenShot
 */
public class ScreenShot {
    private static Ashmem ashmem = null;
    private static int bitmapWidth = 0;
    private static ByteBuffer buffer = null;
    private static IBinder display = null;
    private static DisplayManager dm = null;
    private static Handler handler = null;
    private static int height = 0;
    private static ImageReader imageReader = null;
    private static boolean initialized = false;
    private static final Object lock = new Object();
    
    /**
     * 初始化屏幕截图功能
     * @param ashmemInstance 共享内存实例
     * @throws InterruptedException 中断异常
     */
    public static void init(Ashmem ashmemInstance) throws InterruptedException {
        if (initialized) {
            return;
        }
        initialized = true;
        ashmem = ashmemInstance;
        
        try {
            dm = ServiceManager.getDisplayManager();
        } catch (Exception e) {
            System.err.println("Failed to get display manager: " + e.getMessage());
            System.exit(1);
        }
        
        // 使用ImageReader方式
        startImageReaderScreenshot();
    }
    

    
    /**
     * ImageReader截图方式
     */
    private static void startImageReaderScreenshot() throws InterruptedException {
        try {
            DisplayInfo displayInfo = dm.getDisplayInfo(0);
            HandlerThread handlerThread = new HandlerThread("ImageReaderThread");
            handlerThread.start();
            handler = new Handler(handlerThread.getLooper());

            recreateVirtualDisplay(displayInfo.getSize().getWidth(), displayInfo.getSize().getHeight());

            int currentWidth = displayInfo.getSize().getWidth();
            int currentHeight = displayInfo.getSize().getHeight();

            // 监控分辨率变化
            while (true) {
                DisplayInfo newDisplayInfo = dm.getDisplayInfo(0);
                if (newDisplayInfo.getSize().getWidth() != currentWidth ||
                    newDisplayInfo.getSize().getHeight() != currentHeight) {

                    int newWidth = newDisplayInfo.getSize().getWidth();
                    int newHeight = newDisplayInfo.getSize().getHeight();

                    synchronized (lock) {
                        recreateVirtualDisplay(newWidth, newHeight);
                    }
                    currentWidth = newWidth;
                    currentHeight = newHeight;
                }
                sleep(200L);
            }
        } catch (Exception e) {
            System.err.println("ImageReader setup failed: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * 处理ImageReader图像数据
     */
    private static void processImage(ImageReader imageReader) {
        synchronized (lock) {
            Image image = null;
            try {
                image = imageReader.acquireLatestImage();
                if (image != null) {
                    Image.Plane plane = image.getPlanes()[0];
                    ByteBuffer imageBuffer = plane.getBuffer();
                    buffer = imageBuffer;

                    if (imageBuffer != null) {
                        imageBuffer.rewind();
                        int width = image.getWidth();
                        height = image.getHeight();
                        int pixelStride = plane.getPixelStride();
                        bitmapWidth = width + ((plane.getRowStride() - (pixelStride * width)) / pixelStride);

                        int dataSize = imageBuffer.remaining();
                        byte[] imageData = new byte[dataSize];
                        imageBuffer.get(imageData);

                        ByteBuffer finalBuffer = ByteBuffer.allocate(dataSize + 8);
                        finalBuffer.order(ByteOrder.LITTLE_ENDIAN);
                        finalBuffer.putInt(bitmapWidth);
                        finalBuffer.putInt(height);
                        finalBuffer.put(imageData);

                        if (ashmem.writeData(finalBuffer.array()) != 0) {
                            System.out.println("Shared memory write error");
                            System.exit(1);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }
    }

    /**
     * 获取指定区域的Bitmap
     * @param x 起始X坐标
     * @param y 起始Y坐标
     * @param width 宽度（0表示使用图像宽度）
     * @param height 高度（0表示使用图像高度）
     * @return Bitmap对象
     * @throws InterruptedException 中断异常
     */
    public static Bitmap getBitmap(int x, int y, int width, int height) throws InterruptedException {
        while (true) {
            synchronized (lock) {
                if (buffer != null) {
                    // 创建缓冲区副本
                    ByteBuffer bufferCopy = ByteBuffer.allocate(buffer.capacity());
                    buffer.rewind();
                    bufferCopy.put(buffer);
                    bufferCopy.flip();

                    int currentBitmapWidth = bitmapWidth;
                    int currentHeight = ScreenShot.height;

                    // 创建Bitmap
                    Bitmap bitmap = Bitmap.createBitmap(currentBitmapWidth, currentHeight, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(bufferCopy);

                    // 处理裁剪参数
                    if (width == 0) {
                        width = bitmap.getWidth();
                    }
                    if (height == 0) {
                        height = bitmap.getHeight();
                    }

                    int cropWidth = width - x;
                    int cropHeight = height - y;

                    // 检查是否需要裁剪
                    if (cropWidth != bitmap.getWidth() || cropHeight != bitmap.getHeight()) {
                        return Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight);
                    }
                    return bitmap;
                }
            }
            sleep(20L);
        }
    }

    /**
     * 重新创建虚拟显示
     */
    private static void recreateVirtualDisplay(int width, int height) {
        if (imageReader != null) {
            imageReader.close();
        }

        ImageReader newImageReader = ImageReader.newInstance(width, height, 1, 3);
        imageReader = newImageReader;
        imageReader.setOnImageAvailableListener(
            imageReader1 -> processImage(imageReader1),
            handler
        );

        SurfaceControl.openTransaction();
        try {
            IBinder displayToken = SurfaceControl.createDisplay("autogocap", false);
            display = displayToken;
            SurfaceControl.setDisplaySurface(displayToken, imageReader.getSurface());
            SurfaceControl.setDisplayProjection(display, 0,
                new Rect(0, 0, width, height),
                new Rect(0, 0, width, height));
            SurfaceControl.setDisplayLayerStack(display, 0);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            SurfaceControl.closeTransaction();
        }
    }

    /**
     * 线程睡眠
     */
    public static void sleep(long millis) throws InterruptedException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            // 忽略中断异常
        }
    }
}
