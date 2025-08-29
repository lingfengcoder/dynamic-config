package com.lingfengx.mid.dynamic.config;

/**
 * 配置源类型枚举
 * 定义支持的配置源类型
 */
public enum ConfigSourceType {
    
    /**
     * 本地文件系统配置源
     */
    LOCAL("local", "本地文件系统配置源"),
    
    /**
     * HTTP远程配置源
     */
    HTTP("http", "HTTP远程配置源"),
    
    /**
     * HTTPS远程配置源
     */
    HTTPS("https", "HTTPS远程配置源"),
    
    /**
     * Zookeeper配置源
     */
    ZOOKEEPER("zookeeper", "Zookeeper配置源"),
    
    /**
     * 简化的Zookeeper配置源
     */
    ZK("zk", "Zookeeper配置源(简化)");
    
    private final String type;
    private final String description;
    
    ConfigSourceType(String type, String description) {
        this.type = type;
        this.description = description;
    }
    
    public String getType() {
        return type;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * 根据类型字符串获取配置源类型
     * @param type 类型字符串
     * @return 配置源类型
     */
    public static ConfigSourceType fromString(String type) {
        if (type == null || type.trim().isEmpty()) {
            return null;
        }
        
        String normalizedType = type.toLowerCase().trim();
        for (ConfigSourceType sourceType : values()) {
            if (sourceType.type.equals(normalizedType)) {
                return sourceType;
            }
        }
        return null;
    }
    
    /**
     * 根据URL自动推断配置源类型
     * @param url 配置URL
     * @return 配置源类型
     */
    public static ConfigSourceType inferFromUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return LOCAL;
        }
        
        String lowerUrl = url.toLowerCase().trim();
        if (lowerUrl.startsWith("http://")) {
            return HTTP;
        } else if (lowerUrl.startsWith("https://")) {
            return HTTPS;
        } else if (lowerUrl.startsWith("zk://") || lowerUrl.startsWith("zookeeper://")) {
            return ZOOKEEPER;
        } else if (lowerUrl.startsWith("classpath:")) {
            return LOCAL;
        } else {
            return LOCAL; // 默认为本地配置源
        }
    }
} 