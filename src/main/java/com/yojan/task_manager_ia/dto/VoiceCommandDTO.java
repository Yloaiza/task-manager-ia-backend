package com.yojan.task_manager_ia.dto;

import lombok.Data;

@Data
public class VoiceCommandDTO {
    private String action; // "create" o "complete"
    private Long taskId;   // solo si action = "complete"
    private String title;
    private String subject;
    private String dueDate;
    private String difficulty;
}