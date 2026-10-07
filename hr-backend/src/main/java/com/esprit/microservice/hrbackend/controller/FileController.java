package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.service.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/{folder}/{filename:.+}")
    public ResponseEntity<Resource> getFile(
            @PathVariable String folder,
            @PathVariable String filename) {

        String relativePath = folder + "/" + filename;
        Resource resource = fileStorageService.load(relativePath);

        MediaType mediaType = MediaTypeFactory.getMediaType(resource)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=604800, must-revalidate")
                .body(resource);
    }
}
