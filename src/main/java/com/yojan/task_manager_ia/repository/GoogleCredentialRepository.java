package com.yojan.task_manager_ia.repository;

import com.yojan.task_manager_ia.model.GoogleCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoogleCredentialRepository extends JpaRepository<GoogleCredential, Long> {
    Optional<GoogleCredential> findByUserId(Long userId);
}
