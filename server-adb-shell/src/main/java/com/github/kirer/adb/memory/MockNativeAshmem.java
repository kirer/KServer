package com.github.kirer.adb.memory;

import com.genymobile.scrcpy.util.Ln;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mock 版本的 NativeAshmem，用于测试
 * 使用普通内存模拟共享内存行为
 */
public class MockNativeAshmem implements AshmemInterface {
    
    private ByteBuffer buffer;
    private int fd;
    private long size;
    private AtomicBoolean initialized = new AtomicBoolean(false);
    
    /**
     * 初始化共享内存
     * @param fd 文件描述符
     * @param size 内存大小
     * @return 0表示成功，非0表示失败
     */
    public int init(int fd, long size) {
        try {
            Ln.i("MockNativeAshmem: Initializing with fd=" + fd + ", size=" + size);
            
            if (fd < 0 || size <= 0) {
                Ln.e("MockNativeAshmem: Invalid parameters");
                return -1;
            }
            
            this.fd = fd;
            this.size = size;
            
            // 创建模拟的共享内存缓冲区
            this.buffer = ByteBuffer.allocateDirect((int) size);
            this.buffer.order(ByteOrder.LITTLE_ENDIAN);
            
            initialized.set(true);
            Ln.i("MockNativeAshmem: Initialized successfully");
            return 0;
            
        } catch (Exception e) {
            Ln.e("MockNativeAshmem: Initialization failed", e);
            return -1;
        }
    }
    
    /**
     * 写入数据到共享内存
     * @param data 要写入的数据
     * @return 0表示成功，非0表示失败
     */
    public int writeData(byte[] data) {
        if (!initialized.get() || buffer == null) {
            Ln.e("MockNativeAshmem: Not initialized");
            return -1;
        }
        
        if (data == null) {
            Ln.e("MockNativeAshmem: Data is null");
            return -2;
        }
        
        if (data.length > buffer.capacity()) {
            Ln.e("MockNativeAshmem: Data too large: " + data.length + " > " + buffer.capacity());
            return -3;
        }
        
        try {
            synchronized (buffer) {
                buffer.clear();
                buffer.put(data);
                buffer.flip();
            }
            
            Ln.d("MockNativeAshmem: Data written: " + data.length + " bytes");
            return 0;
            
        } catch (Exception e) {
            Ln.e("MockNativeAshmem: Write failed", e);
            return -4;
        }
    }
    
    /**
     * 从共享内存读取数据
     * @return 读取的数据，失败时返回null
     */
    public byte[] readData() {
        if (!initialized.get() || buffer == null) {
            Ln.e("MockNativeAshmem: Not initialized");
            return null;
        }
        
        try {
            synchronized (buffer) {
                buffer.rewind();
                
                // 检查是否有足够的数据
                if (buffer.remaining() < 8) {
                    Ln.w("MockNativeAshmem: Insufficient data for header");
                    return null;
                }
                
                // 读取宽度和高度
                int width = buffer.getInt();
                int height = buffer.getInt();
                
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
                    Ln.w("MockNativeAshmem: Invalid dimensions: " + width + "x" + height);
                    return null;
                }
                
                // 计算数据大小
                int pixelDataSize = width * height * 4; // ARGB_8888
                int totalSize = 8 + pixelDataSize;
                
                if (buffer.remaining() < pixelDataSize) {
                    Ln.w("MockNativeAshmem: Insufficient pixel data");
                    return null;
                }
                
                // 读取完整数据
                buffer.rewind();
                byte[] data = new byte[totalSize];
                buffer.get(data, 0, Math.min(totalSize, buffer.remaining()));
                
                Ln.d("MockNativeAshmem: Data read: " + data.length + " bytes (" + width + "x" + height + ")");
                return data;
            }
            
        } catch (Exception e) {
            Ln.e("MockNativeAshmem: Read failed", e);
            return null;
        }
    }
    
    /**
     * 销毁共享内存
     */
    public void destroy() {
        Ln.i("MockNativeAshmem: Destroying");
        
        initialized.set(false);
        buffer = null;
        fd = -1;
        size = 0;
        
        Ln.i("MockNativeAshmem: Destroyed");
    }
    
    /**
     * 获取共享内存大小
     * @return 内存大小
     */
    public long getSize() {
        return initialized.get() ? size : 0;
    }
    
    /**
     * 检查共享内存是否已初始化
     * @return true表示已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }
    
    /**
     * 获取文件描述符
     */
    public int getFd() {
        return fd;
    }
    
    /**
     * 获取缓冲区信息（用于调试）
     */
    public String getBufferInfo() {
        if (!initialized.get() || buffer == null) {
            return "Not initialized";
        }
        
        return String.format("Buffer: capacity=%d, position=%d, limit=%d, remaining=%d", 
            buffer.capacity(), buffer.position(), buffer.limit(), buffer.remaining());
    }
}
