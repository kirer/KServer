package com.github.kirer.appk.memory

import com.github.kirer.server.memory.Ashmem
import timber.log.Timber

/**
 * 安全的Ashmem包装类
 * 避免在类加载时就尝试加载native库
 */
class SafeAshmem {

    private var ashmemInstance: Ashmem? = null
    private var isNativeLibraryLoaded = false
    
    /**
     * 尝试加载native库和Ashmem类
     */
    private fun loadNativeLibrary(): Boolean {
        if (isNativeLibraryLoaded) {
            return ashmemInstance != null
        }

        try {
            // 直接创建Ashmem实例 - 它会自动加载.so文件
            ashmemInstance = Ashmem()
            isNativeLibraryLoaded = true

            Timber.d("Successfully created Ashmem instance")
            return true

        } catch (e: Exception) {
            Timber.e(e, "Failed to create Ashmem instance")
            isNativeLibraryLoaded = true
            return false
        }
    }
    
    /**
     * 初始化共享内存
     */
    fun init(fd: Int, size: Long): Int {
        if (!loadNativeLibrary() || ashmemInstance == null) {
            Timber.e("Native library not loaded")
            return -1
        }

        return try {
            ashmemInstance!!.init(fd, size)
        } catch (e: Exception) {
            Timber.e(e, "Failed to call init method")
            -1
        }
    }

    /**
     * 读取共享内存数据
     */
    fun readData(): ByteArray? {
        if (!loadNativeLibrary() || ashmemInstance == null) {
            Timber.e("Native library not loaded")
            return null
        }

        return try {
            ashmemInstance!!.readData()
        } catch (e: Exception) {
            Timber.e(e, "Failed to call readData method")
            null
        }
    }

    /**
     * 销毁共享内存
     */
    fun destroy() {
        if (!loadNativeLibrary() || ashmemInstance == null) {
            return
        }

        try {
            ashmemInstance!!.destroy()
        } catch (e: Exception) {
            Timber.e(e, "Failed to call destroy method")
        }
    }
    
    /**
     * 检查native库是否可用
     */
    fun isNativeLibraryAvailable(): Boolean {
        return loadNativeLibrary() && ashmemInstance != null
    }
}
