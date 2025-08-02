package com.github.kirer.adb.memory;

import com.genymobile.scrcpy.util.Ln;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 高性能缓冲池管理器
 * 提供预分配的缓冲区池，减少内存分配和GC压力
 */
public class BufferPool {
    
    private static final String TAG = "BufferPool";
    
    // 池配置
    private final String poolName;
    private final int bufferSize;
    private final int maxPoolSize;
    private final int minPoolSize;
    
    // 缓冲区队列
    private final BlockingQueue<AshmemManager.AshmemBuffer> availableBuffers;
    private final AtomicInteger currentPoolSize = new AtomicInteger(0);
    private final AtomicInteger activeBuffers = new AtomicInteger(0);
    
    // 统计信息
    private final AtomicLong totalAllocated = new AtomicLong(0);
    private final AtomicLong totalReused = new AtomicLong(0);
    private final AtomicLong totalReleased = new AtomicLong(0);
    
    private volatile boolean closed = false;
    
    /**
     * 创建缓冲池
     * 
     * @param poolName 池名称
     * @param bufferSize 单个缓冲区大小
     * @param minPoolSize 最小池大小
     * @param maxPoolSize 最大池大小
     */
    public BufferPool(String poolName, int bufferSize, int minPoolSize, int maxPoolSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("缓冲区大小必须大于0");
        }
        if (minPoolSize < 0 || maxPoolSize < minPoolSize) {
            throw new IllegalArgumentException("池大小配置无效");
        }
        
        this.poolName = poolName;
        this.bufferSize = bufferSize;
        this.minPoolSize = minPoolSize;
        this.maxPoolSize = maxPoolSize;
        this.availableBuffers = new LinkedBlockingQueue<>(maxPoolSize);
        
        // 预分配最小数量的缓冲区
        preAllocateBuffers();
        
        Ln.i("BufferPool创建成功: " + poolName + 
             ", bufferSize=" + bufferSize + 
             ", minSize=" + minPoolSize + 
             ", maxSize=" + maxPoolSize);
    }
    
    /**
     * 预分配缓冲区
     */
    private void preAllocateBuffers() {
        for (int i = 0; i < minPoolSize; i++) {
            AshmemManager.AshmemBuffer buffer = createNewBuffer();
            if (buffer != null) {
                availableBuffers.offer(buffer);
                currentPoolSize.incrementAndGet();
            } else {
                Ln.w("预分配缓冲区失败: " + i);
                break;
            }
        }
        Ln.d("预分配缓冲区完成: " + currentPoolSize.get() + "/" + minPoolSize);
    }
    
    /**
     * 创建新的缓冲区
     */
    private AshmemManager.AshmemBuffer createNewBuffer() {
        String bufferName = poolName + "_buffer";
        return AshmemManager.createBuffer(bufferName, bufferSize);
    }
    
    /**
     * 获取缓冲区
     * 
     * @return 可用的缓冲区，如果池已关闭则返回null
     */
    public AshmemManager.AshmemBuffer acquire() {
        if (closed) {
            Ln.w("BufferPool已关闭，无法获取缓冲区");
            return null;
        }
        
        AshmemManager.AshmemBuffer buffer = availableBuffers.poll();
        
        if (buffer == null) {
            // 池中没有可用缓冲区，尝试创建新的
            if (currentPoolSize.get() < maxPoolSize) {
                buffer = createNewBuffer();
                if (buffer != null) {
                    currentPoolSize.incrementAndGet();
                    totalAllocated.incrementAndGet();
                    Ln.d("创建新缓冲区: " + buffer.getId());
                } else {
                    Ln.e("创建新缓冲区失败");
                    return null;
                }
            } else {
                Ln.w("缓冲池已满，无法创建新缓冲区");
                return null;
            }
        } else {
            totalReused.incrementAndGet();
            Ln.d("复用缓冲区: " + buffer.getId());
        }
        
        if (buffer != null && !buffer.isClosed()) {
            activeBuffers.incrementAndGet();
            return buffer;
        } else {
            Ln.w("获取到无效缓冲区");
            return null;
        }
    }
    
    /**
     * 释放缓冲区回池中
     * 
     * @param buffer 要释放的缓冲区
     */
    public void release(AshmemManager.AshmemBuffer buffer) {
        if (buffer == null || buffer.isClosed()) {
            return;
        }
        
        activeBuffers.decrementAndGet();
        totalReleased.incrementAndGet();
        
        if (closed) {
            // 池已关闭，直接关闭缓冲区
            buffer.close();
            currentPoolSize.decrementAndGet();
            return;
        }
        
        // 清理缓冲区内容
        try {
            buffer.getBuffer().clear();
        } catch (Exception e) {
            Ln.w("清理缓冲区失败", e);
            buffer.close();
            currentPoolSize.decrementAndGet();
            return;
        }
        
        // 尝试放回池中
        if (!availableBuffers.offer(buffer)) {
            // 池已满，关闭缓冲区
            buffer.close();
            currentPoolSize.decrementAndGet();
            Ln.d("池已满，关闭缓冲区: " + buffer.getId());
        } else {
            Ln.d("缓冲区已释放回池: " + buffer.getId());
        }
    }
    
    /**
     * 关闭缓冲池
     */
    public void close() {
        if (closed) {
            return;
        }
        
        closed = true;
        
        // 关闭所有可用缓冲区
        AshmemManager.AshmemBuffer buffer;
        while ((buffer = availableBuffers.poll()) != null) {
            buffer.close();
        }
        
        currentPoolSize.set(0);
        
        Ln.i("BufferPool已关闭: " + poolName);
        logStatistics();
    }
    
    /**
     * 获取池状态信息
     */
    public PoolStats getStats() {
        return new PoolStats(
            poolName,
            bufferSize,
            currentPoolSize.get(),
            activeBuffers.get(),
            availableBuffers.size(),
            totalAllocated.get(),
            totalReused.get(),
            totalReleased.get()
        );
    }
    
    /**
     * 打印统计信息
     */
    public void logStatistics() {
        PoolStats stats = getStats();
        Ln.i("BufferPool统计 [" + poolName + "]:");
        Ln.i("  缓冲区大小: " + stats.bufferSize + " bytes");
        Ln.i("  当前池大小: " + stats.currentPoolSize);
        Ln.i("  活跃缓冲区: " + stats.activeBuffers);
        Ln.i("  可用缓冲区: " + stats.availableBuffers);
        Ln.i("  总分配数: " + stats.totalAllocated);
        Ln.i("  总复用数: " + stats.totalReused);
        Ln.i("  总释放数: " + stats.totalReleased);
        
        if (stats.totalAllocated > 0) {
            double reuseRate = (double) stats.totalReused / stats.totalAllocated * 100;
            Ln.i("  复用率: " + String.format("%.2f%%", reuseRate));
        }
    }
    
    /**
     * 检查池是否已关闭
     */
    public boolean isClosed() {
        return closed;
    }
    
    /**
     * 池统计信息
     */
    public static class PoolStats {
        public final String poolName;
        public final int bufferSize;
        public final int currentPoolSize;
        public final int activeBuffers;
        public final int availableBuffers;
        public final long totalAllocated;
        public final long totalReused;
        public final long totalReleased;
        
        public PoolStats(String poolName, int bufferSize, int currentPoolSize, 
                        int activeBuffers, int availableBuffers,
                        long totalAllocated, long totalReused, long totalReleased) {
            this.poolName = poolName;
            this.bufferSize = bufferSize;
            this.currentPoolSize = currentPoolSize;
            this.activeBuffers = activeBuffers;
            this.availableBuffers = availableBuffers;
            this.totalAllocated = totalAllocated;
            this.totalReused = totalReused;
            this.totalReleased = totalReleased;
        }
    }
}
