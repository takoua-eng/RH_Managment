package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.EmailAttachment;
import com.esprit.microservice.hrbackend.exception.EmailException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    private final JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@hr-management.com}")
    private String defaultFrom;

    /**
     * Envoyé un email texte simple.
     */
    public void sendSimpleEmail(String to, String subject, String textContent) {
        sendSimpleEmail(to, subject, textContent, defaultFrom);
    }

    public void sendSimpleEmail(String to, String subject, String textContent, String fromAddress) {
        validateEmailAddress(to);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(determineFrom(fromAddress));
            message.setTo(to);
            message.setSubject(subject != null ? subject : "");
            message.setText(textContent != null ? textContent : "");

            log.info("Sending simple text email to recipient [{}]", sanitizeForLog(to));
            mailSender.send(message);
            log.info("Simple email sent successfully to [{}]", sanitizeForLog(to));
        } catch (MailException e) {
            log.error("Failed to send simple email to recipient [{}]. Cause: {}", sanitizeForLog(to), e.getMessage());
            throw new EmailException("Échec de l'envoi de l'email simple à " + sanitizeForLog(to), e);
        } catch (Exception e) {
            log.error("Unexpected error during simple email sending to [{}]: {}", sanitizeForLog(to), e.getMessage());
            throw new EmailException("Erreur inattendue lors de l'envoi de l'email", e);
        }
    }

    /**
     * Envoyer un email HTML.
     */
    public void sendHtmlEmail(String to, String subject, String htmlContent) {
        sendEmailWithAttachments(to, subject, htmlContent, true, null);
    }

    /**
     * Envoyer un email avec pièces jointes (HTML ou Texte).
     */
    public void sendEmailWithAttachments(String to, String subject, String content, boolean isHtml, List<EmailAttachment> attachments) {
        sendEmailWithAttachments(to, subject, content, isHtml, attachments, defaultFrom);
    }

    public void sendEmailWithAttachments(String to, String subject, String content, boolean isHtml, List<EmailAttachment> attachments, String fromAddress) {
        validateEmailAddress(to);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    attachments != null && !attachments.isEmpty(),
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(determineFrom(fromAddress));
            helper.setTo(to);
            helper.setSubject(subject != null ? subject : "");
            helper.setText(content != null ? content : "", isHtml);

            if (attachments != null && !attachments.isEmpty()) {
                for (EmailAttachment attachment : attachments) {
                    addAttachmentToHelper(helper, attachment);
                }
            }

            log.info("Sending email with [{}] attachments to [{}]",
                    attachments != null ? attachments.size() : 0,
                    sanitizeForLog(to));

            mailSender.send(mimeMessage);
            log.info("Email with attachments sent successfully to [{}]", sanitizeForLog(to));
        } catch (MessagingException e) {
            log.error("SMTP Messaging error while sending email to [{}]: {}", sanitizeForLog(to), e.getMessage());
            throw new EmailException("Erreur SMTP lors de l'envoi de l'email à " + sanitizeForLog(to), e);
        } catch (MailException e) {
            log.error("SMTP Mail error while sending email to [{}]: {}", sanitizeForLog(to), e.getMessage());
            throw new EmailException("Échec de l'envoi de l'email à " + sanitizeForLog(to), e);
        } catch (Exception e) {
            log.error("Unexpected error while sending email to [{}]: {}", sanitizeForLog(to), e.getMessage());
            throw new EmailException("Erreur inattendue lors de l'envoi de l'email", e);
        }
    }

    /**
     * Méthode d'aide spécifique pour attacher un CV et/ou une lettre de motivation.
     */
    public void sendCandidateApplicationEmail(String to, String subject, String body, boolean isHtml,
                                              byte[] cvData, String cvFileName,
                                              byte[] motivationLetterData, String motivationLetterFileName) {
        List<EmailAttachment> attachments = new ArrayList<>();

        if (cvData != null && cvData.length > 0) {
            String name = (cvFileName != null && !cvFileName.isBlank()) ? cvFileName : "CV.pdf";
            attachments.add(new EmailAttachment(name, "application/pdf", cvData));
        } else if (cvFileName != null) {
            log.warn("CV attachment is empty or invalid for recipient [{}]", sanitizeForLog(to));
        }

        if (motivationLetterData != null && motivationLetterData.length > 0) {
            String name = (motivationLetterFileName != null && !motivationLetterFileName.isBlank()) ? motivationLetterFileName : "Lettre_de_motivation.pdf";
            attachments.add(new EmailAttachment(name, "application/pdf", motivationLetterData));
        } else if (motivationLetterFileName != null) {
            log.warn("Motivation letter attachment is empty or invalid for recipient [{}]", sanitizeForLog(to));
        }

        sendEmailWithAttachments(to, subject, body, isHtml, attachments);
    }

    // =========================================================================
    // PRIVATE HELPER METHODS & VALIDATIONS
    // =========================================================================

    private void validateEmailAddress(String email) {
        if (email == null || email.isBlank()) {
            log.error("Email address is null or blank");
            throw new EmailException("L'adresse email du destinataire ne peut pas être vide");
        }

        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            log.error("Invalid email address syntax: [{}]", sanitizeForLog(email));
            throw new EmailException("Format d'adresse email invalide : " + sanitizeForLog(email));
        }

        try {
            InternetAddress emailAddr = new InternetAddress(email.trim());
            emailAddr.validate();
        } catch (MessagingException ex) {
            log.error("InternetAddress validation failed for email: [{}]", sanitizeForLog(email));
            throw new EmailException("Adresse email invalide : " + sanitizeForLog(email), ex);
        }
    }

    private void addAttachmentToHelper(MimeMessageHelper helper, EmailAttachment attachment) throws MessagingException {
        if (attachment == null) {
            log.warn("Skipping null EmailAttachment object");
            return;
        }

        String filename = attachment.getFilename() != null ? attachment.getFilename() : "attachment";

        if (attachment.getData() != null) {
            if (attachment.getData().length == 0) {
                log.warn("Attachment [{}] is empty (0 bytes), skipping.", filename);
                return;
            }
            ByteArrayResource resource = new ByteArrayResource(attachment.getData());
            if (attachment.getContentType() != null) {
                helper.addAttachment(filename, resource, attachment.getContentType());
            } else {
                helper.addAttachment(filename, resource);
            }
            log.debug("Attached byte array file [{}] ({} bytes)", filename, attachment.getData().length);
        } else if (attachment.getFile() != null) {
            File file = attachment.getFile();
            if (!file.exists() || !file.isFile() || !file.canRead()) {
                log.error("Attachment file [{}] does not exist or is not readable", file.getAbsolutePath());
                throw new EmailException("Pièce jointe introuvable ou inaccessible : " + filename);
            }
            FileSystemResource resource = new FileSystemResource(file);
            helper.addAttachment(filename, resource);
            log.debug("Attached file [{}]", filename);
        } else {
            log.warn("EmailAttachment [{}] has no data and no file reference, skipping.", filename);
        }
    }

    private String determineFrom(String fromAddress) {
        if (fromAddress != null && !fromAddress.isBlank()) {
            return fromAddress.trim();
        }
        return defaultFrom;
    }

    /**
     * Sanitizes strings (like emails) to avoid log injection or leaking confidential info if needed.
     */
    private String sanitizeForLog(String input) {
        if (input == null) return "";
        return input.replaceAll("[\r\n]", "_");
    }
}
