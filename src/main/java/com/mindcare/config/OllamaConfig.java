package com.mindcare.config;

import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.OllamaEmbeddingClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaConfig {

    @Value("${spring.ai.ollama.base-url}")
    private String baseUrl;

    @Value("${spring.ai.ollama.chat.model}")
    private String chatModel;

    @Value("${spring.ai.ollama.embedding.model}")
    private String embeddingModel;

    @Bean
    public OllamaApi ollamaApi() {
        return new OllamaApi(baseUrl);
    }

    @Bean
    public OllamaChatClient ollamaChatClient(OllamaApi ollamaApi) {
        return new OllamaChatClient(ollamaApi)
            .withModel(chatModel);
    }

    @Bean
    public OllamaEmbeddingClient ollamaEmbeddingClient(OllamaApi ollamaApi) {
        return new OllamaEmbeddingClient(ollamaApi)
            .withModel(embeddingModel);
    }
}