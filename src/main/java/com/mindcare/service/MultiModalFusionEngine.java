package com.mindcare.service;

import com.mindcare.dto.EmotionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MultiModalFusionEngine {

    private static final Logger log = LoggerFactory.getLogger(MultiModalFusionEngine.class);

    // Fixed weights: Visual=0.5, Voice=0.4, Text=0.1
    private static final double VISUAL_WEIGHT = 0.5;
    private static final double VOICE_WEIGHT = 0.4;
    private static final double TEXT_WEIGHT = 0.1;

    private static final double HIGH_THRESHOLD = 0.8;
    private static final double MEDIUM_THRESHOLD = 0.6;
    private static final double LOW_THRESHOLD = 0.4;

    public EmotionResult fuse(List<EmotionResult> results) {
        if (results == null || results.isEmpty()) {
            return new EmotionResult("正常", 0.0, "fusion");
        }

        // Count available modalities
        boolean hasText = false, hasVoice = false, hasVisual = false;
        EmotionResult textResult = null, voiceResult = null, visualResult = null;

        for (EmotionResult r : results) {
            switch (r.getSource()) {
                case "text": hasText = true; textResult = r; break;
                case "voice": hasVoice = true; voiceResult = r; break;
                case "image": hasVisual = true; visualResult = r; break;
            }
        }

        // Calculate effective weights
        double wt = hasText ? TEXT_WEIGHT : 0;
        double wv = hasVoice ? VOICE_WEIGHT : 0;
        double ws = hasVisual ? VISUAL_WEIGHT : 0;
        double total = wt + wv + ws;

        if (total == 0) {
            return new EmotionResult("正常", 0.0, "fusion");
        }

        // Normalize weights
        wt /= total;
        wv /= total;
        ws /= total;

        // Calculate weighted scores for emotion categories
        String[] categories = {"正常", "低落", "焦虑", "高风险"};
        double[] scores = new double[4];

        if (hasText) addWeightedScore(scores, categories, textResult, wt);
        if (hasVoice) addWeightedScore(scores, categories, voiceResult, wv);
        if (hasVisual) addWeightedScore(scores, categories, visualResult, ws);

        // Find the dominant emotion
        int maxIdx = 0;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > scores[maxIdx]) maxIdx = i;
        }

        String fusedLabel = categories[maxIdx];
        double fusedScore = Math.min(scores[maxIdx], 1.0);

        // Risk keyword matching
        String allText = "";
        if (hasText) allText += textResult.getLabel() + " ";
        fusedScore = Math.max(fusedScore, matchRiskKeywords(allText));

        // Determine risk level
        String riskLevel = mapRiskLevel(fusedScore, fusedLabel);

        log.info("Fusion result: label={}, score={}, risk={}", fusedLabel, fusedScore, riskLevel);

        EmotionResult result = new EmotionResult(fusedLabel, fusedScore, "fusion");
        result.setScore(fusedScore);
        return result;
    }

    private void addWeightedScore(double[] scores, String[] categories, EmotionResult result, double weight) {
        String label = result.getLabel();
        double score = result.getScore();
        for (int i = 0; i < categories.length; i++) {
            if (categories[i].equalsIgnoreCase(label)) {
                scores[i] += score * weight;
                return;
            }
        }
    }

    private double matchRiskKeywords(String text) {
        if (text == null || text.isEmpty()) return 0.0;

        String[] highRiskKeywords = {"想死", "自杀", "活不下去", "自残", "结束生命", "不想活了", "伤害自己"};
        String[] mediumRiskKeywords = {"抑郁", "焦虑", "失眠", "痛苦", "绝望", "没有人理解我", "活着没意思"};

        for (String kw : highRiskKeywords) {
            if (text.contains(kw)) return 1.0;
        }
        for (String kw : mediumRiskKeywords) {
            if (text.contains(kw)) return 0.7;
        }
        return 0.0;
    }

    private String mapRiskLevel(double score, String label) {
        if (score >= HIGH_THRESHOLD) return "HIGH";
        if (score >= MEDIUM_THRESHOLD && (label.equals("低落") || label.equals("焦虑"))) return "MEDIUM";
        if (score >= LOW_THRESHOLD) return "LOW";
        return "NONE";
    }
}