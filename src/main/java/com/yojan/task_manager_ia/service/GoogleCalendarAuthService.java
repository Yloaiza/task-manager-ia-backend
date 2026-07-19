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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

@Service
public class GoogleCalendarAuthService {

    private static final String SOURCE_TOKENS_PATH =
            System.getenv().getOrDefault("GOOGLE_TOKENS_PATH", "tokens");
    private static final String CREDENTIALS_FILE_PATH =
            System.getenv().getOrDefault("GOOGLE_CREDENTIALS_PATH", "google-credentials.json");
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR_EVENTS);

    public Credential getCredentials() throws Exception {
        var httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        var jsonFactory = GsonFactory.getDefaultInstance();

        Reader reader = loadCredentialsReader();
        var clientSecrets = GoogleClientSecrets.load(jsonFactory, reader);

        File writableTokensDir = prepareWritableTokensDir();

        var flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, jsonFactory, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(writableTokensDir))
                .setAccessType("offline")
                .build();

        var receiver = new LocalServerReceiver.Builder().setPort(8888).build();

        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    private File prepareWritableTokensDir() throws Exception {
        File sourceDir = new File(SOURCE_TOKENS_PATH);
        File writableDir = new File("/tmp/google-tokens");

        if (!writableDir.exists()) {
            writableDir.mkdirs();
        }

        if (sourceDir.exists() && sourceDir.isDirectory()) {
            for (File file : sourceDir.listFiles()) {
                Path target = writableDir.toPath().resolve(file.getName());
                if (!Files.exists(target)) {
                    Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } else if (sourceDir.exists() && sourceDir.isFile()) {
            // Caso: SOURCE_TOKENS_PATH apunta directo a un archivo (ej: Secret File individual)
            Path target = writableDir.toPath().resolve(sourceDir.getName());
            if (!Files.exists(target)) {
                Files.copy(sourceDir.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return writableDir;
    }

    private Reader loadCredentialsReader() throws Exception {
        File fileOnDisk = new File(CREDENTIALS_FILE_PATH);
        if (fileOnDisk.exists()) {
            return new FileReader(fileOnDisk);
        }

        var in = GoogleCalendarAuthService.class.getResourceAsStream("/google-credentials.json");
        if (in == null) {
            throw new RuntimeException("No se encontro el archivo de credenciales de Google. Ruta buscada: " + CREDENTIALS_FILE_PATH);
        }
        return new InputStreamReader(in);
    }
}
