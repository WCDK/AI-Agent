# AiAgentTest

一个基于 Spring Boot 和本机 Ollama 的简单 AI Agent 示例，默认使用 `deepseek-r1:7b`。

## 准备 Ollama

先确保 Ollama 已启动，并拉取模型：

```powershell
ollama pull deepseek-r1:7b
```

## 启动服务

```powershell
mvn spring-boot:run
```

默认地址：

```text
http://localhost:8080
```

Knife4j 文档地址：

```text
http://localhost:8080/doc.html
```

## 调用接口

健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/api/agent/health
```

聊天：

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri http://localhost:8080/api/agent/chat `
  -ContentType 'application/json' `
  -Body '{"message":"用三句话介绍什么是 AI Agent"}'
```

连续对话时带上返回的 `sessionId`：

```json
{
  "sessionId": "上一轮返回的 sessionId",
  "message": "继续解释它和普通聊天机器人的区别"
}
```

## 配置

可在 `src/main/resources/application.yaml` 中修改：

```yaml
agent:
  ollama:
    base-url: http://localhost:11434
    model: deepseek-r1:7b
    timeout-seconds: 120
  system-prompt: >
    你是一个简洁、可靠的 AI 助手。优先用中文回答，回答要直接、可执行。
  max-history-messages: 12
```
