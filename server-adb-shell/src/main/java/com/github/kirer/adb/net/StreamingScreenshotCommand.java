package com.github.kirer.adb.net;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.image.OptimizedImageProcessor;
import com.github.kirer.adb.screenshot.ScreenshotService;

import java.io.OutputStream;

/**
 * 流式截图命令
 * 使用优化的流式协议传输大图像数据
 */
public class StreamingScreenshotCommand implements ServiceCommand {
    
    private final ScreenshotService screenshotService;
    
    public StreamingScreenshotCommand(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
    }
    
    @Override
    public String getCommandName() {
        return "screenshot_stream";
    }
    
    @Override
    public String getHelp() {
        return "Take screenshot with streaming protocol: screenshot_stream [format] [quality]";
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
            int width = result != null ? result.width : 0;
            int height = result != null ? result.height : 0;
            
            // 创建流式传输结果
            StreamingResult streamingResult = new StreamingResult(
                imageBytes, format, width, height, result
            );
            
            String metadata = "";
            if (result != null) {
                metadata = String.format("format=%s,width=%d,height=%d,size=%d,time=%dms",
                    result.format, result.width, result.height, result.dataSize, result.processingTime);
            }
            
            Ln.i("Streaming screenshot captured: " + metadata);
            return CommandResult.streaming(streamingResult, "Streaming screenshot: " + metadata);
            
        } catch (Exception e) {
            Ln.e("Error executing streaming screenshot command", e);
            return CommandResult.error("Streaming screenshot failed: " + e.getMessage());
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
    
    /**
     * 流式传输结果
     */
    public static class StreamingResult {
        public final byte[] data;
        public final OptimizedImageProcessor.OutputFormat format;
        public final int width;
        public final int height;
        public final OptimizedImageProcessor.ProcessResult processResult;
        
        public StreamingResult(byte[] data, OptimizedImageProcessor.OutputFormat format,
                             int width, int height, OptimizedImageProcessor.ProcessResult processResult) {
            this.data = data;
            this.format = format;
            this.width = width;
            this.height = height;
            this.processResult = processResult;
        }
        
        /**
         * 使用流式协议发送数据
         */
        public void sendToStream(OutputStream out) throws Exception {
            // 转换格式
            StreamingProtocol.DataFormat protocolFormat = StreamingProtocol.convertFormat(format);
            
            // 发送元数据
            String metadata = String.format(
                "format=%s,width=%d,height=%d,size=%d,processing_time=%dms",
                format, width, height, data.length,
                processResult != null ? processResult.processingTime : 0
            );
            StreamingProtocol.sendMetadataMessage(out, metadata);
            
            // 发送图像数据
            StreamingProtocol.sendDataMessage(out, data, protocolFormat, width, height);
            
            Ln.i("Streaming data sent: " + metadata);
        }
    }
}
