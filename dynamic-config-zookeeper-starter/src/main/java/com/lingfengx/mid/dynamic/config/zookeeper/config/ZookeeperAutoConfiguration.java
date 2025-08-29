package com.lingfengx.mid.dynamic.config.zookeeper.config;

import com.lingfengx.mid.dynamic.config.zookeeper.adapter.ZookeeperDynamicValAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Zookeeper动态配置自动配置类
 */
@Configuration
@ConditionalOnProperty(prefix = "dynamic.config.zookeeper", name = "enabled", havingValue = "true", matchIfMissing = true)
@ComponentScan(basePackages = "com.lingfengx.mid.dynamic.config.zookeeper")
public class ZookeeperAutoConfiguration {
    
    // 自动扫描zookeeper包下的所有组件
} 