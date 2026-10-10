package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.TrainingDTO;
import com.esprit.microservice.hrbackend.entity.Training;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.TrainingEnrollmentRepository;
import com.esprit.microservice.hrbackend.repository.TrainingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Calcul de la date de fin d'une formation à partir de sa durée.
 */
@ExtendWith(MockitoExtension.class)
class TrainingServiceTest {

    @Mock private TrainingRepository trainingRepository;
    @Mock private TrainingEnrollmentRepository trainingEnrollmentRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private NotificationService notificationService;
    @Mock private FileStorageService fileStorageService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private TrainingService service;

    private TrainingDTO creer(LocalDate debut, String duree) {
        when(trainingRepository.save(any(Training.class))).thenAnswer(i -> i.getArgument(0));
        TrainingDTO dto = TrainingDTO.builder()
                .title("Formation de test")
                .startDate(debut)
                .duration(duree)
                .availableSeats(10)
                .build();
        return service.createTraining(dto);
    }

    @Test
    @DisplayName("Formation de 3 jours commençant un lundi : fin le mercredi")
    void finCalculeeDepuisLaDuree() {
        LocalDate lundi = LocalDate.now().plusYears(1).with(java.time.DayOfWeek.MONDAY);

        TrainingDTO result = creer(lundi, "3 jours");

        assertThat(result.getEndDate()).isEqualTo(lundi.plusDays(2));
        assertThat(result.getStatus()).isEqualTo("Disponible");
    }

    @Test
    @DisplayName("Durée d'un jour : la formation finit le jour même")
    void dureeDUnJour() {
        LocalDate debut = LocalDate.now().plusMonths(2);

        assertThat(creer(debut, "1 jour").getEndDate()).isEqualTo(debut);
    }

    @Test
    @DisplayName("Durée sans nombre : la date de fin est la date de début")
    void dureeSansNombre() {
        LocalDate debut = LocalDate.now().plusMonths(2);

        assertThat(creer(debut, "à définir").getEndDate()).isEqualTo(debut);
    }

    @Test
    @DisplayName("Formation déjà passée : statut « Terminée »")
    void formationPassee() {
        LocalDate debut = LocalDate.now().minusMonths(1);

        assertThat(creer(debut, "2 jours").getStatus()).isEqualTo("Terminée");
    }
}