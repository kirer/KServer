package com.github.kirer.appk.shizuku

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import timber.log.Timber

/**
 * Shizuku服务管理器
 * 负责Shizuku服务的连接、权限管理和状态监控
 */
class ShizukuManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: ShizukuManager? = null
        
        fun getInstance(): ShizukuManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ShizukuManager().also { INSTANCE = it }
            }
        }
    }
    
    // Shizuku状态
    enum class ShizukuState {
        UNKNOWN,
        AVAILABLE,
        NOT_AVAILABLE,
        PERMISSION_GRANTED,
        PERMISSION_DENIED
    }
    
    private val _shizukuState = MutableStateFlow(ShizukuState.UNKNOWN)
    val shizukuState: StateFlow<ShizukuState> = _shizukuState.asStateFlow()
    
    private var isInitialized = false
    
    // Shizuku状态监听器
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Timber.d("Shizuku binder received")
        checkShizukuState()
    }
    
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Timber.d("Shizuku binder dead")
        _shizukuState.value = ShizukuState.NOT_AVAILABLE
    }
    
    private val requestPermissionResultListener = 
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            Timber.d("Shizuku permission result: code=$requestCode, result=$grantResult")
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            _shizukuState.value = if (granted) {
                ShizukuState.PERMISSION_GRANTED
            } else {
                ShizukuState.PERMISSION_DENIED
            }
        }
    
    /**
     * 初始化Shizuku管理器
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        
        try {
            // 添加监听器
            Shizuku.addBinderReceivedListener(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            
            isInitialized = true
            checkShizukuState()
            
            Timber.d("Shizuku manager initialized")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize Shizuku manager")
            _shizukuState.value = ShizukuState.NOT_AVAILABLE
        }
    }
    
    /**
     * 检查Shizuku状态
     */
    private fun checkShizukuState() {
        try {
            when {
                !Shizuku.pingBinder() -> {
                    _shizukuState.value = ShizukuState.NOT_AVAILABLE
                }
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> {
                    _shizukuState.value = ShizukuState.PERMISSION_GRANTED
                }
                Shizuku.shouldShowRequestPermissionRationale() -> {
                    _shizukuState.value = ShizukuState.PERMISSION_DENIED
                }
                else -> {
                    _shizukuState.value = ShizukuState.AVAILABLE
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check Shizuku state")
            _shizukuState.value = ShizukuState.NOT_AVAILABLE
        }
    }
    
    /**
     * 请求Shizuku权限
     */
    fun requestPermission() {
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(1001)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to request Shizuku permission")
        }
    }
    
    /**
     * 检查是否有Shizuku权限
     */
    fun hasPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Timber.e(e, "Failed to check Shizuku permission")
            false
        }
    }
    
    /**
     * 执行Shell命令
     */
    suspend fun executeShellCommand(command: String): String {
        return try {
            if (!hasPermission()) {
                throw SecurityException("Shizuku permission not granted")
            }

            // 使用反射调用newProcess方法
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true

            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val result = process.inputStream.bufferedReader().readText()
            process.waitFor()
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to execute shell command: $command")
            throw e
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        if (!isInitialized) return
        
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
            
            isInitialized = false
            Timber.d("Shizuku manager cleaned up")
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup Shizuku manager")
        }
    }
}
