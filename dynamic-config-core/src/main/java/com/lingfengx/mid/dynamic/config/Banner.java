package com.lingfengx.mid.dynamic.config;

import com.lingfengx.mid.dynamic.config.util.EarlyLogUtil;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;


public class Banner {

    public static void printLOGO() {
        //从resources目录下读取banner.txt文件内容
        try (InputStream inputStream = new ClassPathResource("/banner.txt").getInputStream()) {
            StringBuilder builder = new StringBuilder();
            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                char[] buffer = new char[1024];
                int length;
                while ((length = bufferedReader.read(buffer)) != -1) {
                    builder.append(buffer, 0, length);
                }
            }
            EarlyLogUtil.info(builder.toString());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}
