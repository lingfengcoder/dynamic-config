package com.lingfengx.mid.dynamic.limiter.algo;

import java.util.List;

public interface Limiter {
    void loadScript(String path);

    boolean rdsLimit(List<String> keys, List<String> args);

    void release(String key, String value);
}
