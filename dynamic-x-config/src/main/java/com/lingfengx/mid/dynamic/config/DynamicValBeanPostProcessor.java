package com.lingfengx.mid.dynamic.config;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.lingfengx.mid.dynamic.config.ann.DynamicValConfig;
import com.lingfengx.mid.dynamic.config.event.ConfigRefreshEvent;
import com.lingfengx.mid.dynamic.config.parser.ConfigFileTypeEnum;
import com.lingfengx.mid.dynamic.config.parser.ConfigParserHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.*;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;

import java.io.IOException;
import java.util.Map;

/**
 * 动态配置注解处理器
 */

@Slf4j
public class DynamicValBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

        Class<?> clazz = AopProxyUtils.ultimateTargetClass(bean);
        DynamicValConfig baseAnnotation = AnnotationUtils.findAnnotation(clazz, DynamicValConfig.class);
        if (baseAnnotation != null) {
            //合并子注解属性: 用于扩展其他自定义注解@AliasFor
            baseAnnotation = AnnotatedElementUtils.getMergedAnnotation(clazz, DynamicValConfig.class);
        }
        if (baseAnnotation != null) {
            checkParam(baseAnnotation);
            String file = baseAnnotation.file();
            String prefix = baseAnnotation.prefix();
            String fileType = baseAnnotation.fileType();
            if (fileType == null || fileType.isEmpty()) {
                fileType = file.toLowerCase().substring(file.lastIndexOf(".") + 1);
            }
            final String finalFileType = fileType;
            //注册监听
            Class<? extends DynamicValListenerRegister> listenerRegister = baseAnnotation.listener();
            //注册适配器
            DynamicValListenerRegister adapter = SpringUtil.getBean(listenerRegister);
            adapter.register(file, prefix, data -> process(data, file, prefix, finalFileType, bean));
        }
        return bean;
    }

    /**
     * 检查参数
     *
     * @param dynamicValConfig
     */
    private void checkParam(DynamicValConfig dynamicValConfig) {
        String file = dynamicValConfig.file();
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("config file can not be null");
        }
        String prefix = dynamicValConfig.prefix();
        if (prefix == null || prefix.isEmpty()) {
            //throw new RuntimeException("config prefix can not be null");
        }
        String fileType = dynamicValConfig.fileType();
        if (fileType == null || fileType.isEmpty()) {
            fileType = file.toLowerCase().substring(file.lastIndexOf(".") + 1);
        }
        if (ConfigFileTypeEnum.of(fileType) == null) {
            throw new RuntimeException("config file type is not support or null");
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }


    private void process(String data, String file, String prefix, String fileType, Object bean) {
        try {
            ConfigFileTypeEnum configFileType = ConfigFileTypeEnum.of(fileType);
            Map<Object, Object> newConfig = null;
            try {
                newConfig = ConfigParserHandler.getInstance().parseConfig(data, configFileType);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (CollectionUtil.isEmpty(newConfig)) {
                return;
            }
            Object newBean = loadConfigBeanByPrefix(prefix, newConfig, bean);
            //BeanUtils.copyProperties(bean, newBean);
            //发布配置更新的事件
            applicationContext.publishEvent(new ConfigRefreshEvent(file, prefix, newBean));
        } catch (Exception e) {
            log.error("process dynamic config error", e);
//            throw new RuntimeException("process dynamic config error", e);
        }
    }

    /**
     * 根据前缀加载配置
     *
     * @param prefix
     * @param configInfo
     * @param bean
     * @param <T>
     * @return
     */
    private <T> T loadConfigBeanByPrefix(String prefix, Map<Object, Object> configInfo, Object bean) {
        ConfigurationPropertySource sources = new MapConfigurationPropertySource(configInfo);
        Binder binder = new Binder(sources);
        Object o = binder.bind(prefix, Bindable.ofInstance(bean)).get();
        return (T) o;
    }

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
