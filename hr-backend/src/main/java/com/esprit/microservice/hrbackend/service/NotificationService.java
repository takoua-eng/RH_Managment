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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.messaging.simp.SimpMessagingTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final EmployeeRepository employeeRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void notify(Long recipientEmployeeId, NotificationType type, String text, String link) {
        if (recipientEmployeeId == null) {
            log.warn("Cannot send notification because recipientEmployeeId is null. Type: {}, Text: {}", type, text);
            return;
        }

        String icon = resolveIcon(type);

        Notification notification = Notification.builder()
                .recipientId(recipientEmployeeId)
                .text(text)
                .icon(icon)
                .type(type)
                .link(link)
                .unread(true)
                .build();

        Notification saved = notificationRepository.save(notification);

        // Attempt WebSocket real-time delivery
        try {
            employeeRepository.findById(recipientEmployeeId).ifPresent(employee -> {
                String keycloakId = employee.getKeycloakId();
                if (keycloakId != null && !keycloakId.trim().isEmpty()) {
                    NotificationDTO dto = mapToDTO(saved);
                    messagingTemplate.convertAndSendToUser(
                            keycloakId,
                            "/queue/notifications",
                            dto
                    );
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
            if (excludeEmployeeId != null && excludeEmployeeId.equals(emp.getId())) {
                continue;
            }
            notify(emp.getId(), type, text, link);
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

    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotifications(Long recipientId) {
        return getNotifications(recipientId, 20);
    }

    @Transactional(readOnly = true)
    public List<NotificationDTO> getNotifications(Long recipientId, int limit) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId).stream()
                .limit(limit > 0 ? limit : 20)
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long recipientId) {
        if (recipientId == null) return 0;
        return notificationRepository.countByRecipientIdAndUnreadTrue(recipientId);
    }

    @Transactional
    public void markAllAsRead(Long recipientId) {
        List<Notification> unread = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId).stream()
                .filter(Notification::isUnread)
                .collect(Collectors.toList());
        
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

    private String calculateTimeAgo(LocalDateTime createdAt) {
        if (createdAt == null) return "À l'instant";

        // Les deux dates sont placées dans le même fuseau horaire avant le calcul de l'écart
        ZoneId zone = ZoneId.systemDefault();
        Duration duration = Duration.between(createdAt.atZone(zone), ZonedDateTime.now(zone));
        if (duration.isNegative()) return "À l'instant";

        long minutes = duration.toMinutes();
        long hours = duration.toHours();
        long days = duration.toDays();

        if (minutes < 1) return "À l'instant";
        if (minutes < 60) return "Il y a " + minutes + " min";
        if (hours < 24) return "Il y a " + hours + " heure(s)";
        if (days == 1) return "Hier";
        if (days < 7) return "Il y a " + days + " jour(s)";
        return "Il y a " + (days / 7) + " semaine(s)";
    }
}
