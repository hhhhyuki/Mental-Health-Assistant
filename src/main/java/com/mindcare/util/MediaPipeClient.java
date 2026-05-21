package com.mindcare.util;

import com.mindcare.dto.EmotionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MediaPipeClient {

    private static final Logger log = LoggerFactory.getLogger(MediaPipeClient.class);

    private final RestTemplate restTemplate;

    @Value("${mediapipe.endpoint}")
    private String endpoint;

    public MediaPipeClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public EmotionResult analyzeFace(MultipartFile imageFile) {
        log.info("Analyzing face from image: {}", imageFile.getOriginalFilename());
        // In v1, return a placeholder result
        // Will integrate with actual Python MediaPipe service in v3
        return new EmotionResult("正常", 0.5, "image");
    }
}