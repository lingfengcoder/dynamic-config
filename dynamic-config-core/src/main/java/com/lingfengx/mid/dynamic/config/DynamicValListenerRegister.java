package com.lingfengx.mid.dynamic.config;

public interface DynamicValListenerRegister {
    void register(String file, String prefix, DynamicValListener listener);
}
