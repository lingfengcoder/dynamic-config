package com.lingfengx.mid.dynamic.config;

import com.lingfengx.mid.dynamic.config.util.EarlyLogUtil;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 配置源管理器
 * 统一管理和使用所有注册的配置源
 */
public class ConfigSourceManager {

    /**
     * 根据配置位置自动选择合适的配置源
     *
     * @param location 配置位置
     * @return 配置内容
     */
    public static String loadConfig(String location) {
        if (!StringUtils.hasText(location)) {
            throw new IllegalArgumentException("Configuration location cannot be empty");
        }

        ConfigSourceProvider provider = findBestProvider(location);
        if (provider == null) {
            throw new RuntimeException("No suitable config source provider found for location: " + location);
        }

        // 验证配置源
        ConfigSourceValidator validator = ConfigSourceRegistry.getValidator(provider.getSourceType());
        if (validator != null) {
            ConfigSourceValidator.ValidationResult result = validator.validateSource(location);
            if (!result.isValid()) {
                throw new RuntimeException("Config source validation failed: " + result.getMessage());
            }
        }

        try {
            String content = provider.loadConfig(location);

            // 验证配置内容
            if (validator != null) {
                ConfigSourceValidator.ValidationResult result = validator.validateContent(content, location);
                if (!result.isValid()) {
                    throw new RuntimeException("Config content validation failed: " + result.getMessage());
                }
            }

            EarlyLogUtil.info("Successfully loaded config from " + location + " using provider: " + provider.getSourceType());
            return content;

        } catch (Exception e) {
            EarlyLogUtil.error("Failed to load config from " + location + " using provider: " + provider.getSourceType(), e);
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    /**
     * 异步加载配置
     *
     * @param location 配置位置
     * @return 配置内容Future
     */
    public static CompletableFuture<String> loadConfigAsync(String location) {
        if (!StringUtils.hasText(location)) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalArgumentException("Configuration location cannot be empty"));
            return future;
        }

        ConfigSourceProvider provider = findBestProvider(location);
        if (provider == null) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("No suitable config source provider found for location: " + location));
            return future;
        }

        return provider.loadConfigAsync(location)
                .thenApply(content -> {
                    // 验证配置内容
                    ConfigSourceValidator validator = ConfigSourceRegistry.getValidator(provider.getSourceType());
                    if (validator != null) {
                        ConfigSourceValidator.ValidationResult result = validator.validateContent(content, location);
                        if (!result.isValid()) {
                            throw new RuntimeException("Config content validation failed: " + result.getMessage());
                        }
                    }
                    return content;
                });
    }

    /**
     * 注册配置变更监听器
     *
     * @param location 配置位置
     * @param listener 监听器
     */
    public static void registerListener(String location, DynamicValListener listener) {
        ConfigSourceProvider provider = findBestProvider(location);
        if (provider == null) {
            throw new RuntimeException("No suitable config source provider found for location: " + location);
        }

        //立马加载一次配置并回调 listener
        String data = provider.loadConfig(location);
        listener.onChange(data);

        provider.registerListener(location, listener);
        EarlyLogUtil.info("Registered listener for location: " + location + " using provider: " + provider.getSourceType());
    }

    /**
     * 移除配置变更监听器
     *
     * @param location 配置位置
     * @param listener 监听器
     */
    public static void removeListener(String location, DynamicValListener listener) {
        ConfigSourceProvider provider = findBestProvider(location);
        if (provider != null) {
            provider.removeListener(location, listener);
            EarlyLogUtil.info("Removed listener for location: " + location + " using provider: " + provider.getSourceType());
        }
    }

    /**
     * 检查配置位置是否支持
     *
     * @param location 配置位置
     * @return 是否支持
     */
    public static boolean isSupported(String location) {
        return findBestProvider(location) != null;
    }

    /**
     * 获取支持指定位置的配置源提供者
     *
     * @param location 配置位置
     * @return 配置源提供者
     */
    public static ConfigSourceProvider getProvider(String location) {
        return findBestProvider(location);
    }

    /**
     * 获取所有可用的配置源提供者
     *
     * @return 配置源提供者列表
     */
    public static List<ConfigSourceProvider> getAllProviders() {
        return ConfigSourceRegistry.getRegisteredSourceTypes().stream()
                .map(ConfigSourceRegistry::getProvider)
                .sorted(Comparator.comparingInt(ConfigSourceProvider::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * 查找最适合的配置源提供者
     *
     * @param location 配置位置
     * @return 配置源提供者
     */
    public static ConfigSourceProvider findBestProvider(String location) {
        return ConfigSourceRegistry.getRegisteredSourceTypes().stream()
                .map(ConfigSourceRegistry::getProvider)
                .filter(provider -> provider != null && provider.isAvailable(location))
                .min(Comparator.comparingInt(ConfigSourceProvider::getPriority))
                .orElse(null);
    }
} 