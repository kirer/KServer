package com.github.kirer.appk

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kirer.appk.server.ServerManager
import com.github.kirer.appk.shizuku.ShizukuManager
import com.github.kirer.appk.test.*
import com.github.kirer.appk.ui.TestResultAdapter

import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * 主Activity
 * 提供Server-K功能测试界面
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnStartServer: Button
    private lateinit var btnStopServer: Button
    private lateinit var btnRunAllTests: Button
    private lateinit var btnClearLog: Button
    private lateinit var rvTestResults: RecyclerView
    
    private lateinit var testResultAdapter: TestResultAdapter
    private lateinit var shizukuManager: ShizukuManager
    private lateinit var serverManager: ServerManager
    
    // 测试实例
    private val screenshotTest = ScreenshotTest()
    private val touchTest = TouchTest()
    private val accessibilityTest = AccessibilityTest()
    private val utilsTest = UtilsTest()
    private val pluginTest = PluginTest()
    
    private val allTests = listOf(
        screenshotTest, touchTest, accessibilityTest, utilsTest, pluginTest
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        initManagers()
        setupObservers()
        setupClickListeners()
        
        logMessage("App-K 测试器已启动")
        checkShizukuStatus()
    }
    
    private fun initViews() {
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus)
        tvLog = findViewById(R.id.tvLog)
        btnStartServer = findViewById(R.id.btnStartServer)
        btnStopServer = findViewById(R.id.btnStopServer)
        btnRunAllTests = findViewById(R.id.btnRunAllTests)
        btnClearLog = findViewById(R.id.btnClearLog)
        rvTestResults = findViewById(R.id.rvTestResults)
        
        // 设置RecyclerView
        testResultAdapter = TestResultAdapter()
        rvTestResults.layoutManager = LinearLayoutManager(this)
        rvTestResults.adapter = testResultAdapter
    }
    
    private fun initManagers() {
        shizukuManager = ShizukuManager.getInstance()
        serverManager = ServerManager.getInstance()
        
        shizukuManager.initialize(this)
    }
    
    private fun setupObservers() {
        // 观察Shizuku状态
        lifecycleScope.launch {
            shizukuManager.shizukuState.collect { state ->
                when (state) {
                    ShizukuManager.ShizukuState.PERMISSION_GRANTED -> {
                        logMessage("Shizuku权限已获取")
                    }
                    ShizukuManager.ShizukuState.PERMISSION_DENIED -> {
                        logMessage("Shizuku权限被拒绝")
                    }
                    ShizukuManager.ShizukuState.NOT_AVAILABLE -> {
                        logMessage("Shizuku服务不可用")
                    }
                    else -> {
                        logMessage("Shizuku状态: $state")
                    }
                }
            }
        }
        
        // 观察服务器状态
        lifecycleScope.launch {
            serverManager.serverState.collect { state ->
                runOnUiThread {
                    when (state) {
                        ServerManager.ServerState.CONNECTED -> {
                            tvConnectionStatus.text = getString(R.string.connected)
                            tvConnectionStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.success_green))
                            btnStartServer.isEnabled = false
                            btnStopServer.isEnabled = true
                            btnRunAllTests.isEnabled = true
                            logMessage("Server-K已连接")
                        }
                        ServerManager.ServerState.CONNECTING -> {
                            tvConnectionStatus.text = getString(R.string.connecting)
                            tvConnectionStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.warning_orange))
                            btnStartServer.isEnabled = false
                            btnStopServer.isEnabled = false
                            btnRunAllTests.isEnabled = false
                            logMessage("正在连接Server-K...")
                        }
                        ServerManager.ServerState.DISCONNECTED -> {
                            tvConnectionStatus.text = getString(R.string.disconnected)
                            tvConnectionStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.error_red))
                            btnStartServer.isEnabled = true
                            btnStopServer.isEnabled = false
                            btnRunAllTests.isEnabled = false
                            logMessage("Server-K已断开")
                        }
                        ServerManager.ServerState.ERROR -> {
                            tvConnectionStatus.text = "错误"
                            tvConnectionStatus.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.error_red))
                            btnStartServer.isEnabled = true
                            btnStopServer.isEnabled = true
                            btnRunAllTests.isEnabled = false
                            logMessage("Server-K连接错误")
                        }
                    }
                }
            }
        }
        
        // 观察测试状态
        allTests.forEach { test ->
            lifecycleScope.launch {
                test.testState.collect { state ->
                    runOnUiThread {
                        testResultAdapter.updateTestResult(test.testName, state)
                    }
                }
            }
        }
    }
    
    private fun setupClickListeners() {
        btnStartServer.setOnClickListener {
            startServer()
        }
        
        btnStopServer.setOnClickListener {
            stopServer()
        }
        
        btnRunAllTests.setOnClickListener {
            runAllTests()
        }
        
        btnClearLog.setOnClickListener {
            clearLog()
        }
        
        // 单项测试按钮
        findViewById<Button>(R.id.btnTestScreenshot).setOnClickListener {
            runSingleTest(screenshotTest)
        }

        // 截图测试按钮长按跳转到截图页面
        findViewById<Button>(R.id.btnTestScreenshot).setOnLongClickListener {
            val intent = Intent(this, com.github.kirer.appk.ui.ScreenshotDisplayActivity::class.java)
            startActivity(intent)
            true
        }
        
        findViewById<Button>(R.id.btnTestTouch).setOnClickListener {
            runSingleTest(touchTest)
        }
        
        findViewById<Button>(R.id.btnTestAccessibility).setOnClickListener {
            runSingleTest(accessibilityTest)
        }
        
        findViewById<Button>(R.id.btnTestUtils).setOnClickListener {
            runSingleTest(utilsTest)
        }
        
        findViewById<Button>(R.id.btnTestPlugin).setOnClickListener {
            runSingleTest(pluginTest)
        }
    }
    
    private fun checkShizukuStatus() {
        if (!shizukuManager.hasPermission()) {
            logMessage("请先授予Shizuku权限")
            shizukuManager.requestPermission()
        }
    }
    
    private fun startServer() {
        lifecycleScope.launch {
            try {
                logMessage("正在启动Server-K...")
                val success = serverManager.startServer()
                if (success) {
                    logMessage("Server-K启动成功")
                } else {
                    logMessage("Server-K启动失败")
                }
            } catch (e: Exception) {
                logMessage("启动Server-K异常: ${e.message}")
                Timber.e(e, "Failed to start server")
            }
        }
    }
    
    private fun stopServer() {
        lifecycleScope.launch {
            try {
                logMessage("正在停止Server-K...")
                serverManager.stopServer()
                logMessage("Server-K已停止")
            } catch (e: Exception) {
                logMessage("停止Server-K异常: ${e.message}")
                Timber.e(e, "Failed to stop server")
            }
        }
    }
    
    private fun runAllTests() {
        lifecycleScope.launch {
            logMessage("开始运行所有测试...")
            
            for (test in allTests) {
                try {
                    val result = test.runTest()
                    logMessage("${test.testName} 测试完成: $result")
                } catch (e: Exception) {
                    logMessage("${test.testName} 测试异常: ${e.message}")
                    Timber.e(e, "Test ${test.testName} failed")
                }
            }
            
            logMessage("所有测试完成")
        }
    }
    
    private fun runSingleTest(test: BaseTest) {
        lifecycleScope.launch {
            try {
                logMessage("开始运行 ${test.testName} 测试...")
                val result = test.runTest()
                logMessage("${test.testName} 测试完成: $result")
            } catch (e: Exception) {
                logMessage("${test.testName} 测试异常: ${e.message}")
                Timber.e(e, "Test ${test.testName} failed")
            }
        }
    }
    
    private fun clearLog() {
        tvLog.text = "日志已清空\n"
        testResultAdapter.clearResults()
    }
    
    private fun logMessage(message: String) {
        runOnUiThread {
            val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
            tvLog.append("[$timestamp] $message\n")
            
            // 自动滚动到底部
            val scrollView = tvLog.parent as? android.widget.ScrollView
            scrollView?.post {
                scrollView.fullScroll(android.view.View.FOCUS_DOWN)
            }
        }
        
        Timber.d(message)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        shizukuManager.cleanup()
        serverManager.cleanup()
    }
}
