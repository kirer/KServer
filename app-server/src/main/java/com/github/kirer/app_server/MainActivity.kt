package com.github.kirer.app_server

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.github.kirer.app_server.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.*

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
    
    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
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
        // 初始状态检查（延迟执行，等待binder连接）
        lifecycleScope.launch {
            kotlinx.coroutines.delay(1000) // 等待1秒让binder连接
            try {
                updateShizukuStatus()
                checkKServerStatus()
            } catch (e: Exception) {
                addLog("初始状态检查失败: ${e.message}")
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        shizukuManager.cleanup()
        kServerManager.stopHealthCheck()
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

        // 长按权限按钮进行权限测试
        binding.btnRequestPermission.setOnLongClickListener {
            testShizukuPermission()
            true
        }
        
        // 启动/停止KServer
        binding.btnStartKServer.setOnClickListener {
            if (viewModel.isKServerRunning.value == true) {
                stopKServer()
            } else {
                startKServer()
            }
        }
        
        // 截图按钮（简化版：只支持RAW格式）
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
            ShizukuManager.ShizukuStatus.NOT_INSTALLED -> 
                "Shizuku: 未安装" to ContextCompat.getColor(this, android.R.color.holo_red_dark)
            ShizukuManager.ShizukuStatus.NOT_RUNNING -> 
                "Shizuku: 未运行" to ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            ShizukuManager.ShizukuStatus.PERMISSION_DENIED -> 
                "Shizuku: 权限被拒绝" to ContextCompat.getColor(this, android.R.color.holo_red_dark)
            ShizukuManager.ShizukuStatus.PERMISSION_GRANTED -> 
                "Shizuku: 权限已授予" to ContextCompat.getColor(this, android.R.color.holo_green_dark)
        }
        
        binding.tvShizukuStatus.text = text
        binding.tvShizukuStatus.setTextColor(color)
        
        // 更新按钮状态
        binding.btnRequestPermission.isEnabled = status == ShizukuManager.ShizukuStatus.PERMISSION_DENIED
        binding.btnStartKServer.isEnabled = status == ShizukuManager.ShizukuStatus.PERMISSION_GRANTED
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
            KServerClient.ConnectionStatus.DISCONNECTED ->
                "连接: 未连接" to ContextCompat.getColor(this, android.R.color.holo_red_dark)
            KServerClient.ConnectionStatus.CONNECTING ->
                "连接: 连接中..." to ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            KServerClient.ConnectionStatus.CONNECTED ->
                "连接: 已连接" to ContextCompat.getColor(this, android.R.color.holo_green_dark)
            KServerClient.ConnectionStatus.ERROR ->
                "连接: 错误" to ContextCompat.getColor(this, android.R.color.holo_red_dark)
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
        val isKServerRunning = viewModel.isKServerRunning.value ?: false
        val isConnected = viewModel.connectionStatus.value == KServerClient.ConnectionStatus.CONNECTED
        val screenshotEnabled = isKServerRunning && isConnected

        binding.btnScreenshot.isEnabled = screenshotEnabled
        // btnBenchmark 已删除，简化版不需要

        // 添加调试日志
        addLog("按钮状态更新: KServer运行=$isKServerRunning, 连接=$isConnected, 按钮启用=$screenshotEnabled")
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

    private fun testShizukuPermission() {
        addLog("测试Shizuku权限...")

        lifecycleScope.launch {
            try {
                // 尝试执行一个简单的命令
                val result = shizukuManager.executeShellCommand("echo 'Shizuku test'")
                if (result.success) {
                    addLog("Shizuku权限测试成功: ${result.output}")
                    viewModel.updateShizukuStatus(ShizukuManager.ShizukuStatus.PERMISSION_GRANTED)
                } else {
                    addLog("Shizuku权限测试失败: ${result.error}")
                }
            } catch (e: Exception) {
                addLog("Shizuku权限测试异常: ${e.message}")
            }
        }
    }
    
    private fun startKServer() {
        addLog("启动KServer...")
        binding.btnStartKServer.isEnabled = false

        lifecycleScope.launch {
            try {
                // 检查APK访问权限
                addLog("检查APK访问权限...")
                val accessSuccess = kServerManager.checkApkAccess(shizukuManager)
                if (!accessSuccess) {
                    addLog("APK访问检查失败")
                    showToast("APK访问检查失败")
                    return@launch
                }

                // 通过Shizuku启动KServer服务
                addLog("通过Shizuku启动KServer...")
                val success = kServerManager.startKServerViaShizuku(shizukuManager)
                if (success) {
                    addLog("KServer启动成功")
                    viewModel.updateKServerStatus(true)
                    // 等待一下然后检查连接
                    kotlinx.coroutines.delay(3000)
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
                val success = kServerManager.stopKServerViaShizuku(shizukuManager)
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
        addLog("开始截图（简化版：只支持RAW格式）...")

        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                // 直接使用KServerManager进行截图（简化版：只支持RAW格式）
                val screenshotData = kServerManager.testScreenshot()
                val totalTime = System.currentTimeMillis() - startTime
                if (screenshotData != null) {
                    addLog("截图成功，大小: ${screenshotData.size} bytes")
                    addLog("总耗时: ${totalTime}ms")
                    // 显示截图
                    displayScreenshot(screenshotData, screenshotData.size)
                } else {
                    addLog("截图失败")
                    showToast("截图失败")
                }
            } catch (e: Exception) {
                addLog("截图时出错: ${e.message}")
                showToast("截图时出错")
            }
        }
    }
    
    @SuppressLint("UseKtx", "SetTextI18n")
    private fun displayScreenshot(data: ByteArray, size: Int) {
        try {
            // server-adb-shell 返回的是 RAW RGBA 格式数据
            val pixelCount = data.size / 4 // RGBA = 4 bytes per pixel
            addLog("RAW RGBA数据，大小: ${data.size} bytes，像素数: $pixelCount")

            // 尝试常见分辨率来匹配像素数
            val possibleResolutions = listOf(
                Pair(1080, 2400), Pair(1080, 2340), Pair(1080, 1920),
                Pair(1440, 3200), Pair(1440, 2960), Pair(1440, 2560),
                Pair(720, 1600), Pair(720, 1520), Pair(720, 1280),
                Pair(1200, 2640), Pair(828, 1792), Pair(750, 1334)
            )

            var bitmap: Bitmap? = null
            for ((w, h) in possibleResolutions) {
                if (w * h == pixelCount) {
                    addLog("匹配分辨率: ${w}x${h}")
                    try {
                        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val buffer = java.nio.ByteBuffer.wrap(data)
                        bitmap.copyPixelsFromBuffer(buffer)
                        addLog("RAW RGBA解码成功: ${w}x${h}")
                        break
                    } catch (e: Exception) {
                        addLog("尝试分辨率 ${w}x${h} 失败: ${e.message}")
                        bitmap = null
                    }
                }
            }

            if (bitmap != null) {
                binding.ivScreenshot.setImageBitmap(bitmap)
                val sizeText = formatFileSize(size)
                binding.tvImageInfo.text = "格式: RAW RGBA, 大小: $sizeText, 分辨率: ${bitmap.width}x${bitmap.height}"
                addLog("截图显示成功: ${bitmap.width}x${bitmap.height}, $sizeText")
            } else {
                addLog("无法找到匹配的分辨率，像素数: $pixelCount")
                // 显示可能的分辨率组合
                val sqrt = kotlin.math.sqrt(pixelCount.toDouble()).toInt()
                addLog("可能的分辨率组合:")
                for (w in (sqrt-50)..(sqrt+50)) {
                    if (w > 0 && pixelCount % w == 0) {
                        val h = pixelCount / w
                        if (h > 0 && w <= h) { // 只显示竖屏比例
                            addLog("  ${w}x${h}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            addLog("显示截图时出错: ${e.message}")
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
