package com.lingfengx.mid.dynamic.config.adapter;


import com.lingfengx.mid.dynamic.config.DynamicValListener;

public class ConfigMapDynamicValAdapter extends DynamicValListenerAdapter {

    @Override
    public void register(String file, String prefix, DynamicValListener listener) {
        //配置中心的监听和通知
        //Config.addListener(file, s -> listener.onChange(s));
    }
}
