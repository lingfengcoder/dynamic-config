package com.lingfengx.mid.dynamic.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置源注册器
 * 用于注册和管理不同的配置源能力
 */
public class ConfigSourceRegistry {
    
    private static final Map<String, ConfigSourceProvider> sourceProviders = new ConcurrentHashMap<>();
    private static final Map<String, ConfigSourceValidator> sourceValidators = new ConcurrentHashMap<>();
    
    /**
     * 注册配置源提供者
     * @param sourceType 配置源类型
     * @param provider 配置源提供者
     */
    public static void registerProvider(String sourceType, ConfigSourceProvider provider) {
        sourceProviders.put(sourceType, provider);
    }
    
    /**
     * 注册配置源验证器
     * @param sourceType 配置源类型
     * @param validator 配置源验证器
     */
    public static void registerValidator(String sourceType, ConfigSourceValidator validator) {
        sourceValidators.put(sourceType, validator);
    }
    
    /**
     * 获取配置源提供者
     * @param sourceType 配置源类型
     * @return 配置源提供者
     */
    public static ConfigSourceProvider getProvider(String sourceType) {
        return sourceProviders.get(sourceType);
    }
    
    /**
     * 获取配置源验证器
     * @param sourceType 配置源类型
     * @return 配置源验证器
     */
    public static ConfigSourceValidator getValidator(String sourceType) {
        return sourceValidators.get(sourceType);
    }
    
    /**
     * 检查配置源类型是否已注册
     * @param sourceType 配置源类型
     * @return 是否已注册
     */
    public static boolean isRegistered(String sourceType) {
        return sourceProviders.containsKey(sourceType);
    }
    
    /**
     * 获取所有已注册的配置源类型
     * @return 配置源类型集合
     */
    public static java.util.Set<String> getRegisteredSourceTypes() {
        return sourceProviders.keySet();
    }
    
    /**
     * 清除所有注册的配置源
     */
    public static void clear() {
        sourceProviders.clear();
        sourceValidators.clear();
    }
} 