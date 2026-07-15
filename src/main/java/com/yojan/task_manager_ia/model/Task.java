package com.yojan.task_manager_ia.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
@Data
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String subject;

    private LocalDateTime dueDate;

    private String difficulty; // "facil", "media", "dificil"

    private boolean completed = false;

    private LocalDateTime createdAt = LocalDateTime.now();
}