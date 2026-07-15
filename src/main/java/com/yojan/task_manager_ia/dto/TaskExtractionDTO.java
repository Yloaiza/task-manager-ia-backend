package com.yojan.task_manager_ia.dto;

import lombok.Data;

@Data
public class TaskExtractionDTO {
    private String title;
    private String subject;
    private String dueDate; // formato ISO: "2026-07-20T00:00:00"
    private String difficulty;
}