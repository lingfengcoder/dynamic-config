package com.lingfengx.mid.dynamic.config.local.provider;

import com.lingfengx.mid.dynamic.config.ConfigSourceProvider;
import com.lingfengx.mid.dynamic.config.DynamicValListener;
import com.lingfengx.mid.dynamic.config.local.adapter.HttpRemoteFileFetcherConfig;
import com.lingfengx.mid.dynamic.config.local.adapter.FileContentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HTTP远程文件配置源提供者
 */
public class HttpRemoteFileConfigSourceProvider implements ConfigSourceProvider {
    
    private static final Logger log = LoggerFactory.getLogger(HttpRemoteFileConfigSourceProvider.class);
    
    private final HttpRemoteFileFetcher fileFetcher;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DynamicValListener> listeners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HttpRemoteFileFetcher.HttpAuthConfig> authConfigs = new ConcurrentHashMap<>();
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    
    public HttpRemoteFileConfigSourceProvider() {
        this(new HttpRemoteFileFetcherConfig());
    }
    
    public HttpRemoteFileConfigSourceProvider(HttpRemoteFileFetcherConfig config) {
        this.fileFetcher = new HttpRemoteFileFetcher(config);
        // 优化线程池大小：使用固定大小而不是CPU核心数
        this.scheduler = Executors.newScheduledThreadPool(
            4, // 固定4个线程，避免过多线程
            r -> {
                Thread t = new Thread(r, "http-remote-file-provider-scheduler");
                t.setDaemon(true);
                return t;
            }
        );
    }
    
    @Override
    public String getSourceType() {
        return "http-remote-file";
    }
    
    @Override
    public int getPriority() {
        return 200;
    }
    
    @Override
    public boolean isAvailable(String location) {
        if (!StringUtils.hasText(location)) {
            return false;
        }
        return location.startsWith("http://") || location.startsWith("https://");
    }
    
    @Override
    public String loadConfig(String location) {
        if (!isAvailable(location)) {
            throw new RuntimeException("HTTP remote file config source not available for location: " + location);
        }
        
        try {
            String localFileName = generateLocalFileName(location);
            HttpRemoteFileFetcher.FetchResult result = fileFetcher.fetchRemoteFile(location, localFileName);
            
            if (!result.isSuccess()) {
                throw new RuntimeException("Failed to fetch HTTP remote file: " + result.getErrorMessage());
            }
            
            log.info("Successfully loaded HTTP remote file from: {}", location);
            return result.getLocalFilePath();
            
        } catch (Exception e) {
            log.error("Failed to load HTTP remote file from: {}", location, e);
            throw new RuntimeException("Failed to load HTTP remote file configuration", e);
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
        
        if (listener == null) {
            log.warn("Listener is null for location: {}", location);
            return;
        }
        
        try {
            listeners.put(location, listener);
            fetchAndNotify(location, null);
            log.info("Registered listener for HTTP remote file: {}", location);
            
        } catch (Exception e) {
            log.error("Failed to register listener for HTTP remote file: {}", location, e);
        }
    }
    
    public void registerListenerWithAuth(String location, DynamicValListener listener, 
                                       HttpRemoteFileFetcher.HttpAuthConfig authConfig) {
        if (!isAvailable(location)) {
            log.warn("Cannot register listener for unavailable location: {}", location);
            return;
        }
        
        if (listener == null) {
            log.warn("Listener is null for location: {}", location);
            return;
        }
        
        try {
            listeners.put(location, listener);
            
            if (authConfig != null) {
                authConfigs.put(location, authConfig);
                log.info("Registered listener with auth for HTTP remote file: {}", location);
            }
            
            fetchAndNotify(location, authConfig);
            
        } catch (Exception e) {
            log.error("Failed to register listener with auth for HTTP remote file: {}", location, e);
        }
    }
    
    public void registerListenerWithSchedule(String location, DynamicValListener listener, 
                                           long intervalSeconds, HttpRemoteFileFetcher.HttpAuthConfig authConfig) {
        if (!isAvailable(location)) {
            log.warn("Cannot register listener for unavailable location: {}", location);
            return;
        }
        
        if (listener == null) {
            log.warn("Listener is null for location: {}", location);
            return;
        }
        
        if (intervalSeconds <= 0) {
            log.warn("Invalid interval: {} for location: {}", intervalSeconds, location);
            return;
        }
        
        try {
            listeners.put(location, listener);
            
            if (authConfig != null) {
                authConfigs.put(location, authConfig);
            }
            
            fetchAndNotify(location, authConfig);
            
            ScheduledFuture<?> scheduledTask = scheduler.scheduleAtFixedRate(
                () -> fetchAndNotify(location, authConfig),
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
            );
            
            scheduledTasks.put(location, scheduledTask);
            
            log.info("Registered scheduled listener for HTTP remote file: {} (interval: {}s)", location, intervalSeconds);
            
        } catch (Exception e) {
            log.error("Failed to register scheduled listener for HTTP remote file: {}", location, e);
        }
    }
    
    @Override
    public void removeListener(String location, DynamicValListener listener) {
        ScheduledFuture<?> scheduledTask = scheduledTasks.remove(location);
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            log.info("Stopped scheduled task for: {}", location);
        }
        
        listeners.remove(location);
        authConfigs.remove(location);
        
        log.info("Removed listener for HTTP remote file: {}", location);
    }
    
    public HttpRemoteFileFetcher.FetchResult fetchRemoteFile(String location, HttpRemoteFileFetcher.HttpAuthConfig authConfig) {
        if (!isAvailable(location)) {
            return HttpRemoteFileFetcher.FetchResult.failure("Location not available: " + location);
        }
        
        return fetchAndNotify(location, authConfig);
    }
    
    private HttpRemoteFileFetcher.FetchResult fetchAndNotify(String location, HttpRemoteFileFetcher.HttpAuthConfig authConfig) {
        try {
            String localFileName = generateLocalFileName(location);
            HttpRemoteFileFetcher.FetchResult result = fileFetcher.fetchRemoteFile(location, localFileName, authConfig);
            
            if (result.isSuccess() && result.isChanged()) {
                notifyListener(location, result.getLocalFilePath());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Error fetching remote file: {}", location, e);
            return HttpRemoteFileFetcher.FetchResult.failure("Error: " + e.getMessage());
        }
    }
    
    private String generateLocalFileName(String remoteUrl) {
        try {
            String fileName = remoteUrl.substring(remoteUrl.lastIndexOf('/') + 1);
            if (fileName.contains("?")) {
                fileName = fileName.substring(0, fileName.indexOf('?'));
            }
            
            if (!StringUtils.hasText(fileName) || fileName.equals(remoteUrl)) {
                fileName = String.valueOf(remoteUrl.hashCode()) + ".config";
            }
            
            return fileName;
            
        } catch (Exception e) {
            return String.valueOf(remoteUrl.hashCode()) + ".config";
        }
    }
    
    private void notifyListener(String location, String localFilePath) {
        FileContentReader.readAndNotify(location, localFilePath, listeners, "http-remote-file-provider");
    }
    
    public String getLocalFilePath(String location) {
        String localFileName = generateLocalFileName(location);
        return fileFetcher.getLocalFilePath(localFileName);
    }
    
    public boolean cleanupCacheFile(String location) {
        String localFileName = generateLocalFileName(location);
        return fileFetcher.cleanupCacheFile(localFileName);
    }
    
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            scheduledTasks.forEach((location, scheduledTask) -> {
                scheduledTask.cancel(false);
                log.info("Stopped scheduled task for: {}", location);
            });
            scheduledTasks.clear();
            
            listeners.clear();
            authConfigs.clear();
            
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            
            log.info("HTTP remote file config source provider shutdown completed");
        }
    }
    
    public boolean isShutdown() {
        return shutdown.get();
    }
} 