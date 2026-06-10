package com.wcdk.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiAgentTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentTestApplication.class, args);
        System.out.println("======================Agent 启动完成=====================");
    }

}
