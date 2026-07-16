package com.yojan.task_manager_ia.repository;

import com.yojan.task_manager_ia.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByDueDateBetweenAndCompletedFalseAndNotifiedFalse(LocalDateTime start, LocalDateTime end);
    List<Task> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<Task> findByCompletedFalse();
}