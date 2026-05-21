package com.mindcare.util;

import com.mindcare.dto.EmotionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Component
public class WhisperClient {

    private static final Logger log = LoggerFactory.getLogger(WhisperClient.class);

    private final RestTemplate restTemplate;

    @Value("${whisper.api-url}")
    private String apiUrl;

    public WhisperClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public EmotionResult transcribe(MultipartFile audioFile) {
        log.info("Transcribing audio file: {}", audioFile.getOriginalFilename());
        // In v1, return a placeholder result
        // Will integrate with actual Ollama Whisper in v3
        return new EmotionResult("正常", 0.5, "voice");
    }
}