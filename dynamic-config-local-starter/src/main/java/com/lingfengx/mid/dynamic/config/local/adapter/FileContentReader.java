package com.lingfengx.mid.dynamic.config.local.adapter;

import com.lingfengx.mid.dynamic.config.DynamicValListener;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件内容读取工具类
 * 统一处理文件内容读取和监听器通知的逻辑
 */
@Slf4j
public class FileContentReader {
    
    /**
     * 读取文件内容并通知监听器
     * 
     * @param key 监听键
     * @param filePath 文件路径
     * @param listeners 监听器映射
     * @param adapterName 适配器名称（用于日志）
     */
    public static void readAndNotify(String key, Path filePath, 
                                   ConcurrentHashMap<String, DynamicValListener> listeners, 
                                   String adapterName) {
        DynamicValListener listener = listeners.get(key);
        if (listener == null) {
            log.warn("[{}] no listener found for key: {}", adapterName, key);
            return;
        }
        
        try {
            // 读取文件内容
            String content = new String(Files.readAllBytes(filePath));
            log.debug("[{}] file content read, size: {} bytes", adapterName, content.length());
            
            // 通知监听器
            listener.onChange(content);
            
        } catch (IOException e) {
            log.error("[{}] error reading file: {}", adapterName, filePath, e);
        } catch (Exception e) {
            log.error("[{}] error notifying listener for file: {}", adapterName, filePath, e);
        }
    }
    
    /**
     * 读取文件内容并通知监听器（重载方法，接受字符串路径）
     * 
     * @param key 监听键
     * @param filePath 文件路径字符串
     * @param listeners 监听器映射
     * @param adapterName 适配器名称（用于日志）
     */
    public static void readAndNotify(String key, String filePath, 
                                   ConcurrentHashMap<String, DynamicValListener> listeners, 
                                   String adapterName) {
        readAndNotify(key, Paths.get(filePath), listeners, adapterName);
    }
} 