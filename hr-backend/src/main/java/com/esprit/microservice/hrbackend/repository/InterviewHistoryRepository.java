package com.esprit.microservice.hrbackend.repository;

import com.esprit.microservice.hrbackend.entity.InterviewHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewHistoryRepository extends JpaRepository<InterviewHistory, Long> {
    List<InterviewHistory> findByInterviewIdOrderByModifiedAtDesc(Long interviewId);
}
