package com.lingfengx.mid.dynamic.config.ann;

import com.lingfengx.mid.dynamic.config.DynamicValListenerRegister;
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
     * @return
     */
    String prefix() default "";

    /**
     * 配置文件的类型(yml/properties)
     * @return
     */
    String fileType() default "";

    /**
     * 监听器适配器
     * @return
     */
    Class<? extends DynamicValListenerRegister> listener() default DynamicValListenerRegister.class;
}
