package com.github.kirer.adb.memory;

import android.os.MemoryFile;
import android.os.ParcelFileDescriptor;
import com.genymobile.scrcpy.util.Ln;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ashmem内存管理器
 * 提供高性能的共享内存操作，用于零拷贝的图像数据传输
 */
public class AshmemManager {
    
    private static final String TAG = "AshmemManager";
    private static final AtomicLong nextId = new AtomicLong(1);
    private static final ConcurrentHashMap<Long, AshmemBuffer> buffers = new ConcurrentHashMap<>();
    
    /**
     * Ashmem缓冲区封装
     */
    public static class AshmemBuffer {
        private final long id;
        private final MemoryFile memoryFile;
        private final int size;
        private final MappedByteBuffer mappedBuffer;
        private volatile boolean closed = false;
        
        private AshmemBuffer(long id, MemoryFile memoryFile, int size, MappedByteBuffer mappedBuffer) {
            this.id = id;
            this.memoryFile = memoryFile;
            this.size = size;
            this.mappedBuffer = mappedBuffer;
        }
        
        public long getId() {
            return id;
        }
        
        public int getSize() {
            return size;
        }
        
        public ByteBuffer getBuffer() {
            if (closed) {
                throw new IllegalStateException("AshmemBuffer已关闭");
            }
            return mappedBuffer;
        }
        
        public FileDescriptor getFileDescriptor() {
            if (closed) {
                throw new IllegalStateException("AshmemBuffer已关闭");
            }
            try {
                return getFileDescriptorFromMemoryFile(memoryFile);
            } catch (Exception e) {
                Ln.e("获取FileDescriptor失败", e);
                return null;
            }
        }
        
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            
            try {
                if (mappedBuffer != null) {
                    // 取消内存映射
                    unmapBuffer(mappedBuffer);
                }
                if (memoryFile != null) {
                    memoryFile.close();
                }
            } catch (Exception e) {
                Ln.w("关闭AshmemBuffer时出错", e);
            }
            
            buffers.remove(id);
            Ln.d("AshmemBuffer已关闭: " + id);
        }
        
        public boolean isClosed() {
            return closed;
        }
    }
    
    /**
     * 创建Ashmem缓冲区
     * 
     * @param name 缓冲区名称
     * @param size 缓冲区大小（字节）
     * @return AshmemBuffer实例，失败时返回null
     */
    public static AshmemBuffer createBuffer(String name, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("缓冲区大小必须大于0");
        }
        
        long id = nextId.getAndIncrement();
        String bufferName = name + "_" + id;
        
        try {
            // 创建MemoryFile（底层使用Ashmem）
            MemoryFile memoryFile = new MemoryFile(bufferName, size);
            
            // 获取FileDescriptor并创建内存映射
            FileDescriptor fd = getFileDescriptorFromMemoryFile(memoryFile);
            if (fd == null) {
                memoryFile.close();
                Ln.e("无法获取FileDescriptor");
                return null;
            }
            
            // 创建内存映射
            MappedByteBuffer mappedBuffer = mapBuffer(fd, size);
            if (mappedBuffer == null) {
                memoryFile.close();
                Ln.e("内存映射失败");
                return null;
            }
            
            AshmemBuffer buffer = new AshmemBuffer(id, memoryFile, size, mappedBuffer);
            buffers.put(id, buffer);
            
            Ln.d("创建AshmemBuffer成功: " + bufferName + ", size=" + size);
            return buffer;
            
        } catch (Exception e) {
            Ln.e("创建AshmemBuffer失败: " + bufferName, e);
            return null;
        }
    }
    
    /**
     * 获取指定ID的缓冲区
     */
    public static AshmemBuffer getBuffer(long id) {
        return buffers.get(id);
    }
    
    /**
     * 关闭所有缓冲区
     */
    public static void closeAllBuffers() {
        for (AshmemBuffer buffer : buffers.values()) {
            buffer.close();
        }
        buffers.clear();
        Ln.i("所有AshmemBuffer已关闭");
    }
    
    /**
     * 获取当前缓冲区数量
     */
    public static int getBufferCount() {
        return buffers.size();
    }
    
    /**
     * 通过反射获取MemoryFile的FileDescriptor
     */
    private static FileDescriptor getFileDescriptorFromMemoryFile(MemoryFile memoryFile) {
        try {
            Method getFileDescriptorMethod = MemoryFile.class.getDeclaredMethod("getFileDescriptor");
            getFileDescriptorMethod.setAccessible(true);
            return (FileDescriptor) getFileDescriptorMethod.invoke(memoryFile);
        } catch (Exception e) {
            Ln.e("获取FileDescriptor失败", e);
            return null;
        }
    }
    
    /**
     * 创建内存映射
     */
    private static MappedByteBuffer mapBuffer(FileDescriptor fd, int size) {
        try {
            // 使用反射获取内存映射，避免FileChannel的限制
            Class<?> memoryFileClass = Class.forName("android.os.MemoryFile");
            java.lang.reflect.Method mapMethod = memoryFileClass.getDeclaredMethod("mmap",
                FileDescriptor.class, int.class, int.class);
            mapMethod.setAccessible(true);

            // 直接映射内存
            long address = (Long) mapMethod.invoke(null, fd, size, 0x01); // PROT_READ | PROT_WRITE

            // 创建DirectByteBuffer
            Class<?> directByteBufferClass = Class.forName("java.nio.DirectByteBuffer");
            java.lang.reflect.Constructor<?> constructor = directByteBufferClass.getDeclaredConstructor(
                long.class, int.class);
            constructor.setAccessible(true);

            return (MappedByteBuffer) constructor.newInstance(address, size);

        } catch (Exception e) {
            Ln.e("创建内存映射失败: " + e.getMessage());
            // 降级到普通ByteBuffer
            return (MappedByteBuffer) ByteBuffer.allocateDirect(size);
        }
    }
    
    /**
     * 取消内存映射
     */
    private static void unmapBuffer(MappedByteBuffer buffer) {
        try {
            Method cleanerMethod = buffer.getClass().getMethod("cleaner");
            cleanerMethod.setAccessible(true);
            Object cleaner = cleanerMethod.invoke(buffer);
            if (cleaner != null) {
                Method cleanMethod = cleaner.getClass().getMethod("clean");
                cleanMethod.invoke(cleaner);
            }
        } catch (Exception e) {
            // 忽略错误，系统会自动清理
            Ln.d("取消内存映射时出现异常（可忽略）: " + e.getMessage());
        }
    }
    
    /**
     * 将字节数组写入Ashmem缓冲区
     */
    public static boolean writeToBuffer(AshmemBuffer buffer, byte[] data, int offset, int length) {
        if (buffer == null || buffer.isClosed()) {
            return false;
        }
        
        if (data == null || offset < 0 || length < 0 || offset + length > data.length) {
            return false;
        }
        
        if (length > buffer.getSize()) {
            Ln.w("数据长度超过缓冲区大小: " + length + " > " + buffer.getSize());
            return false;
        }
        
        try {
            ByteBuffer byteBuffer = buffer.getBuffer();
            byteBuffer.clear();
            byteBuffer.put(data, offset, length);
            byteBuffer.flip();
            return true;
        } catch (Exception e) {
            Ln.e("写入AshmemBuffer失败", e);
            return false;
        }
    }
    
    /**
     * 从Ashmem缓冲区读取字节数组
     */
    public static byte[] readFromBuffer(AshmemBuffer buffer, int length) {
        if (buffer == null || buffer.isClosed()) {
            return null;
        }
        
        if (length <= 0 || length > buffer.getSize()) {
            return null;
        }
        
        try {
            ByteBuffer byteBuffer = buffer.getBuffer();
            byte[] data = new byte[length];
            byteBuffer.rewind();
            byteBuffer.get(data, 0, length);
            return data;
        } catch (Exception e) {
            Ln.e("从AshmemBuffer读取失败", e);
            return null;
        }
    }
}
