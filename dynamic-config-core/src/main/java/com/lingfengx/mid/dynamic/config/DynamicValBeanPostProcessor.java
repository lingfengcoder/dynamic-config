package com.lingfengx.mid.dynamic.config;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.exceptions.ExceptionUtil;
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
import org.springframework.core.annotation.Order;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.lingfengx.mid.dynamic.config.util.SpelUtil.*;

/**
 * 动态配置注解处理器
 */

@Slf4j
@Order(Integer.MAX_VALUE)
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
            throw new RuntimeException("[dynamic-config] config file can not be null");
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
            throw new RuntimeException("[dynamic-config] config file type is not support or null");
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }


    protected static void process(String data, String file, String prefix, String fileType, Object bean) {
        try {
            if (!StringUtils.hasLength(data)) {
                log.warn("[dynamic-config] config has null data {}", file);
                return;
            }
            Properties newConfig = convertProperties(data, file, fileType);
            if (CollectionUtil.isEmpty(newConfig)) {
                return;
            }
            Object newBean = load(newConfig, file, prefix, bean);
            //发布配置更新的事件
            if (applicationContext != null && newBean != null) {
                applicationContext.publishEvent(new ConfigRefreshEvent(file, prefix, newBean));
            }
        } catch (Exception e) {
            log.error("[dynamic-config] dynamic config error {} ", e.getMessage(), e);
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
        parseAndUpdate(newConfig, bean, prefix);
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
    protected static Properties parseAndUpdate(Properties newConfig, Object bean, String prefix) {
        //将新的配置放入SPEL环境中
        for (Object key : newConfig.keySet()) {
            Object value = newConfig.get(key);
            String valStr = value == null ? null : value.toString();
            String parsedValue = parseFormatParam(newConfig, key == null ? null : key.toString(),
                    valStr, bean, null);
            if (key != null && !Objects.equals(parsedValue, valStr)) {
                newConfig.put(key, parsedValue);
                //刷新依赖的bean
                refreshOtherRefBean(newConfig, key);
            }
        }
        return newConfig;
    }


    /**
     * 解析参数成 实际的数据
     * <p>
     * 支持
     * value: abc_${name}_${age} -> value: abc_zhangsan_29
     * time: #{60*60} -> time: 3600
     * mix: abc_${name}_${age}_#{60*60} -> mix: abc_zhangsan_29_3600
     * <p>
     * <p>
     * <p>
     * #{'${shaman.s.aliyun.aksk}'.split(',')[0]}
     * '${shaman.s.aliyun.aksk}'.split(',')[0]
     *
     * @param newConfig
     * @param originalVal
     * @return
     */
    protected static String parseFormatParam(Properties newConfig, String key, String originalVal, Object bean, HashSet<String> visitedKeys) {
        if (key == null || originalVal == null) {
            return null;
        }
        if (visitedKeys == null) {
            visitedKeys = new HashSet<>(10);
        }
        String placeholder = originalVal;
        //将新的配置放入SPEL环境中
        StandardEvaluationContext evaluationContext = SpelUtil.transEvaluationContext(newConfig);
        //如果包含占位符
        if (SpelUtil.isPlaceholder(originalVal) || SpelUtil.isComputesPlaceholder(originalVal)) {
            while (isPlaceholder(placeholder) || isComputesPlaceholder(placeholder)) {
                int headHolderIdx = minPlaceholderIdx(placeholder, 0);
                int endIdx = getEndIdx(placeholder);
                if (endIdx <= 0) {
                    break;
                }
                if (visitedKeys.contains(placeholder)) {
                    break;
                }
                //${abc_#{60*60}} =>abc_#{60*60} 或者 #{60*${age}} => 60*${age}
                {
                    String prefix = placeholder.substring(headHolderIdx, headHolderIdx + 2);
                    String innerPlaceholder = placeholder.substring(headHolderIdx + 2, endIdx);
                    //abc_#{60*60}中 #{ 的位置
                    int innerIdx = minPlaceholderIdx(innerPlaceholder, 0);
                    //如果存在内部的占位符 ${abc_#{60*60}} 或者 ${abc}
                    if (innerIdx >= 0) {
                        //abc_#{60*60} 或者 abc
                        String tmp = parseFormatParam(newConfig, key, innerPlaceholder, bean, visitedKeys);
                        if (!Objects.equals(tmp, innerPlaceholder)) {
                            if (prefix.contains("#")) {
                                innerPlaceholder = parseNoWrapper(evaluationContext, tmp);
                            } else if (prefix.contains("$")) {
                                innerPlaceholder = parseWithWrapper(evaluationContext, tmp);
                            }
                            placeholder = placeholder.substring(0, headHolderIdx) + innerPlaceholder + (endIdx + 1 > placeholder.length() ? "" : placeholder.substring(endIdx + 1));
                        } else {
                            placeholder = placeholder;
                            visitedKeys.add(placeholder);
                        }
                        //todo 无论有没有解析出来，都要则进行记录，以便后续级联刷新
                        if (bean != null) {
                            addBeanRef(innerPlaceholder, key, newConfig.get(key), bean, null);
                        }
                    } else {
                        String value = null;
                        if (StringUtils.hasLength(innerPlaceholder)) {
                            //自我解析最新值
                            if (prefix.contains("#")) {
                                value = SpelUtil.parseNoWrapper(evaluationContext, innerPlaceholder);
                            } else {
                                value = SpelUtil.parseWithWrapper(evaluationContext, innerPlaceholder);
                            }
                            if (value == null) {
                                //如果占位符不能从自己种获取到，则从上下文中获取
                                try {
                                    String wrapperVal = null;
                                    if (prefix.contains("#")) {
                                        wrapperVal = wrapper(innerPlaceholder, "#");
                                    } else {
                                        wrapperVal = wrapper(innerPlaceholder, "$");
                                    }
                                    value = BootConfigProcessor.getVal(wrapperVal);
                                    //如果没有解析出来
                                    if (Objects.equals(value, wrapperVal)) {
                                        value = innerPlaceholder;
                                    }
                                } catch (Exception e) {
                                    log.error("parseFormatParam error:{} {}", e.getMessage(), ExceptionUtil.getMessage(e));
                                }
                            }
                            //todo 无论有没有解析出来，都要则进行记录，以便后续级联刷新
                            if (bean != null) {
                                addBeanRef(innerPlaceholder, key, newConfig.get(key), bean, null);
                            }
                        }
                        //没有解析出来
                        if (innerPlaceholder.equals(value)) {
                            visitedKeys.add(value);
                            //防止死循环
                            break;
                        }
                        innerPlaceholder = value == null ? innerPlaceholder : value;
                        placeholder = placeholder.substring(0, headHolderIdx) + innerPlaceholder + (endIdx + 1 > placeholder.length() ? "" : placeholder.substring(endIdx + 1));
                    }
                }
            }
        }
        return StringUtils.hasLength(placeholder) ? placeholder : originalVal;
    }

    private static String wrapper(String val, String prefix) {
        return prefix + "{" + val + "}";
    }

    /**
     * 添加bean的引用记录
     *
     * @param placeholder
     * @param key
     * @param originalVal
     * @param bean
     * @param prefix
     */
    private static void addBeanRef(String placeholder, Object key, Object originalVal, Object bean, String prefix) {
        dynamicValBeanRefMap.computeIfAbsent(placeholder, k -> new ConcurrentHashMap<>());
        ConcurrentHashMap<Object, CopyOnWriteArrayList<BeanRef>> map = dynamicValBeanRefMap.get(placeholder);
        map.computeIfAbsent(bean, k -> new CopyOnWriteArrayList<>());
        CopyOnWriteArrayList<BeanRef> beanRefs = map.get(bean);
        //将所需要的${xxx.yy}和bean关联
        beanRefs.add(new BeanRef().setPrefix(prefix).setBean(bean)
                .setKey(key.toString()).setOriginalValue(originalVal).setPlaceHolder(placeholder));
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
                    //不能直接简单的使用替换，而需要严格的解析
                    //todo getOriginalValue
                    newKV.put(targetKey, beanRef.getOriginalValue().toString().replace("${" + beanRef.getPlaceHolder() + "}", newVal == null ? null : newVal.toString()));
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
        try {
            ConfigurationPropertySource sources = new MapConfigurationPropertySource(configInfo);
            Binder binder = new Binder(sources);
            Object o = binder.bind(prefix, Bindable.ofInstance(bean)).get();
            return (T) o;
        } catch (Exception e) {
            log.error("load config err:{}{}{}", prefix, bean.getClass(), configInfo, e);
        }
        return null;
    }


    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }


    //test
    public static void main(String[] args) {
        Properties properties = new Properties();
        properties.setProperty("name", "lingfeng");
        properties.setProperty("name_29", "lingfeng");
        properties.setProperty("age1", "29");
        properties.setProperty("age2", "29");
        properties.setProperty("age", "29");
        properties.setProperty("time", "#{60*60}");
        properties.setProperty("name_3600", "finished");

        properties.setProperty("shaman.s.aliyun.aksk", "this_is_ak,this_is_sk");

        String placeholder = "#{'${shaman.s.aliyun.aksk}'.split(',')[0]}";
//        placeholder = "abc_#{${age1}*${age2}}";

        StandardEvaluationContext evaluationContext = SpelUtil.transEvaluationContext(properties);
        String name1 = parseWithWrapper(evaluationContext, "name");
        System.out.println(name1);

        while (placeholder.indexOf(PLACEHOLDER_SUFFIX) > 0) {
            int beginIdx = minPlaceholderIdx(placeholder, 0);
            int endIdx = getEndIdx(placeholder);
            placeholder = placeholder.substring(beginIdx + 2, endIdx);
            System.out.println(placeholder);
        }
        String mup = parseFormatParam(properties, "", "abc_#{${age1}*${age2}}", null, null);
        System.out.println(mup);


        String ak = parseFormatParam(properties, "", "bbq_name_#{'${shaman.s.aliyun.aksk}'.split(',')[0]}", null, null);
        System.out.println(ak);

        String sk = parseFormatParam(properties, "", "bbq_name_#{'${shaman.s.aliyun.aksk}'.split(',')[1]}", null, null);
        System.out.println(sk);

//        String number = parseFormatParam(properties, "#{60*60}");
//        System.out.println(number);
        String name = parseFormatParam(properties, "", "abc_${name_${age}}_${name_${time}}_${age}", null, null);
        System.out.println(name);
        String mix = parseFormatParam(properties, "", "abc_${name}_${age}_#{#{60+60}*#{70*70}}", null, null);
        System.out.println(mix);

    }
}
