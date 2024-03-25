package com.lingfengx.mid.dynamic.limiter.util;

import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionUtil {
    /**
     * @param e           throwable
     * @param messageLine 取异常到行数 default 5
     * @return
     */
    public static String getMessage(Throwable e, int messageLine) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String[] errs = sw.toString().split("\\n\\t");
        StringBuilder builder = new StringBuilder();
        //层级
        int level = 0;
        //返回报错的最深栈
        for (String err : errs) {
            level++;
            builder.append(err);
            if (level >= messageLine) {
                break;
            }
        }
        if (builder.length() <= 0) {
            return e.getMessage();
        }
        return builder.toString();
    }
}
