package com.github.kirer.appk.test

import com.github.kirer.appk.server.ServerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * 测试基类
 * 提供通用的测试功能和状态管理
 */
abstract class BaseTest(val testName: String) {
    
    // 测试结果
    enum class TestResult {
        NOT_STARTED,
        RUNNING,
        PASSED,
        FAILED,
        SKIPPED
    }
    
    // 测试状态
    data class TestState(
        val result: TestResult = TestResult.NOT_STARTED,
        val message: String = "",
        val details: List<String> = emptyList(),
        val progress: Int = 0
    )
    
    private val _testState = MutableStateFlow(TestState())
    val testState: StateFlow<TestState> = _testState.asStateFlow()
    
    protected val serverManager = ServerManager.getInstance()
    
    /**
     * 执行测试
     */
    suspend fun runTest(): TestResult {
        return try {
            updateState(TestResult.RUNNING, "开始测试 $testName")
            
            // 检查服务器连接
            if (!serverManager.isServerConnected()) {
                updateState(TestResult.FAILED, "Server-K未连接")
                return TestResult.FAILED
            }
            
            // 执行具体测试
            val result = executeTest()
            
            val message = when (result) {
                TestResult.PASSED -> "$testName 测试通过"
                TestResult.FAILED -> "$testName 测试失败"
                TestResult.SKIPPED -> "$testName 测试跳过"
                else -> "$testName 测试完成"
            }
            
            updateState(result, message)
            result
        } catch (e: Exception) {
            Timber.e(e, "Test $testName failed with exception")
            updateState(TestResult.FAILED, "测试异常: ${e.message}")
            TestResult.FAILED
        }
    }
    
    /**
     * 子类实现具体的测试逻辑
     */
    protected abstract suspend fun executeTest(): TestResult
    
    /**
     * 更新测试状态
     */
    protected fun updateState(
        result: TestResult,
        message: String = "",
        details: List<String> = emptyList(),
        progress: Int = 0
    ) {
        _testState.value = TestState(result, message, details, progress)
        Timber.d("Test $testName: $result - $message")
    }
    
    /**
     * 添加详细信息
     */
    protected fun addDetail(detail: String) {
        val currentState = _testState.value
        val newDetails = currentState.details + detail
        _testState.value = currentState.copy(details = newDetails)
        Timber.d("Test $testName detail: $detail")
    }
    
    /**
     * 更新进度
     */
    protected fun updateProgress(progress: Int) {
        val currentState = _testState.value
        _testState.value = currentState.copy(progress = progress)
    }
    
    /**
     * 发送命令并验证响应
     */
    protected suspend fun sendCommandAndVerify(
        command: String,
        expectedResponse: String? = null,
        timeout: Long = 5000
    ): Boolean {
        return try {
            addDetail("发送命令: $command")
            
            val response = serverManager.sendCommand(command)
            
            if (response == null) {
                addDetail("命令响应为空")
                return false
            }
            
            addDetail("收到响应: $response")
            
            if (expectedResponse != null && !response.contains(expectedResponse)) {
                addDetail("响应不匹配，期望包含: $expectedResponse")
                return false
            }
            
            true
        } catch (e: Exception) {
            addDetail("命令执行异常: ${e.message}")
            false
        }
    }
    
    /**
     * 重置测试状态
     */
    fun reset() {
        _testState.value = TestState()
    }
}
