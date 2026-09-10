package com.example.ai.controller;

import com.example.ai.dto.ChatRequest;
import com.example.ai.dto.ChatResponse;
import com.example.ai.service.OllamaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final OllamaService ollamaService;

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
}