package com.lingfengx.mid.dynamic.config.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.util.StatusPrinter;
import ch.qos.logback.classic.Level;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 早期日志工具类
 * 用于在Spring Boot启动早期阶段输出日志信息
 * 支持加载logback配置文件
 */
public class EarlyLogUtil {
    
    private static final String LOGGER_NAME = "DynamicConfig";
    private static final Logger logger = LoggerFactory.getLogger(LOGGER_NAME);
    private static final AtomicBoolean logbackInitialized = new AtomicBoolean(false);
    private static final AtomicBoolean silentMode = new AtomicBoolean(false);
    private static final AtomicBoolean debugMode = new AtomicBoolean(false);
    private static Level configuredLevel = Level.INFO;
    
    static {
        initializeLogging();
    }
    
    /**
     * 初始化日志系统
     */
    private static void initializeLogging() {
        // 检查系统属性
        silentMode.set(Boolean.getBoolean("dynamic.config.silent"));
        debugMode.set(Boolean.getBoolean("dynamic.config.debug"));
        
        String logLevel = System.getProperty("dynamic.config.log.level", "INFO");
        configuredLevel = parseLogLevel(logLevel);
        
        // 尝试加载logback配置
        if (!silentMode.get()) {
            tryLoadLogbackConfig();
        }
    }
    
    /**
     * 尝试加载logback配置文件
     * 优先使用项目classpath中的配置文件
     */
    private static void tryLoadLogbackConfig() {
        try {
            // 获取LoggerContext
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            
            // 优先尝试加载项目classpath中的logback配置文件
            String[] configFiles = {
                "logback-spring.xml",
                "logback.xml",
                "logback-test.xml"
            };
            
            boolean configLoaded = false;
            for (String configFile : configFiles) {
                if (loadLogbackConfig(loggerContext, configFile)) {
                    configLoaded = true;
                    if (debugMode.get()) {
                        info("Successfully loaded logback config from classpath: " + configFile);
                    }
                    break;
                }
            }
            
            if (configLoaded) {
                logbackInitialized.set(true);
                // 设置我们的logger级别
                ch.qos.logback.classic.Logger dynamicLogger = loggerContext.getLogger(LOGGER_NAME);
                dynamicLogger.setLevel(configuredLevel);
                
                // 输出初始化信息
                if (debugMode.get()) {
                    info("Logback configuration loaded successfully from project classpath");
                    info("Debug mode: " + debugMode.get());
                    info("Log level: " + configuredLevel);
                    info("Silent mode: " + silentMode.get());
                }
            } else {
                // 如果没有找到配置文件，使用默认配置
                if (debugMode.get()) {
                    info("No logback config found in classpath, using default configuration");
                }
                setupDefaultLogging(loggerContext);
            }
            
        } catch (Exception e) {
            // 如果logback初始化失败，回退到控制台输出
            System.err.println("Failed to initialize logback: " + e.getMessage());
            setupDefaultLogging(null);
        }
    }
    
    /**
     * 加载指定的logback配置文件
     * 优先使用项目classpath中的配置文件，避免使用内部默认配置
     */
    private static boolean loadLogbackConfig(LoggerContext loggerContext, String configFile) {
        try {
            // 优先尝试从当前类的ClassLoader加载配置文件（项目classpath）
            InputStream configStream = EarlyLogUtil.class.getClassLoader().getResourceAsStream(configFile);
            
            // 如果当前ClassLoader没有找到，尝试从线程上下文ClassLoader加载
            if (configStream == null) {
                ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
                if (threadClassLoader != null && threadClassLoader != EarlyLogUtil.class.getClassLoader()) {
                    configStream = threadClassLoader.getResourceAsStream(configFile);
                }
            }
            
            // 最后尝试从系统ClassLoader加载
            if (configStream == null) {
                configStream = ClassLoader.getSystemResourceAsStream(configFile);
            }
            
            if (configStream != null) {
                if (debugMode.get()) {
                    System.out.println("Loading logback config from: " + configFile);
                }
                
                JoranConfigurator configurator = new JoranConfigurator();
                configurator.setContext(loggerContext);
                loggerContext.reset();
                configurator.doConfigure(configStream);
                configStream.close();
                
                // 打印状态信息（仅在调试模式下）
                if (debugMode.get()) {
                    StatusPrinter.printInCaseOfErrorsOrWarnings(loggerContext);
                }
                
                return true;
            } else {
                if (debugMode.get()) {
                    System.out.println("Logback config not found: " + configFile);
                }
            }
        } catch (Exception e) {
            if (debugMode.get()) {
                System.err.println("Failed to load logback config " + configFile + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        return false;
    }
    
    /**
     * 设置默认的日志配置
     */
    private static void setupDefaultLogging(LoggerContext loggerContext) {
        if (loggerContext != null) {
            // 设置根logger级别
            ch.qos.logback.classic.Logger rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            rootLogger.setLevel(configuredLevel);
            
            // 设置我们的logger级别
            ch.qos.logback.classic.Logger dynamicLogger = loggerContext.getLogger(LOGGER_NAME);
            dynamicLogger.setLevel(configuredLevel);
        }
    }
    
    /**
     * 解析日志级别
     */
    private static Level parseLogLevel(String level) {
        switch (level.toUpperCase()) {
            case "DEBUG":
                return Level.DEBUG;
            case "INFO":
                return Level.INFO;
            case "WARN":
                return Level.WARN;
            case "ERROR":
                return Level.ERROR;
            default:
                return Level.INFO;
        }
    }
    
    /**
     * 输出调试信息
     */
    public static void debug(String message) {
        if (shouldLog(Level.DEBUG)) {
            String prefixedMessage = "[dynamic-config] " + message;
            if (logbackInitialized.get()) {
                logger.debug(prefixedMessage);
            } else {
                outputToConsole("DEBUG", prefixedMessage);
            }
        }
    }
    
    /**
     * 输出信息
     */
    public static void info(String message) {
        if (shouldLog(Level.INFO)) {
            String prefixedMessage = "[dynamic-config] " + message;
            if (logbackInitialized.get()) {
                logger.info(prefixedMessage);
            } else {
                outputToConsole("INFO", prefixedMessage);
            }
        }
    }
    
    /**
     * 输出警告信息
     */
    public static void warn(String message) {
        if (shouldLog(Level.WARN)) {
            String prefixedMessage = "[dynamic-config] " + message;
            if (logbackInitialized.get()) {
                logger.warn(prefixedMessage);
            } else {
                outputToConsole("WARN", prefixedMessage);
            }
        }
    }
    
    /**
     * 输出错误信息
     */
    public static void error(String message) {
        if (shouldLog(Level.ERROR)) {
            String prefixedMessage = "[dynamic-config] " + message;
            if (logbackInitialized.get()) {
                logger.error(prefixedMessage);
            } else {
                outputToConsole("ERROR", prefixedMessage);
            }
        }
    }
    
    /**
     * 输出错误信息和异常
     */
    public static void error(String message, Throwable throwable) {
        if (shouldLog(Level.ERROR)) {
            String prefixedMessage = "[dynamic-config] " + message;
            if (logbackInitialized.get()) {
                logger.error(prefixedMessage, throwable);
            } else {
                outputToConsole("ERROR", prefixedMessage);
                if (throwable != null) {
                    throwable.printStackTrace();
                }
            }
        }
    }
    
    /**
     * 判断是否应该输出日志
     */
    private static boolean shouldLog(Level level) {
        if (silentMode.get()) {
            return false;
        }
        return level.isGreaterOrEqual(configuredLevel);
    }
    
    /**
     * 输出到控制台（回退方案）
     */
    private static void outputToConsole(String level, String message) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String timestamp = sdf.format(new Date());
        String threadName = Thread.currentThread().getName();
        
        // 如果消息已经包含前缀，直接使用；否则添加前缀
        String finalMessage = message.startsWith("[dynamic-config]") ? message : "[dynamic-config] " + message;
        
        String logMessage = String.format("%s [%s] [%s] %s - %s", 
                timestamp, LOGGER_NAME, threadName, level, finalMessage);
        
        if (Level.ERROR.toString().equals(level)) {
            System.err.println(logMessage);
        } else {
            System.out.println(logMessage);
        }
    }
    
    /**
     * 输出启动信息
     */
    public static void logStartupInfo() {
        if (debugMode.get()) {
            info("DynamicConfig EnvironmentPostProcessor starting...");
            info("Debug mode: " + debugMode.get());
            info("Log level: " + configuredLevel);
            info("Silent mode: " + silentMode.get());
            info("Logback initialized: " + logbackInitialized.get());
            info("Using project config: " + isUsingProjectConfig());
            info("Current logback config: " + getCurrentLogbackConfig());
        }
    }
    
    /**
     * 检查logback是否已初始化
     */
    public static boolean isLogbackInitialized() {
        return logbackInitialized.get();
    }
    
    /**
     * 获取当前配置的日志级别
     */
    public static Level getConfiguredLevel() {
        return configuredLevel;
    }
    
    /**
     * 检查是否使用了项目classpath中的logback配置
     */
    public static boolean isUsingProjectConfig() {
        if (!logbackInitialized.get()) {
            return false;
        }
        
        // 检查是否存在项目中的logback配置文件
        String[] configFiles = {
            "logback-spring.xml",
            "logback.xml",
            "logback-test.xml"
        };
        
        for (String configFile : configFiles) {
            if (EarlyLogUtil.class.getClassLoader().getResource(configFile) != null) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 获取当前使用的logback配置文件路径
     */
    public static String getCurrentLogbackConfig() {
        String[] configFiles = {
            "logback-spring.xml",
            "logback.xml",
            "logback-test.xml"
        };
        
        for (String configFile : configFiles) {
            if (EarlyLogUtil.class.getClassLoader().getResource(configFile) != null) {
                return configFile;
            }
        }
        
        return "default (no config file found)";
    }
} 