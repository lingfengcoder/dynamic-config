# Dynamic Config

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lingfengcoder/dynamic-config.svg)](https://search.maven.org/artifact/io.github.lingfengcoder/dynamic-config)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Java](https://img.shields.io/badge/java-8+-green.svg)](https://www.oracle.com/java/technologies/)

一个轻量级的动态配置框架，支持多种配置源，实现配置热更新。

![效果演示](show.gif)

## 📋 功能特性
- ✅ **配置热更新** - 配置变更时自动更新到Bean中，无需重启应用
- ✅ **多种文件格式** - 支持YAML、Properties、JSON等主流配置格式
- ✅ **注解驱动** - 使用简单注解即可实现动态配置
- ✅ **本地文件配置** - 监听本地文件变化，支持YAML、Properties、JSON格式
- ✅ **HTTP远程文件** - 通过HTTP拉取远程配置文件，支持定时拉取和认证
- ✅ **自定义配置中心** - 支持自定义配置中心，只需实现适配器即可
- ✅ **已经集成Zookeeper配置中心** - 监听Zookeeper节点变化，实现分布式配置管理

- ✅ **Spring Boot集成** - 完美集成Spring Boot，开箱即用

## 🚀 快速开始

### 1. 添加依赖

根据您的需求选择对应的starter：

```xml
<!-- 本地文件配置 -->
<dependency>
    <groupId>io.github.lingfengcoder</groupId>
    <artifactId>dynamic-config-local-starter</artifactId>
    <version>1.0.5</version>
</dependency>

<!-- Zookeeper配置中心 -->
<dependency>
    <groupId>io.github.lingfengcoder</groupId>
    <artifactId>dynamic-config-zookeeper-starter</artifactId>
    <version>1.0.5</version>
</dependency>
```

### 2. 启用动态配置

在启动类上添加注解：

```java
@SpringBootApplication
@EnableDynamicVal
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 3. 使用示例

#### 本地文件配置

**配置类：**
```java
@Component
@Setter
@Getter
@DynamicValLocalFile(file = "config/app.yml", prefix = "app")
public class AppConfig {
    private String name;
    private int port;
    private boolean enabled;
}
```

**配置文件 (config/app.yml)：**
```yaml
app:
  name: "我的应用"
  port: 8080
  enabled: true
```

#### HTTP远程文件配置

**配置类：**
```java
@Component
@Setter
@Getter
@DynamicValHttpRemoteFile(
    remoteUrl = "https://example.com/config.yml",
    prefix = "app",
    intervalSeconds = 60  // 每60秒拉取一次
)
public class AppConfig {
    private String name;
    private int port;
    private boolean enabled;
}
```

**远程配置文件：**
```yaml
app:
  name: "远程应用"
  port: 9090
  enabled: true
```

#### Zookeeper配置中心

**配置类：**
```java
@Component
@Setter
@Getter
@DynamicValZookeeper(file = "app-config.yml", prefix = "app")
public class AppConfig {
    private String name;
    private int port;
    private boolean enabled;
}
```

**Zookeeper配置：**
```yaml
app:
  name: "Zookeeper应用"
  port: 7070
  enabled: true
```

## 📖 详细配置

### 本地文件配置

#### 基础配置

```java
@DynamicValLocalFile(
    file = "config/app.yml",           // 配置文件路径
    prefix = "app"                     // 配置前缀
)
```

#### 高级配置

```java
@DynamicValLocalFile(
    file = "config/app.yml",           // 配置文件路径
    prefix = "app",                    // 配置前缀
    fileType = "yml",                  // 文件类型（可选，自动推断）
    onlyParseDynamic = false           // 是否只解析动态配置
)
```

#### 支持的文件路径格式

- **绝对路径**：`/path/to/config.yml`
- **相对路径**：`config.yml`（相对于当前工作目录）
- **classpath路径**：`classpath:config.yml`

#### 支持的文件格式

**YAML格式：**
```yaml
app:
  name: "应用名称"
  version: "1.0.0"
  features:
    - "feature1"
    - "feature2"
```

**Properties格式：**
```properties
app.name=应用名称
app.version=1.0.0
app.enabled=true
```

**JSON格式：**
```json
{
  "app": {
    "name": "应用名称",
    "version": "1.0.0",
    "enabled": true
  }
}
```

### HTTP远程文件配置

#### 基础配置

```java
@DynamicValHttpRemoteFile(
    remoteUrl = "https://example.com/config.yml",  // 远程文件URL
    prefix = "app"                                 // 配置前缀
)
```

#### 高级配置

```java
@DynamicValHttpRemoteFile(
    remoteUrl = "https://example.com/config.yml",  // 远程文件URL
    prefix = "app",                                // 配置前缀
    fileType = "yml",                              // 文件类型
    intervalSeconds = 60,                          // 拉取间隔（秒）
    authType = AuthType.BASIC,                     // 认证类型
    username = "admin",                            // 用户名
    password = "password"                          // 密码
)
```

#### 认证类型

**无认证：**
```java
@DynamicValHttpRemoteFile(
    remoteUrl = "https://example.com/config.yml",
    prefix = "app",
    authType = AuthType.NONE
)
```

**BASIC认证：**
```java
@DynamicValHttpRemoteFile(
    remoteUrl = "https://api.example.com/secure/config.yml",
    prefix = "secure",
    authType = AuthType.BASIC,
    username = "${SECURE_USERNAME:admin}",
    password = "${SECURE_PASSWORD:password}"
)
```

**BEARER认证：**
```java
@DynamicValHttpRemoteFile(
    remoteUrl = "https://api.example.com/secure/config.yml",
    prefix = "secure",
    authType = AuthType.BEARER,
    token = "${API_TOKEN}"
)
```

**API Key认证：**
```java
@DynamicValHttpRemoteFile(
    remoteUrl = "https://api.example.com/secure/config.yml",
    prefix = "secure",
    authType = AuthType.API_KEY,
    apiKeyHeader = "X-API-Key",
    apiKeyValue = "${API_KEY}"
)
```

#### 应用配置

在 `application.yml` 中配置HTTP远程文件拉取器：

> **注意**：HTTP远程文件功能默认是禁用的，需要设置 `enabled: true` 来启用。这样可以避免不必要的HTTP客户端创建和资源消耗。

```yaml
dynamic:
  config:
    http-remote-fetcher:
      # 是否启用HTTP远程文件功能
      enabled: true
      # 本地缓存目录
      local-cache-dir: ./dynamic-config-cache
      # 连接超时时间（毫秒）
      connect-timeout: 10000
      # 读取超时时间（毫秒）
      read-timeout: 30000
      # 是否在连接失败时重试
      retry-on-connection-failure: true
      # 最大重试次数
      max-retries: 3
      # 重试间隔（毫秒）
      retry-interval: 1000
```

### Zookeeper配置中心

#### 基础配置

```java
@DynamicValZookeeper(
    file = "app-config.yml",    // Zookeeper中的配置文件名
    prefix = "app"              // 配置前缀
)
```

#### 高级配置

```java
@DynamicValZookeeper(
    file = "app-config.yml",           // 配置文件名
    prefix = "app",                    // 配置前缀
    autoRefresh = true,                // 是否启用自动刷新
    refreshInterval = 30,              // 刷新间隔（秒）
    loadOnStartup = true,              // 是否在启动时立即加载
    encoding = "UTF-8",                // 配置编码格式
    validate = false,                  // 是否启用配置验证
    enableCache = true,                // 是否启用配置缓存
    cacheExpireSeconds = 300           // 缓存过期时间（秒）
)
```

#### 应用配置

在 `application.yml` 中配置Zookeeper连接：

```yaml
dynamic:
  config:
    zookeeper:
      # Zookeeper连接字符串
      connect-string: localhost:2181
      # 会话超时时间（毫秒）
      session-timeout: 60000
      # 连接超时时间（毫秒）
      connection-timeout: 15000
      # 命名空间
      namespace: dynamic-config
      # 重试配置
      retry:
        base-sleep-time: 1000
        max-retries: 3
```

#### Zookeeper配置管理

**设置配置：**
```java
@Autowired
private ZookeeperDynamicValAdapter adapter;

public void setConfig() {
    adapter.setConfig("app-config.yml", "app.name", "新应用名称");
}
```

**获取配置：**
```java
public String getConfig() {
    return adapter.getConfig("app-config.yml", "app.name");
}
```

**删除配置：**
```java
public void deleteConfig() {
    adapter.deleteConfig("app-config.yml", "app.name");
}
```

## 🔄 配置变更监听

### 监听配置变更

```java
@Component
@DynamicValLocalFile(file = "config/app.yml", prefix = "app")
public class AppConfig {
    
    private String name;
    private int port;
    
    @PostConstruct
    public void init() {
        System.out.println("配置初始化完成");
    }
    
    // 当配置变更时，这些setter方法会被自动调用
    public void setName(String name) {
        this.name = name;
        System.out.println("应用名称已更新为: " + name);
    }
    
    public void setPort(int port) {
        this.port = port;
        System.out.println("端口已更新为: " + port);
    }
}
```

### 手动停止监听

```java
@Service
public class ConfigService {
    
    @Autowired
    private LocalFileDynamicValAdapter localFileAdapter;
    
    @Autowired
    private HttpRemoteFileDynamicValAdapter httpAdapter;
    
    @Autowired
    private ZookeeperDynamicValAdapter zkAdapter;
    
    public void stopLocalFileWatching() {
        localFileAdapter.stopWatching("config/app.yml", "app");
    }
    
    public void stopHttpWatching() {
        httpAdapter.stopWatching("https://example.com/config.yml", "app");
    }
    
    public void stopZkWatching() {
        zkAdapter.stopWatching("app-config.yml", "app");
    }
}
```

## 💡 最佳实践

### 1. 配置文件组织

```
config/
├── app.yml          # 应用基础配置
├── database.yml     # 数据库配置
├── redis.yml        # Redis配置
└── security.yml     # 安全配置
```

### 2. 配置类设计

```java
@Component
@DynamicValLocalFile(file = "config/app.yml", prefix = "app")
public class AppConfig {
    
    @Setter
    @Getter
    private String name;
    
    @Setter
    @Getter
    private int port;
    
    @Setter
    @Getter
    private DatabaseConfig database;
    
    @Setter
    @Getter
    public static class DatabaseConfig {
        private String url;
        private String username;
        private String password;
    }
}
```

### 3. 环境隔离

```java
@Component
@DynamicValLocalFile(
    file = "config/app-${spring.profiles.active}.yml",
    prefix = "app"
)
public class AppConfig {
    // 配置字段...
}
```

### 4. 配置验证

```java
@Component
@DynamicValLocalFile(file = "config/app.yml", prefix = "app")
public class AppConfig {
    
    private int port;
    
    public void setPort(int port) {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("端口号必须在1-65535之间");
        }
        this.port = port;
    }
}
```

## ❓ 常见问题

### Q: 配置文件不存在怎么办？
A: 框架会在启动时检查文件是否存在，如果不存在会记录警告日志，但不会影响应用启动。

### Q: 如何调试配置更新？
A: 可以在setter方法中添加日志输出，或者使用Spring Boot的日志配置查看详细日志。

### Q: 支持哪些文件格式？
A: 目前支持YAML、Properties、JSON格式，会根据文件扩展名自动识别。

### Q: 如何停止配置监听？
A: 可以通过注入对应的适配器Bean，调用stopWatching方法停止监听。

## 📦 项目结构

```
dynamic-config/
├── dynamic-config-core/              # 核心模块
├── dynamic-config-local-starter/     # 本地文件配置模块
├── dynamic-config-zookeeper-starter/ # Zookeeper配置模块
└── pom.xml                          # 父POM
```

## 🔧 技术栈

- **Spring Boot**: 2.7.4+
- **Java**: 8+
- **Maven**: 3.6+
- **Hutool**: 5.3.10

## 📝 版本历史

- **v1.0.5** - 当前版本，支持本地文件、HTTP远程文件、Zookeeper配置
- **v1.0.4** - 优化配置更新机制，提升性能
- **v1.0.3** - 新增Zookeeper配置中心支持
- **v1.0.2** - 新增HTTP远程文件支持
- **v1.0.1** - 初始版本，支持本地文件配置

## 🤝 贡献

欢迎提交Issue和Pull Request！

### 贡献指南

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 📄 许可证

本项目采用 Apache 2.0 许可证 - 查看 [LICENSE](https://www.apache.org/licenses/LICENSE-2.0) 文件了解详情。

## 👨‍💻 作者

**LingFeng** - [GitHub](https://github.com/lingfengcoder) - 2650014538@qq.com

## 🔗 相关链接

- [GitHub 仓库](https://github.com/lingfengcoder/dynamic-config)
- [Maven Central](https://search.maven.org/artifact/io.github.lingfengcoder/dynamic-config)
- [Issues](https://github.com/lingfengcoder/dynamic-config/issues)
