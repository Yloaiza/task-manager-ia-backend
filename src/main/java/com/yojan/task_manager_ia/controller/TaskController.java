package com.yojan.task_manager_ia.controller;

import com.yojan.task_manager_ia.dto.TaskExtractionDTO;
import com.yojan.task_manager_ia.model.Task;
import com.yojan.task_manager_ia.repository.TaskRepository;
import com.yojan.task_manager_ia.service.GoogleCalendarService;
import com.yojan.task_manager_ia.service.GroqService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @PostMapping("/from-text")
    public Task createTaskFromText(@RequestBody Map<String, String> body) {
        String userText = body.get("text");
        TaskExtractionDTO extracted = groqService.extractTaskFromText(userText);

        Task task = new Task();
        task.setTitle(extracted.getTitle());
        task.setSubject(extracted.getSubject());
        task.setDifficulty(extracted.getDifficulty());

        if (extracted.getDueDate() != null && !extracted.getDueDate().equals("null")) {
            LocalDateTime dueDate = LocalDateTime.parse(extracted.getDueDate());
            task.setDueDate(dueDate);

            try {
                String eventLink = googleCalendarService.createEvent(
                        extracted.getTitle(),
                        "Materia: " + extracted.getSubject() + " | Dificultad: " + extracted.getDifficulty(),
                        dueDate
                );
                System.out.println("Evento creado en Calendar: " + eventLink);
            } catch (Exception e) {
                System.err.println("Error creando evento en Calendar: " + e.getMessage());
            }
        }

        return taskRepository.save(task);
    }
}