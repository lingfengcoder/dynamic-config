package com.lingfengx.mid.dynamic.limiter;

import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RdsLimit {

    //开关
    boolean enable() default true;

    String key() default "rdslimit";

    //窗口大小
    int boxLen() default -1;

    TimeUnit unit() default TimeUnit.MILLISECONDS;

    //窗口时间
    long boxTime() default 5L;

    //获取限流的超时时间
    long timeout() default 10;

    //被限流处理
    String fallBack() default "";

    //限流异常处理
    String errorBack() default "";

    //在spring容器中的配置类
    Class<? extends RdsLimitConfig> configBean() default NoneClass.class;

    /**
     * 动态配置的类
     */
    String dynamicConfig() default "";

    //任务完成是否主动释放
    boolean autoRelease() default false;

    //限流算法
    LimiterAlgo algo() default LimiterAlgo.SlidingWindow;
}
