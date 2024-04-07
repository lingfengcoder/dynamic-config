package com.lingfengx.mid.dynamic.config;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

public class DynamicValImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[]{DynamicValBeanPostProcessor.class.getName()};
    }
}
