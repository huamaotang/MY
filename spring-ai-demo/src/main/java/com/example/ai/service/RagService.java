package com.example.ai.service;

import com.example.ai.dto.ChatRequest;
import com.example.ai.dto.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final PdfParserService pdfParserService;
    private final VectorStore vectorStore;
    private final OllamaService ollamaService;
    private final String baseUrl;
    private final String model;

    public RagService(PdfParserService pdfParserService,
                      VectorStore vectorStore,
                      OllamaService ollamaService,
                      @Value("${spring.ai.ollama.base-url}") String baseUrl,
                      @Value("${spring.ai.ollama.model}") String model) {
        this.pdfParserService = pdfParserService;
        this.vectorStore = vectorStore;
        this.ollamaService = ollamaService;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    /**
     * Ingest PDF documents from a directory into the vector store
     */
    public void ingestPdfs(String directoryPath) {
        List<String> chunks = pdfParserService.parsePdfDirectory(directoryPath);

        for (int i = 0; i < chunks.size(); i++) {
            VectorStore.Chunk chunk = new VectorStore.Chunk();
            chunk.id = "chunk-" + i;
            chunk.text = chunks.get(i);
            chunk.source = "pdf-" + i;
            chunk.pageNumber = i + 1;
            chunk.embedding = "[]";
            vectorStore.addChunk(chunk);
        }
    }

    /**
     * Get relevant context for a user query
     */
    public List<String> getRelevantContext(String query, int topK) {
        List<VectorStore.Chunk> similarChunks = vectorStore.searchSimilar(query, topK);
        List<String> texts = new ArrayList<>();
        for (VectorStore.Chunk chunk : similarChunks) {
            texts.add(chunk.text);
        }
        return texts;
    }

    /**
     * Generate a RAG response
     */
    public String ragChat(String userMessage) {
        // Get relevant context from the vector store
        List<String> context = getRelevantContext(userMessage, 3);

        // Build the prompt with context
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("基于以下上下文回答用户的问题：\n\n");

        if (context != null && !context.isEmpty()) {
            for (String ctx : context) {
                promptBuilder.append("- ").append(ctx).append("\n\n");
            }
        }

        promptBuilder.append("用户问题: ").append(userMessage).append("\n\n");
        promptBuilder.append("请基于以上上下文提供准确、简洁的答案。如果上下文中没有找到相关信息，请说明你知道的内容或表明您无法从提供的上下文中获得答案。");

        // Use the existing OllamaService.chat() method with enhanced message
        return ollamaService.chat(promptBuilder.toString());
    }
}