package com.github.kirer.server;


/**
 * Ashmem 共享内存实现类
 * 复刻自 com.Ashmem，通过JNI调用native方法实现共享内存功能
 */
public class Ashmem {
    /**
     * 初始化共享内存
     * @param fd 文件描述符
     * @param size 内存大小
     * @return 0表示成功，非0表示失败
     */
    public native int init(int fd, long size);

    /**
     * 写入数据到共享内存
     * @param data 要写入的数据
     * @return 0表示成功，非0表示失败
     */
    public native int writeData(byte[] data);

    /**
     * 从共享内存读取数据
     * @return 读取的数据，失败时返回null
     */
    public native byte[] readData();

    /**
     * 销毁共享内存
     */
    public native void destroy();
}
