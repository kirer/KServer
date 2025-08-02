package com.github.kirer.adb.commands;

import android.graphics.Bitmap;
import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.net.CommandResult;
import com.github.kirer.adb.net.ServiceCommand;
import com.github.kirer.adb.screenshot.ScreenshotService;

/**
 * 截图命令实现 - 支持保存文件和返回bitmap字节数组
 */
public class ScreenshotCommand implements ServiceCommand {
    
    private final ScreenshotService screenshotService;
    
    public ScreenshotCommand(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
    }
    
    @Override
    public CommandResult execute(String[] params) {
        try {
            // 检查服务状态
            if (!screenshotService.isRunning()) {
                return CommandResult.error("Screenshot service is not running");
            }

            if (params.length == 0) {
                // 无参数：返回bitmap字节数组
                return getBitmapResult();
            } else {
                // 有参数：保存到文件
                String outputPath = params[0];

                // 验证路径
                if (outputPath.trim().isEmpty()) {
                    return CommandResult.error("Invalid output path");
                }

                return saveToFileResult(outputPath);
            }

        } catch (Exception e) {
            Ln.e("Error executing screenshot command", e);
            return CommandResult.error(e.getMessage());
        }
    }

    /**
     * 获取PNG字节数组
     */
    private byte[] capturePngBytes() {
        return screenshotService.takePngBytes();
    }

    /**
     * 获取PNG字节数组结果
     */
    private CommandResult getBitmapResult() {
        long startTime = System.currentTimeMillis();
        byte[] pngBytes = capturePngBytes();
        long duration = System.currentTimeMillis() - startTime;

        if (pngBytes != null) {
            String message = "PNG bytes captured in " + duration + "ms, bytes: " + pngBytes.length;
            return CommandResult.bytes(pngBytes, message);
        } else {
            return CommandResult.error("Failed to capture PNG bytes after " + duration + "ms");
        }
    }

    /**
     * 保存到文件并返回布尔结果
     */
    private CommandResult saveToFileResult(String outputPath) {
        byte[] pngBytes = capturePngBytes();
        if (pngBytes == null) {
            return CommandResult.bool(false, "Failed to capture PNG bytes");
        }

        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outputPath)) {
            fos.write(pngBytes);
            fos.flush();

            String message = "Screenshot saved to " + outputPath + " (" + pngBytes.length + " bytes)";
            return CommandResult.bool(true, message);
        } catch (Exception e) {
            Ln.e("Failed to save screenshot", e);
            return CommandResult.bool(false, "Failed to save screenshot: " + e.getMessage());
        }
    }
    
    @Override
    public String getCommandName() {
        return "screenshot";
    }
    
    @Override
    public String getHelp() {
        return "Take a screenshot. Usage: screenshot [output_path] - with path: save to file, without path: return bitmap data";
    }
}
