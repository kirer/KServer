package com.github.kirer.app_server

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * KServer管理器
 * 通过Shizuku shell运行server-adb-shell DEX文件
 */
class KServerManager(private val context: Context) {

    companion object {
        private const val TAG = "KServerManager"
        private const val DEFAULT_PORT = 8888
        private const val HEALTH_CHECK_INTERVAL = 30000L // 30秒检查一次
    }

    private var isServiceRunning = false
    private var healthCheckJob: Job? = null
    private var isHealthCheckEnabled = false
    private val shizukuManager = ShizukuManager(context)
    
    /**
     * 启动KServer服务
     * 通过Shizuku shell运行APK中的server-adb-shell代码
     */
    suspend fun startKServer(port: Int = DEFAULT_PORT): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "启动KServer服务...")

            if (isServiceRunning) {
                Log.d(TAG, "KServer已在运行")
                return@withContext true
            }

            isServiceRunning = true
            Log.d(TAG, "KServer服务启动成功")
            true

        } catch (e: Exception) {
            Log.e(TAG, "启动KServer服务失败", e)
            false
        }
    }
    
    /**
     * 停止KServer服务
     * 通过Shizuku shell停止server-adb-shell进程
     */
    suspend fun stopKServer(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "停止KServer服务...")

            isServiceRunning = false

            // 停止健康检查
            stopHealthCheck()

            Log.d(TAG, "KServer服务已停止")
            true

        } catch (e: Exception) {
            Log.e(TAG, "停止KServer服务失败", e)
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
     * 通过Shizuku shell启动server-adb-shell
     * 直接运行APK中的代码，无需额外DEX文件
     */
    suspend fun startKServerViaShizuku(shizukuManager: ShizukuManager, port: Int = DEFAULT_PORT): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "通过Shizuku启动KServer...")

            // 获取当前应用的APK路径
            val apkPath = getApkPath()
            Log.d(TAG, "APK路径: $apkPath")

            // 构建启动命令
            val command = buildStartCommand(apkPath, port)
            Log.d(TAG, "执行命令: $command")

            // 通过Shizuku执行命令
            val result = shizukuManager.executeShellCommand(command)

            if (result.success) {
                Log.d(TAG, "KServer启动命令执行成功: ${result.output}")
                isServiceRunning = true

                // 等待服务启动
                kotlinx.coroutines.delay(3000)

                // 验证服务是否真正启动
                val isRunning = checkKServerProcess(shizukuManager)
                if (isRunning) {
                    Log.d(TAG, "KServer服务确认启动成功")
                    isServiceRunning = true

                    // 启动健康检查
                    startHealthCheck()

                    true
                } else {
                    Log.w(TAG, "KServer服务启动失败")
                    isServiceRunning = false
                    false
                }
            } else {
                Log.e(TAG, "KServer启动命令执行失败: ${result.error}")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "通过Shizuku启动KServer失败", e)
            false
        }
    }

    /**
     * 通过Shizuku shell停止server-adb-shell
     */
    suspend fun stopKServerViaShizuku(shizukuManager: ShizukuManager): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "通过Shizuku停止KServer...")

            // 停止KServer进程
            val killCommand = "pkill -f 'com.github.kirer.adb.Launcher' || true"
            val result = shizukuManager.executeShellCommand(killCommand)

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
     * 获取当前应用的APK路径
     */
    private fun getApkPath(): String {
        return context.applicationInfo.sourceDir
    }

    /**
     * 构建启动命令
     */
    private fun buildStartCommand(apkPath: String, port: Int): String {
        return """
            # 停止现有进程
            pkill -f 'com.github.kirer.adb.Launcher' || true
            sleep 1

            # 启动KServer Socket模式，使用APK作为CLASSPATH
            # 使用nohup确保进程在后台持续运行
            nohup sh -c "CLASSPATH='$apkPath' app_process /system/bin com.github.kirer.adb.Launcher -s -p $port" > /dev/null 2>&1 &

            # 等待启动
            sleep 3

            # 检查进程是否启动
            if pgrep -f 'com.github.kirer.adb.Launcher' > /dev/null; then
                echo "KServer进程启动成功"
            else
                echo "KServer进程启动失败"
            fi

            # 检查端口是否监听
            if netstat -an | grep ":$port.*LISTEN" > /dev/null; then
                echo "Socket服务器监听端口${port}成功"
            else
                echo "Socket服务器监听端口${port}失败"
            fi
        """.trimIndent()
    }

    /**
     * 检查KServer进程是否运行
     */
    private suspend fun checkKServerProcess(shizukuManager: ShizukuManager): Boolean {
        // 检查进程
        val processResult = shizukuManager.executeShellCommand("pgrep -f 'com.github.kirer.adb.Launcher'")
        val processRunning = processResult.success && processResult.output.trim().isNotEmpty()

        // 检查端口监听
        val portResult = shizukuManager.executeShellCommand("netstat -an | grep ':$DEFAULT_PORT.*LISTEN'")
        val portListening = portResult.success && portResult.output.trim().isNotEmpty()

        Log.d(TAG, "进程检查: 进程运行=$processRunning, 端口监听=$portListening")
        Log.d(TAG, "进程输出: ${processResult.output}")
        Log.d(TAG, "端口输出: ${portResult.output}")

        // 如果进程存在但端口没有监听，说明进程僵死，需要重启
        if (processRunning && !portListening) {
            Log.w(TAG, "检测到僵死进程，尝试清理...")
            cleanupZombieProcess(shizukuManager)
            return false
        }

        return processRunning && portListening
    }

    /**
     * 清理僵死进程
     */
    private suspend fun cleanupZombieProcess(shizukuManager: ShizukuManager) {
        try {
            val killResult = shizukuManager.executeShellCommand("pkill -f 'com.github.kirer.adb.Launcher'")
            Log.d(TAG, "清理僵死进程结果: ${killResult.success}")
            // 等待进程完全停止
            delay(2000)
        } catch (e: Exception) {
            Log.e(TAG, "清理僵死进程失败", e)
        }
    }

    /**
     * 启动健康检查
     */
    fun startHealthCheck() {
        if (isHealthCheckEnabled) return

        isHealthCheckEnabled = true
        healthCheckJob = CoroutineScope(Dispatchers.IO).launch {
            while (isHealthCheckEnabled) {
                try {
                    delay(HEALTH_CHECK_INTERVAL)

                    if (isServiceRunning) {
                        val isActuallyRunning = checkKServerProcess(this@KServerManager.shizukuManager)
                        val canConnect = if (isActuallyRunning) testKServerConnection() else false

                        if (!isActuallyRunning || !canConnect) {
                            Log.w(TAG, "健康检查失败，KServer已停止，尝试自动重启...")

                            // 自动重启
                            val restartSuccess = startKServer()
                            if (restartSuccess) {
                                Log.i(TAG, "KServer自动重启成功")
                            } else {
                                Log.e(TAG, "KServer自动重启失败")
                                isServiceRunning = false
                            }
                        } else {
                            Log.d(TAG, "健康检查通过")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "健康检查异常", e)
                }
            }
        }

        Log.d(TAG, "健康检查已启动")
    }

    /**
     * 停止健康检查
     */
    fun stopHealthCheck() {
        isHealthCheckEnabled = false
        healthCheckJob?.cancel()
        healthCheckJob = null
        Log.d(TAG, "健康检查已停止")
    }

    /**
     * 测试KServer连接
     */
    private suspend fun testKServerConnection(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val client = KServerClient()
            val response = client.sendCommand("help")
            val canConnect = response.success
            Log.d(TAG, "连接测试结果: $canConnect")
            canConnect
        } catch (e: Exception) {
            Log.d(TAG, "连接测试失败: ${e.message}")
            false
        }
    }
    
    /**
     * 检查APK是否可访问（替代DEX部署检查）
     */
    suspend fun checkApkAccess(shizukuManager: ShizukuManager): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "检查APK访问权限...")

            val apkPath = getApkPath()
            val checkCommand = "ls -la '$apkPath' && echo 'APK可访问'"
            val result = shizukuManager.executeShellCommand(checkCommand)

            if (result.success) {
                Log.d(TAG, "APK访问检查成功: ${result.output}")
                true
            } else {
                Log.e(TAG, "APK访问检查失败: ${result.error}")
                false
            }

        } catch (e: Exception) {
            Log.e(TAG, "检查APK访问权限失败", e)
            false
        }
    }
    
    /**
     * 获取服务状态信息
     */
    fun getServiceStatus(): Map<String, Any> {
        return mapOf(
            "isRunning" to isServiceRunning,
            "serviceType" to "Shell-based KServer",
            "apkPath" to getApkPath()
        )
    }
    
    /**
     * 通过Socket客户端执行截图测试
     */
    suspend fun testScreenshot(format: String = "png"): ByteArray? = withContext(Dispatchers.IO) {
        try {
            if (!isServiceRunning) {
                Log.w(TAG, "服务未运行，无法截图")
                return@withContext null
            }

            Log.d(TAG, "执行截图测试，格式: $format")

            // 通过Socket客户端连接KServer
            val client = KServerClient()
            val response = when (format.lowercase()) {
                "jpeg", "jpg" -> client.takeScreenshotOptimized("jpeg", 90)
                "raw" -> client.takeScreenshotOptimized("raw", 100)
                else -> client.takeScreenshotPng()
            }

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
    
    /**
     * 运行性能基准测试
     */
    suspend fun runBenchmark(): String = withContext(Dispatchers.IO) {
        try {
            if (!isServiceRunning) {
                return@withContext "服务未运行"
            }

            Log.d(TAG, "开始性能基准测试...")

            // 通过Socket客户端运行基准测试
            val client = KServerClient()
            val response = client.runBenchmark("full")

            if (response.success) {
                Log.d(TAG, "基准测试完成: ${response.message}")
                response.message
            } else {
                Log.e(TAG, "基准测试失败: ${response.message}")
                "基准测试失败: ${response.message}"
            }

        } catch (e: Exception) {
            Log.e(TAG, "基准测试失败", e)
            "基准测试失败: ${e.message}"
        }
    }
}
