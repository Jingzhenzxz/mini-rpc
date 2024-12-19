package com.jingzhen.example.springboot.provider;

import com.jingzhen.minirpc.springboot.starter.annotation.EnableRpc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 示例 Spring Boot 服务提供者应用
 */
@SpringBootApplication
@EnableRpc
public class ExampleSpringbootProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleSpringbootProviderApplication.class, args);
    }

}
