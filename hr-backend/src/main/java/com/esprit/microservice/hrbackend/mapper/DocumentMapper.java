package com.esprit.microservice.hrbackend.mapper;

import com.esprit.microservice.hrbackend.dto.DocumentDTO;
import com.esprit.microservice.hrbackend.entity.Document;

public class DocumentMapper {

    public static DocumentDTO toDTO(Document document) {
        if (document == null) {
            return null;
        }
        return DocumentDTO.builder()
                .id(document.getId())
                .name(document.getName())
                .type(document.getType())
                .contentType(document.getContentType())
                .size(document.getSize())
                .uploadDate(document.getUploadDate())
                .employeeId(document.getEmployee().getId())
                .employeeName(document.getEmployee().getFirstName() + " " + document.getEmployee().getLastName())
                .build();
    }
}
