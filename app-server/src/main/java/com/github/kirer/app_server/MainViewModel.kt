package com.github.kirer.app_server

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * 主界面ViewModel
 * 管理应用状态和数据
 */
class MainViewModel : ViewModel() {
    
    // Shizuku状态
    private val _shizukuStatus = MutableLiveData<ShizukuManager.ShizukuStatus>()
    val shizukuStatus: LiveData<ShizukuManager.ShizukuStatus> = _shizukuStatus
    
    // KServer运行状态
    private val _isKServerRunning = MutableLiveData<Boolean>(false)
    val isKServerRunning: LiveData<Boolean> = _isKServerRunning
    
    // 连接状态
    private val _connectionStatus = MutableLiveData<KServerClient.ConnectionStatus>(KServerClient.ConnectionStatus.DISCONNECTED)
    val connectionStatus: LiveData<KServerClient.ConnectionStatus> = _connectionStatus
    
    // 日志消息
    private val _logMessages = MutableLiveData<MutableList<String>>(mutableListOf())
    val logMessages: LiveData<MutableList<String>> = _logMessages
    
    // 性能统计
    private val _performanceStats = MutableLiveData<List<KServerClient.PerformanceStats>>(emptyList())
    val performanceStats: LiveData<List<KServerClient.PerformanceStats>> = _performanceStats
    
    /**
     * 更新Shizuku状态
     */
    fun updateShizukuStatus(status: ShizukuManager.ShizukuStatus) {
        _shizukuStatus.value = status
    }
    
    /**
     * 更新KServer运行状态
     */
    fun updateKServerStatus(isRunning: Boolean) {
        _isKServerRunning.value = isRunning
    }
    
    /**
     * 更新连接状态
     */
    fun updateConnectionStatus(status: KServerClient.ConnectionStatus) {
        _connectionStatus.value = status
    }
    
    /**
     * 添加日志消息
     */
    fun addLog(message: String) {
        val currentLogs = _logMessages.value ?: mutableListOf()
        currentLogs.add(message)
        
        // 限制日志数量，避免内存泄漏
        if (currentLogs.size > 100) {
            currentLogs.removeAt(0)
        }
        
        _logMessages.value = currentLogs
    }
    
    /**
     * 清空日志
     */
    fun clearLogs() {
        _logMessages.value = mutableListOf()
    }
    
    /**
     * 添加性能统计
     */
    fun addPerformanceStats(stats: KServerClient.PerformanceStats) {
        val currentStats = _performanceStats.value?.toMutableList() ?: mutableListOf()
        currentStats.add(stats)
        
        // 限制统计数量
        if (currentStats.size > 50) {
            currentStats.removeAt(0)
        }
        
        _performanceStats.value = currentStats
    }
    
    /**
     * 获取平均性能统计
     */
    fun getAveragePerformanceStats(): Map<String, Double> {
        val stats = _performanceStats.value ?: return emptyMap()
        if (stats.isEmpty()) return emptyMap()
        
        val grouped = stats.groupBy { it.commandType }
        return grouped.mapValues { (_, statsList) ->
            statsList.map { it.totalTime }.average()
        }
    }
    
    /**
     * 检查是否可以执行截图操作
     */
    fun canTakeScreenshot(): Boolean {
        return _shizukuStatus.value == ShizukuManager.ShizukuStatus.PERMISSION_GRANTED &&
                _isKServerRunning.value == true &&
                _connectionStatus.value == KServerClient.ConnectionStatus.CONNECTED
    }
    
    /**
     * 获取当前状态摘要
     */
    fun getStatusSummary(): String {
        val shizuku = when (_shizukuStatus.value) {
            ShizukuManager.ShizukuStatus.PERMISSION_GRANTED -> "✓"
            else -> "✗"
        }
        
        val kserver = if (_isKServerRunning.value == true) "✓" else "✗"
        val connection = if (_connectionStatus.value == KServerClient.ConnectionStatus.CONNECTED) "✓" else "✗"
        
        return "Shizuku:$shizuku KServer:$kserver 连接:$connection"
    }
}
