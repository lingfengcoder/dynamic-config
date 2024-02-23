package com.lingfengx.mid.dynamic.config.adapter;

import org.springframework.context.annotation.Bean;

public class BeanAutoConfiguration {

    @Bean
    public ConfigMapDynamicValAdapter configMapDynamicValAdapter() {
        return new ConfigMapDynamicValAdapter();
    }

    @Bean
    public NacosDynamicValAdapter nacosDynamicValAdapter() {
        return new NacosDynamicValAdapter();
    }
}
