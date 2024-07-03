package com.lingfengx.mid.dynamic.config;

import cn.hutool.core.util.ArrayUtil;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Arrays;
import java.util.Set;

public class DynamicValImportSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        //所有被注解扫描的类
        Set<String> beanClazz = BootConfigProcessor.getBeanClazz();
        String[] beans = new String[beanClazz.size() + 1];
        beanClazz.toArray(beans);
        beans[beans.length - 1] = DynamicValBeanPostProcessor.class.getName();
        return beans;
    }
}
