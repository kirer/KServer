package com.github.kirer.app_server

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Shizuku权限管理器
 * 负责Shizuku权限检查、请求和Shell命令执行
 */
class ShizukuManager(private val context: Context) {

    companion object {
        private const val TAG = "ShizukuManager"
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val shizukuShell = ShizukuShell(applicationScope)

    // 状态回调
    private var onStatusChanged: ((ShizukuStatus) -> Unit)? = null

    // Shizuku监听器
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Log.d(TAG, "Shizuku binder received")
        val status = checkShizukuStatus()
        onStatusChanged?.invoke(status)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Log.d(TAG, "Shizuku binder dead")
        onStatusChanged?.invoke(ShizukuStatus.NOT_RUNNING)
    }

    /**
     * 初始化Shizuku监听器
     */
    fun initialize(statusCallback: (ShizukuStatus) -> Unit) {
        try {
            onStatusChanged = statusCallback
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Log.d(TAG, "Shizuku监听器已添加")
        } catch (e: Exception) {
            Log.e(TAG, "添加Shizuku监听器失败", e)
        }
    }

    /**
     * 清理Shizuku监听器
     */
    fun cleanup() {
        try {
            onStatusChanged = null
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Log.d(TAG, "Shizuku监听器已移除")
        } catch (e: Exception) {
            Log.e(TAG, "移除Shizuku监听器失败", e)
        }
    }

    /**
     * Shizuku状态枚举
     */
    enum class ShizukuStatus {
        NOT_INSTALLED,      // 未安装
        NOT_RUNNING,        // 未运行
        PERMISSION_DENIED,  // 权限被拒绝
        PERMISSION_GRANTED  // 权限已授予
    }

    /**
     * Shell命令执行结果
     */
    data class ShellResult(
        val exitCode: Int,
        val output: String,
        val error: String,
        val success: Boolean = exitCode == 0
    )

    /**
     * 检查Shizuku状态
     */
    fun checkShizukuStatus(): ShizukuStatus {
        return try {
            Log.d(TAG, "检查Shizuku状态...")
            // 检查Shizuku版本（这会检查是否安装和可用）
            if (Shizuku.isPreV11()) {
                Log.d(TAG, "Shizuku版本过旧")
                return ShizukuStatus.NOT_INSTALLED
            }
            // 检查权限状态
            val permission = Shizuku.checkSelfPermission()
            Log.d(TAG, "Shizuku权限状态: $permission")
            when (permission) {
                PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "Shizuku权限已授予")
                    ShizukuStatus.PERMISSION_GRANTED
                }

                PackageManager.PERMISSION_DENIED -> {
                    Log.d(TAG, "Shizuku权限被拒绝")
                    ShizukuStatus.PERMISSION_DENIED
                }

                else -> {
                    Log.d(TAG, "Shizuku权限状态未知: $permission")
                    ShizukuStatus.PERMISSION_DENIED
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查Shizuku状态时出错", e)
            // 根据异常类型判断状态
            when {
                e.message?.contains("binder haven't been received") == true -> {
                    Log.d(TAG, "Shizuku binder未连接，但可能已安装")
                    ShizukuStatus.NOT_RUNNING
                }

                else -> {
                    Log.d(TAG, "Shizuku可能未安装")
                    ShizukuStatus.NOT_INSTALLED
                }
            }
        }
    }

    /**
     * 请求Shizuku权限
     */
    fun requestShizukuPermission(): Boolean {
        return try {
            Log.d(TAG, "请求Shizuku权限...")
            if (Shizuku.isPreV11()) {
                Log.d(TAG, "Shizuku版本过旧")
                return false
            }
            val currentPermission = Shizuku.checkSelfPermission()
            Log.d(TAG, "当前权限状态: $currentPermission")
            when (currentPermission) {
                PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "权限已授予")
                    true
                }

                PackageManager.PERMISSION_DENIED -> {
                    if (Shizuku.shouldShowRequestPermissionRationale()) {
                        Log.d(TAG, "需要显示权限说明")
                    }
                    Log.d(TAG, "发起权限请求...")
                    Shizuku.requestPermission(1001)
                    false
                }

                else -> {
                    Log.d(TAG, "权限状态未知，尝试请求")
                    Shizuku.requestPermission(1001)
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求Shizuku权限时出错", e)
            false
        }
    }

    /**
     * 通过Shizuku执行命令
     * 注意：这是一个简化的实现，用于演示目的
     * 在实际项目中，建议使用Shizuku的UserService或transactRemote API
     */
    suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "通过Shizuku执行命令: $command")
            // 检查Shizuku权限
            if (!Shizuku.pingBinder()) {
                Log.e(TAG, "Shizuku服务不可用")
                return@withContext ShellResult(-1, "", "Shizuku服务不可用", false)
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "没有Shizuku权限")
                return@withContext ShellResult(-1, "", "没有Shizuku权限", false)
            }
            // 使用 ShizukuShell 执行命令
            val output = shizukuShell.singleResponseCommand(command)
            Log.d(TAG, "命令执行完成: output='${output.trim()}'")
            ShellResult(0, output.trim(), "", true)
        } catch (e: Exception) {
            Log.e(TAG, "执行命令失败", e)
            ShellResult(-1, "", "命令执行失败: ${e.message}", false)
        }
    }
}
