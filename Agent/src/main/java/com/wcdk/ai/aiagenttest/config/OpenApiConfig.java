package com.wcdk.ai.aiagenttest.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aiAgentOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AiAgentTest API")
                        .description("基于 Ollama deepseek-r1:7b 的 AI Agent 接口，支持聊天、图片生成、意图训练和文档定制模型创建")
                        .version("0.0.1"));
    }
}
