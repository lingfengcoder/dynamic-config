package com.lingfengx.mid.dynamic.config;

import com.lingfengx.mid.dynamic.config.util.EarlyLogUtil;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 配置源选择器
 * 负责根据配置信息选择最合适的配置源提供者
 */
public class ConfigSourceSelector {
    
    /**
     * 根据配置信息选择最合适的配置源提供者
     * 
     * @param file 配置文件路径
     * @param sourceType 指定的配置源类型
     * @param fileType 文件类型
     * @return 最合适的配置源提供者
     */
    public static ConfigSourceProvider selectBestProvider(String file, String sourceType, String fileType) {
        if (!StringUtils.hasText(file)) {
            EarlyLogUtil.warn("[dynamic-config] file path is empty, cannot select provider");
            return null;
        }
        
        // 1. 如果指定了配置源类型，优先使用指定的类型
        if (StringUtils.hasText(sourceType)) {
            ConfigSourceProvider specifiedProvider = findProviderBySourceType(sourceType);
            if (specifiedProvider != null) {
                EarlyLogUtil.debug("[dynamic-config] using specified source type: " + sourceType + " for file: " + file);
                return specifiedProvider;
            } else {
                EarlyLogUtil.warn("[dynamic-config] specified source type not available: " + sourceType + " for file: " + file);
            }
        }
        
        // 2. 根据文件路径自动推断最合适的配置源
        ConfigSourceProvider inferredProvider = inferProviderFromLocation(file);
        if (inferredProvider != null) {
            EarlyLogUtil.debug("[dynamic-config] auto-detected source type: " + inferredProvider.getSourceType() + " for file: " + file);
            return inferredProvider;
        }
        
        // 3. 如果无法推断，尝试使用默认的本地文件配置源
        ConfigSourceProvider defaultProvider = findProviderBySourceType("local");
        if (defaultProvider != null) {
            EarlyLogUtil.debug("[dynamic-config] using default local source type for file: " + file);
            return defaultProvider;
        }
        
        EarlyLogUtil.warn("[dynamic-config] no suitable provider found for file: " + file);
        return null;
    }
    
    /**
     * 根据配置源类型查找提供者
     */
    private static ConfigSourceProvider findProviderBySourceType(String sourceType) {
        try {
            return ConfigSourceRegistry.getProvider(sourceType);
        } catch (Exception e) {
            EarlyLogUtil.debug("[dynamic-config] failed to get provider for source type: " + sourceType);
            return null;
        }
    }
    
    /**
     * 根据文件路径推断最合适的配置源
     */
    private static ConfigSourceProvider inferProviderFromLocation(String location) {
        if (!StringUtils.hasText(location)) {
            return null;
        }
        
        try {
            // 获取所有可用的配置源提供者
            List<ConfigSourceProvider> providers = ConfigSourceManager.getAllProviders();
            
            // 找到所有支持该位置的提供者
            List<ConfigSourceProvider> availableProviders = new ArrayList<>();
            for (ConfigSourceProvider provider : providers) {
                if (provider.isAvailable(location)) {
                    availableProviders.add(provider);
                }
            }
            
            // 按优先级排序
            availableProviders.sort(Comparator.comparingInt(ConfigSourceProvider::getPriority));
            
            // 返回优先级最高的提供者
            return availableProviders.isEmpty() ? null : availableProviders.get(0);
            
        } catch (Exception e) {
            EarlyLogUtil.debug("[dynamic-config] failed to infer provider from location: " + location);
            return null;
        }
    }
    
    /**
     * 检查配置源是否可用
     */
    public static boolean isProviderAvailable(String sourceType) {
        ConfigSourceProvider provider = findProviderBySourceType(sourceType);
        return provider != null;
    }
    
    /**
     * 获取所有可用的配置源类型
     */
    public static List<String> getAvailableSourceTypes() {
        try {
            List<ConfigSourceProvider> providers = ConfigSourceManager.getAllProviders();
            List<String> sourceTypes = new ArrayList<>();
            for (ConfigSourceProvider provider : providers) {
                sourceTypes.add(provider.getSourceType());
            }
            return sourceTypes;
        } catch (Exception e) {
            EarlyLogUtil.debug("[dynamic-config] failed to get available source types");
            return Collections.emptyList();
        }
    }
    
    /**
     * 验证配置源选择
     */
    public static boolean validateProviderSelection(String file, String sourceType) {
        if (StringUtils.hasText(sourceType)) {
            // 验证指定的配置源类型是否可用
            if (!isProviderAvailable(sourceType)) {
                EarlyLogUtil.warn("[dynamic-config] specified source type is not available: " + sourceType);
                return false;
            }
            
            // 验证指定的配置源是否支持该文件位置
            ConfigSourceProvider provider = findProviderBySourceType(sourceType);
            if (provider != null && !provider.isAvailable(file)) {
                EarlyLogUtil.warn("[dynamic-config] specified source type " + sourceType + " does not support file: " + file);
                return false;
            }
        }
        
        return true;
    }
} 