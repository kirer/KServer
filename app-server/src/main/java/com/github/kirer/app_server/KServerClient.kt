package com.github.kirer.app_server

import android.content.Context
import android.util.Log
import com.github.kirer.server.Ashmem
import com.github.kirer.server.Launcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * KServer 共享内存客户端
 * 通过Unix domain socket获取共享内存文件描述符，然后读取截图数据
 */
class KServerClient {

    companion object {
        private const val TAG = "KServerClient"
        // 移除固定大小，改为动态计算
        // private const val SHARED_MEMORY_SIZE = 1024 * 1024 * 10 // 10MB 缓冲区

        // 与服务端保持一致的常量
        private const val BUFFER_PADDING_BYTES = 40 // 缓冲区额外填充字节
        private const val BYTES_PER_PIXEL = 4 // RGBA_8888 每像素4字节

        init {
            try {
                // 由于 app-server 依赖 server-k 模块，native 库会自动包含在 APK 中
                System.loadLibrary("ashmem")
                Log.d(TAG, "成功加载 libashmem.so")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "加载 libashmem.so 失败，这可能是因为库未正确打包到APK中", e)
                // 在实际运行时，如果库加载失败，后续的 JNI 调用会抛出异常
            }
        }

    }

    /**
     * 连接状态
     */
    enum class ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    /**
     * 命令响应
     */
    @Serializable
    data class CommandResponse(
        val success: Boolean,
        val message: String,
        val data: ByteArray? = null,
        val dataSize: Int = 0,
        val processingTime: Long = 0
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CommandResponse

            if (success != other.success) return false
            if (message != other.message) return false
            if (data != null) {
                if (other.data == null) return false
                if (!data.contentEquals(other.data)) return false
            } else if (other.data != null) return false
            if (dataSize != other.dataSize) return false
            if (processingTime != other.processingTime) return false

            return true
        }

        override fun hashCode(): Int {
            var result = success.hashCode()
            result = 31 * result + message.hashCode()
            result = 31 * result + (data?.contentHashCode() ?: 0)
            result = 31 * result + dataSize
            result = 31 * result + processingTime.hashCode()
            return result
        }
    }

    /**
     * 性能统计
     */
    data class PerformanceStats(
        val commandType: String,
        val processingTime: Long,
        val dataSize: Int,
        val networkTime: Long,
        val totalTime: Long
    )

    private var connectionStatus = ConnectionStatus.DISCONNECTED
    private var isSharedMemoryInitialized = false
    private var calculatedMemorySize = 0

    /**
     * 计算所需的共享内存大小（与服务端保持一致）
     * 计算公式：(宽度 + 填充) * (高度 + 填充) * 每像素字节数
     */
    private fun calculateMemorySize(context: Context): Int {
        val displayMetrics = context.resources.displayMetrics
        val width = displayMetrics.widthPixels + BUFFER_PADDING_BYTES
        val height = displayMetrics.heightPixels + BUFFER_PADDING_BYTES
        val memorySize = width * height * BYTES_PER_PIXEL

        Log.d(TAG, "计算共享内存大小:")
        Log.d(TAG, "  屏幕分辨率: ${displayMetrics.widthPixels}x${displayMetrics.heightPixels}")
        Log.d(TAG, "  填充后尺寸: ${width}x${height}")
        Log.d(TAG, "  计算结果: $memorySize 字节 (${memorySize / 1024 / 1024}MB)")

        return memorySize
    }

    // Unix Socket连接方法已移除，只使用TCP连接

    /**
     * 检查连接状态 - 直接连接共享内存文件
     */
    suspend fun checkConnection(context: Context): ConnectionStatus = withContext(Dispatchers.IO) {
        try {
            connectionStatus = ConnectionStatus.CONNECTING
            Log.d(TAG, "尝试连接共享内存文件")

            // 动态计算内存大小，与服务端保持一致
            calculatedMemorySize = calculateMemorySize(context)

            // 直接连接到共享内存文件
            val result = Ashmem.connect(calculatedMemorySize)
            if (result == 0) {
                isSharedMemoryInitialized = true
                connectionStatus = ConnectionStatus.CONNECTED
                Log.d(TAG, "共享内存文件连接成功，大小: $calculatedMemorySize 字节")
                ConnectionStatus.CONNECTED
            } else {
                connectionStatus = ConnectionStatus.ERROR
                Log.e(TAG, "共享内存文件连接失败，错误代码: $result")
                ConnectionStatus.ERROR
            }

        } catch (e: Exception) {
            Log.e(TAG, "共享内存文件连接检查失败", e)
            connectionStatus = ConnectionStatus.ERROR
            ConnectionStatus.ERROR
        }
    }

    /**
     * 从共享内存读取截图数据
     */
    private suspend fun readFromSharedMemory(): CommandResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            if (!isSharedMemoryInitialized) {
                return@withContext CommandResponse(
                    success = false,
                    message = "共享内存未初始化",
                    processingTime = System.currentTimeMillis() - startTime
                )
            }

            Log.d(TAG, "从共享内存读取截图数据...")
            val data = Ashmem.read()
            val processingTime = System.currentTimeMillis() - startTime

            if (data != null && data.isNotEmpty()) {
                Log.d(TAG, "成功从共享内存读取数据，大小: ${data.size} 字节")
                CommandResponse(
                    success = true,
                    message = "截图读取成功，耗时: ${processingTime}ms",
                    data = data,
                    dataSize = data.size,
                    processingTime = processingTime
                )
            } else {
                Log.d(TAG, "共享内存中暂无新数据")
                CommandResponse(
                    success = false, message = "暂无新数据", processingTime = processingTime
                )
            }

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "从共享内存读取数据失败", e)
            CommandResponse(
                success = false, message = "读取失败: ${e.message}", processingTime = processingTime
            )
        }
    }

    /**
     * 截图 - 从共享内存读取最新的截图数据
     */
    suspend fun takeScreenshot(): CommandResponse {
        return readFromSharedMemory()
    }
}
