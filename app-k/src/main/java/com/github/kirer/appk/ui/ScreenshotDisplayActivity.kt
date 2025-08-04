package com.github.kirer.appk.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.github.kirer.appk.R
import com.github.kirer.appk.server.ServerManager
import com.github.kirer.appk.memory.SafeAshmem
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 截图显示Activity
 * 用于显示server-k生成的截图
 */
class ScreenshotDisplayActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var statusText: TextView
    private lateinit var screenshotButton: Button
    private lateinit var serverManager: ServerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screenshot_display)

        imageView = findViewById(R.id.imageView)
        statusText = findViewById(R.id.statusText)
        screenshotButton = findViewById(R.id.screenshotButton)

        // 获取ServerManager实例
        serverManager = ServerManager.getInstance()

        // 设置截图按钮点击事件
        screenshotButton.setOnClickListener {
            takeScreenshot()
        }

        statusText.text = "点击下方按钮进行截图测试"
    }

    /**
     * 执行截图操作
     */
    private fun takeScreenshot() {
        lifecycleScope.launch {
            try {
                statusText.text = "正在截图..."
                screenshotButton.isEnabled = false

                // 确保server-k已启动
                if (!serverManager.isServerConnected()) {
                    statusText.text = "正在启动Server-K..."
                    val started = serverManager.startServer()
                    if (!started) {
                        statusText.text = "Server-K启动失败"
                        screenshotButton.isEnabled = true
                        return@launch
                    }
                }

                // 发送截图初始化命令（如果还没初始化）
                statusText.text = "初始化截图功能..."
                val initResponse = serverManager.sendCommand("screenShotInit")
                Timber.d("Screenshot init response: $initResponse")

                // 等待截图系统稳定
                kotlinx.coroutines.delay(3000)

                // 读取真正的截图数据！
                statusText.text = "正在从共享内存读取截图数据..."
                val bitmap = readScreenshotFromSharedMemory()

                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    statusText.text = "✅ 截图成功！\n大小: ${bitmap.width}x${bitmap.height}\n来源: 共享内存"
                } else {
                    statusText.text = "❌ 无法读取截图数据\n可能原因：\n1. .so文件未加载\n2. 共享内存未初始化\n3. 截图数据为空"
                    displayPlaceholderScreenshot()
                }

            } catch (e: Exception) {
                Timber.e(e, "Screenshot failed")
                statusText.text = "截图失败: ${e.message}"
            } finally {
                screenshotButton.isEnabled = true
            }
        }
    }

    /**
     * 从共享内存读取真正的截图数据
     */
    private fun readScreenshotFromSharedMemory(): Bitmap? {
        return try {
            // 创建SafeAshmem实例
            val ashmem = SafeAshmem()

            // 检查native库是否可用
            if (!ashmem.isNativeLibraryAvailable()) {
                Timber.e("Native library not available")
                return null
            }

            // 使用与server-k相同的参数初始化共享内存
            // fd=1, mmapSize=1048576 (这些是server-k使用的参数)
            val fd = 1
            val mmapSize = 1048576L

            Timber.d("Initializing shared memory: fd=$fd, size=$mmapSize")
            val initResult = ashmem.init(fd, mmapSize)

            if (initResult != 0) {
                Timber.e("Failed to initialize shared memory: $initResult")
                return null
            }

            // 读取共享内存数据
            Timber.d("Reading data from shared memory...")
            val data = ashmem.readData()

            if (data == null || data.isEmpty()) {
                Timber.w("No data in shared memory")
                ashmem.destroy()
                return null
            }

            Timber.d("Read ${data.size} bytes from shared memory")

            // 解析图像数据
            // 根据ScreenShot.java的实现，数据格式是：
            // [4字节宽度][4字节高度][图像数据]
            val buffer = ByteBuffer.wrap(data)
            buffer.order(ByteOrder.LITTLE_ENDIAN)

            if (data.size < 8) {
                Timber.e("Data too small: ${data.size} bytes")
                ashmem.destroy()
                return null
            }

            val width = buffer.int
            val height = buffer.int

            Timber.d("Image dimensions: ${width}x${height}")

            if (width <= 0 || height <= 0 || width > 4096 || height > 4096) {
                Timber.e("Invalid dimensions: ${width}x${height}")
                ashmem.destroy()
                return null
            }

            // 计算剩余数据大小
            val imageDataSize = data.size - 8
            val expectedSize = width * height * 4 // ARGB_8888

            Timber.d("Image data size: $imageDataSize, expected: $expectedSize")

            if (imageDataSize < expectedSize) {
                Timber.e("Insufficient image data: $imageDataSize < $expectedSize")
                ashmem.destroy()
                return null
            }

            // 创建Bitmap
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            // 复制图像数据
            val imageBuffer = ByteBuffer.allocate(imageDataSize)
            imageBuffer.put(data, 8, imageDataSize)
            imageBuffer.rewind()

            bitmap.copyPixelsFromBuffer(imageBuffer)

            ashmem.destroy()

            Timber.d("Successfully created bitmap: ${bitmap.width}x${bitmap.height}")
            bitmap

        } catch (e: Exception) {
            Timber.e(e, "Failed to read screenshot from shared memory")
            null
        }
    }

    /**
     * 显示占位截图（演示用）
     */
    private fun displayPlaceholderScreenshot() {
        try {
            // 创建一个简单的占位图片
            val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.LTGRAY)

            // 在图片上绘制一些文字
            val canvas = android.graphics.Canvas(bitmap)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 24f
                textAlign = android.graphics.Paint.Align.CENTER
            }

            canvas.drawText("截图功能已激活", bitmap.width / 2f, bitmap.height / 2f - 50, paint)
            canvas.drawText("Server-K截图系统运行中", bitmap.width / 2f, bitmap.height / 2f, paint)
            canvas.drawText("图像数据在共享内存中", bitmap.width / 2f, bitmap.height / 2f + 50, paint)

            imageView.setImageBitmap(bitmap)

        } catch (e: Exception) {
            Timber.e(e, "Failed to create placeholder screenshot")
        }
    }

    private fun loadScreenshot(path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                val bitmap = BitmapFactory.decodeFile(path)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    statusText.text = "截图加载成功\n路径: $path\n大小: ${bitmap.width}x${bitmap.height}"
                } else {
                    statusText.text = "截图文件解码失败"
                }
            } else {
                statusText.text = "截图文件不存在: $path"
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load screenshot")
            statusText.text = "截图加载失败: ${e.message}"
        }
    }
}
