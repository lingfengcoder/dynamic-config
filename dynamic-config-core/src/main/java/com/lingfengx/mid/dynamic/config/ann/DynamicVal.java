package com.lingfengx.mid.dynamic.config.ann;

import java.lang.annotation.*;

/**
 * 动态配置值注解
 * 用于标记需要动态配置的字段
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DynamicVal {
    
    /**
     * 配置源路径
     * 支持多种格式：
     * - local://classpath:config/app.yml
     * - local:///tmp/config.yml
     * - http://localhost:8080/api/config
     * - https://api.example.com/config
     * - zookeeper://localhost:2181/config/app
     */
    String value();
    
    /**
     * 配置键名（可选）
     * 如果不指定，将使用字段名作为配置键
     */
    String key() default "";
    
    /**
     * 默认值（可选）
     * 当配置源不可用时使用的默认值
     */
    String defaultValue() default "";
    
    /**
     * 是否启用监听（可选）
     * 默认true，启用配置变更监听
     */
    boolean enableListener() default true;
    
    /**
     * 轮询间隔（秒，可选）
     * 仅对HTTP配置源有效，默认30秒
     */
    int pollInterval() default 30;
} 