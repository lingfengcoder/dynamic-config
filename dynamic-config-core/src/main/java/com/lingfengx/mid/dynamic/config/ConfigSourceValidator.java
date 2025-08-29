package com.lingfengx.mid.dynamic.config;

/**
 * 配置源验证器接口
 * 用于验证配置源的有效性和配置内容的正确性
 */
public interface ConfigSourceValidator {
    
    /**
     * 验证配置源是否有效
     * @param location 配置位置
     * @return 验证结果
     */
    ValidationResult validateSource(String location);
    
    /**
     * 验证配置内容是否有效
     * @param content 配置内容
     * @param location 配置位置
     * @return 验证结果
     */
    ValidationResult validateContent(String content, String location);
    
    /**
     * 获取验证器支持的配置源类型
     * @return 配置源类型
     */
    String getSupportedSourceType();
    
    /**
     * 验证结果
     */
    class ValidationResult {
        private final boolean valid;
        private final String message;
        private final Throwable cause;
        
        public ValidationResult(boolean valid, String message) {
            this(valid, message, null);
        }
        
        public ValidationResult(boolean valid, String message, Throwable cause) {
            this.valid = valid;
            this.message = message;
            this.cause = cause;
        }
        
        public static ValidationResult success() {
            return new ValidationResult(true, "Validation successful");
        }
        
        public static ValidationResult success(String message) {
            return new ValidationResult(true, message);
        }
        
        public static ValidationResult failure(String message) {
            return new ValidationResult(false, message);
        }
        
        public static ValidationResult failure(String message, Throwable cause) {
            return new ValidationResult(false, message, cause);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
        
        public Throwable getCause() {
            return cause;
        }
    }
} 