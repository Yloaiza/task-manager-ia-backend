package com.yojan.task_manager_ia.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.yojan.task_manager_ia.dto.TaskExtractionDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.yojan.task_manager_ia.dto.VoiceCommandDTO;
import com.yojan.task_manager_ia.model.Task;
import java.util.List;

import java.time.LocalDate;
import java.util.Map;

@Service
public class GroqService {

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TaskExtractionDTO extractTaskFromText(String userText) {

 String today = LocalDate.now().toString(); // ej: "2026-07-14"
 
String systemPrompt = """
    Sos un asistente que extrae informacion de tareas academicas de un texto en español.
    La fecha de HOY es: %s
    Debes responder UNICAMENTE con un JSON valido, sin texto adicional, con este formato exacto:
    {
      "title": "string corto describiendo la tarea",
      "subject": "materia o tema, o null si no se menciona",
      "dueDate": "fecha en formato ISO 8601 (YYYY-MM-DDTHH:mm:ss), o null si no se menciona",
      "difficulty": "facil, media o dificil segun el contexto, o 'media' si no se menciona"
    }
    Si el texto menciona el dia del mes pero no el mes, asumi el mes actual.
    Si el texto menciona una fecha sin año, usa el año actual basandote en la fecha de HOY.
    Si la fecha mencionada ya paso este año, asumi que es del año siguiente.
    No agregues explicaciones, solo el JSON.
    """.formatted(today);

        Map<String, Object> requestBody = Map.of(
                "model", "llama-3.3-70b-versatile",
                "messages", new Object[]{
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userText)
                },
                "temperature", 0.2
        );

        String response = restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();

            // Limpieza por si Groq envuelve el JSON en ```json ... ```
            content = content.replaceAll("```json", "").replaceAll("```", "").trim();

            return objectMapper.readValue(content, TaskExtractionDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Error procesando respuesta de Groq: " + e.getMessage(), e);
        }
    }
    public VoiceCommandDTO interpretVoiceCommand(String userText, List<Task> pendingTasks) {
        String today = LocalDate.now().toString();

        StringBuilder taskList = new StringBuilder();
        for (Task t : pendingTasks) {
            taskList.append("id ").append(t.getId()).append(": ").append(t.getTitle()).append("\n");
        }

        String systemPrompt = """
            Sos un asistente que interpreta comandos de voz sobre tareas academicas en español.
            La fecha de HOY es: %s

            Estas son las tareas pendientes del usuario (id: titulo):
            %s

            Decidi si el usuario quiere CREAR una tarea nueva o COMPLETAR una existente.
            - Si el usuario dice algo como "ya hice/termine/complete X", es "complete", y debes encontrar el id de la tarea de la lista que mejor coincida con X.
            - Si no coincide claramente con ninguna tarea de la lista, o el usuario esta describiendo algo nuevo (menciona examen, entrega, fecha), es "create".

            Responde UNICAMENTE con un JSON valido, sin texto adicional, con este formato:
            {
              "action": "create" o "complete",
              "taskId": numero del id si es complete, o null si es create,
              "title": "titulo de la tarea si es create, o null si es complete",
              "subject": "materia si es create, o null",
              "dueDate": "fecha ISO 8601 si es create y se menciona, o null",
              "difficulty": "facil, media o dificil si es create, o null"
            }
            No agregues explicaciones, solo el JSON.
            """.formatted(today, taskList.toString());

        Map<String, Object> requestBody = Map.of(
                "model", "llama-3.3-70b-versatile",
                "messages", new Object[]{
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userText)
                },
                "temperature", 0.2
        );

        String response = restClient.post()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("choices").get(0).path("message").path("content").asText();
            content = content.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readValue(content, VoiceCommandDTO.class);
        } catch (Exception e) {
            throw new RuntimeException("Error procesando comando de voz: " + e.getMessage(), e);
        }
    }
}