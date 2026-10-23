package com.example.ai.controller;

import com.example.ai.dto.ChatRequest;
import com.example.ai.dto.ChatResponse;
import com.example.ai.service.OllamaService;
import com.example.ai.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final OllamaService ollamaService;
    private final RagService ragService;

    @PostMapping("/generate")
    public ResponseEntity<ChatResponse> generate(@RequestBody ChatRequest request) {
        String result = ollamaService.chat(request.getMessage());
        return ResponseEntity.ok(new ChatResponse(result, request.getModel()));
    }

    @PostMapping("/tool-call")
    public ResponseEntity<ChatResponse> toolCall(@RequestBody ChatRequest request) {
        String result = ollamaService.chat(request.getMessage());
        return ResponseEntity.ok(new ChatResponse(result, request.getModel()));
    }

    @PostMapping("/rag")
    public ResponseEntity<ChatResponse> rag(@RequestBody ChatRequest request) {
        String result = ragService.ragChat(request.getMessage());
        return ResponseEntity.ok(new ChatResponse(result, request.getModel()));
    }
}