package com.github.kirer.app_server

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * KServer管理器
 * 通过Shizuku shell运行server-adb-shell DEX文件
 */
class KServerManager(private val context: Context) {

    companion object {
        private const val TAG = "KServerManager"
        private const val DEFAULT_PORT = 8888
    }
    private var isServiceRunning = false

    /**
     * 检查KServer是否运行
     */
    fun isKServerRunning(): Boolean {
        return isServiceRunning
    }

    /**
     * 通过Shizuku shell启动server-adb-shell
     * 直接运行APK中的代码，无需额外DEX文件
     */
    suspend fun startServer(shizukuManager: ShizukuManager, port: Int = DEFAULT_PORT): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "启动KServer...")
                // 获取当前应用的APK路径
                val apkPath = context.packageCodePath
                Log.d(TAG, "APK路径: $apkPath")
                var result = shizukuManager.execute("pkill -f 'com.github.kirer.adb.Launcher' || true")
                Log.d(TAG, "停止结果: ${result.output}")
                result = shizukuManager.execute("CLASSPATH='$apkPath' app_process /system/bin com.github.kirer.adb.Launcher --port $port --debug > /data/local/tmp/kserver.log 2>&1 &")
                if (result.success) {
                    isServiceRunning = true
                    Log.d(TAG, "KServer启动命令执行成功: ${result.output}")
                    true
                } else {
                    Log.e(TAG, "KServer启动命令执行失败: ${result.error}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动KServer失败", e)
                false
            }
        }

    /**
     * 停止
     */
    suspend fun stopServer(shizukuManager: ShizukuManager): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "通过Shizuku停止KServer...")
            // 停止KServer进程
            val killCommand = "pkill -f 'com.github.kirer.adb.Launcher' || true"
            val result = shizukuManager.execute(killCommand)
            if (result.success) {
                Log.d(TAG, "KServer停止命令执行成功")
                isServiceRunning = false
                true
            } else {
                Log.e(TAG, "KServer停止命令执行失败: ${result.error}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "通过Shizuku停止KServer失败", e)
            false
        }
    }

    /**
     * 构建启动命令
     */
    private fun buildStartCommand(apkPath: String, port: Int): String {
        return """
            # 停止现有进程
            pkill -f 'com.github.kirer.adb.Launcher' || true
            # 启动KServer Socket模式，使用APK作为CLASSPATH
            CLASSPATH='$apkPath' app_process /system/bin com.github.kirer.adb.Launcher --port $port --debug > /data/local/tmp/kserver.log 2>&1 &
        """.trimIndent()
    }

    /**
     * 通过Socket客户端执行截图测试（简化版：只支持RAW格式）
     */
    suspend fun testScreenshot(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (!isServiceRunning) {
                Log.w(TAG, "服务未运行，无法截图")
                return@withContext null
            }
            Log.d(TAG, "执行截图测试")

            // 通过Socket客户端连接KServer（简化版：只支持RAW格式）
            val client = KServerClient()
            val response = client.takeScreenshot()
            if (response.success && response.data != null) {
                Log.d(TAG, "截图成功，大小: ${response.data.size} bytes")
                response.data
            } else {
                Log.w(TAG, "截图失败: ${response.message}")
                null
            }

        } catch (e: Exception) {
            Log.e(TAG, "截图测试失败", e)
            null
        }
    }
}
