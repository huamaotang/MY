package com.example.ai.service;

import com.example.ai.dto.ChatRequest;
import com.example.ai.dto.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.stereotype.Service;

@Service
public class OllamaService {

    private final OllamaChatClient ollamaChatClient;

    public OllamaService(OllamaChatClient ollamaChatClient) {
        this.ollamaChatClient = ollamaChatClient;
    }

    public ChatResponse generate(ChatRequest request) {
        org.springframework.ai.chat.ChatResponse aiResponse = ollamaChatClient.call(new Prompt(request.getMessage()));
        String content = aiResponse.getResult().getOutput().getContent();
        return ChatResponse.builder().content(content).model("ollama").build();
    }
}