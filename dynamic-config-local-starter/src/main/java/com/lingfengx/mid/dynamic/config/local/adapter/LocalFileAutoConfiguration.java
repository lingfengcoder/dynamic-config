package com.lingfengx.mid.dynamic.config.local.adapter;

import com.lingfengx.mid.dynamic.config.local.provider.HttpRemoteFileFetcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 本地文件动态配置自动配置类
 * 自动注册 LocalFileDynamicValAdapter 和 HttpRemoteFileDynamicValAdapter 为Spring Bean
 */
@Configuration
@EnableConfigurationProperties(HttpRemoteFileFetcherConfig.class)
public class LocalFileAutoConfiguration {

    /**
     * 注册本地文件动态配置适配器
     */
    @Bean
    @ConditionalOnMissingBean
    public LocalFileDynamicValAdapter localFileDynamicValAdapter() {
        return new LocalFileDynamicValAdapter();
    }

    /**
     * 注册HTTP远程文件动态配置适配器
     * 只有当配置了HTTP远程文件相关配置时才启用
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dynamic.config.http-remote-fetcher", name = "enabled", havingValue = "true", matchIfMissing = false)
    public HttpRemoteFileDynamicValAdapter httpRemoteFileDynamicValAdapter(HttpRemoteFileFetcherConfig config) {
        return new HttpRemoteFileDynamicValAdapter(config);
    }

    /**
     * 注册HTTP远程文件拉取器
     * 只有当配置了HTTP远程文件相关配置时才启用
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "dynamic.config.http-remote-fetcher", name = "enabled", havingValue = "true", matchIfMissing = false)
    public HttpRemoteFileFetcher httpRemoteFileFetcher(HttpRemoteFileFetcherConfig config) {
        return new HttpRemoteFileFetcher(config);
    }
} 