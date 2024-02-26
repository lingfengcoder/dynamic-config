## 基于springboot2.0的自定义动态配置注解
加上注解后，配置中心的配置变更会自动更新到bean中，无需重启服务。
可适配任何配置中心，只需实现适配器即可，目前已适配nacos配置中心

## 展示
![avatar](show.gif)

### demo
#### 基础用法
引如坐标
```xml
        <dependency>
            <groupId>com.lingfengx</groupId>
            <artifactId>dynamic-x-config</artifactId>
            <version>${revision}</version>
        </dependency>
```
需要动态变化的配置类
```java
@Setter
@Getter
@ToString
@DynamicValConfig(file = "dynamic-dubbo.yml", prefix = "clazz-demo", listener = NacosDynamicValAdapter.class)
public class DynamicYmlDemoConfig {
    private Date date;
    private int intVal;
    private boolean bool;
    private InnerConfig innerConfig;
    @PostConstruct
    public void init() {
        System.out.println("DynamicDemoConfig init");
    }
}
```

#### 进阶用法
直接使用适配过的注解，不需指定适配器
```java

@Setter
@Getter
@ToString
@DynamicValNacos(file = "dynamic-dubbo.yml", prefix = "clazz-demo")
public class DynamicYmlDemoConfig {
    private Date date;
    private int intVal;
    private boolean bool;
    private InnerConfig innerConfig;
    @PostConstruct
    public void init() {
        System.out.println("DynamicDemoConfig init");
    }
}
```

### 以下配置一个配置中心只用配置一次即可
nacos配置中心的监听和通知
```java
public class NacosDynamicValAdapter extends DynamicValListenerAdapter {
    @Override
    public void register(String file, String prefix, DynamicValListener listener) {
        //nacos配置中心的监听和通知
        //Config.addListener(file, s -> listener.onChange(s));
    }
}
```
nacos 注册适配器成bean
```java
public class BeanAutoConfiguration {
    
    @Bean
    public NacosDynamicValAdapter nacosDynamicValAdapter() {
        return new NacosDynamicValAdapter();
    }
}
```

配置刷新的监听
```java
@Component
public class DynamicValListenerAdapter{
    @Async
    @EventListener(classes = ConfigRefreshEvent.class)
    public void onApplicationEvent(ConfigRefreshEvent event) {
        log.info("ConfigmapRefreshEventListener {}", event);
        //对刷新的配置进行处理
    }
}
```
