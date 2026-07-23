package com.yojan.task_manager_ia.controller;

import com.yojan.task_manager_ia.dto.TaskExtractionDTO;
import com.yojan.task_manager_ia.dto.VoiceCommandDTO;
import com.yojan.task_manager_ia.model.Task;
import com.yojan.task_manager_ia.model.User;
import com.yojan.task_manager_ia.repository.TaskRepository;
import com.yojan.task_manager_ia.repository.UserRepository;
import com.yojan.task_manager_ia.security.AuthenticatedUser;
import com.yojan.task_manager_ia.service.GoogleCalendarService;
import com.yojan.task_manager_ia.service.GroqService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroqService groqService;

    @Autowired
    private GoogleCalendarService googleCalendarService;

    private Long currentUserId(Authentication authentication) {
        AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
        return user.userId();
    }

    @GetMapping
    public List<Task> getAllTasks(Authentication authentication) {
        return taskRepository.findByUserId(currentUserId(authentication));
    }

    @PostMapping
    public Task createTask(@RequestBody Task task, Authentication authentication) {
        User user = userRepository.findById(currentUserId(authentication)).orElseThrow();
        task.setUser(user);
        return taskRepository.save(task);
    }

    @GetMapping("/upcoming")
    public List<Task> getUpcomingTasks(Authentication authentication) {
        Long userId = currentUserId(authentication);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in48Hours = now.plusHours(48);
        List<Task> upcoming = taskRepository.findByDueDateBetweenAndCompletedFalseAndNotifiedFalseAndUserId(now, in48Hours, userId);

        upcoming.forEach(task -> task.setNotified(true));
        taskRepository.saveAll(upcoming);

        return upcoming;
    }

    @PatchMapping("/{id}/toggle-complete")
    public Task toggleComplete(@PathVariable Long id, Authentication authentication) {
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tarea no encontrada"));

        if (!task.getUser().getId().equals(currentUserId(authentication))) {
            throw new RuntimeException("No autorizado para modificar esta tarea");
        }

        task.setCompleted(!task.isCompleted());
        return taskRepository.save(task);
    }

    @GetMapping("/weekly-summary")
    public List<Task> getWeeklySummary(Authentication authentication) {
        Long userId = currentUserId(authentication);
        LocalDateTime weekAgo = LocalDateTime.now().minusDays(7);
        LocalDateTime now = LocalDateTime.now();
        return taskRepository.findByCreatedAtBetweenAndUserId(weekAgo, now, userId);
    }

    @PostMapping("/from-text")
    public Task createTaskFromText(@RequestBody Map<String, String> body, Authentication authentication) {
        Long userId = currentUserId(authentication);
        String userText = body.get("text");
        TaskExtractionDTO extracted = groqService.extractTaskFromText(userText);

        User user = userRepository.findById(userId).orElseThrow();

        Task task = new Task();
        task.setUser(user);
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
                        userId,
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
    public Map<String, Object> handleVoiceCommand(@RequestBody Map<String, String> body, Authentication authentication) {
        Long userId = currentUserId(authentication);
        String userText = body.get("text");
        List<Task> pending = taskRepository.findByCompletedFalseAndUserId(userId);
        VoiceCommandDTO command = groqService.interpretVoiceCommand(userText, pending);

        if ("complete".equals(command.getAction()) && command.getTaskId() != null) {
            Task task = taskRepository.findById(command.getTaskId())
                    .orElseThrow(() -> new RuntimeException("Tarea no encontrada"));

            if (!task.getUser().getId().equals(userId)) {
                throw new RuntimeException("No autorizado para modificar esta tarea");
            }

            task.setCompleted(true);
            taskRepository.save(task);
            return Map.of("action", "complete", "task", task);
        }

        User user = userRepository.findById(userId).orElseThrow();

        Task task = new Task();
        task.setUser(user);
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
                        userId,
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
