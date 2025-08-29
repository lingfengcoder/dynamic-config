package com.lingfengx.mid.dynamic.config;

import cn.hutool.core.util.ArrayUtil;
import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.util.StringUtils;

import com.lingfengx.mid.dynamic.config.util.EarlyLogUtil;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DynamicValImportSelector implements ImportSelector {
    private static final String BASE_PACKAGE = "com.lingfengx.mid.dynamic";

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        // 所有被注解扫描的类
        Set<String> beanClazz = BootConfigProcessor.getBeanClazz();
        
        // 扫描com.lingfengx.mid.dynamic包下的@Configuration和@Component类
        Set<String> scannedClasses = scanConfigurationAndComponentClasses();
        
        // 合并所有类
        Set<String> allClasses = new HashSet<>(beanClazz);
        allClasses.addAll(scannedClasses);
        
        String[] beans = new String[allClasses.size() + 1];
        allClasses.toArray(beans);
        beans[beans.length - 1] = DynamicValBeanPostProcessor.class.getName();
        
        EarlyLogUtil.info("DynamicValImportSelector selected " + beans.length + " classes for import");
        return beans;
    }
    
    /**
     * 扫描com.lingfengx.mid.dynamic包下所有的@Configuration和@Component类
     * @return 扫描到的类名集合
     */
    private Set<String> scanConfigurationAndComponentClasses() {
        Set<String> scannedClasses = new HashSet<>();
        
        try {
            // 扫描@Configuration注解的类
            scannedClasses.addAll(scanClassesWithAnnotation(Configuration.class, BASE_PACKAGE));
            
            // 扫描@Component注解的类
            scannedClasses.addAll(scanClassesWithAnnotation(Component.class, BASE_PACKAGE));
            
            EarlyLogUtil.info("Scanned " + scannedClasses.size() + " @Configuration and @Component classes from package: " + BASE_PACKAGE);
            
        } catch (Exception e) {
            EarlyLogUtil.warn("Failed to scan @Configuration and @Component classes from package: " + BASE_PACKAGE);
        }
        
        return scannedClasses;
    }
    
    /**
     * 扫描指定包下带有指定注解的类
     * @param annotationClass 要扫描的注解类型
     * @param basePackage 基础包名
     * @return 扫描到的类名集合
     */
    private Set<String> scanClassesWithAnnotation(Class annotationClass, String basePackage) {
        Set<String> classesWithAnnotation = new HashSet<>();
        
        try {
            ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
            provider.addIncludeFilter(new AnnotationTypeFilter(annotationClass));
            
            Set<BeanDefinition> candidates = provider.findCandidateComponents(basePackage);
            
            for (BeanDefinition candidate : candidates) {
                try {
                    String className = candidate.getBeanClassName();
                    // 使用更安全的字符串检查方式，兼容JDK8
                    if (className != null && !className.trim().isEmpty()) {
                        // 验证类是否确实有指定的注解
                        Class<?> clazz = Class.forName(className);
                        if (AnnotationUtils.findAnnotation(clazz, annotationClass) != null) {
                            classesWithAnnotation.add(className);
                            EarlyLogUtil.debug("Found " + annotationClass.getSimpleName() + " class: " + className);
                        }
                    }
                } catch (ClassNotFoundException e) {
                    EarlyLogUtil.warn("Class not found: " + candidate.getBeanClassName());
                } catch (Exception e) {
                    EarlyLogUtil.warn("Error processing class: " + candidate.getBeanClassName());
                }
            }
            
        } catch (Exception e) {
            EarlyLogUtil.warn("Failed to scan classes with annotation: " + annotationClass.getSimpleName());
        }
        
        return classesWithAnnotation;
    }
}
