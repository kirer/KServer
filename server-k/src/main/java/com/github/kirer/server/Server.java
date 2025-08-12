package com.github.kirer.server;

import com.genymobile.scrcpy.util.Ln;

/**
 * Server-K服务器类（新架构）
 * 
 * 新架构特点：
 * 1. 数据层：共享内存永远存在，负责高性能截图数据传输
 * 2. 控制层：Socket服务器负责控制信令和通知机制
 * 3. 分层管理：Java层管理Socket控制层，Native层管理共享内存数据层
 */
public class Server {
    private static final String TAG = "Server";
    
    /**
     * 私有构造函数，防止实例化
     * 该类只提供静态的native方法接口
     */
    private Server() {
        // 工具类，不允许实例化
    }
    

    
    // ==================== Native JNI接口 ====================
    
    /**
     * 初始化native服务器（对应server.h中的nativeInit）
     *
     * @param socketType Socket类型 (1=UNIX_SOCKET, 2=TCP_SOCKET)
     * @param address 地址字符串（TCP: "host:port", Unix: "socket_name"）
     * @param shmSize 共享内存大小
     * @param debug 是否开启调试模式
     * @return 0表示成功，-1表示失败
     */
    public static native int initialize(int socketType, String address, int shmSize, boolean debug);

    /**
     * 启动native服务器（对应server.h中的nativeStart）
     *
     * @return 0表示成功，-1表示失败
     */
    public static native int start();

    /**
     * 停止native服务器（对应server.h中的nativeStop）
     *
     * @return 0表示成功，-1表示失败
     */
    public static native int stop();

    /**
     * 写入截图数据到共享内存（对应server.h中的nativeWriteScreenshot）
     *
     * @param data 要写入的截图数据
     * @return 0表示成功，-1表示失败
     */
    public static native int writeScreenshot(byte[] data);

    /**
     * 获取服务器状态（对应server.h中的nativeGetState）
     *
     * @return 服务器状态值
     */
    public static native int getState();
    
    /**
     * 清理native资源（对应server.h中的nativeCleanup）
     *
     * @return 0表示成功，-1表示失败
     */
    public static native int clean();
}
