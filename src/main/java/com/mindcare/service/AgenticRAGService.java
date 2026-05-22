package com.mindcare.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AgenticRAGService {

    private static final Logger log = LoggerFactory.getLogger(AgenticRAGService.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;

    public AgenticRAGService(VectorStore vectorStore, ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
    }

    public String generateResponse(String query, String emotionLabel, String riskLevel) {
        List<Document> relevantDocs = vectorStore.similaritySearch(
            SearchRequest.query(query).withTopK(3));
        String context = relevantDocs.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n\n"));

        String systemPrompt = buildSystemPrompt(emotionLabel, riskLevel, context);
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPrompt);
        Prompt prompt = new Prompt(new UserMessage(query));

        try {
            return chatClient.call(prompt).getResult().getOutput().getContent();
        } catch (Exception e) {
            log.error("RAG response generation failed", e);
            return "我在这里陪着你。虽然我现在遇到了一些技术问题，但请记住，你并不孤单。如果你感到困扰，建议联系学校的心理咨询中心寻求专业帮助。";
        }
    }

    private String buildSystemPrompt(String emotionLabel, String riskLevel, String context) {
        String prompt = "你是一个专业、温暖、有共情能力的校园心理健康助手\"智心AI\"。\n" +
            "你的定位：为在校学生提供心理支持和情绪疏导，不是医生，不提供诊断。\n\n" +
            "行为准则：\n" +
            "1. 始终保持温和、共情、非评判的语气\n" +
            "2. 不提供医学诊断或用药建议\n" +
            "3. 如果识别到危机信号，优先引导用户寻求专业帮助（心理咨询中心、紧急热线）\n" +
            "4. 回答要简短、温暖、有实际帮助\n\n" +
            "当前用户情绪状态: " + emotionLabel + "\n" +
            "当前风险等级: " + riskLevel + "\n\n";

        if (!context.isEmpty()) {
            prompt += "以下是心理知识库中的参考资料，请用它们来支撑你的回答：\n" + context + "\n\n";
        }

        prompt += "请用中文回答。记住：你面对的可能是一个需要帮助的学生，请用温暖的语言。";
        return prompt;
    }

    public Flux<String> streamResponse(String query, String emotionLabel, String riskLevel) {
        List<Document> relevantDocs = vectorStore.similaritySearch(
            SearchRequest.query(query).withTopK(3));
        String context = relevantDocs.stream()
            .map(Document::getContent)
            .collect(Collectors.joining("\n\n"));

        String systemPrompt = buildSystemPrompt(emotionLabel, riskLevel, context);
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPrompt);
        Prompt prompt = new Prompt(new UserMessage(query));

        return streamingChatClient.stream(prompt)
            .map(response -> {
                Generation generation = response.getResult();
                if (generation != null) {
                    AssistantMessage output = generation.getOutput();
                    if (output != null && output.getContent() != null) {
                        return output.getContent();
                    }
                }
                return "";
            })
            .filter(token -> !token.isEmpty())
            .onErrorResume(e -> {
                log.error("Stream response failed", e);
                return Flux.just("我在这里陪着你。虽然我现在遇到了一些技术问题，但请记住，你并不孤单。如果你感到困扰，建议联系学校的心理咨询中心寻求专业帮助。");
            });
    }

    public String generateDirectResponse(String query) {
        String systemPrompt = "你是一个友善、温暖的校园聊天助手。\n" +
            "请用轻松自然的方式回应用户，保持友好和温暖。\n" +
            "使用中文回复。";

        UserMessage userMessage = new UserMessage(query);
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemPrompt);
        Prompt prompt = new Prompt(List.of(
            systemPromptTemplate.createMessage(),
            userMessage
        ));

        try {
            return chatClient.call(prompt).getResult().getOutput().getContent();
        } catch (Exception e) {
            log.error("Direct response failed", e);
            return "你好呀！有什么我可以帮你的吗？";
        }
    }
}