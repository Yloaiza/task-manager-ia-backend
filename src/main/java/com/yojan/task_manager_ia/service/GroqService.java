package com.yojan.task_manager_ia.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.yojan.task_manager_ia.dto.TaskExtractionDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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
}