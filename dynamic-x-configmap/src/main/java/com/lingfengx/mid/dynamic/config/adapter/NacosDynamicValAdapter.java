package com.lingfengx.mid.dynamic.config.adapter;

import com.lingfengx.mid.dynamic.config.DynamicValListener;

public class NacosDynamicValAdapter extends DynamicValListenerAdapter {
    @Override
    public void register(String file, String prefix, DynamicValListener listener) {
        //nacos配置中心的监听和通知
        //Config.addListener(file, s -> listener.onChange(s));
    }
}
