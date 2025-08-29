package com.lingfengx.mid.dynamic.config.zookeeper.example;

import com.lingfengx.mid.dynamic.config.ann.DynamicVal;
import org.springframework.stereotype.Component;

/**
 * Zookeeper配置使用示例
 */
@Component
public class ZookeeperConfigExample {
    
    // 从Zookeeper加载配置
    @DynamicVal("zookeeper://localhost:2181/config/app")
    private String appConfig;
    
    // 从Zookeeper集群加载配置
    @DynamicVal("zookeeper://zk1:2181,zk2:2181,zk3:2181/config/database")
    private String databaseConfig;
    
    // 从Zookeeper加载用户配置
    @DynamicVal("zookeeper://localhost:2181/config/user")
    private String userConfig;
    
    public String getAppConfig() {
        return appConfig;
    }
    
    public String getDatabaseConfig() {
        return databaseConfig;
    }
    
    public String getUserConfig() {
        return userConfig;
    }
    
    public void printConfigs() {
        System.out.println("=== Zookeeper Config Example ===");
        System.out.println("App config: " + appConfig);
        System.out.println("Database config: " + databaseConfig);
        System.out.println("User config: " + userConfig);
    }
} 