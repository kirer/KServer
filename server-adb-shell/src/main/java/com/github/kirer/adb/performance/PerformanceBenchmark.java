package com.github.kirer.adb.performance;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.image.OptimizedImageProcessor;
import com.github.kirer.adb.screenshot.ScreenshotConfig;
import com.github.kirer.adb.screenshot.ScreenshotService;

import java.util.ArrayList;
import java.util.List;

/**
 * 性能基准测试工具
 * 用于测试和对比优化前后的性能指标
 */
public class PerformanceBenchmark {

    // 测试配置
    private static final int WARMUP_ITERATIONS = 5;
    private static final int TEST_ITERATIONS = 20;
    
    // 性能指标
    public static class BenchmarkResult {
        public final String testName;
        public final int iterations;
        public final long totalTime;
        public final long averageTime;
        public final long minTime;
        public final long maxTime;
        public final double throughput; // 每秒处理数
        public final long totalMemoryUsed;
        public final long averageMemoryUsed;
        
        public BenchmarkResult(String testName, List<Long> times, List<Long> memorySizes) {
            this.testName = testName;
            this.iterations = times.size();
            this.totalTime = times.stream().mapToLong(Long::longValue).sum();
            this.averageTime = iterations > 0 ? totalTime / iterations : 0;
            this.minTime = times.stream().mapToLong(Long::longValue).min().orElse(0);
            this.maxTime = times.stream().mapToLong(Long::longValue).max().orElse(0);
            this.throughput = totalTime > 0 ? iterations * 1000.0 / totalTime : 0; // 每秒处理数
            this.totalMemoryUsed = memorySizes.stream().mapToLong(Long::longValue).sum();
            this.averageMemoryUsed = iterations > 0 ? totalMemoryUsed / iterations : 0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "BenchmarkResult{%s: iterations=%d, avgTime=%dms, minTime=%dms, maxTime=%dms, " +
                "throughput=%.2f/s, avgMemory=%dKB}",
                testName, iterations, averageTime, minTime, maxTime, throughput, averageMemoryUsed / 1024
            );
        }
    }
    
    /**
     * 运行完整的性能基准测试
     */
    public static void runFullBenchmark() {
        Ln.i("开始性能基准测试...");
        
        try {
            // 测试不同配置
            BenchmarkResult defaultResult = testScreenshotPerformance("Default Config", 
                ScreenshotConfig.createDefault());
            
            BenchmarkResult optimizedResult = testScreenshotPerformance("Optimized Config", 
                ScreenshotConfig.createOptimized());
            
            // 测试不同输出格式
            BenchmarkResult pngResult = testFormatPerformance("PNG Format", 
                OptimizedImageProcessor.OutputFormat.PNG, 100);
            
            BenchmarkResult jpegResult = testFormatPerformance("JPEG Format", 
                OptimizedImageProcessor.OutputFormat.JPEG, 90);
            
            BenchmarkResult rawResult = testFormatPerformance("RAW RGBA Format", 
                OptimizedImageProcessor.OutputFormat.RAW_RGBA, 100);
            
            // 打印结果
            Ln.i("=== 性能基准测试结果 ===");
            Ln.i(defaultResult.toString());
            Ln.i(optimizedResult.toString());
            Ln.i(pngResult.toString());
            Ln.i(jpegResult.toString());
            Ln.i(rawResult.toString());
            
            // 计算性能提升
            double configImprovement = (double) defaultResult.averageTime / optimizedResult.averageTime;
            Ln.i(String.format("优化配置性能提升: %.2fx", configImprovement));
            
            double formatImprovement = (double) pngResult.averageTime / rawResult.averageTime;
            Ln.i(String.format("RAW格式相比PNG性能提升: %.2fx", formatImprovement));
            
        } catch (Exception e) {
            Ln.e("性能基准测试失败", e);
        }
        
        Ln.i("性能基准测试完成");
    }
    
    /**
     * 测试截图性能
     */
    private static BenchmarkResult testScreenshotPerformance(String testName, ScreenshotConfig config) {
        Ln.i("开始测试: " + testName);
        
        List<Long> times = new ArrayList<>();
        List<Long> memorySizes = new ArrayList<>();
        
        ScreenshotService service = null;
        try {
            service = new ScreenshotService(config);
            service.start();
            
            // 预热
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                service.takeImageBytes();
                Thread.sleep(10);
            }
            
            // 正式测试
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                long startTime = System.currentTimeMillis();
                long startMemory = getUsedMemory();
                
                byte[] imageBytes = service.takeImageBytes();
                
                long endTime = System.currentTimeMillis();
                long endMemory = getUsedMemory();
                
                if (imageBytes != null) {
                    times.add(endTime - startTime);
                    memorySizes.add(Math.max(0, endMemory - startMemory));
                }
                
                Thread.sleep(10); // 短暂休息
            }
            
        } catch (Exception e) {
            Ln.e("测试失败: " + testName, e);
        } finally {
            if (service != null) {
                service.cleanup();
            }
        }
        
        return new BenchmarkResult(testName, times, memorySizes);
    }
    
    /**
     * 测试不同格式的性能
     */
    private static BenchmarkResult testFormatPerformance(String testName, 
                                                       OptimizedImageProcessor.OutputFormat format, 
                                                       int quality) {
        Ln.i("开始测试: " + testName);
        
        List<Long> times = new ArrayList<>();
        List<Long> memorySizes = new ArrayList<>();
        
        ScreenshotService service = null;
        try {
            service = new ScreenshotService(ScreenshotConfig.createOptimized());
            service.start();
            
            // 设置输出格式
            service.setOutputFormat(format, quality);
            
            // 预热
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                service.takeImageBytes();
                Thread.sleep(10);
            }
            
            // 正式测试
            for (int i = 0; i < TEST_ITERATIONS; i++) {
                long startTime = System.currentTimeMillis();
                long startMemory = getUsedMemory();
                
                byte[] imageBytes = service.takeImageBytes();
                
                long endTime = System.currentTimeMillis();
                long endMemory = getUsedMemory();
                
                if (imageBytes != null) {
                    times.add(endTime - startTime);
                    memorySizes.add(Math.max(0, endMemory - startMemory));
                }
                
                Thread.sleep(10); // 短暂休息
            }
            
        } catch (Exception e) {
            Ln.e("测试失败: " + testName, e);
        } finally {
            if (service != null) {
                service.cleanup();
            }
        }
        
        return new BenchmarkResult(testName, times, memorySizes);
    }
    
    /**
     * 获取当前使用的内存量
     */
    private static long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
    
    /**
     * 强制垃圾回收
     */
    private static void forceGC() {
        System.gc();
        // System.runFinalization(); // 已过时，移除
        System.gc();
    }
    
    /**
     * 测试内存泄漏
     */
    public static void testMemoryLeak() {
        Ln.i("开始内存泄漏测试...");
        
        long initialMemory = getUsedMemory();
        Ln.i("初始内存使用: " + (initialMemory / 1024) + "KB");
        
        ScreenshotService service = null;
        try {
            service = new ScreenshotService(ScreenshotConfig.createOptimized());
            service.start();
            
            // 大量截图操作
            for (int i = 0; i < 100; i++) {
                service.takeImageBytes();
                
                if (i % 20 == 0) {
                    forceGC();
                    long currentMemory = getUsedMemory();
                    Ln.i("第" + i + "次截图后内存使用: " + (currentMemory / 1024) + "KB");
                }
            }
            
        } catch (Exception e) {
            Ln.e("内存泄漏测试失败", e);
        } finally {
            if (service != null) {
                service.cleanup();
            }
        }
        
        forceGC();
        long finalMemory = getUsedMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        Ln.i("最终内存使用: " + (finalMemory / 1024) + "KB");
        Ln.i("内存增长: " + (memoryIncrease / 1024) + "KB");
        
        if (memoryIncrease > 10 * 1024 * 1024) { // 10MB
            Ln.w("可能存在内存泄漏，内存增长超过10MB");
        } else {
            Ln.i("内存泄漏测试通过");
        }
    }
}
