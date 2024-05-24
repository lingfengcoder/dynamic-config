package com.lingfengx.mid.dynamic.config.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@EqualsAndHashCode
@Accessors(chain = true)
public class BeanRef {
    //和配置前缀一致
    private String prefix;
    //${placeHolder}=>placeHolder
    private String placeHolder;
    //bean对应的属性名称
    private String key;
    //原始配置文件中的值
    private Object originalValue;
    //bean
    private Object bean;
    //原始值的hashcode
    private int originalValueHashCode;
}
