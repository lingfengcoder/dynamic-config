package com.lingfengx.mid.dynamic.config.local.adapter;

import com.lingfengx.mid.dynamic.config.DynamicValListener;
import com.lingfengx.mid.dynamic.config.DynamicValListenerRegister;
import com.lingfengx.mid.dynamic.config.adapter.DynamicValListenerAdapter;
import com.lingfengx.mid.dynamic.config.local.ann.DynamicValHttpRemoteFile;
import com.lingfengx.mid.dynamic.config.local.provider.HttpRemoteFileConfigSourceProvider;
import com.lingfengx.mid.dynamic.config.local.provider.HttpRemoteFileFetcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于HTTP远程文件拉取的动态配置适配器
 * 通过 HttpRemoteFileConfigSourceProvider 委托处理文件拉取和监听
 */
@Slf4j
public class HttpRemoteFileDynamicValAdapter extends DynamicValListenerAdapter implements DynamicValListenerRegister {

    private final HttpRemoteFileConfigSourceProvider provider;

    public HttpRemoteFileDynamicValAdapter() {
        this.provider = new HttpRemoteFileConfigSourceProvider();
    }

    public HttpRemoteFileDynamicValAdapter(HttpRemoteFileFetcherConfig config) {
        this.provider = new HttpRemoteFileConfigSourceProvider(config);
    }

    @Override
    public void register(String remoteUrl, String prefix, DynamicValListener listener) {
        if (!StringUtils.hasText(remoteUrl)) {
            log.warn("[http-remote-adapter] remote URL is empty");
            return;
        }

        if (listener == null) {
            log.warn("[http-remote-adapter] listener is null");
            return;
        }

        try {
            // 委托给Provider处理
            provider.registerListener(remoteUrl, listener);
            log.info("[http-remote-adapter] registered remote file listener: {}", remoteUrl);

        } catch (Exception e) {
            log.error("[http-remote-adapter] failed to register remote file listener: {}", remoteUrl, e);
        }
    }

    /**
     * 注册带认证信息的远程文件监听
     */
    public void registerWithAuth(String remoteUrl, String prefix, DynamicValListener listener, DynamicValHttpRemoteFile annotation) {
        if (!StringUtils.hasText(remoteUrl)) {
            log.warn("[http-remote-adapter] remote URL is empty");
            return;
        }

        if (listener == null) {
            log.warn("[http-remote-adapter] listener is null");
            return;
        }

        try {
            // 从注解中提取认证配置
            HttpRemoteFileFetcher.HttpAuthConfig authConfig = extractAuthConfigFromAnnotation(annotation);

            // 检查是否需要定时拉取
            long intervalSeconds = annotation.intervalSeconds();
            if (intervalSeconds > 0) {
                // 使用定时拉取
                provider.registerListenerWithSchedule(remoteUrl, listener, intervalSeconds, authConfig);
            } else {
                // 普通注册
                provider.registerListenerWithAuth(remoteUrl, listener, authConfig);
            }

            log.info("[http-remote-adapter] registered remote file listener with auth: {}", remoteUrl);

        } catch (Exception e) {
            log.error("[http-remote-adapter] failed to register remote file listener with auth: {}", remoteUrl, e);
        }
    }

    /**
     * 实现DynamicValListenerRegister接口的register方法
     */
    public void register(String remoteUrl, String prefix, DynamicValListener listener, Object annotation) {
        if (annotation instanceof DynamicValHttpRemoteFile) {
            registerWithAuth(remoteUrl, prefix, listener, (DynamicValHttpRemoteFile) annotation);
        } else {
            // 降级到普通注册
            register(remoteUrl, prefix, listener);
        }
    }

    @Override
    public boolean isAvailable(String file) {
        if (!StringUtils.hasText(file)) {
            return false;
        }
        file = file.trim().toLowerCase();

        // HTTP远程文件适配器适用于以http://或https://开头的URL
        return file.startsWith("http://") || file.startsWith("https://");
    }

    /**
     * 注册带定时拉取的远程文件监听
     */
    public void registerWithSchedule(String remoteUrl, String prefix, DynamicValListener listener, long intervalSeconds) {
        registerWithSchedule(remoteUrl, prefix, listener, intervalSeconds, null);
    }

    /**
     * 注册带定时拉取和认证的远程文件监听
     */
    public void registerWithSchedule(String remoteUrl, String prefix, DynamicValListener listener,
                                     long intervalSeconds, HttpRemoteFileFetcher.HttpAuthConfig authConfig) {
        if (!StringUtils.hasText(remoteUrl)) {
            log.warn("[http-remote-adapter] remote URL is empty");
            return;
        }

        if (listener == null) {
            log.warn("[http-remote-adapter] listener is null");
            return;
        }

        if (intervalSeconds <= 0) {
            log.warn("[http-remote-adapter] invalid interval: {}", intervalSeconds);
            return;
        }

        try {
            // 委托给Provider处理
            provider.registerListenerWithSchedule(remoteUrl, listener, intervalSeconds, authConfig);
            log.info("[http-remote-adapter] registered scheduled remote file listener: {} (interval: {}s)",
                    remoteUrl, intervalSeconds);

        } catch (Exception e) {
            log.error("[http-remote-adapter] failed to register scheduled remote file listener: {}", remoteUrl, e);
        }
    }

    /**
     * 手动拉取远程文件
     */
    public HttpRemoteFileFetcher.FetchResult fetchRemoteFile(String remoteUrl, String prefix) {
        return fetchRemoteFile(remoteUrl, prefix, null);
    }

    /**
     * 手动拉取远程文件（带认证）
     */
    public HttpRemoteFileFetcher.FetchResult fetchRemoteFile(String remoteUrl, String prefix,
                                                             HttpRemoteFileFetcher.HttpAuthConfig authConfig) {
        if (!StringUtils.hasText(remoteUrl)) {
            return HttpRemoteFileFetcher.FetchResult.failure("Remote URL is empty");
        }

        return provider.fetchRemoteFile(remoteUrl, authConfig);
    }


    /**
     * 从注解中提取认证配置
     */
    private HttpRemoteFileFetcher.HttpAuthConfig extractAuthConfigFromAnnotation(DynamicValHttpRemoteFile annotation) {
        if (annotation == null || annotation.authType() == DynamicValHttpRemoteFile.AuthType.NONE) {
            return null;
        }

        HttpRemoteFileFetcher.HttpAuthConfig authConfig = new HttpRemoteFileFetcher.HttpAuthConfig();

        switch (annotation.authType()) {
            case BASIC:
                if (StringUtils.hasText(annotation.username()) && StringUtils.hasText(annotation.password())) {
                    authConfig.basicAuth(annotation.username(), annotation.password());
                }
                break;
            case BEARER:
                if (StringUtils.hasText(annotation.token())) {
                    authConfig.bearerToken(annotation.token());
                }
                break;
            case API_KEY:
                if (StringUtils.hasText(annotation.apiKeyHeader()) && StringUtils.hasText(annotation.apiKeyValue())) {
                    authConfig.apiKey(annotation.apiKeyHeader(), annotation.apiKeyValue());
                }
                break;
            default:
                return null;
        }

        return authConfig;
    }
} 