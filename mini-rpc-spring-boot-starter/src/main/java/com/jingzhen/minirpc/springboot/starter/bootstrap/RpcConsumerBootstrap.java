package com.jingzhen.minirpc.springboot.starter.bootstrap;

import com.jingzhen.minirpc.proxy.ServiceProxyFactory;
import com.jingzhen.minirpc.springboot.starter.annotation.RpcReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;

/**
 * Rpc 服务消费者启动
 * 如果没有 @RpcReference 注解，消费者需要自己写代理，即Proxy.newProxyInstance和InvocationHandler。
 * 本类在扫描到有 @RpcReference 注解的字段时，自动编写了代理。并采用工厂模式进一步封装了创建代理的逻辑。
 */
@Slf4j
public class RpcConsumerBootstrap implements BeanPostProcessor {

    /**
     * Bean 初始化后执行，注入 RPC 服务的代理对象
     *
     * @param bean     当前 Bean 实例
     * @param beanName 当前 Bean 的名称
     * @return 返回处理后的 Bean 实例，通常返回原始的 Bean
     * @throws BeansException 如果发生异常，抛出 Bean 初始化异常
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // 获取当前 Bean 的类对象
        Class<?> beanClass = bean.getClass();

        // 获取 Bean 类中所有的声明字段（成员变量）
        Field[] declaredFields = beanClass.getDeclaredFields();

        // 遍历所有字段，检查字段是否有 @RpcReference 注解
        for (Field field : declaredFields) {
            // 判断字段是否被 @RpcReference 注解标注
            RpcReference rpcReference = field.getAnnotation(RpcReference.class);

            // 如果字段上有 @RpcReference 注解，说明该字段需要注入 RPC 服务的代理对象
            if (rpcReference != null) {
                // 获取 RPC 引用的接口类，如果注解中没有指定接口类，使用字段的类型
                Class<?> interfaceClass = rpcReference.interfaceClass();
                if (interfaceClass == void.class) {
                    interfaceClass = field.getType(); // 默认使用字段的类型
                }

                // 设置字段可访问，以便能够修改该字段的值
                field.setAccessible(true);

                // 通过 ServiceProxyFactory 创建该接口类的代理对象
                Object proxyObject = ServiceProxyFactory.getProxy(interfaceClass);

                try {
                    // 将生成的代理对象注入到当前字段中
                    field.set(bean, proxyObject);
                    // 注入完后，恢复字段的可访问性
                    field.setAccessible(false);
                } catch (IllegalAccessException e) {
                    // 如果字段无法访问或注入失败，抛出异常
                    throw new RuntimeException("为字段注入代理对象失败", e);
                }
            }
        }

        // 调用父类的 postProcessAfterInitialization 方法，确保 Spring 的默认行为也被执行
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}
