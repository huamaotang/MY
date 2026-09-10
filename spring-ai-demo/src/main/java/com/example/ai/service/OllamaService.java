package com.example.ai.service;

import com.example.ai.dto.ChatRequest;
import com.example.ai.dto.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.*;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.stereotype.Service;

import static java.util.Collections.singletonList;

@Service
public class OllamaService {

    private final OllamaChatClient ollamaChatClient;

    @Value("${spring.ai.ollama.base-url}")
    private String baseUrl;

    @Value("${spring.ai.ollama.model}")
    private String model;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper;

    private final List<Map<String, String>> messages =
            new ArrayList<>();

    public OllamaService(OllamaChatClient ollamaChatClient,
                         RestTemplate restTemplate,
                         ObjectMapper objectMapper) {
        this.ollamaChatClient = ollamaChatClient;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        messages.clear();
    }

    /**
     * 主要的聊天方法 - 支持 Tool Calling
     */
    public String chat(String userMessage) {
        // 1. 将用户消息加入历史
        Map<String, String> userMap = new HashMap<>();
        userMap.put("role", "user");
        userMap.put("content", userMessage);
        messages.add(userMap);

        // 2. 判断是否需要调用工具
        String toolResponse = maybeCallTool(userMessage);

        // 3. 如果有工具响应，将工具结果加入对话历史
        if (toolResponse != null) {
            Map<String, String> assistantMap = new HashMap<>();
            assistantMap.put("role", "assistant");
            assistantMap.put("content", toolResponse);
            messages.add(assistantMap);
        }

        // 4. 调用 LLM 生成最终回复
        // 这里简化处理，实际应通过 OllamaChatClient 进行工具调用
        String llmResponse = callOllama();

        // 5. 将 LLM 回复加入历史
        if (llmResponse != null && !llmResponse.isEmpty()) {
            Map<String, String> replyMap = new HashMap<>();
            replyMap.put("role", "assistant");
            replyMap.put("content", llmResponse);
            messages.add(replyMap);
        }

        return llmResponse != null ? llmResponse : "抱歉，我无法处理您的请求。";
    }

    /**
     * 判断是否需要调用工具，并执行工具调用
     */
    private String maybeCallTool(String userMessage) {
        // 检测是否包含基金查询关键词
        if (userMessage.contains("000001") || userMessage.contains("基金")) {
            // 调用工具 getFundInfo
            String fundInfo = getFundInfo("000001");
            if (fundInfo != null && !fundInfo.equals("没有找到该基金")) {
                return fundInfo;
            }
        }
        return null;
    }

    /**
     * Java 工具方法 - 查询基金信息
     * 在实际项目中，这里会连接 MySQL 或 Redis 数据库
     */
    public String getFundInfo(String fundCode) {
        // 模拟数据库查询
        if ("000001".equals(fundCode)) {
            return """
                    基金代码：000001
                    基金名称：华夏成长
                    基金类型：混合型
                    最新净值：1.235
                    基金规模：50亿元
                    """;
        }
        return "没有找到该基金";
    }

    /**
     * 调用 Ollama LLM 生成回复
     */
    /**
     * 调用 Ollama LLM 生成回复
     */
    private String callOllama() {
        try {
            String url = baseUrl + "/api/chat";

            Map<String, Object> request = new HashMap<>();
            request.put("model", model);

            List<Map<String, String>> msgList = new ArrayList<>();
            // 添加系统提示
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一个金融助手，请用中文回答用户问题。");
            msgList.add(systemMsg);
            // 将 messages list 中的内容直接加入请求（已包含用户消息和工具响应）
            msgList.addAll(messages);
            request.put("messages", msgList);
            request.put("stream", false);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                // 解析 Ollama 返回格式: {"message": {"role": "assistant", "content": "..."}}
                if (root.has("message")) {
                    return root.path("message").path("content").asText();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}