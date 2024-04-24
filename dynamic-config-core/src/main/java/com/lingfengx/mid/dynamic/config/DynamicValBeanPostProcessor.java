package com.lingfengx.mid.dynamic.config;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import com.lingfengx.mid.dynamic.config.ann.DynamicValConfig;
import com.lingfengx.mid.dynamic.config.dto.BeanRef;
import com.lingfengx.mid.dynamic.config.event.ConfigRefreshEvent;
import com.lingfengx.mid.dynamic.config.parser.ConfigFileTypeEnum;
import com.lingfengx.mid.dynamic.config.parser.ConfigParserHandler;
import com.lingfengx.mid.dynamic.config.util.SpelUtil;
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
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 动态配置注解处理器
 */

@Slf4j
public class DynamicValBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {
    private static ApplicationContext applicationContext;
    //<${placeholder},<bean,[key1,key2]>>
    private static final Map<String, ConcurrentHashMap<Object, CopyOnWriteArrayList<BeanRef>>> dynamicValBeanRefMap = new ConcurrentHashMap<>();
    //bean对应的placeholder <bean,placeholder>
    private static final Map<Object, ConcurrentHashSet<String>> beanPlaceHolder = new ConcurrentHashMap<>();

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
            DynamicValListenerRegister adapter = applicationContext.getBean(listenerRegister);
            adapter.register(file, prefix, data -> process(data, file, prefix, finalFileType, bean));
        }
        return bean;
    }

    /**
     * 检查参数
     *
     * @param dynamicValConfig
     */
    private static void checkParam(DynamicValConfig dynamicValConfig) {
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


    protected static void process(String data, String file, String prefix, String fileType, Object bean) {
        try {
            Properties newConfig = convertProperties(data, file, fileType);
            if (CollectionUtil.isEmpty(newConfig)) {
                return;
            }
            Object newBean = load(newConfig, file, prefix, bean);
            //发布配置更新的事件
            if (applicationContext != null) {
                applicationContext.publishEvent(new ConfigRefreshEvent(file, prefix, newBean));
            }
        } catch (Exception e) {
            log.error("process dynamic config error", e);
//            throw new RuntimeException("process dynamic config error", e);
        }
    }

    protected static Properties convertProperties(String data, String filename, String fileType) {

        if (fileType == null || fileType.isEmpty()) {
            fileType = filename.toLowerCase().substring(filename.lastIndexOf(".") + 1);
        }
        ConfigFileTypeEnum configFileType = ConfigFileTypeEnum.of(fileType);
        try {
            return ConfigParserHandler.getInstance().parseConfig(data, configFileType);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected static Object load(Properties newConfig, String file, String prefix, Object bean) {
        //删除已有引用记录
        removeBeanRef(bean);
        //解析并更新配置
        parserAndUpdate(newConfig, bean, prefix);
        //更新配置到环境
        updateConfigToEnv(file, newConfig);
        //加载配置并更新bean
        return loadConfigBeanByPrefix(prefix, newConfig, bean);
    }


    /**
     * 更新配置到环境
     *
     * @param file
     * @param newConfig
     */
    private static void updateConfigToEnv(String file, Properties newConfig) {
        BootConfigProcessor.setVal(new PropertiesPropertySource(file, newConfig));
    }

    /**
     * 解析并更新配置
     *
     * @param newConfig
     * @param bean
     * @param prefix
     */
    protected static Properties parserAndUpdate(Properties newConfig, Object bean, String prefix) {
        //将新的配置放入SPEL环境中
        StandardEvaluationContext evaluationContext = SpelUtil.transEvaluationContext(newConfig);
        for (Object key : newConfig.keySet()) {
            //如果包含占位符
            Object value = newConfig.get(key);
            if (SpelUtil.isPlaceholder(value.toString()) || SpelUtil.isSPEL(value.toString())) {
                String placeholder = SpelUtil.parsePlaceholder(value.toString());
                //自我解析最新值
                String val = SpelUtil.parse(evaluationContext, placeholder);
                if (val == null) {
                    //如果占位符不能从自己种获取到，则从上下文中获取
                    val = BootConfigProcessor.getVal(value.toString());
                }
                //无论有没有解析出来，都要则进行记录，以便后续级联刷新
                if (bean != null) {
                    addBeanRef(placeholder, key, value, bean, prefix);
                }
                newConfig.put(key, val);
            }
        }
        for (Object key : newConfig.keySet()) {
            //刷新依赖的bean
            refreshOtherRefBean(newConfig, key);
        }
        return newConfig;
    }


    /**
     * 添加bean的引用记录
     *
     * @param placeholder
     * @param key
     * @param val
     * @param bean
     * @param prefix
     */
    private static void addBeanRef(String placeholder, Object key, Object val, Object bean, String prefix) {
        dynamicValBeanRefMap.computeIfAbsent(placeholder, k -> new ConcurrentHashMap<>());
        ConcurrentHashMap<Object, CopyOnWriteArrayList<BeanRef>> map = dynamicValBeanRefMap.get(placeholder);
        map.computeIfAbsent(bean, k -> new CopyOnWriteArrayList<>());
        CopyOnWriteArrayList<BeanRef> beanRefs = map.get(bean);
        //将所需要的${xxx.yy}和bean关联
        beanRefs.add(new BeanRef().setPrefix(prefix).setBean(bean)
                .setKey(key.toString()).setValue(val).setPlaceHolder(placeholder));
        //bean对应的placeholder
        beanPlaceHolder.computeIfAbsent(bean, k -> new ConcurrentHashSet<>());
        Set<String> beanHolderSet = beanPlaceHolder.get(bean);
        beanHolderSet.add(placeholder);
    }

    /**
     * 删除bean所有的占位符级联记录
     *
     * @param bean
     */
    private static void removeBeanRef(Object bean) {
        //查询当前bean已经存在的占位符
        Set<String> placeHolderSet = beanPlaceHolder.get(bean);
        if (placeHolderSet != null) {
            for (String p : placeHolderSet) {
                ConcurrentHashMap<Object, CopyOnWriteArrayList<BeanRef>> map = dynamicValBeanRefMap.get(p);
                //删除已有引用记录
                map.remove(bean);
            }
        }
    }

    /**
     * 刷新依赖的bean
     *
     * @param newConfig
     * @param key
     */
    private static void refreshOtherRefBean(Properties newConfig, Object key) {
        ConcurrentHashMap<Object, CopyOnWriteArrayList<BeanRef>> map = dynamicValBeanRefMap.get(key.toString());
        if (map != null) {
            Collection<CopyOnWriteArrayList<BeanRef>> beanRefs = map.values();
            //每个bean一个线程
            beanRefs.parallelStream().forEach(beanRefList -> {
                //逐个刷新
                for (BeanRef beanRef : beanRefList) {
                    //配置文件前缀
                    String targetPrefix = beanRef.getPrefix();
                    //需要刷新的bean
                    Object refBean = beanRef.getBean();
                    //需要刷新的key
                    String targetKey = beanRef.getKey();
                    Map<Object, Object> newKV = new HashMap<>(1);
                    //将placeholder替换为新的值
                    Object newVal = newConfig.get(key);
                    newKV.put(targetKey, beanRef.getValue().toString().replace("${" + beanRef.getPlaceHolder() + "}", newVal == null ? null : newVal.toString()));
                    try {
                        //给bean赋新的值
                        loadConfigBeanByPrefix(targetPrefix, newKV, refBean);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            });
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
    private static <T> T loadConfigBeanByPrefix(String prefix, Map<Object, Object> configInfo, Object bean) {
        ConfigurationPropertySource sources = new MapConfigurationPropertySource(configInfo);
        Binder binder = new Binder(sources);
        Object o = binder.bind(prefix, Bindable.ofInstance(bean)).get();
        return (T) o;
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
