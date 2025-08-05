package com.github.kirer.app_server

import android.content.Context
import android.util.Log
import com.github.kirer.server.Launcher
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
    }

    private var isServiceRunning = false
    private val kServerClient = KServerClient() // 全局KServerClient实例

    /**
     * 连接到KServer共享内存
     */
    suspend fun connectToServer(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "连接到KServer共享内存...")
            val status = kServerClient.checkConnection(context)
            val connected = status == KServerClient.ConnectionStatus.CONNECTED
            if (connected) {
                Log.d(TAG, "KServer连接成功")
            } else {
                Log.w(TAG, "KServer连接失败")
            }
            connected
        } catch (e: Exception) {
            Log.e(TAG, "连接KServer时出错", e)
            false
        }
    }

    /**
     * 检查KServer是否运行
     */
    fun isKServerRunning(): Boolean {
        return isServiceRunning
    }

    /**
     * 通过Shizuku shell启动server-k
     * 直接运行APK中的代码，无需额外DEX文件
     */
    suspend fun startServer(shizukuManager: ShizukuManager, libPath: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "启动KServer...")
                Log.d(TAG, "libPath参数: $libPath")
                // 2. 获取当前应用的APK路径
                val apkPath = context.packageCodePath
                Log.d(TAG, "APK路径: $apkPath")
                // 3. 停止现有进程
                var result = shizukuManager.execute("pkill -f '${Launcher::class.java.name}' || true")
                Log.d(TAG, "停止结果: ${result.output}")
                // 4. 启动KServer，使用直接文件访问
                val command = "CLASSPATH='$apkPath' app_process /system/bin ${Launcher::class.java.name} --libPath=${libPath} --debug > /data/local/tmp/server-k.log 2>&1 &"
                Log.d(TAG, "执行命令: $command")
                result = shizukuManager.execute(command)
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
            val killCommand = "pkill -f '${Launcher::class.java.name}' || true"
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
     * 部署native库到可访问的位置
     */
    private suspend fun deployNativeLibrary(
        shizukuManager: ShizukuManager, libPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始部署native库...")
            Log.d(TAG, "源路径: $libPath")

            // 检查源文件是否存在
            val sourceFile = "$libPath/libashmem.so"
            val checkSourceResult = shizukuManager.execute("ls -la '$sourceFile'")
            if (checkSourceResult.output.contains("No such file")) {
                Log.w(TAG, "源文件不存在: $sourceFile")
                return@withContext false
            }

            // 复制.so文件到/data/local/tmp/
            val targetPath = "/data/local/tmp/libashmem.so"
            val copyCommand = """
                    cp '$sourceFile' '$targetPath' &&
                    chmod 755 '$targetPath' &&
                    ls -la '$targetPath'
                """.trimIndent()

            Log.d(TAG, "执行复制命令: $copyCommand")
            val copyResult = shizukuManager.execute(copyCommand)
            Log.d(TAG, "复制结果: ${copyResult.output}")

            if (copyResult.success && !copyResult.output.contains("No such file")) {
                Log.d(TAG, "native库部署成功")
                true
            } else {
                Log.e(TAG, "native库部署失败: ${copyResult.error}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "部署native库时出错", e)
            false
        }
    }

    /**
     * 构建启动命令
     */
    private fun buildStartCommand(apkPath: String): String {
        return """
            # 停止现有进程
            pkill -f '${Launcher::class.java.name}' || true
            # 启动KServer，使用APK作为CLASSPATH
            CLASSPATH='$apkPath' app_process /system/bin ${Launcher::class.java.name} --libPath '/data/local/tmp' > /data/local/tmp/kserver.log 2>&1 &
        """.trimIndent()
    }

    /**
     * 通过共享内存执行截图测试
     */
    suspend fun testScreenshot(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (!isServiceRunning) {
                Log.w(TAG, "服务未运行，无法截图")
                return@withContext null
            }
            Log.d(TAG, "执行截图测试")

            // 使用全局KServerClient实例读取截图数据
            val response = kServerClient.takeScreenshot()
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
