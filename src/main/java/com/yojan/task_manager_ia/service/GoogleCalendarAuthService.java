package com.yojan.task_manager_ia.service;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleCalendarAuthService {

    private static final String TOKENS_DIRECTORY_PATH =
            System.getenv().getOrDefault("GOOGLE_TOKENS_PATH", "tokens");
    private static final String CREDENTIALS_FILE_PATH =
            System.getenv().getOrDefault("GOOGLE_CREDENTIALS_PATH", "google-credentials.json");
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR_EVENTS);

    public Credential getCredentials() throws Exception {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = GsonFactory.getDefaultInstance();

        Reader reader = loadCredentialsReader();
        var clientSecrets = GoogleClientSecrets.load(jsonFactory, reader);

        var flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        var receiver = new LocalServerReceiver.Builder().setPort(8888).build();

        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private Reader loadCredentialsReader() throws Exception {
        File fileOnDisk = new File(CREDENTIALS_FILE_PATH);
        if (fileOnDisk.exists()) {
            return new FileReader(fileOnDisk);
        }

        var in = GoogleCalendarAuthService.class.getResourceAsStream("/google-credentials.json");
        if (in == null) {
            throw new RuntimeException("No se encontro el archivo de credenciales de Google (ni en disco ni en el classpath). Ruta buscada: " + CREDENTIALS_FILE_PATH);
        }
        return new InputStreamReader(in);
    }
}
