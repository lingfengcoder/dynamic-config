package com.lingfengx.mid.dynamic.config.local.adapter;

import lombok.Data;
import org.springframework.aop.framework.ProxyConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.annotation.PostConstruct;

/**
 * HTTP远程文件拉取器配置
 */
@Data
@ConfigurationProperties(prefix = "dynamic.config.http-remote-fetcher")
public class HttpRemoteFileFetcherConfig {

    public HttpRemoteFileFetcherConfig() {
        System.out.println("HttpRemoteFileFetcherConfig init");
    }

    @PostConstruct
    public void init() {
        System.out.println("HttpRemoteFileFetcherConfig PostConstruct init");
    }

    /**
     * 是否启用HTTP远程文件功能
     * 默认: false
     */
    private boolean enabled = false;

    /**
     * 本地缓存目录
     * 默认: ./dynamic-config-cache
     */
    private String localCacheDir = "./dynamic-config-cache";

    /**
     * 连接超时时间（毫秒）
     * 默认: 10000ms
     */
    private long connectTimeout = 10000;

    /**
     * 读取超时时间（毫秒）
     * 默认: 30000ms
     */
    private long readTimeout = 30000;

    /**
     * 写入超时时间（毫秒）
     * 默认: 10000ms
     */
    private long writeTimeout = 10000;

    /**
     * 是否在连接失败时重试
     * 默认: true
     */
    private boolean retryOnConnectionFailure = true;

    /**
     * 最大重试次数
     * 默认: 3
     */
    private int maxRetries = 3;

    /**
     * 重试间隔（毫秒）
     * 默认: 1000ms
     */
    private long retryInterval = 1000;

    /**
     * 是否启用HTTP缓存
     * 默认: true
     */
    private boolean enableHttpCache = true;

    /**
     * HTTP缓存大小（字节）
     * 默认: 10MB
     */
    private long httpCacheSize = 10 * 1024 * 1024;

    /**
     * 是否启用压缩
     * 默认: true
     */
    private boolean enableCompression = true;

    /**
     * 用户代理
     * 默认: DynamicConfig-HttpFetcher/1.0
     */
    private String userAgent = "DynamicConfig-HttpFetcher/1.0";

    /**
     * 是否验证SSL证书
     * 默认: true
     */
    private boolean verifySsl = true;

    /**
     * 是否跟随重定向
     * 默认: true
     */
    private boolean followRedirects = true;

    /**
     * 最大重定向次数
     * 默认: 5
     */
    private int maxRedirects = 5;

    /**
     * 是否启用连接池
     * 默认: true
     */
    private boolean enableConnectionPool = true;

    /**
     * 连接池最大空闲连接数
     * 默认: 5
     */
    private int maxIdleConnections = 5;

    /**
     * 连接池保持活跃时间（分钟）
     * 默认: 5分钟
     */
    private long keepAliveDuration = 5;

    /**
     * 是否启用请求日志
     * 默认: false
     */
    private boolean enableRequestLog = false;

    /**
     * 是否启用响应日志
     * 默认: false
     */
    private boolean enableResponseLog = false;

    /**
     * 自定义请求头
     */
    private java.util.Map<String, String> customHeaders = new java.util.HashMap<>();

    /**
     * 代理配置
     */
    private ProxyConfig proxy = new ProxyConfig();

    /**
     * 代理配置类
     */
    @Data
    public static class ProxyConfig {
        /**
         * 是否启用代理
         * 默认: false
         */
        private boolean enabled = false;

        /**
         * 代理主机
         */
        private String host;

        /**
         * 代理端口
         */
        private int port;

        /**
         * 代理用户名
         */
        private String username;

        /**
         * 代理密码
         */
        private String password;

        /**
         * 代理类型
         * 默认: HTTP
         */
        private ProxyType type = ProxyType.HTTP;

        public enum ProxyType {
            HTTP, SOCKS4, SOCKS5
        }
    }
} 