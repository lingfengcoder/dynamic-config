package com.lingfengx.mid.dynamic.config.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.env.Environment;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class SpelUtil {
    public static final String PLACEHOLDER_PREFIX = "${";
    public static final String PLACEHOLDER_SUFFIX = "}";
    public static final String SIMPLE_PREFIX = "{";
    public static final String SPEL_PREFIX = "#{";

    public static String getPlaceholder(String key) {
        return PLACEHOLDER_PREFIX + key + PLACEHOLDER_SUFFIX;
    }

    public static boolean isPlaceholder(String strVal) {
        return strVal.indexOf(PLACEHOLDER_PREFIX) >= 0 && strVal.indexOf(PLACEHOLDER_SUFFIX) >= 0;
    }

    public static boolean isSPEL(String strVal) {
        return strVal.indexOf(SPEL_PREFIX) >= 0;
    }

    /**
     * 解析xxxx_${key.val}_name中key.val的值
     *
     * @param str
     * @return
     */
    public static String parsePlaceholder(String str) {
        if (isPlaceholder(str)) {
            int start = str.indexOf(PLACEHOLDER_PREFIX);
            int end = str.indexOf(PLACEHOLDER_SUFFIX);
            return str.substring(start + PLACEHOLDER_PREFIX.length(), end);
        }
        if (isSPEL(str)) {
            int start = str.indexOf(SPEL_PREFIX);
            int end = str.lastIndexOf(PLACEHOLDER_SUFFIX);
            return str.substring(start + SPEL_PREFIX.length(), end);
        }
        return str;
    }


    //spel解析器
    private static final SpelExpressionParser parser = new SpelExpressionParser();


    public static StandardEvaluationContext transEvaluationContext(Map<Object, Object> map) {
        StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
        map.forEach((k, v) -> evaluationContext.setVariable(k.toString(), v));
        return evaluationContext;
    }

    public static String parse(StandardEvaluationContext context, String spel) {
        // 获取 EvaluationContext 对象
        Expression expression = parser.parseExpression(spel);
        try {
            return expression.getValue(context, String.class);
        } catch (Exception e) {
            log.warn("parse error {}", e.getMessage());
        }
        return null;
    }


}
