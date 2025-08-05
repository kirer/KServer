package com.github.kirer.server.commands;

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
        CommandResult result = execute(request);
        return result.toStringResponse();
    }

    /**
     * 处理命令请求并返回结果对象
     */
    public CommandResult execute(String request) {
        if (request == null || request.trim().isEmpty()) {
            return CommandResult.error("Empty command");
        }

        try {
            String[] parts = request.trim().split("\\s+");
            String commandName = parts[0].toLowerCase();
            String[] params = new String[parts.length - 1];
            System.arraycopy(parts, 1, params, 0, params.length);

            Ln.d("Processing command: " + commandName);

            // 内置命令
            if ("help".equals(commandName)) {
                return CommandResult.success(handleHelpCommand().substring(3)); // 移除 "OK " 前缀
            }

            if ("status".equals(commandName)) {
                return CommandResult.success("Service running with " + commands.size() + " commands");
            }

            // 查找并执行注册的命令
            ServiceCommand command = commands.get(commandName);
            if (command == null) {
                return CommandResult.error("Unknown command: " + commandName);
            }

            // 执行命令
            return command.execute(params);

        } catch (Exception e) {
            Ln.e("Error processing command: " + request, e);
            return CommandResult.error(e.getMessage());
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
