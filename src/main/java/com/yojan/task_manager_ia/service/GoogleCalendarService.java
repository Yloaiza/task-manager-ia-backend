package com.yojan.task_manager_ia.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.EventDateTime;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class GoogleCalendarService {

    private final GoogleCalendarAuthService authService;

    public GoogleCalendarService(GoogleCalendarAuthService authService) {
        this.authService = authService;
    }

    public String createEvent(String title, String description, LocalDateTime dueDate) throws Exception {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = GsonFactory.getDefaultInstance();
        var credential = authService.getCredentials();

        var service = new Calendar.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("Task Manager IA")
                .build();

        var event = new com.google.api.services.calendar.model.Event()
                .setSummary(title)
                .setDescription(description);

        Date date = Date.from(dueDate.atZone(ZoneId.systemDefault()).toInstant());
        Date endDate = Date.from(dueDate.plusHours(1).atZone(ZoneId.systemDefault()).toInstant());

        var dateTime = new com.google.api.client.util.DateTime(date);
        event.setStart(new EventDateTime().setDateTime(dateTime).setTimeZone("America/Bogota"));

        var endDateTime = new com.google.api.client.util.DateTime(endDate);
        event.setEnd(new EventDateTime().setDateTime(endDateTime).setTimeZone("America/Bogota"));

        var createdEvent = service.events().insert("primary", event).execute();

        return createdEvent.getHtmlLink();
    }
}