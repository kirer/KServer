package com.github.kirer.app_server

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.kirer.app_server.databinding.ActivityMainBinding
import com.github.kirer.server.Ashmem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var shizukuManager: ShizukuManager
    private lateinit var kServerManager: KServerManager

    // 计数器相关
    private var titleCounter = 0
    private var titleUpdateHandler: Handler? = null
    private var titleUpdateRunnable: Runnable? = null

    private val shizukuPermissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    updateShizukuStatus()
                    showToast("Shizuku权限已授予")
                } else {
                    showToast("Shizuku权限被拒绝")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        System.loadLibrary("ashmem")
        // 启动标题计数器
        startTitleCounter()
        // 初始化管理器
        shizukuManager = ShizukuManager(this)
        kServerManager = KServerManager(this)
        shizukuManager.initialize { status ->
            runOnUiThread {
                viewModel.updateShizukuStatus(status)
                addLog("Shizuku状态更新: $status")
            }
        }
        // 处理系统窗口插入，避免标题栏遮盖内容
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left + 16,
                systemBars.top + 16,
                systemBars.right + 16,
                systemBars.bottom + 16
            )
            insets
        }
        setupUI()
        setupObservers()
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)

        val libPath = applicationInfo.nativeLibraryDir
        Log.d("KKK", "Native lib path: " + libPath)
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        shizukuManager.cleanup()
        stopTitleCounter()
    }

    /**
     * 启动标题计数器
     */
    private fun startTitleCounter() {
        titleUpdateHandler = Handler(Looper.getMainLooper())
        titleUpdateRunnable = object : Runnable {
            override fun run() {
                titleCounter++
                title = "KServer Client - $titleCounter"
                titleUpdateHandler?.postDelayed(this, 1000) // 每秒更新一次
            }
        }
        titleUpdateRunnable?.let { titleUpdateHandler?.post(it) }
    }

    /**
     * 停止标题计数器
     */
    private fun stopTitleCounter() {
        titleUpdateRunnable?.let { titleUpdateHandler?.removeCallbacks(it) }
        titleUpdateHandler = null
        titleUpdateRunnable = null
    }

    private fun setupUI() {
        // 请求Shizuku权限
        binding.btnRequestPermission.setOnClickListener {
            requestShizukuPermission()
        }

        // 启动/停止KServer
        binding.btnStartKServer.setOnClickListener {
            if (viewModel.isKServerRunning.value == true) {
                stopKServer()
            } else {
                startKServer()
            }
        }

        // 截图按钮
        binding.btnScreenshot.setOnClickListener {
            takeScreenshot()
        }

        // 清空日志
        binding.btnClearLog.setOnClickListener {
            binding.tvLog.text = ""
        }
    }

    private fun setupObservers() {
        // 观察Shizuku状态
        viewModel.shizukuStatus.observe(this) { status ->
            updateShizukuStatusUI(status)
        }

        // 观察KServer状态
        viewModel.isKServerRunning.observe(this) { isRunning ->
            updateKServerStatusUI(isRunning)
        }

        // 观察连接状态
        viewModel.connectionStatus.observe(this) { status ->
            updateConnectionStatusUI(status)
        }

        // 观察日志
        viewModel.logMessages.observe(this) { messages ->
            binding.tvLog.text = messages.joinToString("\n")
            // 自动滚动到底部
            binding.tvLog.post {
                val scrollView = binding.tvLog.parent as? android.widget.ScrollView
                scrollView?.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
    }

    private fun updateShizukuStatus() {
        try {
            val status = shizukuManager.checkShizukuStatus()
            viewModel.updateShizukuStatus(status)
            addLog("Shizuku状态检查完成: $status")
        } catch (e: Exception) {
            addLog("Shizuku状态检查失败: ${e.message}")
            // 如果检查失败，假设是未运行状态
            viewModel.updateShizukuStatus(ShizukuManager.ShizukuStatus.NOT_RUNNING)
        }
    }

    private fun updateShizukuStatusUI(status: ShizukuManager.ShizukuStatus) {
        val (text, color) = when (status) {
            ShizukuManager.ShizukuStatus.NOT_INSTALLED -> "Shizuku: 未安装" to ContextCompat.getColor(
                this, android.R.color.holo_red_dark
            )

            ShizukuManager.ShizukuStatus.NOT_RUNNING -> "Shizuku: 未运行" to ContextCompat.getColor(
                this, android.R.color.holo_orange_dark
            )

            ShizukuManager.ShizukuStatus.PERMISSION_DENIED -> "Shizuku: 权限被拒绝" to ContextCompat.getColor(
                this, android.R.color.holo_red_dark
            )

            ShizukuManager.ShizukuStatus.PERMISSION_GRANTED -> "Shizuku: 权限已授予" to ContextCompat.getColor(
                this, android.R.color.holo_green_dark
            )
        }

        binding.tvShizukuStatus.text = text
        binding.tvShizukuStatus.setTextColor(color)

        // 更新按钮状态
        binding.btnRequestPermission.isEnabled =
            status == ShizukuManager.ShizukuStatus.PERMISSION_DENIED
        binding.btnStartKServer.isEnabled =
            status == ShizukuManager.ShizukuStatus.PERMISSION_GRANTED
    }

    private fun updateKServerStatusUI(isRunning: Boolean) {
        val (text, color) = if (isRunning) {
            "KServer: 运行中" to ContextCompat.getColor(this, android.R.color.holo_green_dark)
        } else {
            "KServer: 未启动" to ContextCompat.getColor(this, android.R.color.holo_red_dark)
        }
        binding.tvKServerStatus.text = text
        binding.tvKServerStatus.setTextColor(color)
        // 更新按钮文本
        binding.btnStartKServer.text = if (isRunning) {
            getString(R.string.stop_kserver)
        } else {
            getString(R.string.start_kserver)
        }
        // 更新截图按钮状态
        updateScreenshotButtonsState()
    }

    private fun updateConnectionStatusUI(status: KServerClient.ConnectionStatus) {
        val (text, color) = when (status) {
            KServerClient.ConnectionStatus.DISCONNECTED -> "连接: 未连接" to ContextCompat.getColor(
                this, android.R.color.holo_red_dark
            )

            KServerClient.ConnectionStatus.CONNECTING -> "连接: 连接中..." to ContextCompat.getColor(
                this, android.R.color.holo_orange_dark
            )

            KServerClient.ConnectionStatus.CONNECTED -> "连接: 已连接" to ContextCompat.getColor(
                this, android.R.color.holo_green_dark
            )

            KServerClient.ConnectionStatus.ERROR -> "连接: 错误" to ContextCompat.getColor(
                this, android.R.color.holo_red_dark
            )
        }

        binding.tvConnectionStatus.text = text
        binding.tvConnectionStatus.setTextColor(color)

        // 更新截图按钮状态
        updateScreenshotButtonsState()
    }

    /**
     * 更新截图按钮状态（简化版：只有RAW格式按钮）
     */
    private fun updateScreenshotButtonsState() {
        val isKServerRunning = viewModel.isKServerRunning.value == true
        val isConnected = viewModel.connectionStatus.value == KServerClient.ConnectionStatus.CONNECTED
        val screenshotEnabled = isKServerRunning && isConnected
        binding.btnScreenshot.isEnabled = screenshotEnabled
    }

    private fun requestShizukuPermission() {
        addLog("请求Shizuku权限...")
        val hasPermission = shizukuManager.requestShizukuPermission()
        if (hasPermission) {
            addLog("权限已授予")
            updateShizukuStatus()
        } else {
            addLog("等待用户授权...")
        }
    }

    private fun startKServer() {
        addLog("启动KServer...")
        binding.btnStartKServer.isEnabled = false

        lifecycleScope.launch {
            try {
                addLog("启动KServer...")
                val success = kServerManager.startServer(shizukuManager, applicationInfo.nativeLibraryDir)
                if (success) {
                    addLog("KServer启动成功")
                    viewModel.updateKServerStatus(true)
                    delay(1000)
                    checkConnection()
                } else {
                    addLog("KServer启动失败")
                    showToast("KServer启动失败")
                }
            } catch (e: Exception) {
                addLog("启动KServer时出错: ${e.message}")
                showToast("启动KServer时出错")
            } finally {
                binding.btnStartKServer.isEnabled = true
            }
        }
    }

    private fun stopKServer() {
        addLog("停止KServer...")
        binding.btnStartKServer.isEnabled = false

        lifecycleScope.launch {
            try {
                val success = kServerManager.stopServer(shizukuManager)
                if (success) {
                    addLog("KServer已停止")
                    viewModel.updateKServerStatus(false)
                    viewModel.updateConnectionStatus(KServerClient.ConnectionStatus.DISCONNECTED)
                } else {
                    addLog("停止KServer失败")
                }
            } catch (e: Exception) {
                addLog("停止KServer时出错: ${e.message}")
            } finally {
                binding.btnStartKServer.isEnabled = true
            }
        }
    }

    private fun checkConnection() {
        lifecycleScope.launch {
            // 使用KServerManager的全局连接
            addLog("连接到KServer共享内存文件...")
            val connected = kServerManager.connectToServer()

            val status = if (connected) {
                KServerClient.ConnectionStatus.CONNECTED
            } else {
                KServerClient.ConnectionStatus.ERROR
            }

            viewModel.updateConnectionStatus(status)

            if (connected) {
                addLog("连接成功")
            } else {
                addLog("连接失败")
            }
        }
    }

    private fun takeScreenshot() {
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            val screenshotData = kServerManager.testScreenshot()
            val totalTime = System.currentTimeMillis() - startTime
            if (screenshotData != null) {
                addLog("大小: ${formatFileSize(screenshotData.size)}，耗时: ${totalTime}ms")
                displayScreenshot(screenshotData)
            } else {
                addLog("截图失败")
                showToast("截图失败")
            }
        }
    }

    @SuppressLint("UseKtx", "SetTextI18n")
    private fun displayScreenshot(data: ByteArray) {
        try {
            // 解析共享内存数据格式：前8字节是头部（宽度和高度），后面是RGBA像素数据
            if (data.size < 8) {
                addLog("数据格式错误：数据太小")
                return
            }

            // 读取头部信息（小端格式）
            val width = ((data[3].toInt() and 0xFF) shl 24) or
                    ((data[2].toInt() and 0xFF) shl 16) or
                    ((data[1].toInt() and 0xFF) shl 8) or
                    (data[0].toInt() and 0xFF)

            val height = ((data[7].toInt() and 0xFF) shl 24) or
                    ((data[6].toInt() and 0xFF) shl 16) or
                    ((data[5].toInt() and 0xFF) shl 8) or
                    (data[4].toInt() and 0xFF)

            addLog("解析图像尺寸: ${width}x${height}")

            // 检查数据大小是否合理
            val expectedPixelDataSize = width * height * 4 // RGBA_8888
            val actualPixelDataSize = data.size - 8

            if (actualPixelDataSize < expectedPixelDataSize) {
                addLog("像素数据不完整: 期望${expectedPixelDataSize}字节，实际${actualPixelDataSize}字节")
            }

            // 创建Bitmap并设置像素数据
            val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)

            // 将RGBA数据转换为ARGB格式并设置到bitmap
            val pixelData = data.copyOfRange(8, data.size)
            val pixels = IntArray(width * height)

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

            binding.ivScreenshot.setImageBitmap(bitmap)
            val sizeText = formatFileSize(data.size)
            binding.tvImageInfo.text = "大小: $sizeText, 分辨率: ${width}x${height}"

        } catch (e: Exception) {
            addLog("显示截图失败: ${e.message}")
            Log.e("MainActivity", "显示截图失败", e)
        }
    }

    private fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        viewModel.addLog("[$timestamp] $message")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun formatFileSize(bytes: Int): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }

}
