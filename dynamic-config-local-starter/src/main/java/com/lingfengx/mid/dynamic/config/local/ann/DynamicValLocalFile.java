package com.lingfengx.mid.dynamic.config.local.ann;

import com.lingfengx.mid.dynamic.config.ann.DynamicValConfig;
import com.lingfengx.mid.dynamic.config.local.adapter.LocalFileDynamicValAdapter;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * 本地文件动态配置注解
 * 用于监听本地文件变化并自动更新配置
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@DynamicValConfig(listener = LocalFileDynamicValAdapter.class)
public @interface DynamicValLocalFile {

    /**
     * 配置文件路径
     * 支持以下路径格式：
     * - 绝对路径：/path/to/config.yml
     * - 相对路径：config.yml (相对于当前工作目录)
     * - classpath路径：classpath:config.yml
     */
    @AliasFor(annotation = DynamicValConfig.class, attribute = "file")
    String file() default "";

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
} 