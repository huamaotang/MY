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
        return ResponseEntity.ok(ollamaService.generate(request));
    }

    @PostMapping("/stream")
    public ResponseEntity<ChatResponse> stream(@RequestBody ChatRequest request) {
        return ResponseEntity.ok(ollamaService.generate(request));
    }
}