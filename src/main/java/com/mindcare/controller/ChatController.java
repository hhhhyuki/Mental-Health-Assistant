package com.mindcare.controller;

import com.mindcare.dto.ChatRequest;
import com.mindcare.dto.ChatResponse;
import com.mindcare.dto.ChatStreamResult;
import com.mindcare.service.MentalChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final MentalChatService mentalChatService;

    public ChatController(MentalChatService mentalChatService) {
        this.mentalChatService = mentalChatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        log.info("Chat request from user: {}, text: {}", request.getUserId(), request.getText());
        return mentalChatService.processChat(
            request.getText(),
            request.getUserId(),
            request.getAnonymousId()
        );
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        log.info("Stream chat request from user: {}", request.getUserId());

        ChatStreamResult streamResult = mentalChatService.streamChat(
            request.getText(),
            request.getUserId(),
            request.getAnonymousId()
        );

        StringBuilder fullReply = new StringBuilder();

        return streamResult.getTokenStream()
            .doOnNext(fullReply::append)
            .map(token -> ServerSentEvent.<String>builder()
                .event("message")
                .data(token)
                .build())
            .doOnComplete(() -> {
                mentalChatService.saveChatRecord(
                    request.getText(),
                    fullReply.toString(),
                    streamResult.getEmotionLabel(),
                    streamResult.getEmotionScore(),
                    streamResult.getRiskLevel(),
                    request.getUserId(),
                    request.getAnonymousId()
                );
                log.info("Stream completed for user {}", request.getUserId());
            })
            .concatWithValues(
                ServerSentEvent.<String>builder()
                    .event("emotion")
                    .data(String.format("{\"label\":\"%s\",\"score\":%.2f,\"risk\":\"%s\"}",
                        streamResult.getEmotionLabel(), streamResult.getEmotionScore(),
                        streamResult.getRiskLevel()))
                    .build(),
                ServerSentEvent.<String>builder()
                    .event("end")
                    .data("[DONE]")
                    .build()
            );
    }
}