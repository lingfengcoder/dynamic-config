package com.lingfengx.mid.dynamic.config;

import java.util.concurrent.CompletableFuture;

/**
 * 配置源提供者接口
 * 定义配置源的基本能力
 */
public interface ConfigSourceProvider {
    
    /**
     * 获取配置源类型
     * @return 配置源类型标识
     */
    String getSourceType();
    
    /**
     * 检查配置源是否可用
     * @param location 配置位置
     * @return 是否可用
     */
    boolean isAvailable(String location);
    
    /**
     * 加载配置内容
     * @param location 配置位置
     * @return 配置内容
     */
    String loadConfig(String location);
    
    /**
     * 异步加载配置内容
     * @param location 配置位置
     * @return 配置内容Future
     */
    default CompletableFuture<String> loadConfigAsync(String location) {
        return CompletableFuture.supplyAsync(() -> loadConfig(location));
    }
    
    /**
     * 注册配置变更监听器
     * @param location 配置位置
     * @param listener 监听器
     */
    void registerListener(String location, DynamicValListener listener);
    
    /**
     * 移除配置变更监听器
     * @param location 配置位置
     * @param listener 监听器
     */
    default void removeListener(String location, DynamicValListener listener) {
        // 默认实现为空，子类可以重写
    }
    
    /**
     * 获取配置源描述信息
     * @return 描述信息
     */
    default String getDescription() {
        return "Config source provider for type: " + getSourceType();
    }
    
    /**
     * 获取配置源优先级（数值越小优先级越高）
     * @return 优先级
     */
    default int getPriority() {
        return 100;
    }
} 