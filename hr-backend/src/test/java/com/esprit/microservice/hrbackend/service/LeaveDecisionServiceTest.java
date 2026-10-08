package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Leave;
import com.esprit.microservice.hrbackend.entity.LeaveStatus;
import com.esprit.microservice.hrbackend.entity.LeaveType;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.LeaveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests du nouveau circuit des congés :
 * l'employé demande (PENDING), son manager accepte (APPROVED) ou refuse (REJECTED).
 */
@ExtendWith(MockitoExtension.class)
class LeaveDecisionServiceTest {

    @Mock
    private LeaveRepository leaveRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private LeaveDecisionService service;

    private Employee manager;
    private Employee employee;
    private Leave leave;

    @BeforeEach
    void setUp() {
        manager = Employee.builder()
                .id(24L).firstName("Manager").lastName("Test").keycloakId("kc-manager")
                .build();

        employee = Employee.builder()
                .id(22L).firstName("Employee").lastName("Test").keycloakId("kc-employee")
                .availableLeaveDays(10)
                .manager(manager)
                .build();

        // Congé annuel de 3 jours (du 12 au 14 octobre), en attente
        leave = Leave.builder()
                .id(1L)
                .employeeId(22L)
                .type(LeaveType.ANNUAL)
                .status(LeaveStatus.PENDING)
                .startDate(LocalDate.of(2026, 10, 12))
                .endDate(LocalDate.of(2026, 10, 14))
                .build();
    }

    /** Simule un utilisateur connecté avec un jeton Keycloak. */
    private Authentication authentification(String keycloakId, String role) {
        Jwt jwt = Jwt.withTokenValue("jeton-de-test")
                .header("alg", "none")
                .subject(keycloakId)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(role)));
    }

    /** Prépare les appels communs à une décision prise par le manager de l'employé. */
    private void decisionParLeManager() {
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeRepository.findById(22L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findByKeycloakId("kc-manager")).thenReturn(Optional.of(manager));
    }

    @Test
    @DisplayName("Le manager accepte : statut APPROVED et solde décompté")
    void approbationParLeManager() {
        decisionParLeManager();
        when(leaveRepository.save(any(Leave.class))).thenAnswer(i -> i.getArgument(0));

        Leave resultat = service.decide(1L, true, null, authentification("kc-manager", "ROLE_MANAGER"));

        assertThat(resultat.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(resultat.getDecidedById()).isEqualTo(24L);
        assertThat(resultat.getDecidedAt()).isNotNull();
        assertThat(employee.getAvailableLeaveDays()).isEqualTo(7);   // 10 - 3 jours
        verify(employeeRepository).save(employee);
    }

    @Test
    @DisplayName("Le manager refuse avec un motif : statut REJECTED, solde inchangé")
    void refusAvecMotif() {
        decisionParLeManager();
        when(leaveRepository.save(any(Leave.class))).thenAnswer(i -> i.getArgument(0));

        Leave resultat = service.decide(1L, false, "Période de forte activité",
                authentification("kc-manager", "ROLE_MANAGER"));

        assertThat(resultat.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(resultat.getDecisionComment()).isEqualTo("Période de forte activité");
        assertThat(employee.getAvailableLeaveDays()).isEqualTo(10);
    }

    @Test
    @DisplayName("Un refus sans motif est rejeté (400)")
    void refusSansMotif() {
        decisionParLeManager();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.decide(1L, false, "  ", authentification("kc-manager", "ROLE_MANAGER")));

        assertThat(ex.getStatusCode().value()).isEqualTo(400);
        assertThat(leave.getStatus()).isEqualTo(LeaveStatus.PENDING);
        verify(leaveRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un autre manager ne peut pas décider (403)")
    void autreManagerInterdit() {
        Employee autreManager = Employee.builder().id(99L).keycloakId("kc-autre").build();
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));
        when(employeeRepository.findById(22L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findByKeycloakId("kc-autre")).thenReturn(Optional.of(autreManager));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.decide(1L, true, null, authentification("kc-autre", "ROLE_MANAGER")));

        assertThat(ex.getStatusCode().value()).isEqualTo(403);
        verify(leaveRepository, never()).save(any());
    }

    @Test
    @DisplayName("Solde insuffisant : l'approbation est refusée (400)")
    void soldeInsuffisant() {
        employee.setAvailableLeaveDays(2);   // 3 jours demandés
        decisionParLeManager();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.decide(1L, true, null, authentification("kc-manager", "ROLE_MANAGER")));

        assertThat(ex.getStatusCode().value()).isEqualTo(400);
        assertThat(employee.getAvailableLeaveDays()).isEqualTo(2);
        verify(leaveRepository, never()).save(any());
    }

    @Test
    @DisplayName("Un congé maladie ne décompte pas le solde")
    void congeMaladieSansDecompte() {
        leave.setType(LeaveType.SICK);
        decisionParLeManager();
        when(leaveRepository.save(any(Leave.class))).thenAnswer(i -> i.getArgument(0));

        Leave resultat = service.decide(1L, true, null, authentification("kc-manager", "ROLE_MANAGER"));

        assertThat(resultat.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(employee.getAvailableLeaveDays()).isEqualTo(10);
        verify(employeeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Une demande déjà traitée ne peut plus être modifiée (400)")
    void demandeDejaTraitee() {
        leave.setStatus(LeaveStatus.APPROVED);
        when(leaveRepository.findById(1L)).thenReturn(Optional.of(leave));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.decide(1L, false, "Trop tard", authentification("kc-manager", "ROLE_MANAGER")));

        assertThat(ex.getStatusCode().value()).isEqualTo(400);
        verify(leaveRepository, never()).save(any());
    }
}