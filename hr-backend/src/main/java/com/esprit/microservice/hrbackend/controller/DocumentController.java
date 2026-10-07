package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.DocumentDTO;
import com.esprit.microservice.hrbackend.entity.Document;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.service.DocumentService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final EmployeeRepository employeeRepository;


    // =========================================================
    // RÉCUPÉRER L'EMPLOYÉ CONNECTÉ
    // =========================================================

    private Employee getAuthenticatedEmployee(
            Authentication authentication) {

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Jwt)) {

            throw new AccessDeniedException(
                    "Utilisateur non authentifié."
            );
        }

        Jwt jwt =
                (Jwt) authentication.getPrincipal();

        String keycloakId =
                jwt.getSubject();

        if (keycloakId == null
                || keycloakId.isBlank()) {

            throw new AccessDeniedException(
                    "Identifiant Keycloak introuvable."
            );
        }

        return employeeRepository
                .findByKeycloakId(keycloakId)
                .orElseThrow(() ->
                        new AccessDeniedException(
                                "Employé authentifié introuvable."
                        )
                );
    }


    // =========================================================
    // VÉRIFIER L'ACCÈS À UN EMPLOYÉ
    // =========================================================

    private void checkEmployeeAccess(
            Long employeeId,
            Authentication authentication) {

        boolean hasElevatedRole =
                authentication
                        .getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(
                                authority ->
                                        authority.equals("ROLE_ADMIN")
                                                || authority.equals("ROLE_RH")
                                                || authority.equals("ROLE_MANAGER")
                        );

        /*
         * ADMIN / RH / MANAGER :
         * accès aux documents des autres employés.
         *
         * EMPLOYEE :
         * accès uniquement à ses propres documents.
         */
        if (!hasElevatedRole) {

            Employee authenticatedEmployee =
                    getAuthenticatedEmployee(authentication);

            if (authenticatedEmployee.getId() == null
                    || !authenticatedEmployee
                    .getId()
                    .equals(employeeId)) {

                throw new AccessDeniedException(
                        "Vous n'êtes pas autorisé à accéder aux documents de cet employé."
                );
            }
        }
    }


    // =========================================================
    // UPLOAD DOCUMENT
    // =========================================================

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')"
    )
    public ResponseEntity<DocumentDTO> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("type") String type,
            @RequestParam(
                    value = "employeeId",
                    required = false
            ) Long employeeId,
            Authentication authentication
    ) throws IOException {

        Long targetEmployeeId = employeeId;

        /*
         * Aucun employeeId :
         * l'EMPLOYEE upload automatiquement
         * pour son propre profil.
         */
        if (targetEmployeeId == null) {

            Employee employee =
                    getAuthenticatedEmployee(authentication);

            targetEmployeeId =
                    employee.getId();

        } else {

            /*
             * Vérification de sécurité.
             */
            checkEmployeeAccess(
                    targetEmployeeId,
                    authentication
            );
        }

        DocumentDTO dto =
                documentService.uploadDocument(
                        targetEmployeeId,
                        name,
                        type,
                        file
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(dto);
    }


    // =========================================================
    // DOCUMENTS D'UN EMPLOYÉ
    // =========================================================

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')"
    )
    public ResponseEntity<List<DocumentDTO>>
    getEmployeeDocuments(
            @PathVariable Long employeeId,
            Authentication authentication) {

        /*
         * EMPLOYEE :
         * uniquement ses propres documents.
         */
        checkEmployeeAccess(
                employeeId,
                authentication
        );

        return ResponseEntity.ok(
                documentService.getDocumentsByEmployee(
                        employeeId
                )
        );
    }


    // =========================================================
    // TOUS LES DOCUMENTS
    // =========================================================

    @GetMapping("/all")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RH')"
    )
    public ResponseEntity<List<DocumentDTO>>
    getAllDocuments() {

        return ResponseEntity.ok(
                documentService.getAllDocuments()
        );
    }


    // =========================================================
    // TÉLÉCHARGER / PRÉVISUALISER UN DOCUMENT
    // =========================================================

    @GetMapping("/{id}/download")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')"
    )
    public ResponseEntity<org.springframework.core.io.Resource> downloadDocument(
            @PathVariable Long id,
            Authentication authentication) {

        Document document =
                documentService.getDocumentEntityById(id);

        if (document == null
                || document.getEmployee() == null) {

            throw new AccessDeniedException(
                    "Document introuvable."
            );
        }

        checkEmployeeAccess(
                document.getEmployee().getId(),
                authentication
        );

        HttpHeaders headers =
                new HttpHeaders();

        String contentType =
                document.getContentType();

        if (contentType == null
                || contentType.isBlank()) {

            contentType =
                    MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        /*
         * Les PDF peuvent être affichés directement
         * dans le navigateur.
         */
        if (contentType.equalsIgnoreCase(
                MediaType.APPLICATION_PDF_VALUE)) {

            headers.add(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\""
                            + document.getName()
                            + "\""
            );

        } else {

            headers.add(
                    HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\""
                            + document.getName()
                            + "\""
            );
        }

        headers.setContentType(
                MediaType.parseMediaType(
                        contentType
                )
        );

        org.springframework.core.io.Resource resource =
                documentService.getDocumentResource(id);

        return ResponseEntity.ok()
                .headers(headers)
                .body(resource);
    }


    // =========================================================
    // SUPPRIMER UN DOCUMENT
    // =========================================================

    @DeleteMapping("/{id}")
    @PreAuthorize(
            "hasAnyRole('ADMIN', 'RH', 'MANAGER', 'EMPLOYEE')"
    )
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            Authentication authentication) {

        Document document =
                documentService.getDocumentEntityById(id);

        if (document == null
                || document.getEmployee() == null) {

            throw new AccessDeniedException(
                    "Document introuvable."
            );
        }

        checkEmployeeAccess(
                document.getEmployee().getId(),
                authentication
        );

        /*
         * ADMIN / RH / MANAGER peuvent supprimer.
         *
         * EMPLOYEE ne peut pas supprimer :
         * - Contrat
         * - Fiche de paie
         */
        boolean isElevated =
                authentication
                        .getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(
                                authority ->
                                        authority.equals("ROLE_ADMIN")
                                                || authority.equals("ROLE_RH")
                                                || authority.equals("ROLE_MANAGER")
                        );

        if (!isElevated
                && document.getType() != null
                && (
                document.getType()
                        .equalsIgnoreCase("Contrat")
                        ||
                        document.getType()
                                .equalsIgnoreCase("Fiche de paie")
        )
        ) {

            throw new AccessDeniedException(
                    "Vous n'êtes pas autorisé à supprimer les documents officiels."
            );
        }

        documentService.deleteDocument(id);

        return ResponseEntity.noContent().build();
    }
}