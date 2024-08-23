package com.lingfengx.mid.dynamic.config;

import cn.hutool.core.util.ArrayUtil;
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
import org.springframework.util.StringUtils;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@Slf4j
@Order(Integer.MAX_VALUE)
public class BootConfigProcessor implements EnvironmentPostProcessor {
    private static ConfigurableEnvironment configurableEnvironment;
    private static String[] scanPath = new String[0];
    private static String[] filePath = new String[0];
    //启动类
    private static Class<?>[] startClazz = new Class[0];
    private static String[] packageName = new String[0];
    private static Set<String> beanClazzSet = new HashSet<>();
    private static final Map<String, Map<Properties, List<BeanRef>>> placeHolderRefMap = new HashMap<>();

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        configurableEnvironment = environment;
        Class<?> mainApplicationClass = application.getMainApplicationClass();
        startClazz = ArrayUtil.append(startClazz, mainApplicationClass);
        boolean enable = false;
        for (Class<?> clazz : startClazz) {
            //加载所有注解定义的路径和类
            enable = loadDynamicValBeanRef(clazz) || enable;
        }
        if (enable || filePath.length > 0 || scanPath.length > 0) {
            log.info("====BootConfigProcessor 加载外部配置文件开始==== {}", mainApplicationClass);
            tryLoadLocationConfig();
            clearAllRef();
        }
    }


    public static void tryScanAndLoad(Class<?> clazz) {
        if (loadDynamicValBeanRef(clazz)) {
            tryLoadLocationConfig();
            clearAllRef();
        }
    }

    public static Set<String> getBeanClazz() {
        return beanClazzSet;
    }

    //packageName
    public static void addPackageName(String... name) {
        packageName = ArrayUtil.append(packageName, name);
    }

    public static void addScanPath(String... path) {
        scanPath = ArrayUtil.append(scanPath, path);
    }

    public static void addFilePath(String... path) {
        filePath = ArrayUtil.append(filePath, path);
    }

    public static void addStartClazz(Class<?>... clazz) {
        startClazz = ArrayUtil.append(startClazz, clazz);
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

    private static boolean loadDynamicValBeanRef(Class<?> mainClazz) {
        // 获取ClassLoader
        ClassLoader classLoader = BootConfigProcessor.class.getClassLoader();
        // 使用ClassLoader加载SpringApplication类
        Field[] declaredFields = EnableDynamicVal.class.getDeclaredFields();
        try {
            EnableDynamicVal enableDynamicVal = AnnotationUtils.findAnnotation(mainClazz, EnableDynamicVal.class);
            if (enableDynamicVal != null) {
                addPackageName(mainClazz.getPackage().getName());
                addScanPath(enableDynamicVal.scanPath());
                addFilePath(enableDynamicVal.filePath());
            } else {
                return false;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return true;
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
                    //添加bean类
                    beanClazzSet.add(candidate.getBeanClassName());
                }
                classesWithAnnotation.add(annotation);
            } catch (ClassNotFoundException e) {
                // 处理异常
                log.error(e.getMessage(), e);
            }
        }
        return classesWithAnnotation;
    }

    private static void tryLoadLocationConfig() {
        try {
            Set<DynamicValConfig> configs = new HashSet<>();
            for (String p : packageName) {
                if (StringUtils.hasLength(p)) {
                    configs.addAll(scanClassesWithDynamicValConfig(p));
                }
            }
            for (String path : scanPath) {
                if (StringUtils.hasLength(path)) {
                    configs.addAll(scanClassesWithDynamicValConfig(path));
                }
            }
            Map<String, Properties> propertiesMap = new HashMap<>();
            Map<String, HashSet<DynamicValConfig>> configBeanMap = new HashMap<>();
            //解析出来所有的配置文件
            for (DynamicValConfig config : configs) {
                String file = config.file();
                String filename = config.file();
                String fileType = config.fileType();
                HashSet<String> filePathList = new HashSet<>(Arrays.asList(filePath));
                //classpath自己
                filePathList.add("");
                for (String path : filePathList) {
                    //完整路径读取
                    if (path.startsWith(File.separator)) {
                        File localFile = new File(path + "/" + file);
                        if (localFile.exists() && localFile.isFile()) {
                            String absolutePath = localFile.getAbsolutePath();
                            Properties properties = propertiesMap.get(absolutePath);
                            if (properties == null) {
                                properties = convertProperties(localFile, filename, fileType);
                                propertiesMap.put(absolutePath, properties);
                            }
                            //must inputStream!=null
                            //inputStream已经存在了，直接添加ConfigBean
                            configBeanMap.putIfAbsent(absolutePath, new HashSet<>());
                            configBeanMap.get(absolutePath).add(config);
                        }
                    } else {
                        //classpath读
                        ClassPathResource classPathResource = new ClassPathResource(path + "/" + file);
                        if (classPathResource.exists() && classPathResource.isFile()) {
                            String absolutePath = classPathResource.getFile().getAbsolutePath();
                            Properties properties = propertiesMap.get(absolutePath);
                            if (properties == null) {
                                properties = convertProperties(classPathResource.getFile(), filename, fileType);
                                propertiesMap.put(absolutePath, properties);
                            }
                            //must inputStream!=null
                            //inputStream已经存在了，直接添加ConfigBean
                            configBeanMap.putIfAbsent(absolutePath, new HashSet<>());
                            configBeanMap.get(absolutePath).add(config);
                        }
                    }
                }
            }
            //加载配置文件
            {
                configBeanMap.forEach((path, configHashSet) -> {
                    final Properties properties = propertiesMap.get(path);
                    configHashSet.forEach(configBean -> {
                        try {
                            String file = configBean.file();
                            //解析并更新
                            DynamicValBeanPostProcessor.parseAndUpdate(properties, null);
                            //刷新到环境变量中去
                            BootConfigProcessor.setVal(new PropertiesPropertySource(file, properties));
                            //添加引用
                            properties.forEach((k, v) -> {
                                addRef(k, v, properties);
                                refreshOtherRef(k, v);
                            });
                        } catch (Exception e) {
                            log.error("bootConfigProcessor-err:{}", e.getMessage(), e);
                        }
                    });
                });
            }
            log.info("====BootConfigProcessor 加载外部配置文件完毕====");
        } catch (Exception e) {
            log.error("bootConfigProcessor-err:{}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 转换file -> Properties
     *
     * @param fileAbsolutePath
     * @param filename
     * @param fileType
     * @return
     * @throws IOException
     */
    public static Properties convertProperties(File fileAbsolutePath, String filename, String fileType) throws IOException {
        //存放inputStream
        InputStream inputStream = Files.newInputStream(fileAbsolutePath.toPath());
        String data = convertToString(inputStream);
        Properties properties = DynamicValBeanPostProcessor.convertProperties(data, filename, fileType);
        inputStream.close();
        return properties;
    }


    /**
     * 添加引用
     *
     * @param k
     * @param v
     * @param properties
     */
    private static void addRef(Object k, Object v, Properties properties) {
        //如果结果有占位符，则添加引用，等待更新
        if (SpelUtil.isPlaceholder(v.toString())) {
            String placeholder = SpelUtil.parsePlaceholder(v.toString());
            BeanRef beanRef = new BeanRef().setKey(k.toString()).setOriginalValue(v).setPlaceHolder(placeholder).setBean(properties);
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
    private static void refreshOtherRef(Object k, Object v) {
        //主动通知更新
        Map<Properties, List<BeanRef>> map = placeHolderRefMap.get(k);
        //能找到有引用的配置，且占位符已经被解析了
        if (map != null && !SpelUtil.isPlaceholder(v.toString())) {
            map.values().parallelStream().forEach(beanRefs -> {
                //todo 通知bean，bean的对应的引用属性有变化，需要单独重新加载这个属性值
                //进而可以刷新bean
            });
        }
    }

    /**
     * ${}配置环形检测
     * 例如：
     * A:${B}
     * B:${A}
     * 此时刷新配置会造成死循环
     *
     * @return
     */
    private boolean recycleCheck() {
        return false;
    }

    /**
     * 清除所有引用
     */
    private static void clearAllRef() {
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
