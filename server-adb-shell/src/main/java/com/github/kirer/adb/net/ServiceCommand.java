package com.github.kirer.adb.net;

/**
 * 服务命令接口
 * 所有服务命令都需要实现此接口，提供统一的命令处理方式
 * 支持返回不同类型的数据（字符串、字节数组、布尔值等）
 */
public interface ServiceCommand {

    /**
     * 执行命令并返回结果对象
     *
     * @param params 命令参数数组
     * @return 命令执行结果，包含类型和数据
     */
    CommandResult execute(String[] params);

    /**
     * 获取命令名称
     *
     * @return 命令名称（小写）
     */
    String getCommandName();

    /**
     * 获取命令帮助信息
     *
     * @return 命令使用说明
     */
    String getHelp();
}
