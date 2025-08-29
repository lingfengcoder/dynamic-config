package com.lingfengx.mid.dynamic.config.zookeeper.adapter;

import com.lingfengx.mid.dynamic.config.DynamicValListener;
import com.lingfengx.mid.dynamic.config.adapter.DynamicValListenerAdapter;
import com.lingfengx.mid.dynamic.config.zookeeper.provider.ZookeeperConfigSourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Zookeeper动态配置适配器
 * 通过 ZookeeperConfigSourceProvider 委托处理Zookeeper配置监听
 */
@Slf4j
@Component("zookeeperDynamicValAdapter")
public class ZookeeperDynamicValAdapter extends DynamicValListenerAdapter {

    @Value("${dynamic.config.zookeeper.connect-string:localhost:2181}")
    private String connectString;

    @Value("${dynamic.config.zookeeper.session-timeout:60000}")
    private int sessionTimeout;

    @Value("${dynamic.config.zookeeper.connection-timeout:15000}")
    private int connectionTimeout;

    @Value("${dynamic.config.zookeeper.namespace:dynamic-config}")
    private String namespace;

    @Value("${dynamic.config.zookeeper.retry.base-sleep-time:1000}")
    private int baseSleepTime;

    @Value("${dynamic.config.zookeeper.retry.max-retries:3}")
    private int maxRetries;

    private final ZookeeperConfigSourceProvider provider;

    public ZookeeperDynamicValAdapter() {
        this.provider = new ZookeeperConfigSourceProvider();
    }

    @Override
    public void register(String file, String prefix, DynamicValListener listener) {
        if (!StringUtils.hasText(file)) {
            log.warn("[zookeeper-adapter] file path is empty");
            return;
        }
        
        if (listener == null) {
            log.warn("[zookeeper-adapter] listener is null");
            return;
        }
        
        try {
            // 构建Zookeeper路径
            String zkPath = "/" + namespace + "/" + file;
            String location = buildZookeeperLocation(zkPath);
            
            // 委托给Provider处理
            provider.registerListener(location, listener);
            
            log.info("[zookeeper-adapter] registered listener for zookeeper path: {}", zkPath);
            
        } catch (Exception e) {
            log.error("[zookeeper-adapter] failed to register zookeeper listener: {}", file, e);
        }
    }

    /**
     * 设置配置到Zookeeper
     */
    public void setConfig(String file, String key, String value) {
        try {
            String zkPath = "/" + namespace + "/" + file + "/" + key;
            String location = buildZookeeperLocation(zkPath);
            
            // 委托给Provider处理
            provider.setConfig(location, value);
            
            log.info("[zookeeper-adapter] set config: path={}, value={}", zkPath, value);
            
        } catch (Exception e) {
            log.error("[zookeeper-adapter] failed to set config: file={}, key={}, value={}", file, key, value, e);
        }
    }

    /**
     * 获取配置从Zookeeper
     */
    public String getConfig(String file, String key) {
        try {
            String zkPath = "/" + namespace + "/" + file + "/" + key;
            String location = buildZookeeperLocation(zkPath);
            
            // 委托给Provider处理
            return provider.getConfig(location);
            
        } catch (Exception e) {
            log.error("[zookeeper-adapter] failed to get config: file={}, key={}", file, key, e);
            return null;
        }
    }
    
    @Override
    public boolean isAvailable(String file) {
        if (!StringUtils.hasText(file)) {
            return false;
        }
        
        // Zookeeper适配器适用于以zk://或zookeeper://开头的路径
        return file.startsWith("zk://") || file.startsWith("zookeeper://");
    }

    /**
     * 删除配置从Zookeeper
     */
    public void deleteConfig(String file, String key) {
        try {
            String zkPath = "/" + namespace + "/" + file + "/" + key;
            String location = buildZookeeperLocation(zkPath);
            
            // 委托给Provider处理
            provider.deleteConfig(location);
            
            log.info("[zookeeper-adapter] deleted config: path={}", zkPath);
            
        } catch (Exception e) {
            log.error("[zookeeper-adapter] failed to delete config: file={}, key={}", file, key, e);
        }
    }

    /**
     * 构建Zookeeper位置字符串
     */
    private String buildZookeeperLocation(String path) {
        return String.format("zookeeper://%s?sessionTimeout=%d&connectionTimeout=%d&baseSleepTime=%d&maxRetries=%d&path=%s",
                connectString, sessionTimeout, connectionTimeout, baseSleepTime, maxRetries, path);
    }

    /**
     * 关闭资源
     */
    public void close() {
        try {
            provider.shutdown();
            log.info("[zookeeper-adapter] adapter resources closed");
        } catch (Exception e) {
            log.error("[zookeeper-adapter] error closing adapter resources", e);
        }
    }
} 