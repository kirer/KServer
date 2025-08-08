package com.github.kirer.server;

/**
 * 通信方式枚举
 */
public enum Mode {
    /**
     * 共享内存方式 - 性能最佳
     */
    SHARED_MEMORY,
    
    /**
     * Unix域套接字方式 - 性能良好
     */
    UNIX_SOCKET,
    
    /**
     * TCP套接字方式 - 兼容性最好
     */
    TCP_SOCKET;

    public static int getModeValue(Mode mode) {
        switch (mode) {
            case SHARED_MEMORY:
                return 0;
            case UNIX_SOCKET:
                return 1;
            case TCP_SOCKET:
                return 2;
            default:
                return 2; // 默认TCP
        }
    }
}


