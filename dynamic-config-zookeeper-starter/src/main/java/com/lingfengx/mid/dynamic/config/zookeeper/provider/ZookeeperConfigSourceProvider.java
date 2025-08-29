package com.lingfengx.mid.dynamic.config.zookeeper.provider;

import com.lingfengx.mid.dynamic.config.ConfigSourceProvider;
import com.lingfengx.mid.dynamic.config.DynamicValListener;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zookeeper配置源提供者
 * 支持从Zookeeper加载配置并监听配置变化
 */
public class ZookeeperConfigSourceProvider implements ConfigSourceProvider {
    
    private static final Logger log = LoggerFactory.getLogger(ZookeeperConfigSourceProvider.class);
    
    private final ConcurrentHashMap<String, DynamicValListener> listeners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PathChildrenCache> pathCaches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CuratorFramework> clients = new ConcurrentHashMap<>();
    
    @Override
    public String getSourceType() {
        return "zookeeper";
    }
    
    @Override
    public int getPriority() {
        return 200; // Zookeeper配置源优先级
    }
    
    @Override
    public boolean isAvailable(String location) {
        if (!StringUtils.hasText(location)) {
            return false;
        }
        
        // 解析Zookeeper连接信息
        ZookeeperLocation zkLocation = parseLocation(location);
        if (zkLocation == null) {
            return false;
        }
        
        try {
            CuratorFramework client = getOrCreateClient(zkLocation.getConnectString());
            return client.getZookeeperClient().isConnected();
        } catch (Exception e) {
            log.warn("Zookeeper connection not available for: {}", location, e);
            return false;
        }
    }
    
    @Override
    public String loadConfig(String location) {
        if (!isAvailable(location)) {
            throw new RuntimeException("Zookeeper config source not available for location: " + location);
        }
        
        try {
            ZookeeperLocation zkLocation = parseLocation(location);
            CuratorFramework client = getOrCreateClient(zkLocation.getConnectString());
            
            byte[] data = client.getData().forPath(zkLocation.getPath());
            String content = new String(data, StandardCharsets.UTF_8);
            log.info("Successfully loaded zookeeper config from: {}", zkLocation.getPath());
            return content;
            
        } catch (Exception e) {
            log.error("Failed to load zookeeper config from: {}", location, e);
            throw new RuntimeException("Failed to load zookeeper configuration", e);
        }
    }
    
    @Override
    public CompletableFuture<String> loadConfigAsync(String location) {
        return CompletableFuture.supplyAsync(() -> loadConfig(location));
    }
    
    @Override
    public void registerListener(String location, DynamicValListener listener) {
        if (!isAvailable(location)) {
            log.warn("Cannot register listener for unavailable location: {}", location);
            return;
        }
        
        listeners.put(location, listener);
        
        try {
            ZookeeperLocation zkLocation = parseLocation(location);
            CuratorFramework client = getOrCreateClient(zkLocation.getConnectString());
            
            // 创建路径监听器
            PathChildrenCache pathCache = new PathChildrenCache(client, zkLocation.getPath(), true);
            pathCache.getListenable().addListener((client1, event) -> {
                if (event.getType() == PathChildrenCacheEvent.Type.CHILD_UPDATED ||
                    event.getType() == PathChildrenCacheEvent.Type.CHILD_ADDED) {
                    
                    log.info("Detected zookeeper config change: {}", zkLocation.getPath());
                    
                    try {
                        String newContent = loadConfig(location);
                        listener.onChange(newContent);
                    } catch (Exception e) {
                        log.error("Failed to reload config after zookeeper change: {}", location, e);
                    }
                }
            });
            
            pathCache.start();
            pathCaches.put(location, pathCache);
            
            log.info("Registered listener for zookeeper config: {}", location);
            
        } catch (Exception e) {
            log.error("Failed to register listener for zookeeper config: {}", location, e);
        }
    }
    
    @Override
    public void removeListener(String location, DynamicValListener listener) {
        listeners.remove(location);
        
        // 停止路径监听器
        PathChildrenCache pathCache = pathCaches.remove(location);
        if (pathCache != null) {
            try {
                pathCache.close();
            } catch (Exception e) {
                log.warn("Failed to close path cache for: {}", location, e);
            }
        }
        
        log.info("Removed listener for zookeeper config: {}", location);
    }
    
    /**
     * 获取或创建Zookeeper客户端
     */
    private CuratorFramework getOrCreateClient(String connectString) {
        return clients.computeIfAbsent(connectString, this::createClient);
    }
    
    /**
     * 创建Zookeeper客户端
     */
    private CuratorFramework createClient(String connectString) {
        CuratorFramework client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .connectionTimeoutMs(5000)
                .sessionTimeoutMs(30000)
                // 添加线程池配置，控制并发
                .threadFactory(r -> {
                    Thread t = new Thread(r, "zookeeper-config-provider-" + System.currentTimeMillis());
                    t.setDaemon(true);
                    return t;
                })
                .build();
        
        client.start();
        return client;
    }
    
    /**
     * 解析Zookeeper位置信息
     * 格式: zookeeper://host:port/path 或 zookeeper://host1:port1,host2:port2/path
     */
    private ZookeeperLocation parseLocation(String location) {
        if (!location.startsWith("zookeeper://")) {
            return null;
        }
        
        String content = location.substring("zookeeper://".length());
        int pathIndex = content.indexOf('/');
        
        if (pathIndex == -1) {
            return null;
        }
        
        String connectString = content.substring(0, pathIndex);
        String path = content.substring(pathIndex);
        
        return new ZookeeperLocation(connectString, path);
    }
    
    /**
     * Zookeeper位置信息
     */
    private static class ZookeeperLocation {
        private final String connectString;
        private final String path;
        
        public ZookeeperLocation(String connectString, String path) {
            this.connectString = connectString;
            this.path = path;
        }
        
        public String getConnectString() {
            return connectString;
        }
        
        public String getPath() {
            return path;
        }
    }
    
    /**
     * 设置配置到Zookeeper
     */
    public void setConfig(String location, String value) {
        try {
            ZookeeperLocation zkLocation = parseLocation(location);
            if (zkLocation == null) {
                throw new RuntimeException("Invalid zookeeper location: " + location);
            }
            
            CuratorFramework client = getOrCreateClient(zkLocation.getConnectString());
            
            // 确保路径存在
            if (client.checkExists().forPath(zkLocation.getPath()) == null) {
                client.create().creatingParentsIfNeeded().forPath(zkLocation.getPath());
            }
            
            // 设置数据
            client.setData().forPath(zkLocation.getPath(), value.getBytes(StandardCharsets.UTF_8));
            log.info("Set zookeeper config: path={}, value={}", zkLocation.getPath(), value);
            
        } catch (Exception e) {
            log.error("Failed to set zookeeper config: location={}, value={}", location, value, e);
            throw new RuntimeException("Failed to set zookeeper config", e);
        }
    }
    
    /**
     * 获取配置从Zookeeper
     */
    public String getConfig(String location) {
        try {
            ZookeeperLocation zkLocation = parseLocation(location);
            if (zkLocation == null) {
                return null;
            }
            
            CuratorFramework client = getOrCreateClient(zkLocation.getConnectString());
            
            if (client.checkExists().forPath(zkLocation.getPath()) != null) {
                byte[] data = client.getData().forPath(zkLocation.getPath());
                return new String(data, StandardCharsets.UTF_8);
            }
            
            return null;
        } catch (Exception e) {
            log.error("Failed to get zookeeper config: location={}", location, e);
            return null;
        }
    }
    
    /**
     * 删除配置从Zookeeper
     */
    public void deleteConfig(String location) {
        try {
            ZookeeperLocation zkLocation = parseLocation(location);
            if (zkLocation == null) {
                throw new RuntimeException("Invalid zookeeper location: " + location);
            }
            
            CuratorFramework client = getOrCreateClient(zkLocation.getConnectString());
            
            if (client.checkExists().forPath(zkLocation.getPath()) != null) {
                client.delete().forPath(zkLocation.getPath());
                log.info("Deleted zookeeper config: path={}", zkLocation.getPath());
            }
        } catch (Exception e) {
            log.error("Failed to delete zookeeper config: location={}", location, e);
            throw new RuntimeException("Failed to delete zookeeper config", e);
        }
    }
    
    /**
     * 关闭Provider
     */
    public void shutdown() {
        try {
            // 关闭所有路径监听器
            for (PathChildrenCache pathCache : pathCaches.values()) {
                if (pathCache != null) {
                    pathCache.close();
                }
            }
            pathCaches.clear();
            
            // 关闭所有客户端
            for (CuratorFramework client : clients.values()) {
                if (client != null) {
                    client.close();
                }
            }
            clients.clear();
            
            // 清空监听器
            listeners.clear();
            
            log.info("Zookeeper config source provider shutdown completed");
        } catch (Exception e) {
            log.error("Error during zookeeper config source provider shutdown", e);
        }
    }
} 