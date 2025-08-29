package com.lingfengx.mid.dynamic.config.local.provider;

import com.lingfengx.mid.dynamic.config.local.adapter.HttpRemoteFileFetcherConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

/**
 * HTTP远程文件拉取器
 * 支持通过HTTP拉取远程文件到本地，并进行文件对比
 */
@Slf4j
public class HttpRemoteFileFetcher {
    
    private final OkHttpClient httpClient;
    private final String localCacheDir;
    
    public HttpRemoteFileFetcher() {
        this(new HttpRemoteFileFetcherConfig());
    }
    
    public HttpRemoteFileFetcher(HttpRemoteFileFetcherConfig config) {
        this.httpClient = createHttpClient(config);
        this.localCacheDir = config.getLocalCacheDir();
        createCacheDirectoryIfNotExists();
    }
    
    /**
     * 创建HTTP客户端
     */
    private OkHttpClient createHttpClient(HttpRemoteFileFetcherConfig config) {
        return new OkHttpClient.Builder()
                .connectTimeout(config.getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getReadTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getWriteTimeout(), TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(config.isRetryOnConnectionFailure())
                .build();
    }
    
    /**
     * 创建缓存目录
     */
    private void createCacheDirectoryIfNotExists() {
        try {
            Path cachePath = Paths.get(localCacheDir);
            if (!Files.exists(cachePath)) {
                Files.createDirectories(cachePath);
                log.info("[http-remote-fetcher] created cache directory: {}", cachePath);
            }
        } catch (IOException e) {
            log.error("[http-remote-fetcher] failed to create cache directory: {}", localCacheDir, e);
        }
    }
    
    /**
     * 拉取远程文件到本地
     * 
     * @param remoteUrl 远程文件URL
     * @param localFileName 本地文件名
     * @return 拉取结果
     */
    public FetchResult fetchRemoteFile(String remoteUrl, String localFileName) {
        return fetchRemoteFile(remoteUrl, localFileName, null);
    }
    
    /**
     * 拉取远程文件到本地（带认证）
     * 
     * @param remoteUrl 远程文件URL
     * @param localFileName 本地文件名
     * @param authConfig 认证配置
     * @return 拉取结果
     */
    public FetchResult fetchRemoteFile(String remoteUrl, String localFileName, HttpAuthConfig authConfig) {
        if (!StringUtils.hasText(remoteUrl)) {
            return FetchResult.failure("Remote URL is empty");
        }
        
        if (!StringUtils.hasText(localFileName)) {
            return FetchResult.failure("Local file name is empty");
        }
        
        try {
            // 构建本地文件路径
            Path localFilePath = Paths.get(localCacheDir, localFileName);
            
            // 创建请求
            Request.Builder requestBuilder = new Request.Builder().url(remoteUrl);
            
            // 添加认证信息
            if (authConfig != null) {
                addAuthentication(requestBuilder, authConfig);
            }
            
            Request request = requestBuilder.build();
            
            log.info("[http-remote-fetcher] fetching remote file: {} -> {}", remoteUrl, localFilePath);
            
            // 执行请求
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return FetchResult.failure("HTTP request failed with code: " + response.code());
                }
                
                ResponseBody body = response.body();
                if (body == null) {
                    return FetchResult.failure("Response body is null");
                }
                
                // 读取远程文件内容
                byte[] remoteContent = body.bytes();
                String remoteContentHash = calculateHash(remoteContent);
                
                // 检查本地文件是否存在
                boolean localFileExists = Files.exists(localFilePath);
                String localContentHash = null;
                
                if (localFileExists) {
                    // 读取本地文件内容
                    byte[] localContent = Files.readAllBytes(localFilePath);
                    localContentHash = calculateHash(localContent);
                    
                    // 比较文件内容
                    if (remoteContentHash.equals(localContentHash)) {
                        log.info("[http-remote-fetcher] file content unchanged, skipping update: {}", localFilePath);
                        return FetchResult.unchanged(localFilePath.toString(), localContentHash);
                    }
                }
                
                // 写入本地文件
                Files.write(localFilePath, remoteContent);
                
                log.info("[http-remote-fetcher] successfully fetched remote file: {} -> {} (size: {} bytes)", 
                        remoteUrl, localFilePath, remoteContent.length);
                
                return FetchResult.success(localFilePath.toString(), remoteContentHash, 
                        localFileExists ? localContentHash : null, remoteContent.length);
                
            }
            
        } catch (IOException e) {
            log.error("[http-remote-fetcher] failed to fetch remote file: {}", remoteUrl, e);
            return FetchResult.failure("IO error: " + e.getMessage());
        } catch (Exception e) {
            log.error("[http-remote-fetcher] unexpected error fetching remote file: {}", remoteUrl, e);
            return FetchResult.failure("Unexpected error: " + e.getMessage());
        }
    }
    
    /**
     * 添加认证信息
     */
    private void addAuthentication(Request.Builder requestBuilder, HttpAuthConfig authConfig) {
        if (authConfig.getAuthType() == HttpAuthConfig.AuthType.BASIC) {
            String credentials = Credentials.basic(authConfig.getUsername(), authConfig.getPassword());
            requestBuilder.header("Authorization", credentials);
        } else if (authConfig.getAuthType() == HttpAuthConfig.AuthType.BEARER) {
            requestBuilder.header("Authorization", "Bearer " + authConfig.getToken());
        } else if (authConfig.getAuthType() == HttpAuthConfig.AuthType.API_KEY) {
            requestBuilder.header(authConfig.getApiKeyHeader(), authConfig.getApiKeyValue());
        }
    }
    
    /**
     * 计算文件内容的哈希值
     */
    private String calculateHash(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(content);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("[http-remote-fetcher] failed to calculate hash", e);
            return String.valueOf(content.length); // 降级使用内容长度
        }
    }
    
    /**
     * 获取本地文件路径
     */
    public String getLocalFilePath(String fileName) {
        return Paths.get(localCacheDir, fileName).toString();
    }
    
    /**
     * 清理缓存文件
     */
    public boolean cleanupCacheFile(String fileName) {
        try {
            Path filePath = Paths.get(localCacheDir, fileName);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("[http-remote-fetcher] cleaned up cache file: {}", filePath);
                return true;
            }
            return false;
        } catch (IOException e) {
            log.error("[http-remote-fetcher] failed to cleanup cache file: {}", fileName, e);
            return false;
        }
    }
    
    /**
     * 拉取结果
     */
    public static class FetchResult {
        private final boolean success;
        private final String localFilePath;
        private final String newContentHash;
        private final String oldContentHash;
        private final long fileSize;
        private final String errorMessage;
        private final boolean changed;
        
        private FetchResult(boolean success, String localFilePath, String newContentHash, 
                          String oldContentHash, long fileSize, String errorMessage, boolean changed) {
            this.success = success;
            this.localFilePath = localFilePath;
            this.newContentHash = newContentHash;
            this.oldContentHash = oldContentHash;
            this.fileSize = fileSize;
            this.errorMessage = errorMessage;
            this.changed = changed;
        }
        
        public static FetchResult success(String localFilePath, String newContentHash, 
                                        String oldContentHash, long fileSize) {
            return new FetchResult(true, localFilePath, newContentHash, oldContentHash, fileSize, null, true);
        }
        
        public static FetchResult unchanged(String localFilePath, String contentHash) {
            return new FetchResult(true, localFilePath, contentHash, contentHash, 0, null, false);
        }
        
        public static FetchResult failure(String errorMessage) {
            return new FetchResult(false, null, null, null, 0, errorMessage, false);
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getLocalFilePath() { return localFilePath; }
        public String getNewContentHash() { return newContentHash; }
        public String getOldContentHash() { return oldContentHash; }
        public long getFileSize() { return fileSize; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isChanged() { return changed; }
    }
    
    /**
     * HTTP认证配置
     */
    public static class HttpAuthConfig {
        private AuthType authType;
        private String username;
        private String password;
        private String token;
        private String apiKeyHeader;
        private String apiKeyValue;
        
        public enum AuthType {
            NONE, BASIC, BEARER, API_KEY
        }
        
        // Constructors
        public HttpAuthConfig() {
            this.authType = AuthType.NONE;
        }
        
        public HttpAuthConfig(AuthType authType) {
            this.authType = authType;
        }
        
        // Getters and Setters
        public AuthType getAuthType() { return authType; }
        public void setAuthType(AuthType authType) { this.authType = authType; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
        
        public String getApiKeyHeader() { return apiKeyHeader; }
        public void setApiKeyHeader(String apiKeyHeader) { this.apiKeyHeader = apiKeyHeader; }
        
        public String getApiKeyValue() { return apiKeyValue; }
        public void setApiKeyValue(String apiKeyValue) { this.apiKeyValue = apiKeyValue; }
        
        // Builder methods
        public HttpAuthConfig basicAuth(String username, String password) {
            this.authType = AuthType.BASIC;
            this.username = username;
            this.password = password;
            return this;
        }
        
        public HttpAuthConfig bearerToken(String token) {
            this.authType = AuthType.BEARER;
            this.token = token;
            return this;
        }
        
        public HttpAuthConfig apiKey(String header, String value) {
            this.authType = AuthType.API_KEY;
            this.apiKeyHeader = header;
            this.apiKeyValue = value;
            return this;
        }
    }
} 