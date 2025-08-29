package com.lingfengx.mid.dynamic.config.util;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件路径解析工具类
 * 统一处理文件路径解析逻辑
 */
public class FilePathResolver {


    /**
     * 解析文件路径并返回Resource对象
     * 支持绝对路径、相对路径、classpath路径等多种路径格式
     *
     * @param file 文件路径字符串
     * @return 解析后的Resource对象
     */
    public static Resource resolveFileResource(String file) {
        Resource resource = null;
        // 移除可能的协议前缀
        String cleanLocation = file.replaceFirst("^local://", "");

        // 1. 首先检查是否为classpath资源
        if (cleanLocation.startsWith("classpath:")) {
            String classpathPath = cleanLocation.substring("classpath:".length());
            resource = new ClassPathResource(classpathPath);
            EarlyLogUtil.debug("Loading from classpath with explicit prefix: " + classpathPath);
        } else {
            // 2. 尝试从classpath中查找文件（不带classpath:前缀的情况）
            try {
                ClassPathResource classpathResource = new ClassPathResource(cleanLocation);
                if (classpathResource.exists()) {
                    resource = classpathResource;
                    EarlyLogUtil.debug("Loading from classpath: " + cleanLocation);
                }
            } catch (Exception e) {
                EarlyLogUtil.debug("File not found in classpath: " + cleanLocation);
            }

            // 3. 如果classpath中找不到，尝试文件系统
            if (resource == null) {
                resource = new FileSystemResource(cleanLocation);
                EarlyLogUtil.debug("Loading from file system: " + cleanLocation);
            }
        }

        if (resource == null) {
            throw new RuntimeException("No suitable resource found for location: " + file);
        }
        return resource;
    }

    /**
     * 解析文件路径并返回Path对象
     * 支持绝对路径、相对路径、classpath路径等多种路径格式
     *
     * @param file 文件路径字符串
     * @return 解析后的Path对象
     */
    public static Path resolveFilePath(String file) {
        Resource resource = resolveFileResource(file);
        try {
            Path path = Paths.get(resource.getURI());
            EarlyLogUtil.debug("Converted resource to path: " + file + " -> " + path);
            return path;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert resource to path for location: " + file, e);
        }
    }


    /**
     * Java 8兼容的readAllBytes方法
     */
    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[1024];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }
} 