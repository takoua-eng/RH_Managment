package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.NotificationDTO;
import com.esprit.microservice.hrbackend.entity.Notification;
import com.esprit.microservice.hrbackend.entity.NotificationType;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Affichage de l'ancienneté des notifications (« Il y a 5 min », « Hier »…).
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationService service;

    /** Renvoie le texte d'ancienneté affiché pour une notification créée à la date donnée. */
    private String anciennete(LocalDateTime createdAt) {
        Notification notification = Notification.builder()
                .id(1L)
                .recipientId(7L)
                .text("Notification de test")
                .type(NotificationType.SYSTEM)
                .unread(true)
                .createdAt(createdAt)
                .build();
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(notification));

        List<NotificationDTO> result = service.getNotifications(7L, 20);
        return result.get(0).getTime();
    }

    private LocalDateTime ilYa(long minutes) {
        return LocalDateTime.now().minusMinutes(minutes);
    }

    @Test
    @DisplayName("Moins d'une minute : « À l'instant »")
    void aLInstant() {
        assertThat(anciennete(LocalDateTime.now())).isEqualTo("À l'instant");
    }

    @Test
    @DisplayName("Date absente : « À l'instant »")
    void dateAbsente() {
        assertThat(anciennete(null)).isEqualTo("À l'instant");
    }

    @Test
    @DisplayName("Date dans le futur (horloges décalées) : « À l'instant »")
    void dateFuture() {
        assertThat(anciennete(LocalDateTime.now().plusMinutes(10))).isEqualTo("À l'instant");
    }

    @Test
    @DisplayName("Quelques minutes")
    void minutes() {
        assertThat(anciennete(ilYa(5))).isEqualTo("Il y a 5 min");
    }

    @Test
    @DisplayName("Quelques heures")
    void heures() {
        assertThat(anciennete(ilYa(3 * 60L + 5))).isEqualTo("Il y a 3 heure(s)");
    }

    @Test
    @DisplayName("Un jour : « Hier »")
    void hier() {
        assertThat(anciennete(ilYa(25 * 60L))).isEqualTo("Hier");
    }

    @Test
    @DisplayName("Quelques jours")
    void jours() {
        assertThat(anciennete(ilYa(3 * 24 * 60L + 5))).isEqualTo("Il y a 3 jour(s)");
    }

    @Test
    @DisplayName("Plus d'une semaine")
    void semaines() {
        assertThat(anciennete(ilYa(15 * 24 * 60L))).isEqualTo("Il y a 2 semaine(s)");
    }
}