package com.lingfengx.mid.dynamic.limiter;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import redis.clients.jedis.Jedis;

import java.util.function.Supplier;

//@Configuration
public class BeanAutoConfiguration {

    @Bean
    public RdsLimiterAspect rdsLimiterAspect(@Lazy Supplier<Jedis> jedisSupplier) {
        return new RdsLimiterAspect(new DynamicRedisLimiter(jedisSupplier), true);
    }

}
