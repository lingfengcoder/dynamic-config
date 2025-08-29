package com.lingfengx.mid.dynamic.config.local.ann;

import com.lingfengx.mid.dynamic.config.ann.DynamicValConfig;
import com.lingfengx.mid.dynamic.config.local.adapter.HttpRemoteFileDynamicValAdapter;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * HTTP远程文件动态配置注解
 * 用于通过HTTP拉取远程文件并监听变化
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@DynamicValConfig(listener = HttpRemoteFileDynamicValAdapter.class)
public @interface DynamicValHttpRemoteFile {

    /**
     * 远程文件URL
     * 支持以下URL格式：
     * - HTTP: http://example.com/config.yml
     * - HTTPS: https://example.com/config.yml
     * - 带查询参数: https://example.com/config.yml?v=1.0
     */
    @AliasFor(annotation = DynamicValConfig.class, attribute = "file")
    String remoteUrl() default "";

    /**
     * 配置前缀
     */
    @AliasFor(annotation = DynamicValConfig.class, attribute = "prefix")
    String prefix() default "";

    /**
     * 配置文件类型
     * 如果不指定，将根据文件扩展名自动推断
     */
    @AliasFor(annotation = DynamicValConfig.class, attribute = "fileType")
    String fileType() default "";

    /**
     * 是否只解析动态配置
     * true: 不会去环境变量中获取占位符配置
     */
    @AliasFor(annotation = DynamicValConfig.class, attribute = "onlyParseDynamic")
    boolean onlyParseDynamic() default false;

    /**
     * 拉取间隔（秒）
     * 0表示不启用定时拉取，只拉取一次
     * 默认: 0
     */
    long intervalSeconds() default 0;

    /**
     * 认证类型
     * 默认: NONE
     */
    AuthType authType() default AuthType.NONE;

    /**
     * 用户名（用于BASIC认证）
     */
    String username() default "";

    /**
     * 密码（用于BASIC认证）
     */
    String password() default "";

    /**
     * Token（用于BEARER认证）
     */
    String token() default "";

    /**
     * API Key Header（用于API_KEY认证）
     */
    String apiKeyHeader() default "";

    /**
     * API Key Value（用于API_KEY认证）
     */
    String apiKeyValue() default "";

    /**
     * 认证类型枚举
     */
    enum AuthType {
        NONE, BASIC, BEARER, API_KEY
    }

    /**
     * 兼容性：file属性映射到remoteUrl
     */
    @AliasFor("remoteUrl")
    String file() default "";
} 