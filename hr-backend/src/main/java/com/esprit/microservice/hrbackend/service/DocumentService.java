package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.DocumentDTO;
import com.esprit.microservice.hrbackend.entity.Document;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.exception.EmployeeNotFoundException;
import com.esprit.microservice.hrbackend.mapper.DocumentMapper;
import com.esprit.microservice.hrbackend.repository.DocumentRepository;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.entity.NotificationType;
import com.esprit.microservice.hrbackend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<DocumentDTO> getDocumentsByEmployee(Long employeeId) {
        return documentRepository.findByEmployeeId(employeeId).stream()
                .map(DocumentMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DocumentDTO> getAllDocuments() {
        return documentRepository.findAll().stream()
                .map(DocumentMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public DocumentDTO uploadDocument(Long employeeId, String name, String type, org.springframework.web.multipart.MultipartFile file) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + employeeId));

        String filePath = fileStorageService.store(file, "documents");

        Document document = Document.builder()
                .name(name)
                .type(type)
                .contentType(file.getContentType())
                .size(file.getSize())
                .uploadDate(LocalDate.now())
                .filePath(filePath)
                .employee(employee)
                .build();

        Document saved = documentRepository.save(document);
        
        notificationService.notifyManager(employeeId, "Un nouveau document RH a été ajouté à votre dossier.", NotificationType.DOCUMENT);
        
        return DocumentMapper.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public Document getDocumentEntityById(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource getDocumentResource(Long id) {
        Document document = getDocumentEntityById(id);
        if (document.getFilePath() == null || document.getFilePath().isEmpty()) {
            throw new RuntimeException("Aucun fichier n'est associé à ce document.");
        }
        return fileStorageService.load(document.getFilePath());
    }

    @Transactional
    public void deleteDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found with id: " + id));

        if (document.getFilePath() != null) {
            fileStorageService.delete(document.getFilePath());
        }

        documentRepository.delete(document);
    }
}
