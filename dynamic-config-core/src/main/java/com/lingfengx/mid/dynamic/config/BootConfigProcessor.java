package com.lingfengx.mid.dynamic.config;

import cn.hutool.core.collection.ConcurrentHashSet;
import com.lingfengx.mid.dynamic.config.ann.DynamicValConfig;
import com.lingfengx.mid.dynamic.config.ann.EnableDynamicVal;
import com.lingfengx.mid.dynamic.config.dto.BeanRef;
import com.lingfengx.mid.dynamic.config.util.SpelUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Slf4j
@Order(Integer.MAX_VALUE)
public class BootConfigProcessor implements EnvironmentPostProcessor {
    private static ConfigurableEnvironment configurableEnvironment;
    private static String[] scanPath;
    private static String[] filePath;
    private static String packageName;
    private static final Map<String, Map<Properties, List<BeanRef>>> placeHolderRefMap = new HashMap<>();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        configurableEnvironment = environment;
        Class<?> mainApplicationClass = application.getMainApplicationClass();
        log.info("====BootConfigProcessor 加载外部配置文件开始==== {}", mainApplicationClass);
        loadDynamicValBeanRef(mainApplicationClass);
        tryLoadLocationConfig();
        clearAllRef();
    }

    public static String getVal(String key) {
        return configurableEnvironment.resolvePlaceholders(key);
    }

    public static void setVal(PropertySource<?> source) {
        configurableEnvironment.getPropertySources().addLast(source);
    }

    public static PropertySource<?> getPropertyByName(String name) {
        MutablePropertySources propertySources = configurableEnvironment.getPropertySources();
        return propertySources.get(name);
    }

    private void loadDynamicValBeanRef(Class<?> mainClazz) {
        // 获取ClassLoader
        ClassLoader classLoader = BootConfigProcessor.class.getClassLoader();
        // 使用ClassLoader加载SpringApplication类
        Field[] declaredFields = EnableDynamicVal.class.getDeclaredFields();
        try {
            EnableDynamicVal enableDynamicVal = AnnotationUtils.findAnnotation(mainClazz, EnableDynamicVal.class);
            packageName = mainClazz.getPackage().getName();
            scanPath = enableDynamicVal.scanPath();
            filePath = enableDynamicVal.filePath();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * 扫描所有被DynamicValConfig修饰的类
     *
     * @param packageName
     * @return
     */
    public static Set<DynamicValConfig> scanClassesWithDynamicValConfig(String packageName) {
        ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false);
        provider.addIncludeFilter(new AnnotationTypeFilter(DynamicValConfig.class));

        Set<DynamicValConfig> classesWithAnnotation = new HashSet<>();
        Set<BeanDefinition> candidates = provider.findCandidateComponents(packageName);

        for (BeanDefinition candidate : candidates) {
            try {
                Class<?> clazz = Class.forName(candidate.getBeanClassName());
                DynamicValConfig annotation = AnnotationUtils.findAnnotation(clazz, DynamicValConfig.class);
                if (annotation != null) {
                    //合并子注解属性: 用于扩展其他自定义注解@AliasFor
                    annotation = AnnotatedElementUtils.getMergedAnnotation(clazz, DynamicValConfig.class);
                }
                classesWithAnnotation.add(annotation);
            } catch (ClassNotFoundException e) {
                // 处理异常
                log.error(e.getMessage(), e);
            }
        }
        return classesWithAnnotation;
    }

    private void tryLoadLocationConfig() {
        List<Properties> result = new ArrayList<>();
        try {
            Set<DynamicValConfig> configs = scanClassesWithDynamicValConfig(packageName);
            if (scanPath != null) {
                for (String path : scanPath) {
                    configs.addAll(scanClassesWithDynamicValConfig(path));
                }
            }
            for (DynamicValConfig config : configs) {
                String file = config.file();
                String type = config.fileType();
                String prefix = config.prefix();
                List<String> filePathList = new ArrayList<>(Arrays.asList(filePath));
                //classpath自己
                filePathList.add("");
                for (String path : filePathList) {
                    if (new ClassPathResource(path + "/" + file).exists()) {
                        try (InputStream input = new ClassPathResource(path + "/" + file).getInputStream()) {
                            String data = convertToString(input);
                            Properties properties = DynamicValBeanPostProcessor.convertProperties(data, file, type);
                            //解析并更新
                            DynamicValBeanPostProcessor.parserAndUpdate(properties, null, config.prefix());
                            //刷新到环境变量中去
                            BootConfigProcessor.setVal(new PropertiesPropertySource(file, properties));
                            //添加引用
                            properties.forEach((k, v) -> {
                                addRef(k, v, properties);
                                refreshOtherRef(k, v);
                            });
                            break;
                        } catch (Exception e) {
                            log.error(e.getMessage(), e);
                        }
                    }
                }
            }
            log.info("====BootConfigProcessor 加载外部配置文件完毕====");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }


    /**
     * 添加引用
     *
     * @param k
     * @param v
     * @param properties
     */
    private void addRef(Object k, Object v, Properties properties) {
        //如果结果有占位符，则添加引用，等待更新
        if (SpelUtil.isPlaceholder(v.toString())) {
            String placeholder = SpelUtil.parsePlaceholder(v.toString());
            BeanRef beanRef = new BeanRef().setKey(k.toString()).setValue(v).setPlaceHolder(placeholder).setBean(properties);
            placeHolderRefMap.computeIfAbsent(placeholder, key -> new HashMap<>());
            Map<Properties, List<BeanRef>> map = placeHolderRefMap.get(placeholder);
            map.computeIfAbsent(properties, key -> new ArrayList<>());
            map.get(properties).add(beanRef);
        }
    }

    /**
     * 刷新其他 还有占位符的引用
     *
     * @param k
     * @param v
     */
    private void refreshOtherRef(Object k, Object v) {
        //主动通知更新
        Map<Properties, List<BeanRef>> map = placeHolderRefMap.get(k);
        //能找到有引用的配置，且占位符已经被解析了
        if (map != null && !SpelUtil.isPlaceholder(v.toString())) {
            map.values().parallelStream().forEach(beanRefs -> {
                for (BeanRef beanRef : beanRefs) {
                    Properties p = (Properties) beanRef.getBean();
                    p.setProperty(beanRef.getKey(), beanRef.getValue().toString().replace("${" + beanRef.getPlaceHolder() + "}", v.toString()));
                }
            });
        }
    }

    /**
     * 清除所有引用
     */
    private void clearAllRef() {
        placeHolderRefMap.values().forEach(Map::clear);
        placeHolderRefMap.clear();
    }

    public static String convertToString(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            char[] buffer = new char[1024];
            int length;
            while ((length = bufferedReader.read(buffer)) != -1) {
                builder.append(buffer, 0, length);
            }
        }
        return builder.toString();
    }


}
