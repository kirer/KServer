package com.github.kirer.adb.net;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.performance.PerformanceBenchmark;

/**
 * 性能基准测试命令
 */
public class BenchmarkCommand implements ServiceCommand {
    
    @Override
    public String getCommandName() {
        return "benchmark";
    }
    
    @Override
    public String getHelp() {
        return "Run performance benchmark: benchmark [test_type]";
    }
    
    @Override
    public CommandResult execute(String[] params) {
        try {
            String testType = "full";
            if (params.length > 0) {
                testType = params[0].toLowerCase();
            }
            
            switch (testType) {
                case "full":
                    // 在后台线程运行基准测试
                    Thread benchmarkThread = new Thread(() -> {
                        PerformanceBenchmark.runFullBenchmark();
                    }, "BenchmarkThread");
                    benchmarkThread.start();
                    return CommandResult.success("Performance benchmark started");
                    
                case "memory":
                    // 在后台线程运行内存泄漏测试
                    Thread memoryThread = new Thread(() -> {
                        PerformanceBenchmark.testMemoryLeak();
                    }, "MemoryTestThread");
                    memoryThread.start();
                    return CommandResult.success("Memory leak test started");
                    
                default:
                    return CommandResult.error("Unknown test type: " + testType + 
                        ". Supported: full, memory");
            }
            
        } catch (Exception e) {
            Ln.e("Error executing benchmark command", e);
            return CommandResult.error("Benchmark failed: " + e.getMessage());
        }
    }
}
