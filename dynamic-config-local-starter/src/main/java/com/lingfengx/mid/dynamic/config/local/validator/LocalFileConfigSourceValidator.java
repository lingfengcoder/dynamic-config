package com.lingfengx.mid.dynamic.config.local.validator;

import com.lingfengx.mid.dynamic.config.ConfigSourceValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地文件配置源验证器
 * 验证本地文件的有效性和配置内容的正确性
 */
@Slf4j
@Component
public class LocalFileConfigSourceValidator implements ConfigSourceValidator {
    
    private static final String SUPPORTED_SOURCE_TYPE = "local-file";
    
    @Override
    public ValidationResult validateSource(String location) {
        if (!StringUtils.hasText(location)) {
            return ValidationResult.failure("Location cannot be empty");
        }
        
        try {
            Path path = resolvePath(location);
            
            if (!Files.exists(path)) {
                return ValidationResult.failure("File does not exist: " + path);
            }
            
            if (!Files.isRegularFile(path)) {
                return ValidationResult.failure("Path is not a regular file: " + path);
            }
            
            if (!Files.isReadable(path)) {
                return ValidationResult.failure("File is not readable: " + path);
            }
            
            // 检查文件大小
            long fileSize = Files.size(path);
            if (fileSize > 10 * 1024 * 1024) { // 10MB限制
                return ValidationResult.failure("File size too large: " + fileSize + " bytes");
            }
            
            return ValidationResult.success("Local file validation successful");
            
        } catch (IOException e) {
            return ValidationResult.failure("Failed to validate local file: " + location, e);
        } catch (Exception e) {
            return ValidationResult.failure("Unexpected error during validation: " + e.getMessage(), e);
        }
    }
    
    @Override
    public ValidationResult validateContent(String content, String location) {
        if (content == null) {
            return ValidationResult.failure("Content cannot be null");
        }
        
        if (content.isEmpty()) {
            return ValidationResult.failure("Content cannot be empty");
        }
        
        // 检查内容长度
        if (content.length() > 1024 * 1024) { // 1MB内容限制
            return ValidationResult.failure("Content too large: " + content.length() + " characters");
        }
        
        // 检查文件扩展名，进行基本的格式验证
        String extension = getFileExtension(location);
        if (extension != null) {
            switch (extension.toLowerCase()) {
                case "yml":
                case "yaml":
                    if (!isValidYaml(content)) {
                        return ValidationResult.failure("Invalid YAML format");
                    }
                    break;
                case "properties":
                    if (!isValidProperties(content)) {
                        return ValidationResult.failure("Invalid properties format");
                    }
                    break;
                case "json":
                    if (!isValidJson(content)) {
                        return ValidationResult.failure("Invalid JSON format");
                    }
                    break;
                default:
                    // 对于其他格式，只做基本的内容检查
                    break;
            }
        }
        
        return ValidationResult.success("Content validation successful");
    }
    
    @Override
    public String getSupportedSourceType() {
        return SUPPORTED_SOURCE_TYPE;
    }
    
    /**
     * 解析文件路径
     */
    private Path resolvePath(String location) {
        if (location.startsWith("file://")) {
            return Paths.get(location.substring(7));
        }
        return Paths.get(location);
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String location) {
        if (location == null || location.isEmpty()) {
            return null;
        }
        
        int lastDotIndex = location.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < location.length() - 1) {
            return location.substring(lastDotIndex + 1);
        }
        
        return null;
    }
    
    /**
     * 简单的YAML格式验证
     */
    private boolean isValidYaml(String content) {
        // 这里可以实现更复杂的YAML验证逻辑
        // 目前只做基本的检查
        return content.trim().length() > 0;
    }
    
    /**
     * 简单的Properties格式验证
     */
    private boolean isValidProperties(String content) {
        // 这里可以实现更复杂的Properties验证逻辑
        // 目前只做基本的检查
        return content.trim().length() > 0;
    }
    
    /**
     * 简单的JSON格式验证
     */
    private boolean isValidJson(String content) {
        // 这里可以实现更复杂的JSON验证逻辑
        // 目前只做基本的检查
        String trimmed = content.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}") ||
               trimmed.startsWith("[") && trimmed.endsWith("]");
    }
} 