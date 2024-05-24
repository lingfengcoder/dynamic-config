package com.lingfengx.mid.dynamic.config.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class SpelUtil {
    //$符号
    public static final String PLACEHOLDER_PREFIX = "${";
    public static final String PLACEHOLDER_SUFFIX = "}";
    public static final String SIMPLE_PREFIX = "{";
    //计算占位符
    public static final String COMPUTES_PLACEHOLDER_PREFIX = "#{";

    private final static char doll = '$';
    private final   static char jin = '#';
    private final   static char left = '{';
    private final   static char right = '}';

    public static String getPlaceholder(String key) {
        return PLACEHOLDER_PREFIX + key + PLACEHOLDER_SUFFIX;
    }


    /**
     * 是否是占位符
     *
     * @param strVal
     * @return
     */
    public static boolean isPlaceholder(String strVal) {
        return strVal.indexOf(PLACEHOLDER_PREFIX) >= 0 && strVal.indexOf(PLACEHOLDER_SUFFIX) >= 0;
    }

    /**
     * 是否是计算占位符
     *
     * @param strVal
     * @return
     */
    public static boolean isComputesPlaceholder(String strVal) {
        return strVal.indexOf(COMPUTES_PLACEHOLDER_PREFIX) >= 0 && strVal.indexOf(PLACEHOLDER_SUFFIX) >= 0;
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
            int end = str.lastIndexOf(PLACEHOLDER_SUFFIX);
            return str.substring(start + PLACEHOLDER_PREFIX.length(), end);
        }
        if (isComputesPlaceholder(str)) {
            int start = str.indexOf(COMPUTES_PLACEHOLDER_PREFIX);
            int end = str.lastIndexOf(PLACEHOLDER_SUFFIX);
            return str.substring(start + COMPUTES_PLACEHOLDER_PREFIX.length(), end);
        }
        return str;
    }

    /**
     * 检测不允许占位符嵌套
     * 不允许 ${abc_${name}} ${#{60*60}}
     *
     * @param str
     * @return
     */
    public static boolean checkPlaceholder(String str) {
        String placeHolder = parsePlaceholder(str);
        return !isPlaceholder(placeHolder) && !isComputesPlaceholder(placeHolder);
    }


    //spel解析器
    private static final SpelExpressionParser parser = new SpelExpressionParser();


    public static StandardEvaluationContext transEvaluationContext(Map<Object, Object> map) {
        StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
        map.forEach((k, v) -> evaluationContext.setVariable(k.toString(), v));
        evaluationContext.setRootObject(map);
        return evaluationContext;
    }

    public static String parseNoWrapper(StandardEvaluationContext context, String spel) {
        Expression expression = parser.parseExpression(spel);
        try {
            return expression.getValue(context, String.class);
        } catch (Exception e) {
            e.printStackTrace();
            log.warn("parse error {}", e.getMessage());
        }
        return null;
    }

    public static String parseWithWrapper(StandardEvaluationContext context, String spel) {
        // 获取 EvaluationContext
        if (spel.contains(".")) {
            spel = "['" + spel + "']";
        } else {
            spel = "#" + spel;
        }
        return parseNoWrapper(context, spel);
    }




    /**
     * 解析占位符对应的后面花括号的索引位置
     *
     * @param str
     * @return
     */
    public static int getEndIdx(String str) {
        if (minPlaceholderIdx(str, 0) < 0) {
            return -1;
        }
        int headCount = 0;
        int endCount = 0;
        for (int i = 0; i < str.length(); i++) {
            char curr = str.charAt(i);
            if (doll == curr || jin == curr) {
                if (i + 1 <= str.length()) {
                    char c2 = str.charAt(i + 1);
                    if (left == c2) {
                        headCount++;
                        continue;
                    }
                }
            }
            if (right == curr) {
                endCount++;
            }
            if (headCount != 0 && endCount == headCount) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 获取str中最前面的占位符索引位置
     *
     * @param str
     * @param fromIdx
     * @return
     */
    public static int minPlaceholderIdx(String str, int fromIdx) {
        int i = str.indexOf(PLACEHOLDER_PREFIX, fromIdx);
        int j = str.indexOf(COMPUTES_PLACEHOLDER_PREFIX, fromIdx);
        return i < 0 ? j : (j < 0 ? i : Math.min(i, j));
    }


    public static void main(String[] args) {

        // 创建 Map 对象
        Map<String, Object> map = new HashMap<>();
        map.put("demo.age", 25);
        map.put("demo.name", "lingfeng");
        map.put("name", "xx-lingfeng");
        map.put("name", "xx-lingfeng");
        map.put("shaman.s.aliyun.aksk", "this_is_ak,this_is_sk");

        String placeholder = "shaman.s.aliyun.aksk";
        Properties properties = new Properties();

        map.forEach((k, v) -> properties.setProperty(k, v.toString()));
        // 创建标准评估上下文
        StandardEvaluationContext context = new StandardEvaluationContext();
//        context.setRootObject(properties);
        map.forEach(context::setVariable);

        // 创建 Spel 表达式解析器
        SpelExpressionParser parser = new SpelExpressionParser();
        Expression expression = null;
        String age = null;
        // 解析和评估表达式
        expression = parser.parseExpression("'this_is_ak,this_is_sk'.split(',')[0]");
        age = expression.getValue(context, String.class);
        System.out.println(age);

        expression = parser.parseExpression("#name");
        age = expression.getValue(context, String.class);
        System.out.println(age);
    }
}
