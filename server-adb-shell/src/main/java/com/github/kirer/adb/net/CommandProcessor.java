package com.github.kirer.adb.net;

import com.genymobile.scrcpy.util.Ln;

import java.util.HashMap;
import java.util.Map;

/**
 * 命令处理器
 * 负责注册、路由和执行各种服务命令
 */
public class CommandProcessor {
    
    private final Map<String, ServiceCommand> commands = new HashMap<>();
    
    /**
     * 注册命令
     */
    public void registerCommand(ServiceCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        
        String commandName = command.getCommandName().toLowerCase();
        commands.put(commandName, command);
        Ln.d("Registered command: " + commandName);
    }
    
    /**
     * 处理命令请求
     */
    public String processCommand(String request) {
        if (request == null || request.trim().isEmpty()) {
            return "ERROR Empty command";
        }
        
        try {
            String[] parts = request.trim().split("\\s+");
            String commandName = parts[0].toLowerCase();
            String[] params = new String[parts.length - 1];
            System.arraycopy(parts, 1, params, 0, params.length);
            
            Ln.d("Processing command: " + commandName);
            
            // 内置命令
            if ("help".equals(commandName)) {
                return handleHelpCommand();
            }
            
            if ("status".equals(commandName)) {
                return "OK Service running with " + commands.size() + " commands";
            }
            
            // 查找并执行注册的命令
            ServiceCommand command = commands.get(commandName);
            if (command == null) {
                return "ERROR Unknown command: " + commandName;
            }
            
            return command.execute(params);
            
        } catch (Exception e) {
            Ln.e("Error processing command: " + request, e);
            return "ERROR " + e.getMessage();
        }
    }
    
    private String handleHelpCommand() {
        StringBuilder sb = new StringBuilder("Available commands:\n");
        sb.append("  help - Show this help\n");
        sb.append("  status - Show service status\n");
        
        for (ServiceCommand command : commands.values()) {
            sb.append("  ").append(command.getCommandName())
              .append(" - ").append(command.getHelp()).append("\n");
        }
        
        return "OK " + sb.toString();
    }
}
