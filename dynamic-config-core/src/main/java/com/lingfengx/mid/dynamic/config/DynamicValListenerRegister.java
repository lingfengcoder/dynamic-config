package com.lingfengx.mid.dynamic.config;

public interface DynamicValListenerRegister {

    void register(String file, String prefix, DynamicValListener listener);
    
    /**
     * 注册带额外配置的监听器
     * @param file 文件路径或URL
     * @param prefix 配置前缀
     * @param listener 监听器
     * @param config 额外配置，可以是null
     */
    default void register(String file, String prefix, DynamicValListener listener, Object config) {
        // 默认实现调用原来的方法，保持向后兼容
        register(file, prefix, listener);
    }
    
    /**
     * 检查该适配器是否适用于指定的文件路径或URL
     * @param file 文件路径或URL
     * @return 如果适配器适用于该路径则返回true，否则返回false
     */
    boolean isAvailable(String file);
}
