/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lingfengx.mid.dynamic.config.parser;

import cn.hutool.core.collection.CollectionUtil;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;

/**
 * Yaml config parser.
 */
public class YamlConfigParser extends AbstractConfigParser {

    @Override
    public Properties doParse(String content) {
        if (StringUtils.isEmpty(content)) {
            return new Properties();
        }
        YamlPropertiesFactoryBean yamlPropertiesFactoryBean = new YamlPropertiesFactoryBean();
        yamlPropertiesFactoryBean.setResources(new ByteArrayResource(content.getBytes()));
        return yamlPropertiesFactoryBean.getObject();
    }

    @Override
    public List<ConfigFileTypeEnum> getConfigFileTypes() {
        return CollectionUtil.newArrayList(ConfigFileTypeEnum.YML, ConfigFileTypeEnum.YAML);
    }
}
