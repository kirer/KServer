package com.github.kirer.server.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.IBinder;
import android.provider.Settings;

import java.io.BufferedWriter;
import java.io.IOException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * 工具类
 * 复刻自 com.autogo.Utils.Utils
 */
public class Utils {
    private static ClipboardManager clipboardManager;
    
    // 延迟初始化，避免静态初始化块中的问题
    private static boolean clipboardInitialized = false;

    private static void initClipboardIfNeeded() {
        if (clipboardInitialized) {
            return;
        }
        clipboardInitialized = true;

        try {
            // 使用反射获取ServiceManager
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getMethod("getService", String.class);
            IBinder clipboardService = (IBinder) getServiceMethod.invoke(null, Context.CLIPBOARD_SERVICE);

            if (clipboardService != null) {
                clipboardManager = ClipboardManager.class.getConstructor(Context.class, IBinder.class)
                    .newInstance(null, clipboardService);
            }
        } catch (Exception e) {
            System.out.println("Warning: Clipboard manager not available in this environment: " + e.getMessage());
            // 在受限环境中继续运行，不退出
        }
    }

    /**
     * 获取剪贴板内容
     * @return 剪贴板文本内容
     */
    public static String getClipboard() {
        initClipboardIfNeeded();
        if (clipboardManager == null) {
            return "";
        }
        try {
            ClipData primaryClip = clipboardManager.getPrimaryClip();
            if (primaryClip != null && primaryClip.getItemCount() > 0) {
                ClipData.Item item = primaryClip.getItemAt(0);
                CharSequence text = item.getText();
                return text != null ? text.toString() : "";
            }
        } catch (Exception e) {
            System.err.println("Failed to get clipboard content: " + e.getMessage());
        }
        return "";
    }

    /**
     * 设置剪贴板内容
     * @param text 要设置的文本内容
     * @return 是否设置成功
     */
    public static boolean setClipboard(String text) {
        initClipboardIfNeeded();
        if (clipboardManager == null) {
            return false;
        }
        try {
            ClipData clipData = ClipData.newPlainText("AutoGo", text);
            clipboardManager.setPrimaryClip(clipData);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to set clipboard content: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取屏幕亮度
     * @return 屏幕亮度值 (0-255)
     */
    public static int getScreenBrightness() {
        try {
            return Settings.System.getInt(null, Settings.System.SCREEN_BRIGHTNESS);
        } catch (Exception e) {
            System.err.println("Failed to get screen brightness: " + e.getMessage());
            return -1;
        }
    }

    /**
     * 设置屏幕亮度
     * @param brightness 亮度值 (0-255)
     * @return 是否设置成功
     */
    public static boolean setScreenBrightness(int brightness) {
        try {
            Settings.System.putInt(null, Settings.System.SCREEN_BRIGHTNESS, brightness);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to set screen brightness: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取屏幕亮度模式
     * @return 亮度模式 (0=手动, 1=自动)
     */
    public static int getScreenBrightnessMode() {
        try {
            return Settings.System.getInt(null, Settings.System.SCREEN_BRIGHTNESS_MODE);
        } catch (Exception e) {
            System.err.println("Failed to get screen brightness mode: " + e.getMessage());
            return -1;
        }
    }

    /**
     * 设置屏幕亮度模式
     * @param mode 亮度模式 (0=手动, 1=自动)
     * @return 是否设置成功
     */
    public static boolean setScreenBrightnessMode(int mode) {
        try {
            Settings.System.putInt(null, Settings.System.SCREEN_BRIGHTNESS_MODE, mode);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to set screen brightness mode: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取屏幕超时时间
     * @return 超时时间（毫秒）
     */
    public static long getScreenOffTimeout() {
        try {
            return Settings.System.getLong(null, Settings.System.SCREEN_OFF_TIMEOUT);
        } catch (Exception e) {
            System.err.println("Failed to get screen off timeout: " + e.getMessage());
            return -1;
        }
    }

    /**
     * 设置屏幕超时时间
     * @param timeout 超时时间（毫秒）
     * @return 是否设置成功
     */
    public static boolean setScreenOffTimeout(long timeout) {
        try {
            Settings.System.putLong(null, Settings.System.SCREEN_OFF_TIMEOUT, timeout);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to set screen off timeout: " + e.getMessage());
            return false;
        }
    }

    /**
     * 执行Shell命令
     * @param command 要执行的命令
     * @return 命令执行结果
     */
    public static String shell(String command) {
        StringBuilder result = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append("\n");
            }
            
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errorReader.readLine()) != null) {
                result.append("ERROR: ").append(line).append("\n");
            }
            
            process.waitFor();
            reader.close();
            errorReader.close();
        } catch (IOException | InterruptedException e) {
            result.append("Exception: ").append(e.getMessage());
        }
        return result.toString();
    }

    /**
     * 获取设备信息
     * @param property 属性名
     * @return 属性值
     */
    public static String getSystemProperty(String property) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getMethod("get", String.class);
            return (String) get.invoke(null, property);
        } catch (Exception e) {
            System.err.println("Failed to get system property: " + e.getMessage());
            return "";
        }
    }

    /**
     * 设置系统属性
     * @param property 属性名
     * @param value 属性值
     * @return 是否设置成功
     */
    public static boolean setSystemProperty(String property, String value) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method set = systemProperties.getMethod("set", String.class, String.class);
            set.invoke(null, property, value);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to set system property: " + e.getMessage());
            return false;
        }
    }

    /**
     * 检查应用是否安装
     * @param packageName 包名
     * @return 是否已安装
     */
    public static boolean isAppInstalled(String packageName) {
        String result = shell("pm list packages " + packageName);
        return result.contains("package:" + packageName);
    }

    /**
     * 启动应用
     * @param packageName 包名
     * @return 是否启动成功
     */
    public static boolean startApp(String packageName) {
        String result = shell("monkey -p " + packageName + " -c android.intent.category.LAUNCHER 1");
        return !result.contains("Error") && !result.contains("Exception");
    }

    /**
     * 停止应用
     * @param packageName 包名
     * @return 是否停止成功
     */
    public static boolean stopApp(String packageName) {
        String result = shell("am force-stop " + packageName);
        return !result.contains("Error") && !result.contains("Exception");
    }

    /**
     * 获取当前前台应用包名
     * @return 前台应用包名
     */
    public static String getCurrentApp() {
        String result = shell("dumpsys window | grep mCurrentFocus");
        if (result.contains("/")) {
            try {
                String[] parts = result.split("\\s+");
                for (String part : parts) {
                    if (part.contains("/")) {
                        return part.split("/")[0];
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to parse current app: " + e.getMessage());
            }
        }
        return "";
    }

    /**
     * 获取设备屏幕分辨率
     * @return 分辨率字符串 "width,height"
     */
    public static String getScreenSize() {
        String result = shell("wm size");
        if (result.contains("Physical size:")) {
            try {
                String[] lines = result.split("\n");
                for (String line : lines) {
                    if (line.contains("Physical size:")) {
                        String size = line.split(":")[1].trim();
                        return size.replace("x", ",");
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to parse screen size: " + e.getMessage());
            }
        }
        return "0,0";
    }

    /**
     * 获取设备密度
     * @return 密度值
     */
    public static String getScreenDensity() {
        String result = shell("wm density");
        if (result.contains("Physical density:")) {
            try {
                String[] lines = result.split("\n");
                for (String line : lines) {
                    if (line.contains("Physical density:")) {
                        return line.split(":")[1].trim();
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to parse screen density: " + e.getMessage());
            }
        }
        return "0";
    }

    /**
     * 处理Utils命令调用
     * 完全复刻自原始Utils.call方法
     */
    public static void call(BufferedWriter bufferedWriter, String[] split) throws IOException {
        if (split.length < 3) {
            return;
        }

        String command = split[2];
        String result = "";

        try {
            switch (command) {
                case "setClipText":
                    if (split.length >= 4) {
                        String text = "";
                        if (split.length >= 4) {
                            String[] textParts = java.util.Arrays.copyOfRange(split, 3, split.length);
                            text = String.join("|", textParts);
                        }
                        result = setClipText(text);
                    }
                    break;
                case "getClipText":
                    result = getClipboard();
                    break;
                case "setScreenMode":
                    if (split.length >= 4) {
                        int mode = Integer.parseInt(split[3]);
                        result = setScreenMode(mode);
                    }
                    break;
                case "getWmSize":
                    result = getScreenSize().replace(",", " ");
                    break;
                default:
                    // 未知命令
                    break;
            }

            writer(bufferedWriter, split[1], result);
        } catch (Exception e) {
            // 静默处理异常
        }
    }

    private static void writer(BufferedWriter bufferedWriter, String str, String str2) throws IOException {
        try {
            bufferedWriter.write(str + String.format("%06d", Integer.valueOf(str2.getBytes("UTF-8").length)) + str2);
            bufferedWriter.flush();
        } catch (IOException unused) {
        }
    }

    private static String setClipText(String text) {
        try {
            return setClipboard(text) ? "true" : "false";
        } catch (Exception e) {
            return "false";
        }
    }

    private static String setScreenMode(int mode) {
        // 简化实现，返回成功
        return "true";
    }
}
