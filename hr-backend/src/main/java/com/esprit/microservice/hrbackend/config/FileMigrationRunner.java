package com.esprit.microservice.hrbackend.config;

import com.esprit.microservice.hrbackend.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final FileStorageService fileStorageService;

    @Value("${app.migration.enabled:false}")
    private boolean migrationEnabled;

    @Override
    public void run(String... args) throws Exception {
        if (!migrationEnabled) {
            log.info("File migration runner is disabled (app.migration.enabled=false).");
            return;
        }

        log.info("Starting file migration from PostgreSQL bytea columns to disk storage...");

        // 1. Migrate Employees photos
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, photo FROM employees WHERE photo IS NOT NULL AND (photo_path IS NULL OR photo_path = '')"
            );
            for (Map<String, Object> row : rows) {
                Long id = ((Number) row.get("id")).longValue();
                byte[] bytes = (byte[]) row.get("photo");
                if (bytes != null && bytes.length > 0) {
                    String relativePath = fileStorageService.storeBytes(bytes, "photo.jpg", "photos");
                    jdbcTemplate.update("UPDATE employees SET photo_path = ? WHERE id = ?", relativePath, id);
                    log.info("Migrated photo for Employee ID: {} -> {}", id, relativePath);
                }
            }
        } catch (Exception e) {
            log.warn("Employee photo migration info: {}", e.getMessage());
        }

        // 2. Migrate Documents data
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, name, data FROM documents WHERE data IS NOT NULL AND (file_path IS NULL OR file_path = '')"
            );
            for (Map<String, Object> row : rows) {
                Long id = ((Number) row.get("id")).longValue();
                String name = (String) row.get("name");
                byte[] bytes = (byte[]) row.get("data");
                if (bytes != null && bytes.length > 0) {
                    String relativePath = fileStorageService.storeBytes(bytes, name != null ? name : "document.pdf", "documents");
                    jdbcTemplate.update("UPDATE documents SET file_path = ? WHERE id = ?", relativePath, id);
                    log.info("Migrated document for Document ID: {} -> {}", id, relativePath);
                }
            }
        } catch (Exception e) {
            log.warn("Document data migration info: {}", e.getMessage());
        }

        // 3. Migrate Candidates CV and Motivation Letter
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, cv_file_name, cv_data, motivation_letter_file_name, motivation_letter_data FROM candidates"
            );
            for (Map<String, Object> row : rows) {
                Long id = ((Number) row.get("id")).longValue();
                byte[] cvBytes = (byte[]) row.get("cv_data");
                byte[] mlBytes = (byte[]) row.get("motivation_letter_data");

                if (cvBytes != null && cvBytes.length > 0) {
                    String fileName = (String) row.get("cv_file_name");
                    String cvPath = fileStorageService.storeBytes(cvBytes, fileName != null ? fileName : "cv.pdf", "cvs");
                    jdbcTemplate.update("UPDATE candidates SET cv_path = ? WHERE id = ?", cvPath, id);
                    log.info("Migrated CV for Candidate ID: {} -> {}", id, cvPath);
                }

                if (mlBytes != null && mlBytes.length > 0) {
                    String fileName = (String) row.get("motivation_letter_file_name");
                    String mlPath = fileStorageService.storeBytes(mlBytes, fileName != null ? fileName : "letter.pdf", "motivation-letters");
                    jdbcTemplate.update("UPDATE candidates SET motivation_letter_path = ? WHERE id = ?", mlPath, id);
                    log.info("Migrated Motivation Letter for Candidate ID: {} -> {}", id, mlPath);
                }
            }
        } catch (Exception e) {
            log.warn("Candidate migration info: {}", e.getMessage());
        }

        // 4. Migrate TrainingEnrollments certificates
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, certificate FROM training_enrollments WHERE certificate IS NOT NULL AND (certificate_path IS NULL OR certificate_path = '')"
            );
            for (Map<String, Object> row : rows) {
                Long id = ((Number) row.get("id")).longValue();
                byte[] bytes = (byte[]) row.get("certificate");
                if (bytes != null && bytes.length > 0) {
                    String relativePath = fileStorageService.storeBytes(bytes, "certificate.pdf", "certificates");
                    jdbcTemplate.update("UPDATE training_enrollments SET certificate_path = ? WHERE id = ?", relativePath, id);
                    log.info("Migrated certificate for TrainingEnrollment ID: {} -> {}", id, relativePath);
                }
            }
        } catch (Exception e) {
            log.warn("TrainingEnrollment certificate migration info: {}", e.getMessage());
        }

        log.info("File migration finished!");
    }
}
