package com.lingfengx.mid.dynamic.limiter;

import java.util.Map;
import java.util.function.Function;

public interface RdsLimitConfig {
    boolean getEnable();

    //窗口大小
    int getBoxLen();// default -1;

    //窗口时间
    long getBoxTime();// default 5L;

    //获取限流的超时时间
    long getTimeout();

    //被限流处理
    String getFallBack();

    //限流异常处理
    String getErrorBack();

    Function<Map<String, Object>, String> dynamicKey();

    boolean whiteEnable();

    boolean blackEnable();

    Function<Map<String, Object>, Boolean> whiteListHandler();

    Function<Map<String, Object>, Boolean> blackListHandler();
}
