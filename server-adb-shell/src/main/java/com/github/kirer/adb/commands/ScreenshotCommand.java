package com.github.kirer.adb.commands;

import com.genymobile.scrcpy.util.Ln;
import com.github.kirer.adb.net.ServiceCommand;
import com.github.kirer.adb.screenshot.ScreenshotService;

/**
 * 截图命令实现
 */
public class ScreenshotCommand implements ServiceCommand {
    
    private final ScreenshotService screenshotService;
    
    public ScreenshotCommand(ScreenshotService screenshotService) {
        this.screenshotService = screenshotService;
    }
    
    @Override
    public String execute(String[] params) {
        try {
            if (params.length == 0) {
                return "ERROR Missing output path. Usage: screenshot <output_path>";
            }
            
            String outputPath = params[0];
            
            // 验证路径
            if (outputPath.trim().isEmpty()) {
                return "ERROR Invalid output path";
            }
            
            // 执行截图
            screenshotService.saveScreenshot(outputPath);
            
            return "OK Screenshot saved to " + outputPath;
            
        } catch (Exception e) {
            Ln.e("Error executing screenshot command", e);
            return "ERROR " + e.getMessage();
        }
    }
    
    @Override
    public String getCommandName() {
        return "screenshot";
    }
    
    @Override
    public String getHelp() {
        return "Take a screenshot and save to specified path. Usage: screenshot <output_path>";
    }
}
