package com.github.kirer.appk.test

import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * 工具功能测试
 * 测试server-k的剪贴板、Shell命令、系统信息等工具功能
 */
class UtilsTest : BaseTest("Utils") {
    
    override suspend fun executeTest(): TestResult {
        try {
            updateProgress(10)
            addDetail("开始测试工具功能")
            
            // 测试1: 剪贴板功能
            updateProgress(25)
            if (!testClipboard()) {
                addDetail("剪贴板测试失败，但继续其他测试")
            }
            
            // 测试2: Shell命令执行
            updateProgress(50)
            if (!testShellCommands()) {
                return TestResult.FAILED
            }
            
            // 测试3: 系统信息获取
            updateProgress(75)
            if (!testSystemInfo()) {
                return TestResult.FAILED
            }
            
            // 测试4: 屏幕设置
            updateProgress(90)
            if (!testScreenSettings()) {
                addDetail("屏幕设置测试失败，但继续")
            }
            
            updateProgress(100)
            addDetail("工具功能测试完成")
            return TestResult.PASSED
            
        } catch (e: Exception) {
            Timber.e(e, "Utils test failed")
            addDetail("测试异常: ${e.message}")
            return TestResult.FAILED
        }
    }
    
    /**
     * 测试剪贴板功能
     */
    private suspend fun testClipboard(): Boolean {
        addDetail("测试剪贴板功能...")
        
        // 测试设置剪贴板内容 (utils|setClipText|测试文本)
        val setClipSuccess = sendCommandAndVerify("utils|setClipText|Hello from App-K Test")
        if (!setClipSuccess) {
            addDetail("设置剪贴板内容失败")
            return false
        }
        
        delay(500)
        
        // 测试获取剪贴板内容 (utils|getClipText)
        val getClipSuccess = sendCommandAndVerify("utils|getClipText")
        if (!getClipSuccess) {
            addDetail("获取剪贴板内容失败")
            return false
        }
        
        delay(500)
        
        // 测试设置空内容
        val setClearSuccess = sendCommandAndVerify("utils|setClipText|")
        if (!setClearSuccess) {
            addDetail("清空剪贴板失败")
        } else {
            addDetail("清空剪贴板成功")
        }
        
        addDetail("剪贴板功能测试完成")
        return true
    }
    
    /**
     * 测试Shell命令执行
     */
    private suspend fun testShellCommands(): Boolean {
        addDetail("测试Shell命令执行...")
        
        // 测试基本echo命令
        val echoSuccess = sendCommandAndVerify("utils|shell|echo 'Hello World'")
        if (!echoSuccess) {
            addDetail("echo命令测试失败")
            return false
        }
        
        delay(300)
        
        // 测试获取当前时间
        val dateSuccess = sendCommandAndVerify("utils|shell|date")
        if (!dateSuccess) {
            addDetail("date命令测试失败")
            return false
        }
        
        delay(300)
        
        // 测试获取系统信息
        val unameSuccess = sendCommandAndVerify("utils|shell|uname -a")
        if (!unameSuccess) {
            addDetail("uname命令测试失败")
            return false
        }
        
        delay(300)
        
        // 测试获取进程信息
        val psSuccess = sendCommandAndVerify("utils|shell|ps | head -5")
        if (!psSuccess) {
            addDetail("ps命令测试失败")
            return false
        }
        
        delay(300)
        
        // 测试文件系统命令
        val lsSuccess = sendCommandAndVerify("utils|shell|ls /system/bin | head -10")
        if (!lsSuccess) {
            addDetail("ls命令测试失败")
            return false
        }
        
        addDetail("Shell命令执行测试通过")
        return true
    }
    
    /**
     * 测试系统信息获取
     */
    private suspend fun testSystemInfo(): Boolean {
        addDetail("测试系统信息获取...")
        
        // 测试获取屏幕尺寸 (utils|getWmSize)
        val screenSizeSuccess = sendCommandAndVerify("utils|getWmSize")
        if (!screenSizeSuccess) {
            addDetail("获取屏幕尺寸失败")
            return false
        }
        
        delay(300)
        
        // 通过shell命令获取更多系统信息
        val androidVersionSuccess = sendCommandAndVerify("utils|shell|getprop ro.build.version.release")
        if (!androidVersionSuccess) {
            addDetail("获取Android版本失败")
        } else {
            addDetail("获取Android版本成功")
        }
        
        delay(300)
        
        // 获取设备型号
        val deviceModelSuccess = sendCommandAndVerify("utils|shell|getprop ro.product.model")
        if (!deviceModelSuccess) {
            addDetail("获取设备型号失败")
        } else {
            addDetail("获取设备型号成功")
        }
        
        delay(300)
        
        // 获取CPU架构
        val cpuAbiSuccess = sendCommandAndVerify("utils|shell|getprop ro.product.cpu.abi")
        if (!cpuAbiSuccess) {
            addDetail("获取CPU架构失败")
        } else {
            addDetail("获取CPU架构成功")
        }
        
        delay(300)
        
        // 获取内存信息
        val memInfoSuccess = sendCommandAndVerify("utils|shell|cat /proc/meminfo | head -3")
        if (!memInfoSuccess) {
            addDetail("获取内存信息失败")
        } else {
            addDetail("获取内存信息成功")
        }
        
        addDetail("系统信息获取测试完成")
        return true
    }
    
    /**
     * 测试屏幕设置
     */
    private suspend fun testScreenSettings(): Boolean {
        addDetail("测试屏幕设置...")
        
        // 测试设置屏幕模式 (utils|setScreenMode|1)
        val setModeSuccess = sendCommandAndVerify("utils|setScreenMode|1")
        if (!setModeSuccess) {
            addDetail("设置屏幕模式失败")
            return false
        }
        
        delay(500)
        
        // 恢复默认模式
        val resetModeSuccess = sendCommandAndVerify("utils|setScreenMode|0")
        if (!resetModeSuccess) {
            addDetail("恢复屏幕模式失败")
        } else {
            addDetail("恢复屏幕模式成功")
        }
        
        addDetail("屏幕设置测试完成")
        return true
    }
    
    /**
     * 获取测试描述
     */
    fun getTestDescription(): String {
        return """
            工具功能测试包括：
            1. 剪贴板操作 (设置和获取剪贴板内容)
            2. Shell命令执行 (echo, date, uname, ps, ls等)
            3. 系统信息获取 (屏幕尺寸、Android版本、设备型号等)
            4. 屏幕设置 (屏幕模式设置)
            
            此测试验证server-k的工具类功能是否正常工作。
            注意：某些功能可能需要特定的系统权限。
        """.trimIndent()
    }
}
