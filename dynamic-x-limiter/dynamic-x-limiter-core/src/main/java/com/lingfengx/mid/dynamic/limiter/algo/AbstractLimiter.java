package com.lingfengx.mid.dynamic.limiter.algo;

import com.lingfengx.mid.dynamic.limiter.util.ExceptionUtil;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisDataException;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
public abstract class AbstractLimiter implements Limiter {
    protected static String scriptLua;
    protected Supplier<Jedis> jedisSupplier;

    protected abstract String getPrefix();

    protected String generateKey(String key) {
        return getPrefix() + ":" + key;
    }


    public void loadScript(String path) {
        try (Jedis jedis = jedisSupplier.get()) {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            URL url = classLoader.getResource(path);
            if (url != null) {
                try (InputStream inputStream = url.openStream()) {
                    byte[] buffer = new byte[(int) url.getFile().length()];
                    // 读取文件内容
                    int read = 0;
                    StringBuilder builder = new StringBuilder();
                    while ((read = inputStream.read(buffer)) > 0) {
                        builder.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                    scriptLua = jedis.scriptLoad(builder.toString());
                } catch (Exception e) {
                    log.error("redis-script-异常 {} {}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
                }
            }
        } catch (Exception e) {
            log.error("redis-script-异常 {} {}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
        }
    }

    @Override
    public boolean rdsLimit(List<String> keys, List<String> args) {
        Object isAccess = null;
        try (Jedis jedis = jedisSupplier.get()) {
            isAccess = jedis.evalsha(scriptLua, keys, args);
        } catch (JedisDataException e) {
            // 处理脚本执行出错的情况
            log.error("redis-script-异常 {} {}", e.getMessage(), e);
        }
        if (isAccess == null || "0".equals(isAccess.toString())) {
            //throw new RuntimeException("redisLimitScript execute error key=" + keys);
            return false;
        }
        return "1".equals(isAccess.toString());
//        return Boolean.parseBoolean(isAccess.toString());
    }

    /**
     * 释放资源
     *
     * @param key
     * @param value
     */
    public void release(String key, String value) {
        key = generateKey(key);
        try (Jedis jedis = jedisSupplier.get()) {
            jedis.zrem(key, value);
        } catch (Exception e) {
            log.error("redis-异常 {} {}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
        }
    }
}
