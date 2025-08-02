package com.github.kirer.adb.net;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.image.OptimizedImageProcessor;
import com.github.kirer.adb.screenshot.ScreenshotService;

/**
 * 优化的截图命令
 * 支持多种输出格式和高性能传输
 */
public class OptimizedScreenshotCommand implements ServiceCommand {
    
    private final ScreenshotService screenshotService;
    
    public OptimizedScreenshotCommand(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
    }
    
    @Override
    public String getCommandName() {
        return "screenshot_opt";
    }
    
    @Override
    public String getHelp() {
        return "Take optimized screenshot with format options: screenshot_opt [format] [quality]";
    }
    
    @Override
    public CommandResult execute(String[] params) {
        if (screenshotService == null || !screenshotService.isRunning()) {
            return CommandResult.error("Screenshot service not available");
        }
        
        try {
            // 解析参数
            OptimizedImageProcessor.OutputFormat format = OptimizedImageProcessor.OutputFormat.PNG;
            int quality = 90;
            
            if (params.length > 0) {
                format = parseFormat(params[0]);
                if (format == null) {
                    return CommandResult.error("Invalid format: " + params[0] + 
                        ". Supported: raw, png, jpeg, webp");
                }
            }
            
            if (params.length > 1) {
                try {
                    quality = Integer.parseInt(params[1]);
                    if (quality < 0 || quality > 100) {
                        return CommandResult.error("Quality must be between 0-100");
                    }
                } catch (NumberFormatException e) {
                    return CommandResult.error("Invalid quality value: " + params[1]);
                }
            }
            
            // 设置输出格式
            screenshotService.setOutputFormat(format, quality);
            
            // 获取截图数据
            byte[] imageBytes = screenshotService.takeImageBytes();
            if (imageBytes == null) {
                return CommandResult.error("Failed to capture screenshot");
            }
            
            // 获取处理结果元数据
            OptimizedImageProcessor.ProcessResult result = screenshotService.getProcessResult();
            String metadata = "";
            if (result != null) {
                metadata = String.format("format=%s,width=%d,height=%d,size=%d,time=%dms",
                    result.format, result.width, result.height, result.dataSize, result.processingTime);
            }
            
            Ln.i("Optimized screenshot captured: " + metadata);
            return CommandResult.bytes(imageBytes, "Screenshot captured: " + metadata);
            
        } catch (Exception e) {
            Ln.e("Error executing optimized screenshot command", e);
            return CommandResult.error("Screenshot failed: " + e.getMessage());
        }
    }
    
    /**
     * 解析格式参数
     */
    private OptimizedImageProcessor.OutputFormat parseFormat(String formatStr) {
        if (formatStr == null) {
            return null;
        }
        
        switch (formatStr.toLowerCase()) {
            case "raw":
            case "rgba":
                return OptimizedImageProcessor.OutputFormat.RAW_RGBA;
            case "png":
                return OptimizedImageProcessor.OutputFormat.PNG;
            case "jpeg":
            case "jpg":
                return OptimizedImageProcessor.OutputFormat.JPEG;
            case "webp":
                return OptimizedImageProcessor.OutputFormat.WEBP;
            default:
                return null;
        }
    }
}
