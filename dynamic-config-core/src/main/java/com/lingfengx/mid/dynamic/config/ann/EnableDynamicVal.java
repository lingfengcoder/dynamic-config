package com.lingfengx.mid.dynamic.config.ann;

import com.lingfengx.mid.dynamic.config.DynamicValImportSelector;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import({DynamicValImportSelector.class})
public @interface EnableDynamicVal {
    String[] scanPath() default "";

    String[] filePath() default "";
}
