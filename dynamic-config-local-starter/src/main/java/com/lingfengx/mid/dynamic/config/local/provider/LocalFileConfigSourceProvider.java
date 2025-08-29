package com.lingfengx.mid.dynamic.config.local.provider;

import com.lingfengx.mid.dynamic.config.ConfigSourceProvider;
import com.lingfengx.mid.dynamic.config.DynamicValListener;
import com.lingfengx.mid.dynamic.config.util.FilePathResolver;
import com.lingfengx.mid.dynamic.config.util.EarlyLogUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Map;

import static com.lingfengx.mid.dynamic.config.util.FilePathResolver.readAllBytes;

/**
 * 本地文件配置源提供者
 * 支持从本地文件系统或classpath加载配置
 */
public class LocalFileConfigSourceProvider implements ConfigSourceProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalFileConfigSourceProvider.class);

    // 监听器映射表
    private final ConcurrentHashMap<String, DynamicValListener> listeners = new ConcurrentHashMap<>();

    // 轮询队列：存储需要轮询的文件信息
    private final ConcurrentLinkedQueue<PollingFileInfo> pollingQueue = new ConcurrentLinkedQueue<>();

    // 轮询线程控制
    private final AtomicBoolean pollingStarted = new AtomicBoolean(false);
    private ScheduledFuture<?> pollingTask;

    // 文件系统监听器任务管理
    private final Map<String, ScheduledFuture<?>> fileWatcherTasks = new ConcurrentHashMap<>();

    // WatchService 线程池
    private final ScheduledExecutorService watchExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "local-file-config-watcher-" + System.currentTimeMillis());
        t.setDaemon(true);
        return t;
    });

    /**
     * 轮询文件信息
     */
    private static class PollingFileInfo {
        private final String filePath;
        private final DynamicValListener listener;
        private String lastContent;
        private long lastCheckTime;

        public PollingFileInfo(String filePath, DynamicValListener listener) {
            this.filePath = filePath;
            this.listener = listener;
            this.lastContent = null;
            this.lastCheckTime = System.currentTimeMillis();
        }

        public String getFilePath() {
            return filePath;
        }

        public DynamicValListener getListener() {
            return listener;
        }

        public String getLastContent() {
            return lastContent;
        }

        public void setLastContent(String content) {
            this.lastContent = content;
        }

        public long getLastCheckTime() {
            return lastCheckTime;
        }

        public void setLastCheckTime(long time) {
            this.lastCheckTime = time;
        }
    }

    @Override
    public String getSourceType() {
        return "local";
    }

    @Override
    public int getPriority() {
        return 100; // 本地配置源优先级较高
    }

    @Override
    public boolean isAvailable(String location) {
        if (!StringUtils.hasText(location)) {
            return false;
        }

        Resource resource = FilePathResolver.resolveFileResource(location);
        return resource.exists();
    }

    @Override
    public String loadConfig(String location) {
        if (!isAvailable(location)) {
            throw new RuntimeException("Local file config source not available for location: " + location);
        }

        try {
            Resource resource = FilePathResolver.resolveFileResource(location);
            if (resource == null) {
                throw new RuntimeException("No suitable resource found for location: " + location);
            }

            try (InputStream inputStream = resource.getInputStream()) {
                byte[] bytes = readAllBytes(inputStream);
                String content = new String(bytes, StandardCharsets.UTF_8);
                log.info("Successfully loaded local file config from: {} (resource type: {})",
                        location, resource.getClass().getSimpleName());
                return content;
            }

        } catch (IOException e) {
            log.error("Failed to load local file config from: {}", location, e);
            throw new RuntimeException("Failed to load local file configuration", e);
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

        // 启动文件监听（仅对本地文件系统有效）
        String cleanLocation = location.replaceFirst("^local://", "");
        {
            startFileWatcher(cleanLocation, listener);
        }

        log.info("Registered listener for local file config: {}", location);
    }

    @Override
    public void removeListener(String location, DynamicValListener listener) {
        listeners.remove(location);

        // 从轮询队列中移除对应的文件
        pollingQueue.removeIf(fileInfo ->
                fileInfo.getFilePath().equals(location) && fileInfo.getListener() == listener);

        // 如果轮询队列为空，停止轮询任务
        if (pollingQueue.isEmpty() && pollingTask != null) {
            stopPollingTask();
        }

        // 停止并移除文件系统监听器任务
        ScheduledFuture<?> watcherTask = fileWatcherTasks.remove(location);
        if (watcherTask != null && !watcherTask.isCancelled()) {
            watcherTask.cancel(false);
            log.info("Stopped file system watcher for: {}", location);
        }

        log.info("Removed listener for local file config: {}", location);
    }

    /**
     * 停止轮询任务
     */
    private void stopPollingTask() {
        if (pollingTask != null && !pollingTask.isCancelled()) {
            pollingTask.cancel(false);
            pollingTask = null;
            pollingStarted.set(false);
            log.info("Stopped polling task");
        }
    }

    /**
     * 启动文件监听器
     * 支持本地文件系统和classpath资源的监听
     */
    private void startFileWatcher(String filePath, DynamicValListener listener) {
        // 检查是否为classpath资源
        if (isClasspathResource(filePath)) {
            startClasspathResourceWatcher(filePath, listener);
        } else {
            startFileSystemWatcher(filePath, listener);
        }
    }

    /**
     * 检查是否为classpath资源
     */
    private boolean isClasspathResource(String filePath) {
        return filePath.startsWith("classpath:") ||
                EarlyLogUtil.class.getClassLoader().getResource(filePath) != null;
    }

    /**
     * 启动classpath资源监听器
     * 使用统一的轮询队列模式
     */
    private void startClasspathResourceWatcher(String filePath, DynamicValListener listener) {
        try {
            // 将文件添加到轮询队列
            PollingFileInfo fileInfo = new PollingFileInfo(filePath, listener);
            pollingQueue.offer(fileInfo);

            // 启动轮询线程（如果还没启动）
            startPollingThread();

            log.info("Added classpath resource to polling queue: {}", filePath);

        } catch (Exception e) {
            log.error("Failed to add classpath resource to polling queue: {}", filePath, e);
        }
    }

    /**
     * 启动轮询线程
     */
    private void startPollingThread() {
        if (pollingStarted.compareAndSet(false, true)) {
            log.info("Starting unified polling thread for classpath resources");

            pollingTask = watchExecutor.scheduleWithFixedDelay(() -> {
                try {
                    // 遍历轮询队列中的所有文件
                    for (PollingFileInfo fileInfo : pollingQueue) {
                        try {
                            // 检查是否需要轮询（避免过于频繁的检查）
                            long now = System.currentTimeMillis();
                            if (now - fileInfo.getLastCheckTime() < 5000) { // 5秒间隔
                                continue;
                            }

                            // 读取当前内容
                            String configLocation;
                            if (fileInfo.getFilePath().startsWith("classpath:") || fileInfo.getFilePath().startsWith("local://classpath:")) {
                                configLocation = "local://" + fileInfo.getFilePath();
                            } else {
                                configLocation = "local://classpath:" + fileInfo.getFilePath();
                            }
                            String currentContent = loadConfig(configLocation);

                            // 检查内容是否发生变化
                            if (fileInfo.getLastContent() == null) {
                                fileInfo.setLastContent(currentContent);
                                log.debug("Initial content loaded for: {}", fileInfo.getFilePath());
                            } else if (!fileInfo.getLastContent().equals(currentContent)) {
                                log.info("Detected content change in: {}", fileInfo.getFilePath());
                                fileInfo.setLastContent(currentContent);
                                fileInfo.getListener().onChange(currentContent);
                            }

                            fileInfo.setLastCheckTime(now);

                        } catch (Exception e) {
                            log.error("Error polling file: {}", fileInfo.getFilePath(), e);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error in polling thread", e);
                }
            }, 0, 1, TimeUnit.SECONDS); // 立即开始，每1秒执行一次
        }
    }

    /**
     * 启动文件系统监听器
     * 使用ScheduledExecutorService管理WatchService任务
     */
    private void startFileSystemWatcher(String filePath, DynamicValListener listener) {
        try {
            Path path = Paths.get(filePath);
            Path parentDir = path.getParent();

            if (parentDir == null || !Files.exists(parentDir)) {
                log.warn("Parent directory does not exist for file watching: {}", filePath);
                return;
            }

            // 创建WatchService任务
            watchExecutor.submit(() -> {
                watchFileChanges(filePath, parentDir, listener);
            }); // 立即执行

        } catch (Exception e) {
            log.error("Failed to start file system watcher for: {}", filePath, e);
        }
    }

    /**
     * 监听文件变化的核心方法
     * 使用事件驱动的WatchService机制
     */
    private void watchFileChanges(String filePath, Path parentDir, DynamicValListener listener) {
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            // 注册监听目录
            parentDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
            log.info("Started file system watcher for: {}", filePath);

            // 事件监听循环 - 这是WatchService的标准用法
            // 大部分时间在 watchService.take() 处阻塞等待文件变化事件
            while (!Thread.currentThread().isInterrupted()) {
                // 阻塞等待文件系统事件
                WatchKey key = watchService.take();

                // 处理所有待处理的事件
                processWatchEvents(key, parentDir, filePath, listener);

                // 重新注册监听器，准备接收下一个事件
                if (!key.reset()) {
                    log.warn("WatchKey reset failed for: {}", filePath);
                    break;
                }
            }

            log.info("File system watcher stopped for: {}", filePath);

        } catch (InterruptedException e) {
            log.info("File system watcher interrupted for: {}", filePath);
            Thread.currentThread().interrupt(); // 重新设置中断状态
        } catch (Exception e) {
            log.error("Error in file system watcher for: {}", filePath, e);
        }
    }

    /**
     * 处理WatchService事件
     */
    private void processWatchEvents(WatchKey key, Path parentDir, String targetFilePath, DynamicValListener listener) {
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();

            // 跳过溢出事件
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }

            // 处理文件修改事件
            if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                @SuppressWarnings("unchecked")
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path fileName = ev.context();
                Path fullPath = parentDir.resolve(fileName);

                // 检查是否是目标文件发生变化
                if (fullPath.toString().equals(targetFilePath)) {
                    handleFileChange(targetFilePath, listener);
                }
            }
        }
    }

    /**
     * 处理文件变化事件
     */
    private void handleFileChange(String filePath, DynamicValListener listener) {
        log.info("Detected file change: {}", filePath);

        try {
            // 短暂延迟，确保文件写入完成
            Thread.sleep(100);

            // 重新加载配置
            String newContent = loadConfig("local://" + filePath);
            listener.onChange(newContent);

        } catch (InterruptedException e) {
            log.info("File change handler interrupted for: {}", filePath);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Failed to reload config after file change: {}", filePath, e);
        }
    }


} 