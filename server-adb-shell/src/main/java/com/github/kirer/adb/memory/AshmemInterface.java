package com.github.kirer.adb.memory;

/**
 * Ashmem 接口，统一 NativeAshmem 和 MockNativeAshmem
 */
public interface AshmemInterface {
    
    /**
     * 初始化共享内存
     * @param fd 文件描述符
     * @param size 内存大小
     * @return 0表示成功，非0表示失败
     */
    int init(int fd, long size);
    
    /**
     * 写入数据到共享内存
     * @param data 要写入的数据
     * @return 0表示成功，非0表示失败
     */
    int writeData(byte[] data);
    
    /**
     * 从共享内存读取数据
     * @return 读取的数据，失败时返回null
     */
    byte[] readData();
    
    /**
     * 销毁共享内存
     */
    void destroy();
    
    /**
     * 获取共享内存大小
     * @return 内存大小
     */
    long getSize();
    
    /**
     * 检查共享内存是否已初始化
     * @return true表示已初始化
     */
    boolean isInitialized();
}
