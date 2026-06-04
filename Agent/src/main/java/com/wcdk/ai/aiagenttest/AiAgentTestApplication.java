package com.wcdk.ai.aiagenttest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AiAgentTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentTestApplication.class, args);
        System.out.println("======================Agent 启动完成=====================");
    }

}
