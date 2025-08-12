package com.github.kirer.server;

/**
 * Server-K 配置类 (v2.0.0)
 * 定义了新分层架构的配置参数
 * 
 * 新架构特点:
 * - 数据层: 共享内存配置（库路径、内存大小）
 * - 控制层: Socket通信配置（类型、地址）
 * - 简化设计: 移除冗余配置，专注核心参数
 */
public class Config {
    
    // Socket控制层配置
    private Mode socketType = Mode.TCP_SOCKET; // Socket通信类型
    private String address = "127.0.0.1:8080"; // 统一地址格式

    // 共享内存数据层配置
    private String libPath = "/data/local/tmp/lib/arm64"; // Native库路径
    private int memorySize = 1024 * 1024 * 10; // 共享内存大小，默认10MB

    private boolean debug = false; // 调试模式
    
    // 构造函数
    public Config() {
    }

    public Config(Mode socketType, String address) {
        this.socketType = socketType;
        this.address = address;
    }

    public Config(Mode socketType, String address, String libPath) {
        this.socketType = socketType;
        this.address = address;
        this.libPath = libPath;
    }

    // Socket控制层配置
    public Mode getSocketType() {
        return socketType;
    }

    public void setSocketType(Mode socketType) {
        this.socketType = socketType;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    // 共享内存数据层配置
    public String getLibPath() {
        return libPath;
    }

    public void setLibPath(String libPath) {
        this.libPath = libPath;
    }

    public int getMemorySize() {
        return memorySize;
    }

    public void setMemorySize(int memorySize) {
        this.memorySize = memorySize;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public String toString() {
        return "Config{" +
                "socketType=" + socketType +
                ", address='" + address + '\'' +
                ", libPath='" + libPath + '\'' +
                ", memorySize=" + memorySize +
                ", debug=" + debug +
                '}';
    }
}
