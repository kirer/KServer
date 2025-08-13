package com.github.kirer.server;

/**
 * Server-K 配置类
 */
public class Config {
    private Mode socketType = Mode.TCP_SOCKET; // Socket通信类型
    private String address = "127.0.0.1:8080"; // 统一地址格式
    private String libPath = "/data/local/tmp/lib/arm64"; // Native库路径
    private boolean debug = false; // 调试模式

    // 构造函数
    public Config() {
    }

    public Config(String libPath, Mode socketType, String address) {
        this.libPath = libPath;
        this.socketType = socketType;
        this.address = address;
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


    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public String toString() {
        return "Config{" + "socketType=" + socketType + ", address='" + address + '\'' + ", libPath='" + libPath + '\'' + ", debug=" + debug + '}';
    }
}
