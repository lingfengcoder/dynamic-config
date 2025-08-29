package com.lingfengx.mid.dynamic.config.zookeeper.ann;

import com.lingfengx.mid.dynamic.config.zookeeper.adapter.ZookeeperDynamicValAdapter;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Zookeeper动态配置注解
 * 用于标记需要从Zookeeper动态获取配置的类
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DynamicValZookeeper {

    /**
     * Zookeeper中的配置文件名（对应Zookeeper中的路径）
     */
    String file();

    /**
     * 配置前缀，用于过滤相关的配置项
     */
    String prefix();

    /**
     * 配置监听器适配器类
     */
    Class<?> listener() default ZookeeperDynamicValAdapter.class;

    /**
     * 是否启用自动刷新
     */
    boolean autoRefresh() default true;

    /**
     * 刷新间隔（秒），仅在autoRefresh为true时生效
     */
    int refreshInterval() default 30;

    /**
     * 是否在启动时立即加载配置
     */
    boolean loadOnStartup() default true;

    /**
     * 配置编码格式
     */
    String encoding() default "UTF-8";

    /**
     * 是否启用配置验证
     */
    boolean validate() default false;

    /**
     * 配置验证规则（JSON格式）
     */
    String validationRules() default "";

    /**
     * 是否启用配置缓存
     */
    boolean enableCache() default true;

    /**
     * 缓存过期时间（秒）
     */
    int cacheExpireSeconds() default 300;

    /**
     * 是否启用配置加密
     */
    boolean encrypted() default false;

    /**
     * 加密密钥（仅在encrypted为true时生效）
     */
    String encryptionKey() default "";

    /**
     * 是否启用配置压缩
     */
    boolean compressed() default false;

    /**
     * 压缩算法（仅在compressed为true时生效）
     */
    String compressionAlgorithm() default "GZIP";

    /**
     * 是否启用配置备份
     */
    boolean enableBackup() default false;

    /**
     * 备份路径
     */
    String backupPath() default "./config-backup";

    /**
     * 是否启用配置版本控制
     */
    boolean enableVersioning() default false;

    /**
     * 版本控制策略
     */
    VersioningStrategy versioningStrategy() default VersioningStrategy.TIMESTAMP;

    /**
     * 最大版本数量
     */
    int maxVersions() default 10;

    /**
     * 版本控制策略枚举
     */
    enum VersioningStrategy {
        /**
         * 基于时间戳的版本控制
         */
        TIMESTAMP,
        
        /**
         * 基于递增数字的版本控制
         */
        INCREMENTAL,
        
        /**
         * 基于哈希值的版本控制
         */
        HASH
    }
} 