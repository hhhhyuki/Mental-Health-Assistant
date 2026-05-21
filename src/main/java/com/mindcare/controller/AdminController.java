package com.mindcare.controller;

import com.mindcare.entity.ChatMessage;
import com.mindcare.repository.ChatHistoryRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ChatHistoryRepository chatHistoryRepo;

    public AdminController(ChatHistoryRepository chatHistoryRepo) {
        this.chatHistoryRepo = chatHistoryRepo;
    }

    @GetMapping("/messages")
    public List<ChatMessage> getAllMessages() {
        return chatHistoryRepo.findAll();
    }
}