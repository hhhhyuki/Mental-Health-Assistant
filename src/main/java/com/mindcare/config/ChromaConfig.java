package com.mindcare.config;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * v1: 使用内存向量存储, 无需 Docker/Chroma。
 * 后续版本引入 Chroma 后替换为此类。
 */
@Configuration
public class ChromaConfig {

    @Bean
    public VectorStore vectorStore(EmbeddingClient embeddingClient) {
        return new InMemoryVectorStore(embeddingClient);
    }
}
