package com.yojan.task_manager_ia.service;

import com.yojan.task_manager_ia.model.GoogleCredential;
import com.yojan.task_manager_ia.repository.GoogleCredentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class GoogleCalendarService {

    @Autowired
    private GoogleOAuthService googleOAuthService;

    @Autowired
    private GoogleCredentialRepository googleCredentialRepository;

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String createEvent(Long userId, String title, String description, LocalDateTime dueDate) throws Exception {
        GoogleCredential credential = googleCredentialRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("El usuario no ha conectado Google Calendar"));

        String accessToken = googleOAuthService.getValidAccessToken(credential, googleCredentialRepository);

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        String startIso = dueDate.format(formatter);
        String endIso = dueDate.plusHours(1).format(formatter);

        Map<String, Object> eventBody = Map.of(
                "summary", title,
                "description", description,
                "start", Map.of("dateTime", startIso, "timeZone", "America/Bogota"),
                "end", Map.of("dateTime", endIso, "timeZone", "America/Bogota")
        );

        String response = restClient.post()
                .uri("https://www.googleapis.com/calendar/v3/calendars/primary/events")
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .body(eventBody)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(response);
        return root.path("htmlLink").asText();
    }
}
