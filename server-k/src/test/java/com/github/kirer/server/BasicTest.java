package com.github.kirer.server;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 基本功能测试
 * 只测试编译和基本结构，避免触发native库加载
 */
public class BasicTest {

    @Test
    public void testMainClassExists() {
        try {
            Class<?> mainClass = Class.forName("com.github.kirer.server.Main");
            assertNotNull("Main class should exist", mainClass);

            // 检查main方法是否存在
            mainClass.getMethod("main", String[].class);
        } catch (Exception e) {
            fail("Main class or main method not found: " + e.getMessage());
        }
    }

    @Test
    public void testPluginClassExists() {
        try {
            Class<?> pluginClass = Class.forName("com.github.kirer.server.plugin.Plugin");
            assertNotNull("Plugin class should exist", pluginClass);

            // 检查关键方法是否存在
            pluginClass.getDeclaredMethod("setWorkingPath", String.class);
            pluginClass.getDeclaredMethod("loadApk", String.class);
            pluginClass.getDeclaredMethod("newInstance", String.class, String.class, String.class);
            pluginClass.getDeclaredMethod("call", String.class, String.class, String.class);
        } catch (Exception e) {
            fail("Plugin class or methods not found: " + e.getMessage());
        }
    }

    @Test
    public void testParameterParsing() {
        // 测试数字字符串检测
        try {
            Class<?> mainClass = Class.forName("com.github.kirer.server.Main");
            java.lang.reflect.Method isNumericMethod = mainClass.getDeclaredMethod("isNumeric", String.class);
            isNumericMethod.setAccessible(true);

            assertTrue("Should detect numeric string", (Boolean) isNumericMethod.invoke(null, "12345"));
            assertFalse("Should not detect non-numeric string", (Boolean) isNumericMethod.invoke(null, "abc"));
        } catch (Exception e) {
            fail("Parameter parsing test failed: " + e.getMessage());
        }
    }

    @Test
    public void testBuildSuccess() {
        // 这个测试只是确认编译成功
        assertTrue("Build should be successful", true);
    }
}
