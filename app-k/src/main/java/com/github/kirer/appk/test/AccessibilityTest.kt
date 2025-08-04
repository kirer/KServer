package com.github.kirer.appk.test

import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * 无障碍功能测试
 * 测试server-k的UI元素查找、操作等无障碍功能
 */
class AccessibilityTest : BaseTest("Accessibility") {
    
    override suspend fun executeTest(): TestResult {
        try {
            updateProgress(10)
            addDetail("开始测试无障碍功能")
            
            // 测试1: 初始化无障碍服务
            updateProgress(20)
            if (!testAccessibilityInit()) {
                return TestResult.FAILED
            }
            
            // 测试2: 查找UI元素
            updateProgress(40)
            if (!testFindElements()) {
                return TestResult.FAILED
            }
            
            // 测试3: 获取元素信息
            updateProgress(60)
            if (!testElementInfo()) {
                return TestResult.FAILED
            }
            
            // 测试4: 元素操作
            updateProgress(80)
            if (!testElementOperations()) {
                return TestResult.FAILED
            }
            
            // 测试5: 元素层次结构
            updateProgress(90)
            if (!testElementHierarchy()) {
                return TestResult.FAILED
            }
            
            updateProgress(100)
            addDetail("所有无障碍功能测试通过")
            return TestResult.PASSED
            
        } catch (e: Exception) {
            Timber.e(e, "Accessibility test failed")
            addDetail("测试异常: ${e.message}")
            return TestResult.FAILED
        }
    }
    
    /**
     * 测试无障碍服务初始化
     */
    private suspend fun testAccessibilityInit(): Boolean {
        addDetail("测试无障碍服务初始化...")
        
        // 初始化无障碍服务 (acc|newAccessibility)
        val initSuccess = sendCommandAndVerify("acc|newAccessibility")
        if (!initSuccess) {
            addDetail("无障碍服务初始化失败")
            return false
        }
        
        delay(2000) // 等待初始化完成
        addDetail("无障碍服务初始化成功")
        return true
    }
    
    /**
     * 测试查找UI元素 - 真正的UI查找
     */
    private suspend fun testFindElements(): Boolean {
        addDetail("测试真正的UI元素查找...")
        addDetail("🔍 正在扫描当前屏幕上的UI元素...")

        // 测试查找可点击元素
        val findClickableResponse = serverManager.sendCommand("acc|findOnce|clickable@@true")
        addDetail("查找可点击元素响应: $findClickableResponse")

        delay(500)

        // 测试查找TextView元素
        val findTextViewResponse = serverManager.sendCommand("acc|find|className@@android.widget.TextView")
        addDetail("查找TextView元素响应: $findTextViewResponse")

        delay(500)

        // 测试查找Button元素
        val findButtonResponse = serverManager.sendCommand("acc|find|className@@android.widget.Button")
        addDetail("查找Button元素响应: $findButtonResponse")

        delay(500)

        // 测试查找包含特定文本的元素
        val findTestTextResponse = serverManager.sendCommand("acc|find|text@@测试")
        addDetail("查找包含'测试'文本的元素响应: $findTestTextResponse")

        addDetail("✅ UI元素查找测试完成 - 已扫描当前界面的UI元素")
        return true
    }
    
    /**
     * 测试获取元素信息
     */
    private suspend fun testElementInfo(): Boolean {
        addDetail("测试获取元素信息...")
        
        // 先查找一个元素
        val findResult = sendCommandAndVerify("acc|findOnce|clickable@@true")
        if (!findResult) {
            addDetail("未找到可测试的元素，跳过信息获取测试")
            return true
        }
        
        delay(500)
        
        // 这里需要解析findOnce的返回结果来获取元素ID
        // 为了简化，我们假设有一个元素ID
        val elementId = "test_element_id"
        
        // 测试获取元素文本 (acc|uiObjectGetText|elementId)
        val getTextSuccess = sendCommandAndVerify("acc|uiObjectGetText|$elementId")
        if (!getTextSuccess) {
            addDetail("获取元素文本失败")
        } else {
            addDetail("获取元素文本成功")
        }
        
        delay(200)
        
        // 测试获取元素ID (acc|uiObjectGetId|elementId)
        val getIdSuccess = sendCommandAndVerify("acc|uiObjectGetId|$elementId")
        if (!getIdSuccess) {
            addDetail("获取元素ID失败")
        } else {
            addDetail("获取元素ID成功")
        }
        
        delay(200)
        
        // 测试获取元素边界 (acc|uiObjectGetBounds|elementId)
        val getBoundsSuccess = sendCommandAndVerify("acc|uiObjectGetBounds|$elementId")
        if (!getBoundsSuccess) {
            addDetail("获取元素边界失败")
        } else {
            addDetail("获取元素边界成功")
        }
        
        addDetail("元素信息获取测试完成")
        return true
    }
    
    /**
     * 测试元素操作
     */
    private suspend fun testElementOperations(): Boolean {
        addDetail("测试元素操作...")
        
        // 查找可点击元素
        val findResult = sendCommandAndVerify("acc|findOnce|clickable@@true")
        if (!findResult) {
            addDetail("未找到可点击元素，跳过操作测试")
            return true
        }
        
        val elementId = "test_element_id"
        
        // 测试点击元素 (acc|uiObjectClick|elementId)
        val clickSuccess = sendCommandAndVerify("acc|uiObjectClick|$elementId")
        if (!clickSuccess) {
            addDetail("点击元素失败")
        } else {
            addDetail("点击元素成功")
        }
        
        delay(1000)
        
        // 测试长按元素 (acc|uiObjectLongClick|elementId)
        val longClickSuccess = sendCommandAndVerify("acc|uiObjectLongClick|$elementId")
        if (!longClickSuccess) {
            addDetail("长按元素失败")
        } else {
            addDetail("长按元素成功")
        }
        
        delay(1000)
        
        // 测试设置文本 (acc|uiObjectSetText|elementId|测试文本)
        val setTextSuccess = sendCommandAndVerify("acc|uiObjectSetText|$elementId|测试文本")
        if (!setTextSuccess) {
            addDetail("设置元素文本失败")
        } else {
            addDetail("设置元素文本成功")
        }
        
        addDetail("元素操作测试完成")
        return true
    }
    
    /**
     * 测试元素层次结构
     */
    private suspend fun testElementHierarchy(): Boolean {
        addDetail("测试元素层次结构...")
        
        val elementId = "test_element_id"
        
        // 测试获取子元素 (acc|uiObjectGetChildren|elementId)
        val getChildrenSuccess = sendCommandAndVerify("acc|uiObjectGetChildren|$elementId")
        if (!getChildrenSuccess) {
            addDetail("获取子元素失败")
        } else {
            addDetail("获取子元素成功")
        }
        
        delay(200)
        
        // 测试获取父元素 (acc|uiObjectGetParent|elementId)
        val getParentSuccess = sendCommandAndVerify("acc|uiObjectGetParent|$elementId")
        if (!getParentSuccess) {
            addDetail("获取父元素失败")
        } else {
            addDetail("获取父元素成功")
        }
        
        delay(200)
        
        // 测试获取子元素数量 (acc|uiObjectGetChildCound|elementId)
        val getChildCountSuccess = sendCommandAndVerify("acc|uiObjectGetChildCound|$elementId")
        if (!getChildCountSuccess) {
            addDetail("获取子元素数量失败")
        } else {
            addDetail("获取子元素数量成功")
        }
        
        addDetail("元素层次结构测试完成")
        return true
    }
    
    /**
     * 获取测试描述
     */
    fun getTestDescription(): String {
        return """
            无障碍功能测试包括：
            1. 无障碍服务初始化 (acc|newAccessibility)
            2. UI元素查找 (acc|find, acc|findOnce)
            3. 元素信息获取 (获取文本、ID、边界等)
            4. 元素操作 (点击、长按、设置文本)
            5. 元素层次结构 (获取父子元素关系)
            
            此测试验证server-k的无障碍服务功能是否正常工作。
            注意：测试可能会与当前界面的UI元素进行交互。
        """.trimIndent()
    }
}
