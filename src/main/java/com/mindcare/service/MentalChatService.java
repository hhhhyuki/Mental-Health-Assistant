package com.mindcare.service;

import com.mindcare.dto.ChatStreamResult;
import com.mindcare.dto.EmotionResult;
import com.mindcare.dto.ChatResponse;
import com.mindcare.entity.ChatMessage;
import com.mindcare.repository.ChatHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Service
public class MentalChatService {

    private static final Logger log = LoggerFactory.getLogger(MentalChatService.class);

    private final EmotionAnalysisService emotionService;
    private final MultiModalFusionEngine fusionEngine;
    private final AgenticRAGService ragService;
    private final MCPExternalService mcpService;
    private final ChatHistoryRepository chatHistoryRepo;

    public MentalChatService(EmotionAnalysisService emotionService,
                              MultiModalFusionEngine fusionEngine,
                              AgenticRAGService ragService,
                              MCPExternalService mcpService,
                              ChatHistoryRepository chatHistoryRepo) {
        this.emotionService = emotionService;
        this.fusionEngine = fusionEngine;
        this.ragService = ragService;
        this.mcpService = mcpService;
        this.chatHistoryRepo = chatHistoryRepo;
    }

    public ChatResponse processChat(String text, Long userId, String anonymousId) {
        // Step 1: Analyze text emotion
        EmotionResult textEmotion = emotionService.analyzeText(text);

        // Step 2: Fusion (single modality for v1)
        List<EmotionResult> results = new ArrayList<>();
        results.add(textEmotion);
        EmotionResult fused = fusionEngine.fuse(results);

        // Step 3: Detect risk keywords
        String riskLevel = fused.getSource(); // Reuse source field temporarily for risk level
        riskLevel = determineRiskLevel(fused.getScore(), fused.getLabel(), text);

        // Step 4: Generate response via RAG
        String reply = ragService.generateResponse(text, fused.getLabel(), riskLevel);

        // Step 5: Save chat message
        ChatMessage userMsg = new ChatMessage(userId, "user", text);
        userMsg.setAnonymousId(anonymousId);
        userMsg.setEmotionLabel(fused.getLabel());
        userMsg.setEmotionScore(fused.getScore());
        userMsg.setRiskLevel(riskLevel);
        chatHistoryRepo.save(userMsg);

        ChatMessage botMsg = new ChatMessage(userId, "assistant", reply);
        botMsg.setAnonymousId(anonymousId);
        chatHistoryRepo.save(botMsg);

        // Step 6: Record to Excel if not normal
        if (!"NONE".equals(riskLevel)) {
            mcpService.writeRecordToExcel(
                anonymousId != null ? anonymousId : String.valueOf(userId),
                text, fused.getLabel(), riskLevel
            );
        }

        return new ChatResponse(reply, fused.getLabel(), fused.getScore(), riskLevel);
    }

    public ChatStreamResult streamChat(String text, Long userId, String anonymousId) {
        EmotionResult textEmotion = emotionService.analyzeText(text);
        EmotionResult fused = fusionEngine.fuse(List.of(textEmotion));
        String riskLevel = determineRiskLevel(fused.getScore(), fused.getLabel(), text);

        Flux<String> tokenStream = ragService.streamResponse(text, fused.getLabel(), riskLevel);

        return new ChatStreamResult(fused.getLabel(), fused.getScore(), riskLevel, tokenStream);
    }

    public void saveChatRecord(String text, String reply, String emotionLabel,
                                double emotionScore, String riskLevel,
                                Long userId, String anonymousId) {
        ChatMessage userMsg = new ChatMessage(userId, "user", text);
        userMsg.setAnonymousId(anonymousId);
        userMsg.setEmotionLabel(emotionLabel);
        userMsg.setEmotionScore(emotionScore);
        userMsg.setRiskLevel(riskLevel);
        chatHistoryRepo.save(userMsg);

        ChatMessage botMsg = new ChatMessage(userId, "assistant", reply);
        botMsg.setAnonymousId(anonymousId);
        chatHistoryRepo.save(botMsg);

        if (!"NONE".equals(riskLevel)) {
            mcpService.writeRecordToExcel(
                anonymousId != null ? anonymousId : String.valueOf(userId),
                text, emotionLabel, riskLevel
            );
        }
    }

    private String determineRiskLevel(double score, String label, String text) {
        // Keyword override (highest priority)
        if (text != null) {
            String[] highRisk = {"想死", "自杀", "活不下去", "自残", "结束生命", "不想活了", "伤害自己"};
            String[] mediumRisk = {"抑郁", "焦虑", "失眠", "痛苦", "绝望", "没有人理解我", "活着没意思"};

            for (String kw : highRisk) {
                if (text.contains(kw)) return "HIGH";
            }
            for (String kw : mediumRisk) {
                if (text.contains(kw)) return "MEDIUM";
            }
        }

        // Label-based risk (only trust label if confidence >= 0.3)
        if (score >= 0.3) {
            if ("高风险".equals(label)) return "HIGH";
            if ("焦虑".equals(label)) return "MEDIUM";
            if ("低落".equals(label)) return "LOW";
        }
        return "NONE";
    }
}