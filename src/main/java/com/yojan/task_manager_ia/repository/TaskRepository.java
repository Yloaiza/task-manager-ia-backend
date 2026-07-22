package com.yojan.task_manager_ia.repository;

import com.yojan.task_manager_ia.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByUserId(Long userId);

    List<Task> findByDueDateBetweenAndCompletedFalseAndNotifiedFalseAndUserId(
            LocalDateTime start, LocalDateTime end, Long userId);

    List<Task> findByCreatedAtBetweenAndUserId(LocalDateTime start, LocalDateTime end, Long userId);

    List<Task> findByCompletedFalseAndUserId(Long userId);

    // Se mantienen los metodos viejos por compatibilidad con datos existentes sin dueño,
    // no se usan en los endpoints protegidos nuevos.
    List<Task> findByDueDateBetweenAndCompletedFalseAndNotifiedFalse(LocalDateTime start, LocalDateTime end);
    List<Task> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    List<Task> findByCompletedFalse();
}
