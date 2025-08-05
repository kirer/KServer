package com.github.kirer.adb.commands;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.screenshot.ScreenshotService;

import java.io.FileOutputStream;

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
            if (params.length == 0) {
                return getBitmapResult();
            }
            // 有参数：保存到文件
            String outputPath = params[0];
            // 验证路径
            if (outputPath.trim().isEmpty()) {
                return CommandResult.error("Invalid output path");
            }
            return saveBitmapResult(outputPath);
        } catch (Exception e) {
            Ln.e("Error executing screenshot command", e);
            return CommandResult.error(e.getMessage());
        }
    }

    /**
     * 获取Bitmap字节数组结果
     */
    private CommandResult getBitmapResult() {
        long startTime = System.currentTimeMillis();
        byte[] bytes = screenshotService.getBitmapBytes();
        long duration = System.currentTimeMillis() - startTime;
        if (bytes != null) {
            String message = "Bitmap bytes captured in " + duration + "ms, bytes: " + bytes.length;
            return CommandResult.bytes(bytes, message);
        } else {
            return CommandResult.error("Failed to capture bitmap bytes after " + duration + "ms");
        }
    }

    /**
     * 保存到文件并返回布尔结果
     */
    private CommandResult saveBitmapResult(String outputPath) {
        byte[] bytes =  screenshotService.getBitmapBytes();
        if (bytes == null) {
            return CommandResult.bool(false, "Failed to capture bitmap bytes");
        }
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
            fos.write(bytes);
            fos.flush();
            String message = "Screenshot saved to " + outputPath + " (" + bytes.length + " bytes)";
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
