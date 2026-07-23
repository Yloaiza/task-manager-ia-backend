package com.yojan.task_manager_ia.controller;

import com.yojan.task_manager_ia.model.User;
import com.yojan.task_manager_ia.repository.UserRepository;
import com.yojan.task_manager_ia.repository.GoogleCredentialRepository;
import com.yojan.task_manager_ia.security.AuthenticatedUser;
import com.yojan.task_manager_ia.service.GoogleOAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    @Autowired
    private GoogleOAuthService googleOAuthService;

    @Autowired
    private GoogleCredentialRepository googleCredentialRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/connect")
    public ResponseEntity<Void> connect(Authentication authentication) {
        AuthenticatedUser authUser = (AuthenticatedUser) authentication.getPrincipal();
        String authUrl = googleOAuthService.buildAuthUrl(authUser.userId());

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authUrl))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(
            @RequestParam("code") String code,
            @RequestParam("state") Long userId
    ) {
        User user = userRepository.findById(userId).orElseThrow();
        googleOAuthService.exchangeCodeForTokens(code, userId, googleCredentialRepository, user);

        String html = "<html><body style='font-family: sans-serif; text-align: center; padding-top: 60px;'>"
                + "<h2>Google Calendar conectado correctamente</h2>"
                + "<p>Ya podes cerrar esta ventana y volver a la app.</p>"
                + "</body></html>";

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, "text/html").body(html);
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(Authentication authentication) {
        AuthenticatedUser authUser = (AuthenticatedUser) authentication.getPrincipal();
        boolean connected = googleCredentialRepository.findByUserId(authUser.userId()).isPresent();
        return ResponseEntity.ok(java.util.Map.of("connected", connected));
    }
}
