package com.lingfengx.mid.dynamic.limiter;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.lingfengx.mid.dynamic.limiter.algo.Limiter;
import com.lingfengx.mid.dynamic.limiter.algo.LimiterAlgo;
import com.lingfengx.mid.dynamic.limiter.algo.SlidingWindowLimiter;
import com.lingfengx.mid.dynamic.limiter.util.ExceptionUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Aspect
public class RdsLimiterAspect {
    private DynamicRedisLimiter dynamicRedisLimiter;
    //是否是spring的上下文
    private boolean isSpringContext = false;

    public RdsLimiterAspect(DynamicRedisLimiter dynamicRedisLimiter, boolean isSpringContext) {
        this.dynamicRedisLimiter = dynamicRedisLimiter;
        this.isSpringContext = isSpringContext;
    }

    public static final String KEY_PREFIX = "#RdsLimitPrefix";
    public static final String KEY_DEFAULT_LIMIT_KEY = "#RdsDefaultLimitKey";
    private static final ExpressionParser parser = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(com.lingfengx.mid.dynamic.limiter.RdsLimit)")
    public Object doAround(ProceedingJoinPoint call) throws Throwable {

        long begin = System.currentTimeMillis();
        try {
            MethodSignature signature = (MethodSignature) call.getSignature();
            Object self = call.getThis();
            Object[] args = call.getArgs();
            Method method = signature.getMethod();
            RdsLimit rdsLimit = method.getAnnotation(RdsLimit.class);
            boolean autoRelease = rdsLimit.autoRelease();
            LimiterAlgo algo = rdsLimit.algo();
            RdsLimitConfig config = getConfig(rdsLimit, self);
            boolean enable = getEnable(rdsLimit, config);
            if (enable) {
                //&& !jump(config)
                //获取静态的key
                String key = getKey(rdsLimit, config);
                //解析key支持spel
                Map<String, Object> params = parseKey(key, method, args);
                //黑白名单检测是否跳过限流
                if (whiteBlackCheck(params, config)) {
                    return call.proceed();
                }
                //动态获取redis的key
                String dKey = null;
                if (config != null && config.dynamicKey() != null) {
                    dKey = config.dynamicKey().apply(params);
                }
                dKey = StringUtils.hasLength(dKey) ? dKey : params.get(KEY_DEFAULT_LIMIT_KEY).toString();
                key = StringUtils.hasLength(dKey) ? dKey : key;
                int boxLen = getBoxLen(rdsLimit, config);
                int boxTime = getBoxTime(rdsLimit, config);
                boolean success = false;
                String accessKey = null;
                Limiter limiter = null;
                try {
                    accessKey = UUID.randomUUID().toString();
                    limiter = dynamicRedisLimiter.switchLimiter(algo);
                    if (limiter instanceof SlidingWindowLimiter) {
                        success = ((SlidingWindowLimiter) limiter).rdsLimit(key, boxLen, boxTime, accessKey);
                    } else {
                        throw new RuntimeException("not support limiter");
                    }
                } catch (Exception e) {
                    //限流异常的就直接走限流失败
                    log.error("[RdsLimit]find dynamicRedisLimiter error occur {} {}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
                }
                //通过限流
                if (success) {
                    try {
                        return call.proceed();
                    } catch (Exception e) {
                        log.error("[RdsLimit]find error occur {} {}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
                        //call errorBack
                        return errorBackCall(rdsLimit, config, self, args);
                    } finally {
                        if (autoRelease) {
                            //释放资源
                            limiter.release(key, accessKey);
                        }
                    }
                }
                //被限流
                else {
                    //统计失败的个数
                    //call fallBack
                    return fallbackCall(rdsLimit, config, self, args);
                }
            } else {
                return call.proceed();
            }
        } finally {
            long end = System.currentTimeMillis();
            log.info("[rdsLimit] cost {}", (end - begin));
        }
    }


    /**
     * 黑白名单检测
     *
     * @param params
     * @param config
     * @return true 跳过限流 false 不跳过限流
     */
    private boolean whiteBlackCheck(Map<String, Object> params, RdsLimitConfig config) {
        if (config == null) {
            return false;
        }
        if (config.whiteEnable() && config.whiteListHandler() != null) {
            if (config.whiteListHandler().apply(params)) {
                //白名单跳过限流
                return true;
            }
        }
        //黑名单
        if (config.blackEnable() && config.blackListHandler() != null) {
            if (config.blackListHandler().apply(params)) {
                return false;
            } else {
                //不在黑名单内，跳过限流
                return true;
            }
        }
        //默认不跳过
        return false;
    }


    private Object errorBackCall(RdsLimit rdsLimit, RdsLimitConfig config, Object self, Object[] args) {
        try {
            String errorBack = config.getErrorBack();
            if (StringUtils.isEmpty(errorBack)) {
                errorBack = rdsLimit.errorBack();
            }
            if (StringUtils.isEmpty(errorBack)) {
                log.error("[rdsLimit]errorBack is null{}", self);
                return null;
            }
            String[] split = errorBack.split("\\.");
            //如果只有一个参数，则执行在当前bean里的方法
            if (split.length == 1) {
                return ReflectUtil.invoke(self, split[0], args);
            } else {
                //todo 调用 非本类方法
            }
        } catch (Exception e) {
            log.error("[rdsLimit]{}{}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
        }
        return null;
    }

    private Object fallbackCall(RdsLimit rdsLimit, RdsLimitConfig config, Object self, Object[] args) {
        try {
            String fallBack = config.getFallBack();
            if (StringUtils.isEmpty(fallBack)) {
                fallBack = rdsLimit.fallBack();
            }
            if (StringUtils.isEmpty(fallBack)) {
                log.error("[rdsLimit]fallback is null {}", self);
                return null;
            }
            String[] split = fallBack.split("\\.");
            //如果只有一个参数，则执行在当前bean里的方法
            if (split.length == 1) {
                return ReflectUtil.invoke(self, split[0], args);
            } else {
                //todo 调用 非本类方法
            }
        } catch (Exception e) {
            log.error("[rdsLimit]{}{}", e.getMessage(), ExceptionUtil.getMessage(e, 10));
        }
        return null;
    }

    private RdsLimitConfig getConfig(RdsLimit rdsLimit, Object self) {
        Class<? extends RdsLimitConfig> clazz = rdsLimit.configBean();
        String dynamicConfig = rdsLimit.dynamicConfig();
        if (clazz == NoneClass.class && !StringUtils.hasLength(dynamicConfig)) {
            throw new RuntimeException("RdsLimit.config must be not null!");
        }
        if (isSpringContext) {
            RdsLimitConfig bean = SpringUtil.getBean(rdsLimit.configBean());
            if (bean != null) {
                return bean;
            }
        }
        Object invoke = ReflectUtil.invoke(self, dynamicConfig, new Object[0]);
        return invoke != null ? (RdsLimitConfig) invoke : null;
    }

    private boolean getEnable(RdsLimit rdsLimit, RdsLimitConfig config) {
        //通过配置config的方式获取
        // Boolean configData = readOtherConfig(rdsLimit, rdsLimit.DEnable());
        //通过直接指定的方式获取 beanName.key
        return config == null ? rdsLimit.enable() : config.getEnable();
    }

    private String getKey(RdsLimit rdsLimit, RdsLimitConfig config) {
        //通过配置config的方式获取
        // Boolean configData = readOtherConfig(rdsLimit, rdsLimit.DEnable());
        //通过直接指定的方式获取 beanName.key
        return rdsLimit.key();
    }

    private int getBoxTime(RdsLimit rdsLimit, RdsLimitConfig config) {
        //通过配置config的方式获取
        //  Integer configData = readOtherConfig(rdsLimit, rdsLimit.DBoxTime());
        //通过直接指定的方式获取 beanName.key
        return config == null ? (int) rdsLimit.boxTime() : (int) config.getBoxTime();
    }


    private int getBoxLen(RdsLimit rdsLimit, RdsLimitConfig config) {
        // Integer configData = readOtherConfig(rdsLimit, rdsLimit.DBoxLen());
        return config == null ? rdsLimit.boxLen() : config.getBoxLen();
    }

    private long getTimeout(RdsLimit rdsLimit, RdsLimitConfig config) {
        //Integer configData = readOtherConfig(rdsLimit, rdsLimit.DTimeout());
        return config == null ? rdsLimit.timeout() : config.getTimeout();
    }


    /**
     * 解析 key -spel
     *
     * @param key    prefix(#tenantId,#projectId)
     * @param method
     * @param args
     * @return prefix+tenantId+projectId
     */
    private Map<String, Object> parseKey(String key, Method method, Object[] args) {
        Map<String, Object> result = new LinkedHashMap<>(args.length);
        //解析出的tenantId和projectId字符串
        int sIdx = key.indexOf("(");
        int eIdx = key.indexOf(")");
        if (sIdx < 0) {
            result.put(KEY_PREFIX, key);
            result.put(KEY_DEFAULT_LIMIT_KEY, key);
            return result;
        }
        String prefix = sIdx > 0 ? key.substring(0, sIdx) : "";
        result.put(KEY_PREFIX, prefix);
        String evalSpel = key.substring(sIdx + 1, eIdx);
        // evalSpel = evalSpel.replace("#", "");
        String[] spels = evalSpel.split(",");
        StringBuilder builder = new StringBuilder(prefix);
        //解析spel
        String[] params = parameterNameDiscoverer.getParameterNames(method);//解析参数名
        if (params == null || params.length == 0) {
            return result;
        }
        EvaluationContext context = new StandardEvaluationContext();//el解析需要的上下文对象
        for (int i = 0; i < params.length; i++) {
            context.setVariable(params[i], args[i]);//所有参数都作为原材料扔进去
        }
        for (String spel : spels) {
            Expression expression = parser.parseExpression(spel);
            String value = expression.getValue(context, String.class);
            result.put(spel.replace("#", ""), value);
            if (StringUtils.hasLength(value)) {
                builder.append(":").append(value);
            }
        }
        //默认的动态key= prefix(#tenantId,#projectId)
        result.put(KEY_DEFAULT_LIMIT_KEY, builder.toString());
        return result;
    }

}
