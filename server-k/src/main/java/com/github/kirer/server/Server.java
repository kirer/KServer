package com.github.kirer.server;

/**
 * Server-K服务器类（新架构）
 * <p>
 * 新架构特点：
 * 1. 数据层：共享内存永远存在，负责高性能截图数据传输
 * 2. 控制层：Socket服务器负责控制信令和通知机制
 * 3. 分层管理：Java层管理Socket控制层，Native层管理共享内存数据层
 */
public class Server {

    public static final int MSG_TYPE_INIT_SCREEN_CAPTURE = 111;
    public static final int MSG_TYPE_NOTIFY_SHARE_MEMORY_SIZE = 112;
    public static final int MSG_TYPE_NOTIFY_SCREEN_CAPTURE = 113;

    public interface ServerMessageListener {
        void onMessage(int type, byte[] data);
    }

    /**
     * 初始化native服务器（对应server.h中的nativeInit）
     *
     * @param socketType Socket类型 (1=UNIX_SOCKET, 2=TCP_SOCKET)
     * @param address    地址字符串（TCP: "host:port", Unix: "socket_name"）
     * @param debug      是否开启调试模式
     * @return 0表示成功，-1表示失败
     */
    public static native int initialize(int socketType, String address, boolean debug);

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
     * 设置消息回调监听器
     */
    public static native void setMessageCallback(ServerMessageListener listener);

    /**
     * 发送消息给服务器
     *
     * @param msgType 消息类型
     * @param data    消息数据
     * @return 0表示成功，-1表示失败
     */
    public static native int sendMessage(int msgType, byte[] data);

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
