package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.NotificationDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Notification;
import com.esprit.microservice.hrbackend.entity.NotificationType;
import com.esprit.microservice.hrbackend.entity.Role;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String A_L_INSTANT = "À l'instant";
    private static final int LIMITE_PAR_DEFAUT = 20;

    private final NotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // =========================================================
    // ENVOI
    // =========================================================

    /**
     * Enregistre la notification, puis l'envoie en temps réel (WebSocket)
     * si le destinataire a un compte Keycloak.
     */
    @Transactional
    public void notify(Long recipientEmployeeId, NotificationType type, String text, String link) {
        if (recipientEmployeeId == null) {
            log.warn("Cannot send notification because recipientEmployeeId is null. Type: {}, Text: {}", type, text);
            return;
        }

        Notification notification = Notification.builder()
                .recipientId(recipientEmployeeId)
                .text(text)
                .icon(resolveIcon(type))
                .type(type)
                .link(link)
                .unread(true)
                .build();

        Notification saved = notificationRepository.save(notification);

        // Envoi en temps réel : un échec n'empêche pas l'enregistrement de la notification
        try {
            employeeRepository.findById(recipientEmployeeId).ifPresent(employee -> {
                String keycloakId = employee.getKeycloakId();
                if (keycloakId != null && !keycloakId.isBlank()) {
                    NotificationDTO dto = mapToDTO(saved);
                    messagingTemplate.convertAndSendToUser(keycloakId, "/queue/notifications", dto);
                    log.debug("[WebSocket] Notification sent to user {}: {}", keycloakId, dto.getText());
                }
            });
        } catch (Exception e) {
            log.debug("[WebSocket] Notification delivery skipped/failed for recipient {}: {}", recipientEmployeeId, e.getMessage());
        }
    }

    @Transactional
    public void notifyManager(Long managerId, String text, NotificationType type) {
        notify(managerId, type, text, null);
    }

    @Transactional
    public void notifyManager(Long managerId, String text, NotificationType type, String link) {
        notify(managerId, type, text, link);
    }

    /** Notifie tous les employés ayant l'un des rôles donnés, sauf éventuellement l'auteur de l'action. */
    @Transactional
    public void notifyRoles(Set<Role> roles, NotificationType type, String text, String link, Long excludeEmployeeId) {
        if (roles == null || roles.isEmpty()) {
            return;
        }

        List<Employee> recipients = employeeRepository.findByRoleIn(roles);
        if (recipients.isEmpty()) {
            log.warn("[NOTIFICATION] No employee records found with roles {}. Notification skipped: {}", roles, text);
            return;
        }

        for (Employee emp : recipients) {
            if (excludeEmployeeId == null || !excludeEmployeeId.equals(emp.getId())) {
                notify(emp.getId(), type, text, link);
            }
        }
    }

    private String resolveIcon(NotificationType type) {
        if (type == null) return "notifications";
        return switch (type) {
            case LEAVE -> "calendar_today";
            case TRAINING, FORMATION -> "school";
            case EVALUATION -> "star";
            case DOCUMENT -> "description";
            case EQUIPE -> "groups";
            case CANDIDATURE -> "person_search";
            case ENTRETIEN -> "event";
            case IA -> "psychology";
            case SYSTEM, SYSTEME -> "settings";
            default -> "notifications";
        };
    }

    // =========================================================
    // CONSULTATION
    // =========================================================

    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotifications(Long recipientId) {
        return getNotifications(recipientId, LIMITE_PAR_DEFAUT);
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotifications(Long recipientId, int limit) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId).stream()
                .limit(limit > 0 ? limit : LIMITE_PAR_DEFAUT)
                .map(this::mapToDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long recipientId) {
        if (recipientId == null) return 0;
        return notificationRepository.countByRecipientIdAndUnreadTrue(recipientId);
    }

    // =========================================================
    // LECTURE
    // =========================================================

    @Transactional
    public void markAllAsRead(Long recipientId) {
        List<Notification> unread = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId).stream()
                .filter(Notification::isUnread)
                .toList();

        unread.forEach(n -> n.setUnread(false));
        notificationRepository.saveAll(unread);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setUnread(false);
            notificationRepository.save(n);
        });
    }

    // =========================================================
    // CONVERSION
    // =========================================================

    private NotificationDTO mapToDTO(Notification n) {
        return NotificationDTO.builder()
                .id(n.getId())
                .text(n.getText())
                .icon(n.getIcon())
                .unread(n.isUnread())
                .type(n.getType() != null ? n.getType().name() : null)
                .link(n.getLink())
                .time(calculateTimeAgo(n.getCreatedAt()))
                .build();
    }

    /** Ancienneté affichée dans la cloche : « À l'instant », « Il y a 5 min », « Hier »… */
    private String calculateTimeAgo(LocalDateTime createdAt) {
        if (createdAt == null) return A_L_INSTANT;

        // Les deux dates sont placées dans le même fuseau horaire avant le calcul de l'écart
        ZoneId zone = ZoneId.systemDefault();
        Duration duration = Duration.between(createdAt.atZone(zone), ZonedDateTime.now(zone));

        long minutes = duration.toMinutes();
        // Écart négatif (horloges décalées) ou moins d'une minute : notification toute récente
        if (minutes < 1) return A_L_INSTANT;

        long hours = duration.toHours();
        long days = duration.toDays();

        if (minutes < 60) return "Il y a " + minutes + " min";
        if (hours < 24) return "Il y a " + hours + " heure(s)";
        if (days == 1) return "Hier";
        if (days < 7) return "Il y a " + days + " jour(s)";
        return "Il y a " + (days / 7) + " semaine(s)";
    }
}