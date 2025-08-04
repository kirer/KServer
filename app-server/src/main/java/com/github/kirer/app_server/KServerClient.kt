package com.github.kirer.app_server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException
import java.io.InputStream
import java.net.Socket

/**
 * KServer Socket客户端
 * 使用OkHttp连接KServer并执行各种命令
 */
class KServerClient(
    private val host: String = "localhost", private val port: Int = 8888
) {

    companion object {
        private const val TAG = "KServerClient"
        private const val CONNECT_TIMEOUT = 10L
        private const val READ_TIMEOUT = 30L
        private const val WRITE_TIMEOUT = 30L
    }

    /**
     * 连接状态
     */
    enum class ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED, ERROR
    }

    /**
     * 命令响应
     */
    @Serializable
    data class CommandResponse(
        val success: Boolean,
        val message: String,
        val data: ByteArray? = null,
        val dataSize: Int = 0,
        val processingTime: Long = 0
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CommandResponse

            if (success != other.success) return false
            if (message != other.message) return false
            if (data != null) {
                if (other.data == null) return false
                if (!data.contentEquals(other.data)) return false
            } else if (other.data != null) return false
            if (dataSize != other.dataSize) return false
            if (processingTime != other.processingTime) return false

            return true
        }

        override fun hashCode(): Int {
            var result = success.hashCode()
            result = 31 * result + message.hashCode()
            result = 31 * result + (data?.contentHashCode() ?: 0)
            result = 31 * result + dataSize
            result = 31 * result + processingTime.hashCode()
            return result
        }
    }

    /**
     * 性能统计
     */
    data class PerformanceStats(
        val commandType: String,
        val processingTime: Long,
        val dataSize: Int,
        val networkTime: Long,
        val totalTime: Long
    )

    private var connectionStatus = ConnectionStatus.DISCONNECTED

    /**
     * 检查连接状态
     */
    suspend fun checkConnection(): ConnectionStatus = withContext(Dispatchers.IO) {
        try {
            connectionStatus = ConnectionStatus.CONNECTING

            // 尝试连接到真正的KServer
            val socket = Socket()
            socket.connect(java.net.InetSocketAddress(host, port), 5000)

            // 发送状态检查命令
            val writer = socket.getOutputStream().bufferedWriter()
            writer.write("status\n")
            writer.flush()

            // 读取响应
            val reader = socket.getInputStream().bufferedReader()
            val response = reader.readLine()

            socket.close()

            // 检查响应是否包含OK
            if (response != null && response.contains("OK")) {
                connectionStatus = ConnectionStatus.CONNECTED
                ConnectionStatus.CONNECTED
            } else {
                connectionStatus = ConnectionStatus.ERROR
                ConnectionStatus.ERROR
            }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Connection check failed", e)
            connectionStatus = ConnectionStatus.ERROR
            ConnectionStatus.ERROR
        }
    }

    /**
     * 发送命令到KServer
     */
    suspend fun sendCommand(command: String): CommandResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            val socket = Socket()
            socket.connect(java.net.InetSocketAddress(host, port), 10000)

            val outputStream = socket.getOutputStream()
            val inputStream = socket.getInputStream()
            val writer = outputStream.bufferedWriter()

            // 发送命令
            writer.write(command)
            writer.newLine()
            writer.flush()

            // 读取响应头（使用原始InputStream避免BufferedReader缓存问题）
            val responseLine = readLine(inputStream) ?: throw IOException("No response from server")

            android.util.Log.d(TAG, "Server response: $responseLine")

            val networkTime = System.currentTimeMillis() - startTime

            // 解析响应
            when {
                responseLine.startsWith("Response: OK") -> {
                    val message = responseLine.removePrefix("Response: OK").trim()
                    CommandResponse(
                        success = true,
                        message = message,
                        processingTime = extractProcessingTime(message),
                        dataSize = 0
                    )
                }

                responseLine.startsWith("BYTES:") -> {
                    // 解析二进制响应
                    val parts = responseLine.split(":")
                    if (parts.size >= 3) {
                        val dataSize = parts[1].toIntOrNull() ?: 0
                        val message = parts.drop(2).joinToString(":")

                        // 读取二进制数据
                        val data = ByteArray(dataSize)
                        var totalRead = 0
                        while (totalRead < dataSize) {
                            val bytesRead = inputStream.read(data, totalRead, dataSize - totalRead)
                            if (bytesRead == -1) break
                            totalRead += bytesRead
                        }

                        CommandResponse(
                            success = true,
                            message = message,
                            data = data,
                            dataSize = dataSize,
                            processingTime = extractProcessingTime(message)
                        )
                    } else {
                        CommandResponse(
                            false, "Invalid BYTES response format", processingTime = networkTime
                        )
                    }
                }

                responseLine.startsWith("ERROR") -> {
                    val message = responseLine.removePrefix("ERROR").trim()
                    CommandResponse(false, message, processingTime = networkTime)
                }

                else -> {
                    CommandResponse(
                        success = true, message = responseLine, processingTime = networkTime
                    )
                }
            }

        } catch (e: Exception) {
            val networkTime = System.currentTimeMillis() - startTime
            CommandResponse(
                success = false,
                message = "Network error: ${e.message}",
                processingTime = networkTime
            )
        }
    }

    /**
     * 从响应消息中提取处理时间
     */
    private fun extractProcessingTime(message: String): Long {
        return try {
            val regex = """(\d+)ms""".toRegex()
            val match = regex.find(message)
            match?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 截图（
     */
    suspend fun takeScreenshot(): CommandResponse {
        return sendCommand("screenshot")
    }

    /**
     * 从InputStream读取一行文本
     */
    private fun readLine(inputStream: InputStream): String? {
        val buffer = StringBuilder()
        var byte: Int
        while (inputStream.read().also { byte = it } != -1) {
            val char = byte.toChar()
            if (char == '\n') {
                break
            } else if (char != '\r') {
                buffer.append(char)
            }
        }
        return if (buffer.isEmpty() && byte == -1) null else buffer.toString()
    }
}
