package com.github.kirer.appk.test

import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * 触摸功能测试
 * 测试server-k的触摸、滑动、按键等功能
 */
class TouchTest : BaseTest("Touch") {
    
    override suspend fun executeTest(): TestResult {
        try {
            updateProgress(10)
            addDetail("开始测试触摸功能")
            
            // 测试1: 触摸按下和抬起
            updateProgress(20)
            if (!testTouchDownUp()) {
                return TestResult.FAILED
            }
            
            // 测试2: 触摸移动
            updateProgress(40)
            if (!testTouchMove()) {
                return TestResult.FAILED
            }
            
            // 测试3: 按键输入
            updateProgress(60)
            if (!testKeyInput()) {
                return TestResult.FAILED
            }
            
            // 测试4: 直线滑动
            updateProgress(80)
            if (!testLinearSwipe()) {
                return TestResult.FAILED
            }
            
            // 测试5: 贝塞尔曲线滑动
            updateProgress(90)
            if (!testBezierSwipe()) {
                return TestResult.FAILED
            }
            
            updateProgress(100)
            addDetail("所有触摸功能测试通过")
            return TestResult.PASSED
            
        } catch (e: Exception) {
            Timber.e(e, "Touch test failed")
            addDetail("测试异常: ${e.message}")
            return TestResult.FAILED
        }
    }
    
    /**
     * 测试触摸按下和抬起 - 真正的触摸测试
     */
    private suspend fun testTouchDownUp(): Boolean {
        addDetail("测试真正的触摸按下和抬起...")
        addDetail("将在屏幕中央(500,500)执行点击操作")

        // 测试触摸按下 (d|x|y|pointerId)
        val downResponse = serverManager.sendCommand("d|500|500|0")
        if (downResponse?.contains("success") != true) {
            addDetail("触摸按下失败: $downResponse")
            return false
        }
        addDetail("触摸按下成功: $downResponse")

        delay(500) // 保持按下状态，让用户能看到效果

        // 测试触摸抬起 (u|x|y|pointerId)
        val upResponse = serverManager.sendCommand("u|500|500|0")
        if (upResponse?.contains("success") != true) {
            addDetail("触摸抬起失败: $upResponse")
            return false
        }
        addDetail("触摸抬起成功: $upResponse")

        addDetail("✅ 屏幕中央点击测试完成 - 您应该看到了实际的触摸效果")
        return true
    }
    
    /**
     * 测试触摸移动
     */
    private suspend fun testTouchMove(): Boolean {
        addDetail("测试触摸移动...")
        
        // 先按下
        if (!sendCommandAndVerify("d|300|300|0")) {
            addDetail("触摸移动测试 - 按下失败")
            return false
        }
        
        delay(50)
        
        // 移动 (m|x|y|pointerId)
        val moveSuccess = sendCommandAndVerify("m|400|400|0")
        if (!moveSuccess) {
            addDetail("触摸移动测试失败")
            return false
        }
        
        delay(50)
        
        // 抬起
        if (!sendCommandAndVerify("u|400|400|0")) {
            addDetail("触摸移动测试 - 抬起失败")
            return false
        }
        
        addDetail("触摸移动测试通过")
        return true
    }
    
    /**
     * 测试按键输入 - 真正的按键测试
     */
    private suspend fun testKeyInput(): Boolean {
        addDetail("测试真正的按键输入...")
        addDetail("⚠️ 注意：将执行真实的系统按键操作")

        // 测试音量下键 (比较安全的测试键)
        addDetail("按下音量下键...")
        val volumeDownResponse = serverManager.sendCommand("k|25") // KEYCODE_VOLUME_DOWN = 25
        if (volumeDownResponse?.contains("success") != true) {
            addDetail("音量下键失败: $volumeDownResponse")
            return false
        }
        addDetail("音量下键成功: $volumeDownResponse")

        delay(1000)

        // 测试音量上键
        addDetail("按下音量上键...")
        val volumeUpResponse = serverManager.sendCommand("k|24") // KEYCODE_VOLUME_UP = 24
        if (volumeUpResponse?.contains("success") != true) {
            addDetail("音量上键失败: $volumeUpResponse")
            return false
        }
        addDetail("音量上键成功: $volumeUpResponse")

        delay(1000)

        addDetail("✅ 按键输入测试完成 - 您应该听到了音量变化的声音")
        return true
    }
    
    /**
     * 测试直线滑动 - 真正的滑动测试
     */
    private suspend fun testLinearSwipe(): Boolean {
        addDetail("测试真正的直线滑动...")
        addDetail("将执行从屏幕底部向上的滑动操作")

        // 直线滑动 (s1|startX|startY|endX|endY|duration)
        val swipeResponse = serverManager.sendCommand("s1|200|800|200|200|1000")
        if (swipeResponse?.contains("success") != true) {
            addDetail("直线滑动失败: $swipeResponse")
            return false
        }
        addDetail("直线滑动命令成功: $swipeResponse")

        delay(1500) // 等待滑动完成

        addDetail("✅ 直线滑动测试完成 - 您应该看到了从底部向上的滑动效果")
        return true
    }
    
    /**
     * 测试贝塞尔曲线滑动
     */
    private suspend fun testBezierSwipe(): Boolean {
        addDetail("测试贝塞尔曲线滑动...")
        
        // 贝塞尔曲线滑动 (s2|startX|startY|endX|endY|duration)
        val bezierSuccess = sendCommandAndVerify("s2|100|600|500|200|1500")
        if (!bezierSuccess) {
            addDetail("贝塞尔曲线滑动测试失败")
            return false
        }
        
        delay(2000) // 等待滑动完成
        
        addDetail("贝塞尔曲线滑动测试通过")
        return true
    }
    
    /**
     * 获取测试描述
     */
    fun getTestDescription(): String {
        return """
            触摸功能测试包括：
            1. 触摸按下和抬起 (d/u命令)
            2. 触摸移动 (m命令)
            3. 按键输入 (k命令) - Home、Back、Menu键
            4. 直线滑动 (s1命令)
            5. 贝塞尔曲线滑动 (s2命令)
            
            此测试验证server-k的触摸输入系统是否正常工作。
            注意：测试会在屏幕上执行实际的触摸操作。
        """.trimIndent()
    }
}
