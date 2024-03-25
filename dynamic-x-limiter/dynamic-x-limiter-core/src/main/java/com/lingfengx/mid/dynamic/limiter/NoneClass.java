package com.lingfengx.mid.dynamic.limiter;

import java.util.Map;
import java.util.function.Function;

public class NoneClass implements RdsLimitConfig{
    @Override
    public boolean getEnable() {
        return false;
    }

    @Override
    public int getBoxLen() {
        return 0;
    }

    @Override
    public long getBoxTime() {
        return 0;
    }

    @Override
    public long getTimeout() {
        return 0;
    }

    @Override
    public String getFallBack() {
        return null;
    }

    @Override
    public String getErrorBack() {
        return null;
    }

    @Override
    public Function<Map<String, Object>, String> dynamicKey() {
        return null;
    }

    @Override
    public boolean whiteEnable() {
        return false;
    }

    @Override
    public boolean blackEnable() {
        return false;
    }

    @Override
    public Function<Map<String, Object>, Boolean> whiteListHandler() {
        return null;
    }

    @Override
    public Function<Map<String, Object>, Boolean> blackListHandler() {
        return null;
    }

}
