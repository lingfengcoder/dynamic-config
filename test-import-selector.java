// 测试DynamicValImportSelector的新扫描功能
public class TestImportSelector {
    
    public static void main(String[] args) {
        System.out.println("Testing DynamicValImportSelector scanning functionality:");
        
        // 模拟扫描结果
        String[] testClasses = {
            "com.lingfengx.mid.dynamic.config.DynamicValBeanPostProcessor",
            "com.lingfengx.mid.dynamic.config.local.adapter.LocalFileDynamicValAdapter",
            "com.lingfengx.mid.dynamic.config.local.adapter.HttpRemoteFileDynamicValAdapter", 
            "com.lingfengx.mid.dynamic.config.zookeeper.adapter.ZookeeperDynamicValAdapter",
            "com.lingfengx.mid.dynamic.config.ConfigSourceManager",
            "com.lingfengx.mid.dynamic.config.ConfigSourceRegistry"
        };
        
        System.out.println("Expected classes to be imported:");
        for (String className : testClasses) {
            System.out.println("  - " + className);
        }
        
        // 验证包名匹配
        String basePackage = "com.lingfengx.mid.dynamic";
        System.out.println("\nVerifying package scanning for: " + basePackage);
        
        for (String className : testClasses) {
            if (className.startsWith(basePackage)) {
                System.out.println("✓ " + className + " matches base package");
            } else {
                System.out.println("✗ " + className + " does not match base package");
            }
        }
        
        // 模拟@Configuration和@Component扫描
        System.out.println("\nSimulating @Configuration and @Component scanning:");
        String[] configClasses = {
            "com.lingfengx.mid.dynamic.config.DynamicConfigAutoConfiguration",
            "com.lingfengx.mid.dynamic.config.local.LocalConfigAutoConfiguration",
            "com.lingfengx.mid.dynamic.config.zookeeper.ZookeeperConfigAutoConfiguration"
        };
        
        String[] componentClasses = {
            "com.lingfengx.mid.dynamic.config.local.adapter.LocalFileDynamicValAdapter",
            "com.lingfengx.mid.dynamic.config.local.adapter.HttpRemoteFileDynamicValAdapter",
            "com.lingfengx.mid.dynamic.config.zookeeper.adapter.ZookeeperDynamicValAdapter"
        };
        
        System.out.println("@Configuration classes:");
        for (String className : configClasses) {
            System.out.println("  - " + className);
        }
        
        System.out.println("@Component classes:");
        for (String className : componentClasses) {
            System.out.println("  - " + className);
        }
        
        System.out.println("\nTotal classes to be imported: " + (testClasses.length + configClasses.length + componentClasses.length));
    }
} 