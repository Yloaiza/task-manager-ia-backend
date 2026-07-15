package com.yojan.task_manager_ia.repository;

import com.yojan.task_manager_ia.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskRepository extends JpaRepository<Task, Long> {
}