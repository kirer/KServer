package com.github.kirer.adb.memory;

/**
 * Native Ashmem 共享内存管理器
 * 参考 autogo 项目实现，通过文件描述符实现跨进程共享内存
 */
public class NativeAshmem implements AshmemInterface {
    
    static {
        try {
            System.loadLibrary("ashmem");
        } catch (UnsatisfiedLinkError e) {
            // 如果找不到库，尝试从当前路径加载
            String classPath = System.getProperty("java.class.path");
            if (classPath != null) {
                String libPath = new java.io.File(classPath).getParent() + "/libashmem.so";
                System.load(libPath);
            }
        }
    }
    
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
    
    /**
     * 获取共享内存大小
     * @return 内存大小
     */
    public native long getSize();
    
    /**
     * 检查共享内存是否已初始化
     * @return true表示已初始化
     */
    public native boolean isInitialized();
}
