//package com.lingfengx.mid.dynamic.config.adapter;
//
//import org.springframework.scheduling.annotation.AsyncConfigurer;
//import org.springframework.scheduling.annotation.EnableAsync;
//import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
//
//import java.util.concurrent.Executor;
//
//@EnableAsync
//@ConditionalOnMissingBean(AsyncConfigurer.class)
//public class AsyncThreadConfig implements AsyncConfigurer {
//
//
//    @Override
//    public Executor getAsyncExecutor() {
//        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
//        executor.setCorePoolSize(3);
//        executor.setMaxPoolSize(5);
//        executor.setQueueCapacity(10);
//        executor.setThreadNamePrefix("GssAsyncThread-");
//        executor.initialize();
//        return executor;
//    }
//
//}
