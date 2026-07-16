package com.yojan.task_manager_ia.controller;

import com.yojan.task_manager_ia.dto.TaskExtractionDTO;
import com.yojan.task_manager_ia.model.Task;
import com.yojan.task_manager_ia.repository.TaskRepository;
import com.yojan.task_manager_ia.service.GoogleCalendarService;
import com.yojan.task_manager_ia.service.GroqService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.yojan.task_manager_ia.dto.VoiceCommandDTO;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private GroqService groqService;

    @Autowired
    private GoogleCalendarService googleCalendarService;

    @GetMapping
    public List<Task> getAllTasks() {
        return taskRepository.findAll();
    }

    @PostMapping
    public Task createTask(@RequestBody Task task) {
        return taskRepository.save(task);
    }

    @GetMapping("/upcoming")
    public List<Task> getUpcomingTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in48Hours = now.plusHours(48);
        List<Task> upcoming = taskRepository.findByDueDateBetweenAndCompletedFalseAndNotifiedFalse(now, in48Hours);

        upcoming.forEach(task -> task.setNotified(true));
        taskRepository.saveAll(upcoming);

        return upcoming;
    }

    @PatchMapping("/{id}/toggle-complete")
    public Task toggleComplete(@PathVariable Long id) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada"));
        task.setCompleted(!task.isCompleted());
        return taskRepository.save(task);
    }

    @GetMapping("/weekly-summary")
    public List<Task> getWeeklySummary() {
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        LocalDateTime now = LocalDateTime.now();
        return taskRepository.findByCreatedAtBetween(weekAgo, now);
    }

    @PostMapping("/from-text")
    public Task createTaskFromText(@RequestBody Map<String, String> body) {
        String userText = body.get("text");
        TaskExtractionDTO extracted = groqService.extractTaskFromText(userText);

        Task task = new Task();
        task.setTitle(extracted.getTitle());
        task.setSubject(extracted.getSubject());
        task.setDifficulty(extracted.getDifficulty());

        if (extracted.getDueDate() != null && !extracted.getDueDate().equals("null")) {
            String rawDate = extracted.getDueDate();
            LocalDateTime dueDate = rawDate.contains("T")
                    ? LocalDateTime.parse(rawDate)
                    : LocalDateTime.parse(rawDate + "T00:00:00");
            task.setDueDate(dueDate);

            try {
                String eventLink = googleCalendarService.createEvent(
                        extracted.getTitle(),
                        "Materia: " + extracted.getSubject() + " | Dificultad: " + extracted.getDifficulty(),
                        dueDate);
                System.out.println("Evento creado en Calendar: " + eventLink);
            } catch (Exception e) {
                System.err.println("Error creando evento en Calendar: " + e.getMessage());
            }
        }

        return taskRepository.save(task);
    }

    @PostMapping("/voice-command")
    public Map<String, Object> handleVoiceCommand(@RequestBody Map<String, String> body) {
        String userText = body.get("text");
        List<Task> pending = taskRepository.findByCompletedFalse();
        VoiceCommandDTO command = groqService.interpretVoiceCommand(userText, pending);

        if ("complete".equals(command.getAction()) && command.getTaskId() != null) {
            Task task = taskRepository.findById(command.getTaskId())
                    .orElseThrow(() -> new RuntimeException("Tarea no encontrada"));
            task.setCompleted(true);
            taskRepository.save(task);
            return Map.of("action", "complete", "task", task);
        }

        Task task = new Task();
        task.setTitle(command.getTitle());
        task.setSubject(command.getSubject());
        task.setDifficulty(command.getDifficulty());

        if (command.getDueDate() != null && !command.getDueDate().equals("null")) {
            String rawDate = command.getDueDate();
            LocalDateTime dueDate = rawDate.contains("T")
                    ? LocalDateTime.parse(rawDate)
                    : LocalDateTime.parse(rawDate + "T00:00:00");
            task.setDueDate(dueDate);
            try {
                googleCalendarService.createEvent(
                        command.getTitle(),
                        "Materia: " + command.getSubject() + " | Dificultad: " + command.getDifficulty(),
                        dueDate
                );
            } catch (Exception e) {
                System.err.println("Error creando evento en Calendar: " + e.getMessage());
            }
        }

        taskRepository.save(task);
        return Map.of("action", "create", "task", task);
    }
}