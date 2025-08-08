package com.github.kirer.server;

import java.io.File;
import java.util.Objects;

/**
 * 通信配置类
 */
public class Config {
    
    // 通信方式
    private Mode mode = Mode.TCP_SOCKET; 
    
    // 共享内存配置
    private int memorySize = 0;
    private String libPath = new File(Objects.requireNonNull(System.getProperty("java.class.path"))).getParent();
    
    // Unix套接字配置
    private String socketName = "server-k";
    
    // TCP套接字配置
    private String tcpHost = "127.0.0.1";
    private int tcpPort = 8888;
    
    // 构造函数
    public Config() {
    }

    public Config(Mode mode) {
        this.mode = mode;
    }

    public Config(Mode mode, String libPath) {
        this.mode = mode;
        this.libPath = libPath;
    }
    
    // Getter和Setter方法
    public Mode getMode() {
        return mode;
    }
    
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public int getMemorySize() {
        return memorySize;
    }

    public String getLibPath() {
        return libPath;
    }

    public void setLibPath(String libPath) {
        this.libPath = libPath;
    }
    
    public void setMemorySize(int memorySize) {
        this.memorySize = memorySize;
    }
    
    public String getSocketName() {
        return socketName;
    }
    
    public void setSocketName(String socketName) {
        this.socketName = socketName;
    }
    
    public String getTcpHost() {
        return tcpHost;
    }
    
    public void setTcpHost(String tcpHost) {
        this.tcpHost = tcpHost;
    }
    
    public int getTcpPort() {
        return tcpPort;
    }
    
    public void setTcpPort(int tcpPort) {
        this.tcpPort = tcpPort;
    }
    
    @Override
    public String toString() {
        return "Config{" +
                "mode=" + mode +
                ", memorySize=" + memorySize +
                ", libPath='" + libPath + '\'' +
                ", socketName='" + socketName + '\'' +
                ", tcpHost='" + tcpHost + '\'' +
                ", tcpPort=" + tcpPort +
                '}';
    }
}
