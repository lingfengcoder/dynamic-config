package com.lingfengx.mid.dynamic.config.ann;

import com.lingfengx.mid.dynamic.config.BootConfigProcessor;
import com.lingfengx.mid.dynamic.config.DynamicValListenerRegister;
import lombok.EqualsAndHashCode;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
public @interface DynamicValConfig {

    /**
     * 配置文件（可使用路径）
     */

    String file() default "";

    /**
     * 配置前缀
     *
     * @return
     */
    String prefix() default "";

    /**
     * 配置文件的类型(yml/properties)
     *
     * @return
     */
    String fileType() default "";

    /**
     * 监听器适配器
     *
     * @return
     */
    Class<? extends DynamicValListenerRegister> listener() default DynamicValListenerRegister.class;

    /**
     * 是否只解析动态配置 ，默认false
     * true: 不会去环境变量中获取占位符配置 比如${application.name}
     *
     * @return
     */
    boolean onlyParseDynamic() default false;
}
