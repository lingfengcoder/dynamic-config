package com.lingfengx.mid.dynamic.config.event;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.io.Serializable;

@Getter
@Setter
@ToString
public class ConfigRefreshEvent extends ApplicationEvent implements Serializable {
    private String file;
    private String prefix;

    public ConfigRefreshEvent(String file, String prefix, Object source) {
        super(source);
        this.file = file;
        this.prefix = prefix;
    }
}
