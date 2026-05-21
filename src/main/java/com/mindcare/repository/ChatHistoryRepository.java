package com.mindcare.repository;

import com.mindcare.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserIdOrderByTimestampAsc(Long userId);
    List<ChatMessage> findByAnonymousIdOrderByTimestampAsc(String anonymousId);
}