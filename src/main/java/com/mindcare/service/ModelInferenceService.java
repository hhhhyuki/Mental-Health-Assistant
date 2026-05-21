package com.mindcare.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.stereotype.Service;

@Service
public class ModelInferenceService {

    private static final Logger log = LoggerFactory.getLogger(ModelInferenceService.class);

    private final OllamaChatClient chatClient;

    public ModelInferenceService(OllamaChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public String chat(String message) {
        try {
            log.debug("Sending message to Ollama: {}", message);
            return chatClient.call(message);
        } catch (Exception e) {
            log.error("Ollama call failed", e);
            return "抱歉，我现在暂时无法回应，请稍后再试。";
        }
    }

    public String analyzeEmotion(String text) {
        String prompt = "你是一个情绪分析器。分析以下文本的情绪，只返回情绪标签（正常、低落、焦虑、高风险）和置信度（0-1），格式严格为 JSON：\n" +
                        "{\"label\": \"情绪标签\", \"confidence\": 0.xx}\n\n" +
                        "文本：" + text;
        try {
            return chatClient.call(prompt);
        } catch (Exception e) {
            log.error("Emotion analysis failed", e);
            return "{\"label\": \"正常\", \"confidence\": 0.5}";
        }
    }

    public String analyzeIntent(String text) {
        String prompt = "你是一个用户意图分类器，只做意图识别，不回答问题。\n" +
                        "用户输入内容: " + text + "\n" +
                        "请将用户意图严格分为以下三类之一，只输出标签，不要其他任何内容:\n" +
                        "- CHAT: 日常闲聊、问候、天气、娱乐、无关内容\n" +
                        "- CONSULT: 心理咨询、情绪倾诉、压力、焦虑、低落、失眠、亲密关系、学习压力等心理相关\n" +
                        "- RISK: 自杀、自残、绝望、自伤、伤人、严重抑郁等高危内容";
        try {
            return chatClient.call(prompt).trim();
        } catch (Exception e) {
            log.error("Intent analysis failed", e);
            return "CHAT";
        }
    }
}