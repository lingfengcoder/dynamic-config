package com.lingfengx.mid.dynamic.config;

import com.lingfengx.mid.dynamic.config.util.EarlyLogUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 配置源自动注册器
 * 在Spring Bean工厂初始化时自动注册所有配置源提供者和验证器
 * 设置为最高优先级，确保在EnvironmentPostProcessor执行之前完成注册
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConfigSourceAutoRegistrar implements BeanFactoryPostProcessor {

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        EarlyLogUtil.info("Starting to register config source providers and validators...");

        // 使用配置源扫描器自动发现和注册所有可用的配置源提供者
        ConfigSourceScanner.scanAndRegisterConfigSources();

        // 获取所有配置源验证器（仍然使用@Bean方式，因为验证器通常需要Spring上下文）
        List<ConfigSourceValidator> configSourceValidators = getBeansOfType(beanFactory, ConfigSourceValidator.class);

        // 注册配置源验证器
        if (configSourceValidators != null && !configSourceValidators.isEmpty()) {
            for (ConfigSourceValidator validator : configSourceValidators) {
                try {
                    ConfigSourceRegistry.registerValidator(validator.getSupportedSourceType(), validator);
                    EarlyLogUtil.info("Registered config source validator for type: " + validator.getSupportedSourceType());
                } catch (Exception e) {
                    EarlyLogUtil.error("Failed to register config source validator for type: " + validator.getSupportedSourceType(), e);
                }
            }
        }

        // 输出注册结果
        EarlyLogUtil.info("Config source registration completed. Registered providers: " + ConfigSourceRegistry.getRegisteredSourceTypes().size() + ", validators: " + (configSourceValidators != null ? configSourceValidators.size() : 0));

        // 列出所有注册的配置源类型
        if (!ConfigSourceRegistry.getRegisteredSourceTypes().isEmpty()) {
            EarlyLogUtil.info("Available config source types: " + ConfigSourceRegistry.getRegisteredSourceTypes());
        }
    }

    /**
     * 安全地获取指定类型的Bean列表
     *
     * @param beanFactory Bean工厂
     * @param type        Bean类型
     * @param <T>         泛型类型
     * @return Bean列表
     */
    private <T> List<T> getBeansOfType(ConfigurableListableBeanFactory beanFactory, Class<T> type) {
        try {
            return new ArrayList<>(beanFactory.getBeansOfType(type).values());
        } catch (Exception e) {
            EarlyLogUtil.warn("Failed to get beans of type: " + type.getSimpleName());
            return Collections.emptyList();
        }
    }
} 