package com.github.kirer.server;


/**
 * Server-K共享内存数据层管理类（新架构）
 * <p>
 * 新架构特点：
 * 1. 共享内存永远存在，作为高性能数据传输层
 * 2. 独立于Socket控制层，专注于截图数据传输
 * 3. 提供统一的数据层接口，支持多客户端访问
 */
public class ShareMemory {

    /**
     * 创建共享内存（服务端）
     *
     * @param size 共享内存大小（字节）
     * @return 0表示成功，-1表示失败
     */
    public static native int create(int size);

    /**
     * 连接到共享内存（客户端）
     *
     * @param size 共享内存大小（字节）
     * @return 0表示成功，-1表示失败
     */
    public static native int connect(int size);

    /**
     * 写入数据到共享内存
     *
     * @param data 要写入的字节数组
     * @return 0表示成功，-1表示失败
     */
    public static native int write(byte[] data);

    /**
     * 从共享内存读取数据
     *
     * @return 包含数据的字节数组，如果无数据或出错则返回null
     */
    public static native byte[] read();

    /**
     * 清理共享内存资源
     *
     * @return 0表示成功，-1表示失败
     */
    public static native int clean();

}
