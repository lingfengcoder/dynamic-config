package com.lingfengx.mid.dynamic.config.adapter;

import com.lingfengx.mid.dynamic.config.DynamicValListenerRegister;
import com.lingfengx.mid.dynamic.config.ann.DynamicValConfig;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;


@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@DynamicValConfig
public @interface DynamicValNacos {

    @AliasFor(annotation = DynamicValConfig.class, value = "file")
    String file() default "";

    @AliasFor(annotation = DynamicValConfig.class, value = "prefix")
    String prefix() default "";

    @AliasFor(annotation = DynamicValConfig.class, value = "fileType")
    String fileType() default "";

    @AliasFor(annotation = DynamicValConfig.class, value = "listener")
    Class<? extends DynamicValListenerRegister> listener() default ConfigMapDynamicValAdapter.class;

}
