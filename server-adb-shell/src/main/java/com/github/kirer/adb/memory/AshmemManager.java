package com.github.kirer.adb.memory;

import com.genymobile.scrcpy.util.Ln;

import java.io.FileDescriptor;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

/**
 * 简化的共享内存管理器
 * 只提供三个核心方法：init, writeData, readData
 */
public class AshmemManager {
    
    private static final String TAG = "AshmemManager";
    
    // 简化版：直接使用字节数组存储数据
    private volatile byte[] sharedData;
    private long memorySize;
    private boolean initialized = false;
    
    /**
     * 初始化共享内存
     * @param fd 文件描述符
     * @param size 内存大小
     * @return 0表示成功，非0表示失败
     */
    public int init(int fd, long size) {
        try {
            Ln.d(TAG + ": 初始化共享内存, fd=" + fd + ", size=" + size);

            // 超级简化处理：直接分配字节数组
            this.memorySize = size;
            this.sharedData = null; // 初始为空
            this.initialized = true;

            Ln.i(TAG + ": 共享内存初始化成功");
            return 0;

        } catch (Exception e) {
            Ln.e(TAG + ": 初始化共享内存失败", e);
            return -1;
        }
    }
    
    /**
     * 写入数据到共享内存
     * @param data 要写入的数据
     * @return 0表示成功，非0表示失败
     */
    public int writeData(byte[] data) {
        if (!initialized) {
            Ln.e(TAG + ": 共享内存未初始化");
            return -1;
        }

        if (data == null) {
            Ln.e(TAG + ": 数据为null");
            return -2;
        }

        if (data.length > memorySize) {
            Ln.e(TAG + ": 数据大小超过共享内存大小: " + data.length + " > " + memorySize);
            return -3;
        }

        try {
            // 超级简化：直接复制数组
            synchronized (this) {
                this.sharedData = new byte[data.length];
                System.arraycopy(data, 0, this.sharedData, 0, data.length);
            }

            Ln.d(TAG + ": 写入数据成功, 大小: " + data.length + " bytes");
            return 0;

        } catch (Exception e) {
            Ln.e(TAG + ": 写入数据失败", e);
            return -4;
        }
    }
    
    /**
     * 从共享内存读取数据
     * @return 读取的数据，失败时返回null
     */
    public byte[] readData() {
        if (!initialized) {
            Ln.e(TAG + ": 共享内存未初始化");
            return null;
        }

        try {
            synchronized (this) {
                if (sharedData == null) {
                    Ln.w(TAG + ": 共享内存中没有数据");
                    return null;
                }

                // 超级简化：直接复制并返回数组
                byte[] data = new byte[sharedData.length];
                System.arraycopy(sharedData, 0, data, 0, sharedData.length);

                Ln.d(TAG + ": 读取数据成功, 大小: " + data.length + " bytes");
                return data;
            }

        } catch (Exception e) {
            Ln.e(TAG + ": 读取数据失败", e);
            return null;
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        try {
            synchronized (this) {
                sharedData = null;
            }
            initialized = false;
            Ln.d(TAG + ": 资源清理完成");
        } catch (Exception e) {
            Ln.e(TAG + ": 清理资源失败", e);
        }
    }
    
    /**
     * 检查是否已初始化
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * 获取内存大小
     */
    public long getSize() {
        return memorySize;
    }
    
    // 兼容性方法：保留一些旧的静态方法以避免编译错误
    public static byte[] readFromBuffer(Object buffer, int size) {
        if (buffer instanceof ByteBuffer) {
            ByteBuffer bb = (ByteBuffer) buffer;
            byte[] data = new byte[size];
            bb.get(data);
            return data;
        }
        return null;
    }
}
