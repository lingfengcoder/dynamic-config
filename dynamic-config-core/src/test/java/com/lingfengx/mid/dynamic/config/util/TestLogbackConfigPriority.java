package com.lingfengx.mid.dynamic.config.util;

/**
 * 测试 EarlyLogUtil 是否正确优先使用项目 classpath 中的 logback 配置文件
 */
public class TestLogbackConfigPriority {
    
    public static void main(String[] args) {
        System.out.println("=== 测试 EarlyLogUtil 配置文件优先级 ===");
        
        // 启用调试模式
        System.setProperty("dynamic.config.debug", "true");
        
        // 输出启动信息
        EarlyLogUtil.logStartupInfo();
        
        // 测试日志输出
        System.out.println("\n=== 测试日志输出 ===");
        EarlyLogUtil.debug("这是一条调试日志");
        EarlyLogUtil.info("这是一条信息日志");
        EarlyLogUtil.warn("这是一条警告日志");
        EarlyLogUtil.error("这是一条错误日志");
        
        // 检查配置状态
        System.out.println("\n=== 配置状态检查 ===");
        System.out.println("Logback 是否已初始化: " + EarlyLogUtil.isLogbackInitialized());
        System.out.println("是否使用项目配置: " + EarlyLogUtil.isUsingProjectConfig());
        System.out.println("当前使用的配置文件: " + EarlyLogUtil.getCurrentLogbackConfig());
        System.out.println("配置的日志级别: " + EarlyLogUtil.getConfiguredLevel());
        
        // 检查 classpath 中的配置文件
        System.out.println("\n=== Classpath 配置文件检查 ===");
        String[] configFiles = {
            "logback-spring.xml",
            "logback.xml",
            "logback-test.xml"
        };
        
        for (String configFile : configFiles) {
            boolean exists = TestLogbackConfigPriority.class.getClassLoader().getResource(configFile) != null;
            System.out.println("配置文件 " + configFile + " 存在: " + exists);
        }
        
        System.out.println("\n=== 测试完成 ===");
    }
} 