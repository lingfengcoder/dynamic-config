package com.lingfengx.mid.dynamic.config;

import cn.hutool.core.util.ArrayUtil;
import com.lingfengx.mid.dynamic.config.ann.DynamicValConfig;
import com.lingfengx.mid.dynamic.config.ann.EnableDynamicVal;
import com.lingfengx.mid.dynamic.config.dto.BeanRef;
import com.lingfengx.mid.dynamic.config.util.SpelUtil;
import com.lingfengx.mid.dynamic.config.util.EarlyLogUtil;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.StringUtils;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static com.lingfengx.mid.dynamic.config.Banner.printLOGO;

public class BootConfigProcessor implements EnvironmentPostProcessor {
    private static ConfigurableEnvironment configurableEnvironment;
    private static String[] scanPath = new String[0];
    private static String[] filePath = new String[0];
    //启动类
    private static Class<?>[] startClazz = new Class[0];
    private static String[] packageName = new String[0];
    private static Set<String> beanClazzSet = new HashSet<>();
    private static final Map<String, Map<Properties, List<BeanRef>>> placeHolderRefMap = new HashMap<>();
    
    // 添加调试标志，可以通过系统属性控制
    private static final boolean DEBUG_MODE = Boolean.getBoolean("dynamic.config.debug");

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
            printLOGO();
            // 使用早期日志工具输出
            EarlyLogUtil.info("====BootConfigProcessor 加载外部配置文件开始==== " + mainApplicationClass);
            EarlyLogUtil.logStartupInfo();
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

        if (DEBUG_MODE) {
            EarlyLogUtil.debug("Scanning package: " + packageName + " for DynamicValConfig annotations");
        }

        for (BeanDefinition candidate : candidates) {
            try {
                Class<?> clazz = Class.forName(candidate.getBeanClassName());
                DynamicValConfig annotation = AnnotationUtils.findAnnotation(clazz, DynamicValConfig.class);
                if (annotation != null) {
                    //合并子注解属性: 用于扩展其他自定义注解@AliasFor
                    annotation = AnnotatedElementUtils.getMergedAnnotation(clazz, DynamicValConfig.class);
                    //添加bean类
                    beanClazzSet.add(candidate.getBeanClassName());
                    
                    if (DEBUG_MODE) {
                        EarlyLogUtil.debug("Found DynamicValConfig on class: " + candidate.getBeanClassName());
                    }
                }
                classesWithAnnotation.add(annotation);
            } catch (ClassNotFoundException e) {
                // 处理异常 - 使用早期日志工具
                EarlyLogUtil.error("Class not found: " + candidate.getBeanClassName() + " - " + e.getMessage(), e);
            }
        }
        return classesWithAnnotation;
    }

    private static void tryLoadLocationConfig() {
        try {
            // 在加载配置之前，先确保配置源已注册
            ensureConfigSourcesRegistered();
            
            if (DEBUG_MODE) {
                EarlyLogUtil.debug("Scanning packages: " + Arrays.toString(packageName));
                EarlyLogUtil.debug("Scanning paths: " + Arrays.toString(scanPath));
            }
            
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
            
            if (DEBUG_MODE) {
                EarlyLogUtil.debug("Found " + configs.size() + " DynamicValConfig annotations");
            }
            
            Map<String, Properties> propertiesMap = new HashMap<>();
            Map<String, HashSet<DynamicValConfig>> configBeanMap = new HashMap<>();
            
            // 解析出来所有的配置文件
            for (DynamicValConfig config : configs) {
                String file = config.file();
                String filename = config.file();
                String fileType = config.fileType();
                String sourceType = config.sourceType();
                
                // 构建配置位置
                String configLocation = buildConfigLocation(file, sourceType);
                
                if (DEBUG_MODE) {
                    EarlyLogUtil.debug("Processing config: " + file + " (type: " + fileType + ", source: " + sourceType + ")");
                }
                
                try {
                    // 使用ConfigSourceManager加载配置
                    String configContent = ConfigSourceManager.loadConfig(configLocation);
                    
                    // 转换配置内容为Properties
                    Properties properties = DynamicValBeanPostProcessor.convertProperties(configContent, filename, fileType);
                    
                    // 缓存Properties，避免重复加载
                    String cacheKey = configLocation + ":" + fileType;
                    propertiesMap.put(cacheKey, properties);
                    
                    // 添加配置Bean映射
                    configBeanMap.putIfAbsent(cacheKey, new HashSet<>());
                    configBeanMap.get(cacheKey).add(config);
                    
                    // 使用早期日志工具输出
                    EarlyLogUtil.info("Successfully loaded config from: " + configLocation + " using source type: " + 
                            (sourceType.isEmpty() ? "auto-detected" : sourceType));
                    
                } catch (Exception e) {
                    EarlyLogUtil.error("Failed to load config from: " + configLocation + " with source type: " + 
                            (sourceType.isEmpty() ? "auto-detected" : sourceType), e);
                }
            }
            
            // 加载配置文件到环境变量
            configBeanMap.forEach((cacheKey, configHashSet) -> {
                final Properties properties = propertiesMap.get(cacheKey);
                if (properties != null) {
                    configHashSet.forEach(configBean -> {
                        try {
                            String file = configBean.file();
                            // 解析并更新
                            DynamicValBeanPostProcessor.parseAndUpdate(properties, null);
                            // 刷新到环境变量中去
                            BootConfigProcessor.setVal(new PropertiesPropertySource(file, properties));
                            // 添加引用
                            properties.forEach((k, v) -> {
                                addRef(k, v, properties);
                                refreshOtherRef(k, v);
                            });
                        } catch (Exception e) {
                            EarlyLogUtil.error("bootConfigProcessor-err:" + e.getMessage(), e);
                        }
                    });
                }
            });
            
            EarlyLogUtil.info("====BootConfigProcessor 加载外部配置文件完毕====");
        } catch (Exception e) {
            EarlyLogUtil.error("bootConfigProcessor-err:" + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    
    /**
     * 确保配置源已注册
     * 在加载配置之前调用，确保所有配置源提供者和验证器都已注册
     */
    private static void ensureConfigSourcesRegistered() {
        try {
            // 检查是否已有配置源注册
            if (!ConfigSourceRegistry.getRegisteredSourceTypes().isEmpty()) {
                EarlyLogUtil.debug("Config sources already registered, skipping registration");
                return;
            }
            
            EarlyLogUtil.info("Ensuring config sources are registered before loading configurations");
            
            // 使用配置源扫描器自动发现和注册所有可用的配置源提供者
            ConfigSourceScanner.scanAndRegisterConfigSources();
            
            EarlyLogUtil.info("Config sources registration completed");
            
        } catch (Exception e) {
            EarlyLogUtil.warn("Failed to ensure config sources registration, will continue with existing sources");
        }
    }
    
    /**
     * 构建配置位置
     * @param file 文件路径
     * @param sourceType 配置源类型
     * @return 配置位置
     */
    private static String buildConfigLocation(String file, String sourceType) {
        if (!StringUtils.hasText(file)) {
            throw new IllegalArgumentException("Configuration file path cannot be empty");
        }
        
        // 如果指定了sourceType，使用指定的类型
        if (StringUtils.hasText(sourceType)) {
            ConfigSourceType configSourceType = ConfigSourceType.fromString(sourceType);
            if (configSourceType != null) {
                return configSourceType.getType() + "://" + file;
            }
        }
        
        // 根据文件路径自动推断配置源类型
        ConfigSourceType inferredType = ConfigSourceType.inferFromUrl(file);
        if (inferredType == ConfigSourceType.LOCAL) {
            // 对于本地配置源，处理classpath前缀
            if (file.startsWith("classpath:")) {
                return "local://" + file.substring("classpath:".length());
            } else if (file.startsWith("/") || file.contains(File.separator)) {
                return "local://" + file;
            } else {
                return "local://" + file;
            }
        } else {
            // 对于其他类型，直接返回原路径
            return file;
        }
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
