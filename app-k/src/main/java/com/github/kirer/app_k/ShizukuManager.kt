package com.github.kirer.app_k

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import kotlin.coroutines.resumeWithException

/**
 * Shizuku权限管理器
 * 负责Shizuku权限检查、请求和Shell命令执行
 */
class ShizukuManager() {
    interface CommandResultListener {
        /*
         * Runs after the command executes, at least partially. Does not run with 'done' if the command throws an error.
         * output: The output of the command
         * done: If the command has finished executing
         */
        fun onCommandResult(output: String, done: Boolean) {}

        /*
         * Runs if the command throws an error.
         * error: The error message
         */
        fun onCommandError(error: String) {}
    }

    companion object {
        private const val TAG = "ShizukuManager"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Shell命令执行结果
     */
    data class ShellResult(
        val exitCode: Int,
        val output: String,
        val error: String,
        val success: Boolean = exitCode == 0
    )

    init {
        Shizuku.addRequestPermissionResultListener(Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == 1001) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Shizuku权限已授予")
                } else {
                    Log.d(TAG, "Shizuku权限被拒绝")
                }
            }
        })
    }

    /**
     * 请求Shizuku权限
     */
    fun requestPermission(): Boolean {
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
            val output = singleResponseCommand(command)
            Log.d(TAG, "Shell: $command output='${output.trim()}'")
            ShellResult(0, output.trim(), "", true)
        } catch (e: Exception) {
            Log.e(TAG, "执行命令失败", e)
            ShellResult(-1, "", "命令执行失败: ${e.message}", false)
        }
    }

    fun command(command: String, listener: CommandResultListener, lineBundle: Int = 50) {
        applicationScope.launch {
            var process: IRemoteProcess? = null
            var inputStreamPfd: ParcelFileDescriptor? = null
            var errorStreamPfd: ParcelFileDescriptor? = null

            try {
                process =
                    IShizukuService.Stub.asInterface(Shizuku.getBinder())
                        .newProcess(arrayOf("sh", "-c", command), null, null)
                inputStreamPfd = process.inputStream
                errorStreamPfd = process.errorStream

                FileInputStream(inputStreamPfd.fileDescriptor).use { inputStream ->
                    FileInputStream(errorStreamPfd.fileDescriptor).use { errorStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            BufferedReader(InputStreamReader(errorStream)).use { err ->
                                val output = StringBuilder()
                                val errorData = StringBuilder()
                                var line: String?
                                var lineCount = 0

                                // Read output first
                                while (reader.readLine().also { line = it } != null) {
                                    lineCount++
                                    output.append(line).append("\n")
                                    if (lineCount == lineBundle) {
                                        lineCount = 0
                                        listener.onCommandResult(
                                            output.toString().trim().replace(Regex("[\n\r]"), ""),
                                            false,
                                        )
                                        output.clear()
                                    }
                                }
                                // Send any remaining buffered output
                                if (output.isNotBlank()) {
                                    listener.onCommandResult(
                                        output.toString().trim().replace(Regex("[\n\r]"), ""),
                                        false,
                                    )
                                }

                                // Read error stream
                                while (err.readLine().also { line = it } != null) {
                                    errorData.append(line).append("\n")
                                }

                                if (errorData.isNotBlank()) {
                                    listener.onCommandError(
                                        errorData.toString().trim().replace(Regex("[\n\r]"), "")
                                    )
                                } else {
                                    listener.onCommandResult(
                                        output.toString().trim().replace(Regex("[\n\r]"), ""),
                                        true,
                                    )
                                }

                                // Wait for the process to complete
                                process.waitFor()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("KKK","ShizukuShell command failed for command: $command")
                listener.onCommandError(e.message ?: "Unknown Shizuku command error")
            } finally {
                inputStreamPfd?.close()
                errorStreamPfd?.close()
                process?.destroy()
            }
        }
    }

    suspend fun singleResponseCommand(command: String) =
        suspendCancellableCoroutine { continuation ->
            command(
                command,
                object : CommandResultListener {
                    override fun onCommandResult(output: String, done: Boolean) {
                        if (done) continuation.resumeWith(Result.success(output))
                    }

                    override fun onCommandError(error: String) {
                        continuation.resumeWithException(Exception(error))
                    }
                },
            )
        }
}
