package com.esprit.microservice.hrbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailAttachment {
    private String filename;
    private String contentType;
    private byte[] data;
    private File file;

    public EmailAttachment(String filename, String contentType, byte[] data) {
        this.filename = filename;
        this.contentType = contentType;
        this.data = data;
    }

    public EmailAttachment(String filename, File file) {
        this.filename = filename;
        this.file = file;
    }
}
