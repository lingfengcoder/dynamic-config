package com.lingfengx.mid.dynamic.config;

import com.lingfengx.mid.dynamic.config.util.EarlyLogUtil;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;

import java.util.List;
import java.util.ServiceLoader;

/**
 * 配置源扫描器
 * 用于在配置加载前自动发现和注册ConfigSourceProvider实现类
 */
public class ConfigSourceScanner {
    private static final String CONFIG_SOURCE_PROVIDER_INTERFACE = "com.lingfengx.mid.dynamic.config.ConfigSourceProvider";
    
    /**
     * 扫描并注册所有可用的配置源提供者
     */
    public static void scanAndRegisterConfigSources() {
        EarlyLogUtil.info("Starting to scan and register config source providers...");
        
        try {
            // 方法1: 使用ServiceLoader机制
            scanWithServiceLoader();
            
            // 方法2: 使用SpringFactoriesLoader机制
            scanWithSpringFactoriesLoader();
            
            // 方法3: 手动扫描已知的实现类
            scanKnownImplementations();
            
            EarlyLogUtil.info("Config source scanning completed. Registered providers: " + 
                    ConfigSourceRegistry.getRegisteredSourceTypes());
                    
        } catch (Exception e) {
            EarlyLogUtil.error("Failed to scan and register config sources", e);
        }
    }
    
    /**
     * 使用ServiceLoader机制扫描
     */
    private static void scanWithServiceLoader() {
        try {
            if (ClassUtils.isPresent(CONFIG_SOURCE_PROVIDER_INTERFACE, null)) {
                ServiceLoader<ConfigSourceProvider> serviceLoader = 
                    ServiceLoader.load(ConfigSourceProvider.class);
                
                for (ConfigSourceProvider provider : serviceLoader) {
                    registerProvider(provider);
                }
                
                EarlyLogUtil.debug("ServiceLoader found " + ConfigSourceRegistry.getRegisteredSourceTypes().size() + " config source providers");
            }
        } catch (Exception e) {
            EarlyLogUtil.debug("ServiceLoader scanning failed, trying other methods: " + e.getMessage());
        }
    }
    
    /**
     * 使用SpringFactoriesLoader机制扫描
     */
    private static void scanWithSpringFactoriesLoader() {
        try {
            List<ConfigSourceProvider> providers = SpringFactoriesLoader.loadFactories(
                    ConfigSourceProvider.class, null);
            
            for (ConfigSourceProvider provider : providers) {
                registerProvider(provider);
            }
            
            EarlyLogUtil.debug("SpringFactoriesLoader found " + ConfigSourceRegistry.getRegisteredSourceTypes().size() + " config source providers");
                    
        } catch (Exception e) {
            EarlyLogUtil.debug("SpringFactoriesLoader scanning failed, trying other methods: " + e.getMessage());
        }
    }
    
    /**
     * 手动扫描已知的实现类
     */
    private static void scanKnownImplementations() {
        // 定义已知的ConfigSourceProvider实现类
        String[] knownProviders = {
            "com.lingfengx.mid.dynamic.config.local.provider.LocalFileConfigSourceProvider",
            "com.lingfengx.mid.dynamic.config.local.provider.HttpRemoteFileConfigSourceProvider", 
            "com.lingfengx.mid.dynamic.config.zookeeper.provider.ZookeeperConfigSourceProvider"
        };
        
        for (String className : knownProviders) {
            try {
                if (ClassUtils.isPresent(className, null)) {
                    Class<?> clazz = ClassUtils.forName(className, null);
                    if (ConfigSourceProvider.class.isAssignableFrom(clazz)) {
                        ConfigSourceProvider provider = (ConfigSourceProvider) clazz.getDeclaredConstructor().newInstance();
                        registerProvider(provider);
                    }
                }
            } catch (Exception e) {
                EarlyLogUtil.debug("Failed to instantiate known provider: " + className + " - " + e.getMessage());
            }
        }
    }
    
    /**
     * 注册配置源提供者
     */
    private static void registerProvider(ConfigSourceProvider provider) {
        try {
            ConfigSourceRegistry.registerProvider(provider.getSourceType(), provider);
            EarlyLogUtil.info("Registered config source provider: " + provider.getSourceType() + " - " + provider.getDescription());
        } catch (Exception e) {
            EarlyLogUtil.warn("Failed to register config source provider: " + provider.getSourceType());
        }
    }
    
    /**
     * 检查是否有已注册的配置源提供者
     */
    public static boolean hasRegisteredProviders() {
        return !ConfigSourceRegistry.getRegisteredSourceTypes().isEmpty();
    }
    
    /**
     * 获取已注册的配置源提供者数量
     */
    public static int getRegisteredProviderCount() {
        return ConfigSourceRegistry.getRegisteredSourceTypes().size();
    }
} 