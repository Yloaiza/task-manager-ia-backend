package com.yojan.task_manager_ia.service;

import com.yojan.task_manager_ia.model.GoogleCredential;
import com.yojan.task_manager_ia.model.User;
import com.yojan.task_manager_ia.repository.GoogleCredentialRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class GoogleOAuthService {

    @Value("${google.web.client-id}")
    private String clientId;

    @Value("${google.web.client-secret}")
    private String clientSecret;

    @Value("${google.web.redirect-uri}")
    private String redirectUri;

    private final RestClient restClient = RestClient.create();

    private static final String SCOPE = "https://www.googleapis.com/auth/calendar.events";

    public String buildAuthUrl(Long userId) {
        String encodedRedirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        String encodedScope = URLEncoder.encode(SCOPE, StandardCharsets.UTF_8);

        return "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + clientId
                + "&redirect_uri=" + encodedRedirect
                + "&response_type=code"
                + "&scope=" + encodedScope
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + userId;
    }

    @SuppressWarnings("unchecked")
    public void exchangeCodeForTokens(String code, Long userId, GoogleCredentialRepository repo, User user) {
        Map<String, Object> response = restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                        + "&client_id=" + clientId
                        + "&client_secret=" + clientSecret
                        + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                        + "&grant_type=authorization_code")
                .retrieve()
                .body(Map.class);

        String accessToken = (String) response.get("access_token");
        String refreshToken = (String) response.get("refresh_token");
        Integer expiresIn = (Integer) response.get("expires_in");

        GoogleCredential credential = repo.findByUserId(userId).orElse(new GoogleCredential());
        credential.setUser(user);
        credential.setAccessToken(accessToken);
        if (refreshToken != null) {
            credential.setRefreshToken(refreshToken);
        }
        credential.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn != null ? expiresIn : 3600));

        repo.save(credential);
    }

    @SuppressWarnings("unchecked")
    public String getValidAccessToken(GoogleCredential credential, GoogleCredentialRepository repo) {
        if (credential.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(2))) {
            return credential.getAccessToken();
        }

        Map<String, Object> response = restClient.post()
                .uri("https://oauth2.googleapis.com/token")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("refresh_token=" + URLEncoder.encode(credential.getRefreshToken(), StandardCharsets.UTF_8)
                        + "&client_id=" + clientId
                        + "&client_secret=" + clientSecret
                        + "&grant_type=refresh_token")
                .retrieve()
                .body(Map.class);

        String newAccessToken = (String) response.get("access_token");
        Integer expiresIn = (Integer) response.get("expires_in");

        credential.setAccessToken(newAccessToken);
        credential.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn != null ? expiresIn : 3600));
        repo.save(credential);

        return newAccessToken;
    }
}
