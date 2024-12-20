package com.jingzhen.minirpc.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.dialect.Props;

/**
 * 配置工具类
 * 提供加载配置文件并将其映射到指定对象的功能，支持不同的环境配置。
 */
public class ConfigUtils {

    /**
     * 加载配置对象
     * 此方法通过配置前缀加载配置文件，默认情况下加载 `application.properties` 配置文件。
     *
     * @param tClass 配置类的类型，用于将配置映射到该类的对象。
     * @param prefix 配置项的前缀，用于在配置文件中查找以指定前缀开头的配置项。
     * @param <T> 配置类的类型。
     * @return 返回类型为 T 的配置对象，包含所有匹配的配置项。
     */
    public static <T> T loadConfig(Class<T> tClass, String prefix) {
        // 默认情况下没有指定环境，调用另一个支持环境区分的 loadConfig 方法
        return loadConfig(tClass, prefix, "");
    }

    /**
     * 加载配置对象，支持区分环境
     * 此方法支持加载不同环境的配置文件（例如，开发环境使用 `application-dev.properties`，生产环境使用 `application-prod.properties`）。
     *
     * @param tClass 配置类的类型，用于将配置映射到该类的对象。
     * @param prefix 配置项的前缀，用于在配置文件中查找以指定前缀开头的配置项。
     * @param environment 配置环境，用于区分不同的配置文件（例如，开发环境、生产环境等）。
     * @param <T> 配置类的类型。
     * @return 返回类型为 T 的配置对象，包含所有匹配的配置项。
     */
    public static <T> T loadConfig(Class<T> tClass, String prefix, String environment) {
        // 构建配置文件的名称，默认是 "application.properties"
        StringBuilder configFileBuilder = new StringBuilder("application");

        // 如果环境字符串不为空，拼接成如 "application-dev.properties"
        if (StrUtil.isNotBlank(environment)) {
            configFileBuilder.append("-").append(environment);
        }

        // 配置文件的完整路径：例如，"application.properties" 或 "application-dev.properties"
        configFileBuilder.append(".properties");

        // 加载配置文件
        Props props = new Props(configFileBuilder.toString());

        // 将配置文件中的具有指定前缀的数据映射到指定的配置类tClass并返回。例如把配置文件中的rpc.name映射到RpcConfig的name属性
        return props.toBean(tClass, prefix);
    }
}
