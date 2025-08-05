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
    private val kServerClient = KServerClient()

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
        // 添加Shizuku权限监听器
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

    private fun checkKServerStatus() {
        lifecycleScope.launch {
            try {
                val isRunning = kServerManager.isKServerRunning()
                viewModel.updateKServerStatus(isRunning)

                if (isRunning) {
                    addLog("KServer正在运行")
                    checkConnection()
                } else {
                    addLog("KServer未运行")
                }
            } catch (e: Exception) {
                addLog("检查KServer状态时出错: ${e.message}")
            }
        }
    }

    private fun checkConnection() {
        lifecycleScope.launch {
            val status = kServerClient.checkConnection()
            viewModel.updateConnectionStatus(status)

            if (status == KServerClient.ConnectionStatus.CONNECTED) {
                addLog("成功连接到KServer")
            } else {
                addLog("无法连接到KServer")
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
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        binding.ivScreenshot.setImageBitmap(bitmap)
        val sizeText = formatFileSize(data.size)
        binding.tvImageInfo.text = "大小: $sizeText, 分辨率: ${bitmap.width}x${bitmap.height}"
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
