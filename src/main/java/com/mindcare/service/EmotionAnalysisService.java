package com.mindcare.service;

import com.mindcare.dto.EmotionResult;
import com.mindcare.util.MediaPipeClient;
import com.mindcare.util.WhisperClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EmotionAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(EmotionAnalysisService.class);

    private final ModelInferenceService modelService;
    private final WhisperClient whisperClient;
    private final MediaPipeClient mediaPipeClient;

    public EmotionAnalysisService(ModelInferenceService modelService,
                                   WhisperClient whisperClient,
                                   MediaPipeClient mediaPipeClient) {
        this.modelService = modelService;
        this.whisperClient = whisperClient;
        this.mediaPipeClient = mediaPipeClient;
    }

    public EmotionResult analyzeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new EmotionResult("正常", 0.5, "text");
        }
        try {
            String raw = modelService.analyzeEmotion(text);
            // Parse JSON response from LLM
            String label = "正常";
            double confidence = 0.5;
            if (raw.contains("\"label\"")) {
                label = extractJsonValue(raw, "label");
                confidence = extractJsonDouble(raw, "confidence");
            }
            return new EmotionResult(label, confidence, "text");
        } catch (Exception e) {
            log.warn("Text emotion analysis failed, using default", e);
            return new EmotionResult("正常", 0.5, "text");
        }
    }

    public EmotionResult analyzeAudio(MultipartFile audioFile) {
        try {
            return whisperClient.transcribe(audioFile);
        } catch (Exception e) {
            log.warn("Audio analysis failed", e);
            return new EmotionResult("正常", 0.5, "voice");
        }
    }

    public EmotionResult analyzeImage(MultipartFile imageFile) {
        try {
            return mediaPipeClient.analyzeFace(imageFile);
        } catch (Exception e) {
            log.warn("Image analysis failed", e);
            return new EmotionResult("正常", 0.5, "image");
        }
    }

    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\": \"";
        int start = json.indexOf(searchKey);
        if (start == -1) {
            searchKey = "\"" + key + "\":\"";
            start = json.indexOf(searchKey);
        }
        if (start == -1) return "正常";
        start += searchKey.length();
        int end = json.indexOf("\"", start);
        if (end == -1) end = json.length();
        return json.substring(start, end);
    }

    private double extractJsonDouble(String json, String key) {
        String searchKey = "\"" + key + "\": ";
        int start = json.indexOf(searchKey);
        if (start == -1) {
            searchKey = "\"" + key + "\":";
            start = json.indexOf(searchKey);
        }
        if (start == -1) return 0.5;
        start += searchKey.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        if (end == -1) end = json.length();
        try {
            return Double.parseDouble(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return 0.5;
        }
    }
}