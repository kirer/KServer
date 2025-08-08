package com.github.kirer.server;

/**
 * Native服务器JNI接口类
 * 提供C语言实现的服务器功能
 */
public class Server {

    /**
     * 使用参数初始化服务器
     *
     * @param mode 通信模式 (0=SHARED_MEMORY, 1=UNIX_SOCKET, 2=TCP_SOCKET)
     * @param memorySize 共享内存大小（仅共享内存模式使用）
     * @param libPath 库路径（仅共享内存模式使用）
     * @param socketName Unix套接字名称（仅Unix套接字模式使用）
     * @param tcpHost TCP主机地址（仅TCP模式使用）
     * @param tcpPort TCP端口（仅TCP模式使用）
     * @return 0表示成功，-1表示失败
     */
    public static native int initializeWithParams(int mode, int memorySize, String libPath,
                                                  String socketName, String tcpHost, int tcpPort);

    /**
     * 启动服务器
     *
     * @return 0表示成功，-1表示失败
     */
    public static native int start();

    /**
     * 停止服务器
     *
     * @return 0表示成功，-1表示失败
     */
    public static native int stop();

    /**
     * 写入数据到服务器
     *
     * @param data 要写入的数据
     * @return 0表示成功，-1表示失败
     */
    public static native int writeData(byte[] data);

    /**
     * 获取服务器状态信息
     *
     * @return 状态信息字符串
     */
    public static native String getStatusInfo();
}
