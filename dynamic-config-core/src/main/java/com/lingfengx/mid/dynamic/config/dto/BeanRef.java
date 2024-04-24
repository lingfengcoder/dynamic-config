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
    private String prefix;
    private String placeHolder;
    private String key;
    private Object value;
    private Object bean;
}
