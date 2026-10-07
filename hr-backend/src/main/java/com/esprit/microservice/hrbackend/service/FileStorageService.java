package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.exception.InvalidFileException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path rootLocation;
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".pdf", ".docx");

    public FileStorageService(@Value("${app.upload-dir:C:/Users/Lenovo/Desktop/stage 4/stage/uploads}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.rootLocation);
            Files.createDirectories(this.rootLocation.resolve("photos"));
            Files.createDirectories(this.rootLocation.resolve("documents"));
            Files.createDirectories(this.rootLocation.resolve("cvs"));
            Files.createDirectories(this.rootLocation.resolve("motivation-letters"));
            Files.createDirectories(this.rootLocation.resolve("certificates"));
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize upload storage folder", e);
        }
    }

    public String store(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Le fichier est vide.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
        String extension = getFileExtension(originalFilename);

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new InvalidFileException("Type de fichier non autorisé. Extensions acceptées : " + ALLOWED_EXTENSIONS);
        }

        String newFilename = UUID.randomUUID().toString() + extension;
        Path folderPath = this.rootLocation.resolve(folder).normalize();

        try {
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }

            Path destinationFile = folderPath.resolve(newFilename).normalize();
            if (!destinationFile.startsWith(this.rootLocation)) {
                throw new InvalidFileException("Impossible de stocker le fichier en dehors du répertoire autorisé.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
            }

            return folder + "/" + newFilename;
        } catch (IOException e) {
            throw new RuntimeException("Échec du stockage du fichier " + originalFilename, e);
        }
    }

    public String storeBytes(byte[] data, String originalFilename, String folder) {
        if (data == null || data.length == 0) {
            return null;
        }

        String extension = getFileExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            extension = ".pdf";
        }

        String newFilename = UUID.randomUUID().toString() + extension;
        Path folderPath = this.rootLocation.resolve(folder).normalize();

        try {
            if (!Files.exists(folderPath)) {
                Files.createDirectories(folderPath);
            }

            Path destinationFile = folderPath.resolve(newFilename).normalize();
            if (!destinationFile.startsWith(this.rootLocation)) {
                throw new InvalidFileException("Impossible de stocker le fichier en dehors du répertoire autorisé.");
            }

            Files.write(destinationFile, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return folder + "/" + newFilename;
        } catch (IOException e) {
            throw new RuntimeException("Échec du stockage des données sur le disque", e);
        }
    }

    public Resource load(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable.");
        }

        try {
            Path file = this.rootLocation.resolve(relativePath).normalize();

            // Protection contre le Path Traversal
            if (!file.startsWith(this.rootLocation)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé au fichier.");
            }

            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable sur le disque : " + relativePath);
            }
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable : " + relativePath, e);
        }
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.trim().isEmpty()) {
            return;
        }

        try {
            Path file = this.rootLocation.resolve(relativePath).normalize();
            if (file.startsWith(this.rootLocation)) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            System.err.println("Avertissement : Impossible de supprimer le fichier du disque : " + relativePath);
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty() || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
