package com.github.kirer.server;

/**
 * Socket控制层类型枚举（Server-K新架构）
 * 
 * 注意：数据传输统一使用共享内存，此枚举仅用于控制层Socket类型
 */
public enum Mode {
    /**
     * Unix域套接字控制层 - 本地高性能控制通信
     */
    UNIX_SOCKET,
    
    /**
     * TCP套接字控制层 - 网络兼容性控制通信（默认）
     */
    TCP_SOCKET;

    /**
     * 获取模式对应的数值
     * @param mode Socket控制层类型
     * @return 对应的数值
     */
    public static int getModeValue(Mode mode) {
        switch (mode) {
            case UNIX_SOCKET:
                return 1;
            case TCP_SOCKET:
                return 2;
            default:
                return 2; // 默认TCP
        }
    }
    
    /**
     * 从字符串获取模式
     * @param modeStr 模式字符串
     * @return 对应的模式
     */
    public static Mode fromString(String modeStr) {
        if ("unix".equalsIgnoreCase(modeStr)) {
            return UNIX_SOCKET;
        } else if ("tcp".equalsIgnoreCase(modeStr)) {
            return TCP_SOCKET;
        } else {
            return TCP_SOCKET; // 默认
        }
    }
}


