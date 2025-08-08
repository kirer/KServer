package com.github.kirer.app_k

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.genymobile.scrcpy.util.Ln
import com.github.kirer.server.Client
import com.github.kirer.server.Config
import com.github.kirer.server.Launcher
import com.github.kirer.server.Mode
import com.github.kirer.server.ScreenCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SimpleTestActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SimpleTestActivity"
        private const val REQUEST_CODE_STORAGE_PERMISSION = 100
    }

    private val shizukuManager: ShizukuManager by lazy { ShizukuManager() }

    private val manageExternalStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Log.d(TAG, "✅ 所有文件访问权限已获得")
                showToast("所有文件访问权限已获得")
                // 权限获得后请求Shizuku权限
                shizukuManager.requestPermission()
            } else {
                Log.w(TAG, "❌ 所有文件访问权限被拒绝")
                showToast("需要所有文件访问权限才能正常工作")
            }
        }
    }

    private val toolbar: Toolbar by lazy { findViewById<Toolbar>(R.id.toolbar) }
    private val spinner: AppCompatSpinner by lazy { findViewById<AppCompatSpinner>(R.id.spinnerMode) }
    private val btnStart: Button by lazy { findViewById<Button>(R.id.btnStart) }
    private val tvScreenshot: TextView by lazy { findViewById<TextView>(R.id.tvScreenshot) }
    private val ivScreenshot: ImageView by lazy { findViewById<ImageView>(R.id.ivScreenshot) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ln.initLogLevel(Ln.Level.DEBUG)
        setContentView(R.layout.activity_simple_test)
        loadNativeLibrary()
        setupUI()
        requestStoragePermissions()
    }

    private fun setupUI() {
        setSupportActionBar(toolbar)
        var seconds = 0
        // 每一秒更新Toolbar标题
        lifecycleScope.launch {
            while (true) {
                toolbar.title = "运行时间: ${++seconds}秒"
                delay(1000)
            }
        }
        val arr = resources.getStringArray(R.array.modeSwitch)
        btnStart.setOnClickListener {
            if (btnStart.text == "停止") {
                btnStart.text = "启动"
                lifecycleScope.launch {
                    stopServer()
                }
                return@setOnClickListener
            }
            btnStart.text = "停止"
            when (spinner.selectedItem.toString()) {
                arr[0] -> useSharedMemory()
                arr[1] -> useUnixSocket()
                arr[2] -> useTcpSocket()
            }
        }
        findViewById<Button>(R.id.btnScreenshot).setOnClickListener {
            takeScreenshot()
        }
    }

    /**
     * 申请存储权限
     */
    private fun requestStoragePermissions() {
        Log.d(TAG, "开始申请存储权限...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要申请所有文件访问权限
            if (Environment.isExternalStorageManager()) {
                Log.d(TAG, "✅ 已有所有文件访问权限")
                shizukuManager.requestPermission()
            } else {
                Log.d(TAG, "申请所有文件访问权限...")
                showToast("需要申请所有文件访问权限")
                try {
                    val intent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = "package:$packageName".toUri()
                        }
                    manageExternalStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "无法打开所有文件访问权限设置", e)
                    showToast("请手动在设置中授予所有文件访问权限")
                    shizukuManager.requestPermission()
                }
            }
        } else {
            // Android 6-10 申请传统存储权限
            val permissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (permissions.isNotEmpty()) {
                Log.d(TAG, "申请传统存储权限: $permissions")
                ActivityCompat.requestPermissions(
                    this,
                    permissions.toTypedArray(),
                    REQUEST_CODE_STORAGE_PERMISSION
                )
            } else {
                Log.d(TAG, "✅ 已有传统存储权限")
                shizukuManager.requestPermission()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CODE_STORAGE_PERMISSION -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    Log.d(TAG, "✅ 传统存储权限已获得")
                    showToast("存储权限已获得")
                    shizukuManager.requestPermission()
                } else {
                    Log.w(TAG, "❌ 部分存储权限被拒绝")
                    showToast("部分权限被拒绝，可能影响功能")
                    // 即使部分权限被拒绝，也继续申请Shizuku权限
                    shizukuManager.requestPermission()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            stopServer()
        }
    }

    private fun loadNativeLibrary(): Boolean {
        try {
            // 由于 app-k 依赖 server-k 模块，native 库会自动包含在 APK 中
            System.loadLibrary("server-k")
            Ln.d("[$TAG] 成功加载 libserver-k.so")
            return true
        } catch (e: UnsatisfiedLinkError) {
            Ln.e(
                "[$TAG] 加载 libserver-k.so 失败，这可能是因为库未正确打包到APK中",
                e
            )
            return false
        }
    }

    private fun useSharedMemory() {
        lifecycleScope.launch {
            val config = Config(Mode.SHARED_MEMORY, applicationInfo.nativeLibraryDir)
            config.memorySize = ScreenCaptureService.getScreenshotMemorySize()
            val serverStarted = startServer(config)
            if (serverStarted) {
                delay(1000)
                connect(config)
            }
        }
    }

    private fun useUnixSocket() {
        lifecycleScope.launch {
            val config = Config(Mode.UNIX_SOCKET, applicationInfo.nativeLibraryDir)
            config.socketName = "server-k.socket"
            val serverStarted = startServer(config)
            if (serverStarted) {
                delay(1000)
                connect(config)
            }
        }
    }

    private fun useTcpSocket() {
        lifecycleScope.launch {
            val config = Config(Mode.TCP_SOCKET, applicationInfo.nativeLibraryDir)
            config.tcpHost = "127.0.0.1"
            config.tcpPort = 7777
            val serverStarted = startServer(config)
            if (serverStarted) {
                delay(1000)
                connect(config)
            }
        }
    }

    private suspend fun startServer(config: Config): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val appCodePath = applicationInfo.sourceDir
                Log.d(TAG, "========== 启动 ${config.mode} 服务器 ==========")
                val launcherClass = Launcher::class.java.name
                val command = when (config.mode) {
                    Mode.SHARED_MEMORY -> {
                        "CLASSPATH=${appCodePath} app_process /system/bin $launcherClass --mode=SHARED_MEMORY --libPath=${config.libPath} > /data/local/tmp/server-k.log 2>&1 &"
                    }

                    Mode.UNIX_SOCKET -> {
                        "CLASSPATH=${appCodePath} app_process /system/bin $launcherClass --mode=UNIX_SOCKET --libPath=${config.libPath} --socketName=${config.socketName} > /data/local/tmp/server-k.log 2>&1 &"
                    }

                    Mode.TCP_SOCKET -> {
                        "CLASSPATH=${appCodePath} app_process /system/bin $launcherClass --mode=TCP_SOCKET --libPath=${config.libPath} --tcpPort=${config.tcpPort} --tcpHost=${config.tcpHost} > /data/local/tmp/server-k.log 2>&1 &"
                    }
                }
                val result = shizukuManager.execute(command)
                if (result.success) {
                    Log.d(TAG, "✅ 服务器启动成功")
                    Log.d(TAG, "输出: ${result.output}")
                    showToast("${config.mode} 服务器已启动")
                    return@withContext true
                } else {
                    Log.d(TAG, "❌ ${config.mode} 服务器启动失败")
                    Log.d(TAG, "错误: ${result.error}")
                    Log.d(TAG, "退出码: ${result.exitCode}")
                    showToast("服务器启动失败")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.d(TAG, "❌ 启动 ${config.mode} 服务器时出错: ${e.message}")
                Log.e(TAG, "启动服务器失败", e)
                showToast("启动失败: ${e.message}")
                return@withContext false
            }
        }

    private suspend fun stopServer() {
        try {
            // 使用pkill命令停止Launcher进程
            val killCommand = "pkill -f '${Launcher::class.java.name}'"
            val result = shizukuManager.execute(killCommand)
            if (result.success) {
                Log.d(TAG, "已停止之前的服务器进程")
            } else {
                Log.d(TAG, "停止服务器进程: ${result.error}")
            }
            updateButtonsState(false)
        } catch (e: Exception) {
            Log.d(TAG, "停止服务器进程时出错: ${e.message}")
        }
    }

    private fun connect(config: Config) {
        updateButtonsState(false)
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                Log.d(TAG, "========== 开始连接 ${config.mode} 模式 ==========")
                Client.disconnect()
                Client.initializeWithParams(Mode.getModeValue(config.mode), config.memorySize, config.socketName, config.tcpHost, config.tcpPort)
                val startTime = System.currentTimeMillis()
                val status = withContext(Dispatchers.IO) {
                    Client.connect()
                }
                Log.d(TAG, "   - 连接耗时: ${System.currentTimeMillis() - startTime}ms")
                Log.d(TAG, "   - 连接状态: $status")
                if(status == 0) {
                    Log.d(TAG, "✅ ${config.mode} 连接成功!")
                    showToast("${config.mode} 连接成功")
                }else{
                    Log.d(TAG, "❌ ${config.mode} 连接失败")
                    showToast("${config.mode} 连接失败")
                }
                Log.d(TAG, "========== ${config.mode} 连接完成 ==========\n")
            } catch (e: Exception) {
                Log.d(TAG, "❌ 连接 ${config.mode} 时出错: ${e.message}")
                Log.e(TAG, "连接失败", e)
                showToast("连接失败: ${e.message}")
            } finally {
                updateButtonsState(true)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun takeScreenshot() {
        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val startTime = System.currentTimeMillis()
                val response: Client.Response = withContext(Dispatchers.IO) {
                    Client.takeScreenshot()
                }
                val totalTime = System.currentTimeMillis() - startTime
                if (response.isSuccess) {
                    tvScreenshot.text =
                        "截图成功，耗时: ${totalTime}ms，服务器处理时间: ${response.processingTime}ms，文件大小: ${
                            formatFileSize(response.dataSize)
                        }"
                    response.data?.apply { displayScreenshot(this) }
                } else {
                    tvScreenshot.text = "截图失败: ${response.message}"
                    showToast("截图失败: ${response.message}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "❌ 截图异常: ${e.message}")
                Log.e(TAG, "截图失败", e)
                showToast("截图异常: ${e.message}")
            }
        }
    }

    private fun updateButtonsState(enabled: Boolean) {
        // 确保在主线程中更新按钮状态
        runOnUiThread {
            findViewById<Button>(R.id.btnScreenshot).isEnabled = enabled
        }
    }

    private fun showToast(message: String) {
        // 确保在主线程中显示Toast
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatFileSize(bytes: Int): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

    private fun displayScreenshot(data: ByteArray) {
        try {
            // 解析共享内存数据格式：前8字节是头部（宽度和高度），后面是RGBA像素数据
            if (data.size < 8) {
                Log.d(TAG, "数据格式错误：数据太小")
                return
            }

            // 解析宽高（小端序）
            val width = ((data[3].toInt() and 0xFF) shl 24) or
                    ((data[2].toInt() and 0xFF) shl 16) or
                    ((data[1].toInt() and 0xFF) shl 8) or
                    (data[0].toInt() and 0xFF)
            val height = ((data[7].toInt() and 0xFF) shl 24) or
                    ((data[6].toInt() and 0xFF) shl 16) or
                    ((data[5].toInt() and 0xFF) shl 8) or
                    (data[4].toInt() and 0xFF)

            // 验证数据大小
            val pixelData = data.copyOfRange(8, data.size)
            val expectedPixelDataSize = width * height * 4
            Log.d(TAG, "解析截图数据: ${width}x${height}, 像素数据大小: ${pixelData.size}, 期望: $expectedPixelDataSize")

            if (pixelData.size != expectedPixelDataSize) {
                Log.w(TAG, "像素数据大小不匹配，可能影响显示效果")
            }

            val bitmap = createBitmap(width, height)
            val pixels = IntArray(width * height)

            // 转换RGBA到ARGB
            for (i in pixels.indices) {
                val baseIndex = i * 4
                if (baseIndex + 3 < pixelData.size) {
                    val r = pixelData[baseIndex].toInt() and 0xFF
                    val g = pixelData[baseIndex + 1].toInt() and 0xFF
                    val b = pixelData[baseIndex + 2].toInt() and 0xFF
                    val a = pixelData[baseIndex + 3].toInt() and 0xFF
                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            Log.d(TAG, "✅ 显示截图成功: ${bitmap.width}x${bitmap.height}")
            ivScreenshot.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "❌ 显示截图失败: ${e.message}", e)
        }
    }
}
