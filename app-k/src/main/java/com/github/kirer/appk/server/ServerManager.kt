package com.github.kirer.appk.server

import android.content.Context
import com.github.kirer.appk.shizuku.ShizukuManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.*
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Server-K连接管理器
 * 负责启动server-k进程，管理Socket连接，处理命令通信
 */
class ServerManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: ServerManager? = null

        fun getInstance(): ServerManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServerManager().also { INSTANCE = it }
            }
        }

        private const val SERVER_DEX_PATH = "/data/local/tmp/server-k.dex"
        private const val SERVER_SO_PATH = "/data/local/tmp/libashmem.so"
        private const val SERVER_PORT = 12348
        private const val SERVER_FD = 1
        private const val SERVER_MMAP_SIZE = 1048576L

        // App内置的资源文件名
        private const val ASSET_DEX_NAME = "server-k.dex"
        private const val ASSET_SO_NAME = "libashmem.so"
    }
    
    // 服务器状态
    enum class ServerState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
    
    private val _serverState = MutableStateFlow(ServerState.DISCONNECTED)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()
    
    private val shizukuManager = ShizukuManager.getInstance()
    private var serverSocket: Socket? = null
    private var serverWriter: BufferedWriter? = null
    private var serverReader: BufferedReader? = null
    private var serverProcess: Process? = null
    private val isConnected = AtomicBoolean(false)
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * 启动Server-K服务
     */
    suspend fun startServer(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                _serverState.value = ServerState.CONNECTING
                Timber.d("Starting Server-K service...")

                // 检查Shizuku权限
                if (!shizukuManager.hasPermission()) {
                    Timber.e("Shizuku permission not granted")
                    _serverState.value = ServerState.ERROR
                    return@withContext false
                }

                // 停止现有服务
                stopServer()
                delay(1000)

                // 启动server-k进程
                Timber.d("Starting server process...")
                val success = startServerProcess()
                if (!success) {
                    Timber.e("Failed to start server process")
                    _serverState.value = ServerState.ERROR
                    return@withContext false
                }

                // 等待服务启动
                Timber.d("Waiting for server to start...")
                delay(3000) // 增加等待时间

                // 连接到服务器
                Timber.d("Attempting to connect to server...")
                val connected = connectToServer()
                if (connected) {
                    _serverState.value = ServerState.CONNECTED
                    isConnected.set(true)
                    Timber.d("Server-K started and connected successfully")
                } else {
                    Timber.e("Failed to connect to server")
                    _serverState.value = ServerState.ERROR
                    stopServer()
                }

                connected
            } catch (e: Exception) {
                Timber.e(e, "Failed to start server")
                _serverState.value = ServerState.ERROR
                stopServer()
                false
            }
        }
    }
    
    /**
     * 启动server-k进程 - 从APK启动并部署.so文件
     */
    private suspend fun startServerProcess(): Boolean {
        return try {
            // 1. 杀死可能存在的旧进程
            try {
                shizukuManager.executeShellCommand("pkill -f 'com.github.kirer.server.Main'")
                delay(1000) // 等待进程完全终止
            } catch (e: Exception) {
                Timber.d("No existing server process to kill")
            }

            // 2. 部署.so文件到server-k可以访问的位置
            Timber.d("Deploying native library...")
            val deploySuccess = deployNativeLibrary()
            if (!deploySuccess) {
                Timber.w("Failed to deploy native library, continuing without it")
            } else {
                Timber.d("Native library deployed successfully")
            }

            // 3. 获取当前APK路径
            val apkPath = getApkPath()
            Timber.d("APK path: $apkPath")

            // 4. 构建启动命令 - 从APK启动Java代码
            val command = """
                nohup sh -c "CLASSPATH='$apkPath' app_process /system/bin com.github.kirer.server.Main $SERVER_PORT $SERVER_FD $SERVER_MMAP_SIZE" > /dev/null 2>&1 &
                sleep 2
                echo "Server started from APK"
            """.trimIndent()

            Timber.d("Starting server with command: $command")

            // 5. 通过Shizuku执行命令
            val result = shizukuManager.executeShellCommand(command)
            Timber.d("Server process started: $result")

            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to start server process")
            false
        }
    }

    /**
     * 确保Native库已部署
     */
    private suspend fun ensureNativeLibraryDeployed(): Boolean {
        return try {
            // 检查.so文件是否存在
            val checkResult = shizukuManager.executeShellCommand("ls -la $SERVER_SO_PATH")
            if (checkResult.contains("No such file")) {
                Timber.d("Native library not found, attempting to deploy from APK...")
                return deployNativeLibraryFromApk()
            } else {
                Timber.d("Native library already exists: $SERVER_SO_PATH")
                return true
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check native library")
            false
        }
    }

    /**
     * 从APK部署Native库
     */
    private suspend fun deployNativeLibraryFromApk(): Boolean {
        return try {
            // 获取APK路径
            val apkPath = getApkPath()

            // 尝试从APK中提取.so文件
            val extractCommand = """
                # 尝试从APK中提取.so文件
                cd /data/local/tmp
                unzip -j '$apkPath' 'lib/arm64-v8a/libashmem.so' -o 2>/dev/null || \
                unzip -j '$apkPath' 'lib/armeabi-v7a/libashmem.so' -o 2>/dev/null || \
                echo "No native library found in APK"

                # 设置权限
                if [ -f libashmem.so ]; then
                    chmod 755 libashmem.so
                    echo "Native library extracted and permissions set"
                else
                    echo "Native library extraction failed"
                fi
            """.trimIndent()

            val result = shizukuManager.executeShellCommand(extractCommand)
            Timber.d("Native library extraction result: $result")

            // 验证是否成功
            val verifyResult = shizukuManager.executeShellCommand("ls -la $SERVER_SO_PATH")
            val success = !verifyResult.contains("No such file")

            if (success) {
                Timber.d("Native library successfully deployed from APK")
            } else {
                Timber.w("Native library deployment from APK failed")
            }

            success
        } catch (e: Exception) {
            Timber.e(e, "Failed to deploy native library from APK")
            false
        }
    }

    /**
     * 部署native库到server-k可以访问的位置
     */
    private suspend fun deployNativeLibrary(): Boolean {
        return try {
            // 获取当前设备的ABI
            val abi = shizukuManager.executeShellCommand("getprop ro.product.cpu.abi").trim()
            Timber.d("Device ABI: $abi")

            // 确定使用哪个ABI的.so文件
            val libName = when {
                abi.startsWith("arm64") -> "lib/arm64-v8a/libashmem.so"
                abi.startsWith("arm") -> "lib/armeabi-v7a/libashmem.so"
                else -> "lib/arm64-v8a/libashmem.so" // 默认使用arm64
            }

            // 从APK中提取.so文件
            val apkPath = getApkPath()
            val targetPath = "/data/local/tmp/libashmem.so"

            // 使用unzip命令从APK中提取.so文件
            val extractCommand = """
                cd /data/local/tmp &&
                unzip -o '$apkPath' '$libName' &&
                mv '$libName' libashmem.so &&
                chmod 755 libashmem.so &&
                ls -la libashmem.so
            """.trimIndent()

            Timber.d("Extracting native library: $extractCommand")
            val result = shizukuManager.executeShellCommand(extractCommand)
            Timber.d("Extract result: $result")

            // 检查文件是否成功提取
            val checkResult = shizukuManager.executeShellCommand("ls -la /data/local/tmp/libashmem.so")
            val success = !checkResult.contains("No such file")

            if (success) {
                Timber.d("Native library deployed successfully: $checkResult")
            } else {
                Timber.e("Failed to deploy native library: $checkResult")
            }

            success
        } catch (e: Exception) {
            Timber.e(e, "Failed to deploy native library")
            false
        }
    }

    /**
     * 获取当前应用的APK路径
     */
    private suspend fun getApkPath(): String {
        return try {
            // 通过shell命令查找App-K的APK路径
            val result = shizukuManager.executeShellCommand("pm path com.github.kirer.appk")
            if (result.startsWith("package:")) {
                val apkPath = result.substring(8).trim()
                Timber.d("Found APK path: $apkPath")
                apkPath
            } else {
                // 备用方法：查找/data/app目录
                val findResult = shizukuManager.executeShellCommand("find /data/app -name '*com.github.kirer.appk*' -type d 2>/dev/null | head -1")
                if (findResult.isNotEmpty()) {
                    val apkPath = "$findResult/base.apk"
                    Timber.d("Found APK path via find: $apkPath")
                    apkPath
                } else {
                    // 最后的备用路径
                    val fallbackPath = "/data/app/com.github.kirer.appk/base.apk"
                    Timber.w("Using fallback APK path: $fallbackPath")
                    fallbackPath
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get APK path")
            "/data/app/com.github.kirer.appk/base.apk"
        }
    }


    
    /**
     * 连接到Server-K
     */
    private suspend fun connectToServer(): Boolean {
        return try {
            Timber.d("Attempting to connect to server at 127.0.0.1:$SERVER_PORT")

            // 尝试连接多次
            repeat(15) { attempt ->
                try {
                    // 检查端口是否开放
                    val portCheck = shizukuManager.executeShellCommand("netstat -an | grep :$SERVER_PORT")
                    if (portCheck.isNotEmpty()) {
                        Timber.d("Port $SERVER_PORT is open, attempting connection...")
                    }

                    val socket = Socket("127.0.0.1", SERVER_PORT)
                    socket.soTimeout = 10000 // 10秒超时

                    serverSocket = socket
                    serverWriter = BufferedWriter(OutputStreamWriter(socket.getOutputStream()))
                    serverReader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    // 连接成功，不发送测试命令
                    Timber.d("Connected to server on attempt ${attempt + 1}")
                    return true
                } catch (e: Exception) {
                    Timber.d("Connection attempt ${attempt + 1} failed: ${e.message}")
                    delay(1000) // 等待1秒再重试
                }
            }

            Timber.e("Failed to connect after 15 attempts")
            false
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to server")
            false
        }
    }
    
    /**
     * 停止Server-K服务
     */
    suspend fun stopServer() {
        withContext(Dispatchers.IO) {
            try {
                isConnected.set(false)
                
                // 关闭Socket连接
                serverWriter?.close()
                serverReader?.close()
                serverSocket?.close()
                
                serverWriter = null
                serverReader = null
                serverSocket = null
                
                // 终止服务器进程
                serverProcess?.destroy()
                serverProcess = null
                
                // 通过Shizuku杀死进程
                try {
                    shizukuManager.executeShellCommand("pkill -f 'com.github.kirer.server.Main'")
                } catch (e: Exception) {
                    Timber.d("Failed to kill server process: ${e.message}")
                }
                
                _serverState.value = ServerState.DISCONNECTED
                Timber.d("Server stopped")
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop server")
            }
        }
    }
    
    /**
     * 发送命令到Server-K
     */
    suspend fun sendCommand(command: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                if (!isConnected.get() || serverWriter == null || serverReader == null) {
                    Timber.e("Server not connected")
                    return@withContext null
                }
                
                Timber.d("Sending command: $command")
                
                // 发送命令
                serverWriter?.write(command)
                serverWriter?.newLine()
                serverWriter?.flush()
                
                // 读取响应
                val response = serverReader?.readLine()
                Timber.d("Received response: $response")
                
                response
            } catch (e: Exception) {
                Timber.e(e, "Failed to send command: $command")
                null
            }
        }
    }
    
    /**
     * 检查服务器是否连接
     */
    fun isServerConnected(): Boolean {
        return isConnected.get() && serverSocket?.isConnected == true
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
        runBlocking {
            stopServer()
        }
    }
}
