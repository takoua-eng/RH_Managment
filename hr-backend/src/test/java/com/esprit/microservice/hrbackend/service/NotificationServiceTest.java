package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.NotificationDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Notification;
import com.esprit.microservice.hrbackend.entity.NotificationType;
import com.esprit.microservice.hrbackend.entity.Role;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

    private Notification notification(Long id, boolean unread, LocalDateTime createdAt) {
        return Notification.builder()
                .id(id)
                .recipientId(7L)
                .text("Notification de test")
                .type(NotificationType.SYSTEM)
                .unread(unread)
                .createdAt(createdAt)
                .build();
    }

    // =====================================================================
    // Envoi des notifications
    // =====================================================================

    @Nested
    @DisplayName("Envoi")
    class Envoi {

        @Test
        @DisplayName("Notification enregistrée puis envoyée en temps réel à l'utilisateur Keycloak")
        void enregistreeEtEnvoyee() {
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
            Employee destinataire = Employee.builder().id(5L).keycloakId("kc-5").build();
            when(employeeRepository.findById(5L)).thenReturn(Optional.of(destinataire));

            service.notify(5L, NotificationType.LEAVE, "Congé accepté", "/dashboard/leave");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            Notification enregistree = captor.getValue();
            assertThat(enregistree.getRecipientId()).isEqualTo(5L);
            assertThat(enregistree.getIcon()).isEqualTo("calendar_today");
            assertThat(enregistree.isUnread()).isTrue();

            verify(messagingTemplate).convertAndSendToUser(eq("kc-5"), eq("/queue/notifications"), any(NotificationDTO.class));
        }

        @Test
        @DisplayName("Destinataire sans compte Keycloak : enregistrée, mais pas d'envoi en temps réel")
        void sansCompteKeycloak() {
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
            when(employeeRepository.findById(5L)).thenReturn(Optional.of(Employee.builder().id(5L).build()));

            service.notify(5L, NotificationType.TRAINING, "Nouvelle formation", null);

            verify(notificationRepository).save(any(Notification.class));
            verifyNoInteractions(messagingTemplate);
        }

        @Test
        @DisplayName("Échec de l'envoi en temps réel : la notification reste enregistrée")
        void echecDuTempsReel() {
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
            when(employeeRepository.findById(5L)).thenReturn(Optional.of(Employee.builder().id(5L).keycloakId("kc-5").build()));
            doThrow(new RuntimeException("WebSocket indisponible"))
                    .when(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any(Object.class));

            service.notify(5L, NotificationType.EVALUATION, "Évaluation", null);

            verify(notificationRepository).save(any(Notification.class));
        }

        @Test
        @DisplayName("Destinataire absent : rien n'est enregistré")
        void destinataireAbsent() {
            service.notify(null, NotificationType.SYSTEM, "Sans destinataire", null);

            verifyNoInteractions(notificationRepository, messagingTemplate);
        }

        @Test
        @DisplayName("Type absent : icône par défaut")
        void iconeParDefaut() {
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

            service.notifyManager(9L, "Message", null);

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getIcon()).isEqualTo("notifications");
        }

        @Test
        @DisplayName("Notification du manager avec lien")
        void managerAvecLien() {
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

            service.notifyManager(9L, "Nouvelle demande", NotificationType.LEAVE, "/manager/leaves");

            ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
            verify(notificationRepository).save(captor.capture());
            assertThat(captor.getValue().getLink()).isEqualTo("/manager/leaves");
        }

        @Test
        @DisplayName("Notification par rôle : tous les destinataires sauf l'auteur de l'action")
        void parRoleSaufAuteur() {
            when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));
            Employee rh1 = Employee.builder().id(1L).build();
            Employee rh2 = Employee.builder().id(2L).build();
            Employee auteur = Employee.builder().id(3L).build();
            when(employeeRepository.findByRoleIn(Set.of(Role.RH))).thenReturn(List.of(rh1, rh2, auteur));

            service.notifyRoles(Set.of(Role.RH), NotificationType.CANDIDATURE, "Nouvelle candidature", null, 3L);

            verify(notificationRepository, times(2)).save(any(Notification.class));
        }

        @Test
        @DisplayName("Notification par rôle sans rôle ou sans destinataire : rien n'est envoyé")
        void parRoleSansDestinataire() {
            service.notifyRoles(Set.of(), NotificationType.SYSTEM, "Rien", null, null);

            when(employeeRepository.findByRoleIn(Set.of(Role.ADMIN))).thenReturn(List.of());
            service.notifyRoles(Set.of(Role.ADMIN), NotificationType.SYSTEM, "Personne", null, null);

            verify(notificationRepository, never()).save(any());
        }
    }

    // =====================================================================
    // Lecture
    // =====================================================================

    @Nested
    @DisplayName("Lecture")
    class Lecture {

        @Test
        @DisplayName("Tout marquer comme lu : seules les notifications non lues sont modifiées")
        void toutMarquerCommeLu() {
            Notification nonLue = notification(1L, true, LocalDateTime.now());
            Notification dejaLue = notification(2L, false, LocalDateTime.now());
            when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(nonLue, dejaLue));

            service.markAllAsRead(7L);

            assertThat(nonLue.isUnread()).isFalse();
            verify(notificationRepository).saveAll(List.of(nonLue));
        }

        @Test
        @DisplayName("Marquer une notification comme lue")
        void marquerUneCommeLue() {
            Notification n = notification(1L, true, LocalDateTime.now());
            when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

            service.markAsRead(1L);

            assertThat(n.isUnread()).isFalse();
            verify(notificationRepository).save(n);
        }

        @Test
        @DisplayName("Nombre de notifications non lues")
        void nombreNonLues() {
            when(notificationRepository.countByRecipientIdAndUnreadTrue(7L)).thenReturn(4L);

            assertThat(service.getUnreadCount(7L)).isEqualTo(4L);
            assertThat(service.getUnreadCount(null)).isZero();
        }

        @Test
        @DisplayName("Liste limitée au nombre demandé, 20 par défaut")
        void listeLimitee() {
            List<Notification> trente = java.util.stream.LongStream.rangeClosed(1, 30)
                    .mapToObj(id -> notification(id, true, LocalDateTime.now()))
                    .toList();
            when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(7L)).thenReturn(trente);

            assertThat(service.getNotifications(7L)).hasSize(20);
            assertThat(service.getNotifications(7L, 5)).hasSize(5);
            assertThat(service.getNotifications(7L, 0)).hasSize(20);
        }
    }

    // =====================================================================
    // Ancienneté affichée (« Il y a 5 min », « Hier »…)
    // =====================================================================

    @Nested
    @DisplayName("Ancienneté")
    class Anciennete {

        private String anciennete(LocalDateTime createdAt) {
            when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(7L))
                    .thenReturn(List.of(notification(1L, true, createdAt)));
            return service.getNotifications(7L, 20).get(0).getTime();
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
        @DisplayName("Date dans le futur, horloges décalées : « À l'instant »")
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
}