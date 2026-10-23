package com.example.ai.service;

import java.util.ArrayList;
import java.util.List;

public class VectorStore {

    /** Inner class representing a document chunk with embedding */
    public static class Chunk {
        public String id;
        public String text;
        public String embedding; // JSON-encoded float list
        public String source;
        public int pageNumber;

        public Chunk() {}

        public Chunk(String id, String text, String source, int pageNumber) {
            this.id = id;
            this.text = text;
            this.source = source;
            this.pageNumber = pageNumber;
            this.embedding = "[]";
        }
    }

    private final List<Chunk> chunks = new ArrayList<>();

    public VectorStore() {}

    /**
     * Add a chunk to the vector store
     */
    public void addChunk(Chunk chunk) {
        chunks.add(chunk);
    }

    /**
     * Get all chunks
     */
    public List<Chunk> getAllChunks() {
        return chunks;
    }

    /**
     * Search for similar chunks based on text content.
     * In a real implementation, this would use vector similarity search.
     * For now, it returns chunks whose text contains the query terms.
     *
     * @parameter queryText the query text to search for
     * @param topK number of results to return
     * @return list of chunks that match the query
     */
    public List<Chunk> searchSimilar(String queryText, int topK) {
        if (chunks.isEmpty()) {
            return new ArrayList<>();
        }

        // Simple text-based search: check if chunk text contains query terms
        List<Chunk> matchingChunks = new ArrayList<>();

        String lowerQuery = queryText.toLowerCase();

        for (Chunk chunk : chunks) {
            if (chunk.text.toLowerCase().contains(lowerQuery)) {
                matchingChunks.add(chunk);
            }
        }

        // If no text matches, return first K chunks
        if (matchingChunks.isEmpty()) {
            return chunks.subList(0, Math.min(topK, chunks.size()));
        }

        // Return matching chunks, limited to topK
        return matchingChunks.subList(0, Math.min(topK, matchingChunks.size()));
    }
}