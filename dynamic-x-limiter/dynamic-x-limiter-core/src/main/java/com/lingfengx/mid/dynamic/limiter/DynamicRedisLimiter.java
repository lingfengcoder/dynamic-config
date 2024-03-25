package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.algo.Limiter;
import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;
import com.lingfengx.mid.dynamic.limiter.algo.SlidingWindowLimiter;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;


/**
 * 基于redis的动态限流
 */
@Slf4j
public class DynamicRedisLimiter {

    private final static ConcurrentMap<LimiterAlgo, Limiter> limiterMap = new ConcurrentHashMap<>();

    private DynamicRedisLimiter() {
    }

    public DynamicRedisLimiter(Supplier<Jedis> jedisSupplier) {
        // 限流器
        SlidingWindowLimiter slidingWindowLimiter = new SlidingWindowLimiter(jedisSupplier);
        limiterMap.put(LimiterAlgo.SlidingWindow, slidingWindowLimiter);
    }

    public Limiter switchLimiter(LimiterAlgo limiterAlgo) {
        return limiterMap.get(limiterAlgo);
    }
}
