package com.mindcare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgenticRAGService {

    private static final Logger log = LoggerFactory.getLogger(AgenticRAGService.class);

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final StreamingChatClient streamingChatClient;
    private final ObjectMapper objectMapper;

    public AgenticRAGService(VectorStore vectorStore, ChatClient chatClient, StreamingChatClient streamingChatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
        this.streamingChatClient = streamingChatClient;
        this.objectMapper = new ObjectMapper();
    }

    // ========== Intent Classification ==========

    public String classifyIntent(String query) {
        String prompt = String.format(PromptConfig.INTENT_CLASSIFIER, query);
        try {
            String result = chatClient.call(prompt).trim().toUpperCase();
            if (result.contains("CHAT")) return "CHAT";
            if (result.contains("RISK")) return "RISK";
            if (result.contains("CONSULT")) return "CONSULT";
            return "CONSULT";
        } catch (Exception e) {
            log.warn("Intent classification failed, defaulting to CONSULT", e);
            return "CONSULT";
        }
    }

    // ========== Combined Reasoning (intent + rewrite + thought + action in one LLM call) ==========

    public CombinedReasoningResult combinedReasoning(String query, String emotionLabel, String riskLevel) {
        String prompt = String.format(PromptConfig.AGENTIC_REASONING, query, emotionLabel, riskLevel);
        try {
            String raw = chatClient.call(prompt).trim();
            return parseCombinedJson(raw, query);
        } catch (Exception e) {
            log.warn("Combined reasoning failed, defaulting to CONSULT/RETRIEVE", e);
            return new CombinedReasoningResult("CONSULT", query, "", "RETRIEVE", query);
        }
    }

    private CombinedReasoningResult parseCombinedJson(String raw, String originalQuery) {
        try {
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start == -1 || end == -1) {
                return new CombinedReasoningResult("CONSULT", originalQuery, "", "RETRIEVE", originalQuery);
            }
            String json = raw.substring(start, end + 1);
            JsonNode root = objectMapper.readTree(json);

            String intent = root.has("intent") ? root.get("intent").asText("CONSULT").toUpperCase() : "CONSULT";
            if (!"CHAT".equals(intent) && !"RISK".equals(intent)) intent = "CONSULT";

            String rewrittenQuery = root.has("rewritten_query") ? root.get("rewritten_query").asText(originalQuery) : originalQuery;
            String thought = root.has("thought") ? root.get("thought").asText("") : "";
            String action = root.has("action") ? root.get("action").asText("RETRIEVE") : "RETRIEVE";
            String searchQuery = root.has("search_query") ? root.get("search_query").asText(rewrittenQuery) : rewrittenQuery;

            return new CombinedReasoningResult(intent, rewrittenQuery, thought, action, searchQuery);
        } catch (Exception e) {
            log.warn("Failed to parse combined JSON, using defaults", e);
            return new CombinedReasoningResult("CONSULT", originalQuery, "", "RETRIEVE", originalQuery);
        }
    }

    public static class CombinedReasoningResult {
        private final String intent;
        private final String rewrittenQuery;
        private final String thought;
        private final String action;
        private final String searchQuery;

        public CombinedReasoningResult(String intent, String rewrittenQuery, String thought, String action, String searchQuery) {
            this.intent = intent;
            this.rewrittenQuery = rewrittenQuery;
            this.thought = thought;
            this.action = action;
            this.searchQuery = searchQuery;
        }

        public String getIntent() { return intent; }
        public String getRewrittenQuery() { return rewrittenQuery; }
        public String getThought() { return thought; }
        public String getAction() { return action; }
        public String getSearchQuery() { return searchQuery; }
    }

    // ========== Quality Validation (rules only, no LLM) ==========

    private static final String[] HOTLINE_NUMBERS = {"400-161-9995", "12355", "120", "110", "82951332"};
    private static final String[] HARMFUL_PATTERNS = {"你可以去死", "你活该", "放弃吧", "没救了", "你完了"};

    public ValidationResult validateResponse(String query, String response, String context) {
        List<String> allIssues = new ArrayList<>();

        if (response == null || response.trim().length() < 15) {
            allIssues.add("回答为空或过短");
            return new ValidationResult(false, "回答为空或过短", allIssues);
        }

        if (response.length() > 3000) {
            allIssues.add("回答过长（超过3000字符），可能啰嗦");
        }

        if (context != null && !context.isEmpty()) {
            List<String> keyTerms = extractKeyTerms(context);
            if (!keyTerms.isEmpty()) {
                long matchedTerms = keyTerms.stream()
                    .filter(term -> response.contains(term))
                    .count();
                if (matchedTerms == 0) {
                    allIssues.add("回答未引用知识库中的关键术语，可能没有使用检索到的参考内容");
                }
            }
        }

        for (String harmful : HARMFUL_PATTERNS) {
            if (response.contains(harmful)) {
                allIssues.add("回答包含有害内容: \"" + harmful + "\"");
                return new ValidationResult(false, "回答包含有害内容", allIssues);
            }
        }

        String[] crisisKeywords = {"自杀", "想死", "活不下去", "结束生命", "自残", "伤害自己"};
        boolean isCrisis = false;
        for (String kw : crisisKeywords) {
            if (query.contains(kw)) { isCrisis = true; break; }
        }
        if (isCrisis) {
            boolean hasHotline = false;
            for (String num : HOTLINE_NUMBERS) {
                if (response.contains(num)) { hasHotline = true; break; }
            }
            if (!hasHotline) {
                allIssues.add("危机相关回答未包含心理援助热线号码");
            }
        }

        String reason = allIssues.isEmpty() ? "规则校验通过" : String.join("; ", allIssues);
        return new ValidationResult(allIssues.isEmpty(), reason, allIssues);
    }

    private List<String> extractKeyTerms(String context) {
        List<String> terms = new ArrayList<>();
        String[] lines = context.split("\n");
        for (String line : lines) {
            line = line.trim().replaceAll("^[\\d.、\\s]+", "");
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                String heading = line.replaceAll("^#+\\s*", "").trim();
                if (heading.length() >= 2 && heading.length() <= 20) terms.add(heading);
                continue;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\*\\*(.+?)\\*\\*").matcher(line);
            while (m.find()) {
                String bold = m.group(1).trim();
                if (bold.length() >= 2 && bold.length() <= 20) terms.add(bold);
            }
        }
        return terms.stream().distinct().limit(8).collect(Collectors.toList());
    }

    public static class ValidationResult {
        private final boolean isValid;
        private final String reason;
        private final List<String> issues;
        public ValidationResult(boolean isValid, String reason, List<String> issues) {
            this.isValid = isValid; this.reason = reason; this.issues = issues;
        }
        public boolean isValid() { return isValid; }
        public String getReason() { return reason; }
        public List<String> getIssues() { return issues; }
    }

    // ========== Context Retrieval ==========

    private String retrieveContext(String query) {
        try {
            List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.query(query).withTopK(3));
            return docs.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n\n"));
        } catch (Exception e) {
            log.warn("Context retrieval failed, continuing without context: {}", e.getMessage());
            return "";
        }
    }

    // ========== Simple Chat Detection (pure keyword, no LLM) ==========

    private boolean isSimpleChat(String query) {
        if (query == null) return false;
        String q = query.trim();
        String[] patterns = {"你好", "嗨", "hi", "hello", "在吗", "早上好", "晚上好", "下午好",
            "哈哈", "谢谢", "thanks", "嗯", "好的", "ok", "bye", "再见", "拜拜", "hey"};
        for (String p : patterns) {
            if (q.equalsIgnoreCase(p) || q.startsWith(p) || q.contains(p)) {
                if (q.length() <= 10) return true;
            }
        }
        return false;
    }

    // ========== Agentic Generation (non-streaming) ==========

    public String agenticGenerate(String query, String emotionLabel, String riskLevel) {
        // Quick path for simple greetings
        if (isSimpleChat(query)) {
            log.info("Simple chat detected, returning instant greeting");
            return "你好呀！有什么我可以帮你的吗？";
        }

        // High risk path
        if ("HIGH".equals(riskLevel) || "高风险".equals(emotionLabel)) {
            log.warn("High risk detected: emotion={}, risk={}", emotionLabel, riskLevel);
            return generateCrisisResponse();
        }

        // Combined reasoning: intent + rewrite + action in one call
        CombinedReasoningResult reasoning = combinedReasoning(query, emotionLabel, riskLevel);

        switch (reasoning.getIntent()) {
            case "RISK":
                return generateCrisisResponse();
            case "CHAT":
                return generateDirectChat(query);
            default: // CONSULT
                String context = "";
                if ("RETRIEVE".equals(reasoning.getAction())) {
                    context = retrieveContext(reasoning.getSearchQuery());
                }
                String answer = generateAnswer(query, context, emotionLabel, riskLevel);

                // Validate and retry once with broader context if needed
                ValidationResult validation = validateResponse(query, answer, context);
                if (!validation.isValid() && !context.isEmpty()) {
                    log.warn("Validation failed, retrying with broader context: {}", validation.getReason());
                    String broaderContext = retrieveContext(reasoning.getRewrittenQuery());
                    answer = generateAnswer(query, broaderContext, emotionLabel, riskLevel);
                }
                return answer;
        }
    }

    // ========== Agentic Streaming ==========

    public Flux<String> agenticStream(String query, String emotionLabel, String riskLevel) {
        if (isSimpleChat(query)) {
            log.info("Stream simple chat detected");
            return Flux.just("你好呀！有什么我可以帮你的吗？");
        }

        if ("HIGH".equals(riskLevel) || "高风险".equals(emotionLabel)) {
            log.warn("High risk detected in stream: {}", query);
            return streamCrisisResponse();
        }

        CombinedReasoningResult reasoning = combinedReasoning(query, emotionLabel, riskLevel);

        switch (reasoning.getIntent()) {
            case "RISK":
                return streamCrisisResponse();
            case "CHAT":
                return streamDirectChat(query);
            default:
                String context = "";
                if ("RETRIEVE".equals(reasoning.getAction())) {
                    context = retrieveContext(reasoning.getSearchQuery());
                }
                return streamAnswer(query, context, emotionLabel, riskLevel);
        }
    }

    // ========== Answer Generation ==========

    private String generateAnswer(String query, String context, String emotionLabel, String riskLevel) {
        String prompt = String.format(PromptConfig.GENERATE_ANSWER,
            emotionLabel, riskLevel, context, query);
        try {
            return chatClient.call(prompt);
        } catch (Exception e) {
            log.error("Answer generation failed", e);
            return getDefaultResponse();
        }
    }

    private Flux<String> streamAnswer(String query, String context, String emotionLabel, String riskLevel) {
        String prompt = String.format(PromptConfig.GENERATE_ANSWER,
            emotionLabel, riskLevel, context, query);
        Prompt llmPrompt = new Prompt(new UserMessage(prompt));
        return streamingChatClient.stream(llmPrompt)
            .map(response -> {
                Generation generation = response.getResult();
                if (generation != null) {
                    AssistantMessage output = generation.getOutput();
                    if (output != null && output.getContent() != null) return output.getContent();
                }
                return "";
            })
            .filter(token -> !token.isEmpty())
            .onErrorResume(e -> {
                log.error("Stream answer failed", e);
                return Flux.just(getDefaultResponse());
            });
    }

    private String generateDirectChat(String query) {
        Prompt prompt = new Prompt(List.of(
            new SystemPromptTemplate(PromptConfig.DIRECT_CHAT).createMessage(),
            new UserMessage(query)
        ));
        try {
            return chatClient.call(prompt).getResult().getOutput().getContent();
        } catch (Exception e) {
            log.error("Direct chat failed", e);
            return "你好呀！有什么我可以帮你的吗？";
        }
    }

    private Flux<String> streamDirectChat(String query) {
        Prompt prompt = new Prompt(List.of(
            new SystemPromptTemplate(PromptConfig.DIRECT_CHAT).createMessage(),
            new UserMessage(query)
        ));
        return streamingChatClient.stream(prompt)
            .map(response -> {
                Generation generation = response.getResult();
                if (generation != null) {
                    AssistantMessage output = generation.getOutput();
                    if (output != null && output.getContent() != null) return output.getContent();
                }
                return "";
            })
            .filter(token -> !token.isEmpty())
            .onErrorResume(e -> {
                log.error("Direct stream failed", e);
                return Flux.just("你好呀！有什么我可以帮你的吗？");
            });
    }

    private String generateCrisisResponse() {
        return chatClient.call(new Prompt(
            new SystemPromptTemplate(PromptConfig.CRISIS_GUIDE).createMessage()))
            .getResult().getOutput().getContent();
    }

    private Flux<String> streamCrisisResponse() {
        Prompt prompt = new Prompt(
            new SystemPromptTemplate(PromptConfig.CRISIS_GUIDE).createMessage());
        return streamingChatClient.stream(prompt)
            .map(response -> {
                Generation generation = response.getResult();
                if (generation != null) {
                    AssistantMessage output = generation.getOutput();
                    if (output != null && output.getContent() != null) return output.getContent();
                }
                return "";
            })
            .filter(token -> !token.isEmpty())
            .onErrorResume(e -> {
                log.error("Crisis stream failed", e);
                return Flux.just("如果你感到绝望，请立即拨打全国24小时心理援助热线：400-161-9995。你并不孤单，有人愿意倾听。");
            });
    }

    private String getDefaultResponse() {
        return "我在这里陪着你。虽然我现在遇到了一些技术问题，但请记住，你并不孤单。如果你感到困扰，建议联系学校的心理咨询中心寻求专业帮助。";
    }

    // ========== Legacy methods (called by MentalChatService) ==========

    public String generateResponse(String query, String emotionLabel, String riskLevel) {
        return agenticGenerate(query, emotionLabel, riskLevel);
    }

    public Flux<String> streamResponse(String query, String emotionLabel, String riskLevel) {
        return agenticStream(query, emotionLabel, riskLevel);
    }

    public String generateDirectResponse(String query) {
        Prompt prompt = new Prompt(List.of(
            new SystemPromptTemplate(PromptConfig.DIRECT_CHAT).createMessage(),
            new UserMessage(query)
        ));
        try {
            return chatClient.call(prompt).getResult().getOutput().getContent();
        } catch (Exception e) {
            log.error("Direct response failed", e);
            return "你好呀！有什么我可以帮你的吗？";
        }
    }
}