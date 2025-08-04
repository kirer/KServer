package com.github.kirer.server.memory;

/**
 * Ashmem 共享内存实现类
 * 复刻自 com.Ashmem，通过JNI调用native方法实现共享内存功能
 */
public class Ashmem {
    
    static {
        try {
            // 尝试加载native库 - 这应该能从APK中找到.so文件
            System.loadLibrary("ashmem");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load libashmem from library path: " + e.getMessage());

            // 尝试多种可能的路径
            String[] possiblePaths = {
                "/data/local/tmp/libashmem.so",
                "/system/lib64/libashmem.so",
                "/system/lib/libashmem.so"
            };

            boolean loaded = false;
            for (String path : possiblePaths) {
                try {
                    System.load(path);
                    System.out.println("Successfully loaded libashmem from: " + path);
                    loaded = true;
                    break;
                } catch (UnsatisfiedLinkError | SecurityException ex) {
                    System.err.println("Failed to load from " + path + ": " + ex.getMessage());
                }
            }

            if (!loaded) {
                System.err.println("WARNING: Could not load libashmem.so from any path. Native methods will not work.");
                // 不抛出异常，让程序继续运行，但native方法会失败
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
}
