package com.lingfengx.mid.dynamic.limiter.algo;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
public class SlidingWindowLimiter extends AbstractLimiter {
    private final static String REDIS_PREFIX = "rdsLimit";
    private final static String LUA_PATH = "META-INF/lua/sliding_window.lua";

    public SlidingWindowLimiter(Supplier<Jedis> jedisSupplier) {
        this.jedisSupplier = jedisSupplier;
        loadScript(LUA_PATH);
    }

    @Override
    protected String getPrefix() {
        return REDIS_PREFIX;
    }

    /**
     * 释放资源
     *
     * @param key
     * @param value
     */
    public void release(String key, String value) {
        super.release(key, value);
    }


    public boolean rdsLimit(String key, int boxLen, long boxTime, String value) {
        long now = System.currentTimeMillis();
        //调用lua脚本获取限流结果
        List<String> keys = new ArrayList<>(1);
        List<String> args = new ArrayList<>(4);
        keys.add(generateKey(key));
        args.add(String.valueOf(boxLen));
        args.add(String.valueOf(now - boxTime));
        args.add(String.valueOf(now));
        args.add(value);
        return super.rdsLimit(keys, args);
    }


}
