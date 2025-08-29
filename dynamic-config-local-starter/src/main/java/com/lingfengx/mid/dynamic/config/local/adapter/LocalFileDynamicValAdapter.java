package com.lingfengx.mid.dynamic.config.local.adapter;

import com.lingfengx.mid.dynamic.config.DynamicValListener;
import com.lingfengx.mid.dynamic.config.ConfigSourceManager;
import com.lingfengx.mid.dynamic.config.adapter.DynamicValListenerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于本地文件监听的动态配置适配器
 * 通过 ConfigSourceManager 委托给 LocalFileConfigSourceProvider 处理文件监听
 */
@Slf4j
public class LocalFileDynamicValAdapter extends DynamicValListenerAdapter {

    @Override
    public void register(String file, String prefix, DynamicValListener listener) {
        if (!StringUtils.hasText(file)) {
            log.warn("[local-file-adapter] file path is empty");
            return;
        }

        try {

            // 使用 ConfigSourceManager 统一管理监听器注册
            // LocalFileConfigSourceProvider 会自动处理文件监听
            ConfigSourceManager.registerListener(file, listener);

            log.info("[local-file-adapter] registered listener for file: {} with ConfigSourceManager", file);

        } catch (Exception e) {
            log.error("[local-file-adapter] failed to register file listener: {}", file, e);
        }
    }

    @Override
    public boolean isAvailable(String file) {
        if (!StringUtils.hasText(file)) {
            return false;
        }
        file = file.trim().toLowerCase();
        return file.startsWith("file:") || file.startsWith("classpath:")
                || file.startsWith("local:") || !file.contains("://");
    }

} 