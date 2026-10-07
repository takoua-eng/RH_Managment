package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.TrainingDTO;
import com.esprit.microservice.hrbackend.dto.TrainingEnrollmentDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Training;
import com.esprit.microservice.hrbackend.entity.TrainingEnrollment;
import com.esprit.microservice.hrbackend.exception.EmployeeNotFoundException;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.TrainingEnrollmentRepository;
import com.esprit.microservice.hrbackend.repository.TrainingRepository;
import com.esprit.microservice.hrbackend.entity.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import com.esprit.microservice.hrbackend.event.TrainingAssignedEvent;
import com.esprit.microservice.hrbackend.event.TrainingCompletedEvent;

@Service
@RequiredArgsConstructor
public class TrainingService {

    private final TrainingRepository trainingRepository;
    private final TrainingEnrollmentRepository trainingEnrollmentRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void autoUpdateExpiredTrainings() {
        LocalDate today = LocalDate.now();
        List<Training> trainings = trainingRepository.findAll();
        boolean changed = false;

        for (Training t : trainings) {
            if ("Annulée".equalsIgnoreCase(t.getStatus())) {
                continue;
            }
            LocalDate startDate = t.getStartDate();
            LocalDate endDate = t.getEndDate();
            if (endDate == null && startDate != null) {
                endDate = calculateEndDateFromDuration(startDate, t.getDuration());
                if (endDate != null) {
                    t.setEndDate(endDate);
                    changed = true;
                }
            }

            String targetStatus = null;
            if (endDate != null && endDate.isBefore(today)) {
                targetStatus = "Terminée";
            } else if (startDate != null && endDate != null && !today.isBefore(startDate) && !today.isAfter(endDate)) {
                targetStatus = "En cours";
            }

            if (targetStatus != null && !targetStatus.equalsIgnoreCase(t.getStatus())) {
                t.setStatus(targetStatus);
                changed = true;
            }

            // Update enrollments status
            List<TrainingEnrollment> enrollments = trainingEnrollmentRepository.findByTrainingId(t.getId());
            for (TrainingEnrollment e : enrollments) {
                if ("Terminée".equalsIgnoreCase(targetStatus)) {
                    if (!"Terminée".equalsIgnoreCase(e.getStatus())) {
                        e.setStatus("Terminée");
                        if (e.getProgression() < 100) {
                            e.setProgression(100);
                        }
                        trainingEnrollmentRepository.save(e);
                    }
                } else if ("En cours".equalsIgnoreCase(targetStatus)) {
                    if (!"Terminée".equalsIgnoreCase(e.getStatus()) && !"En cours".equalsIgnoreCase(e.getStatus())) {
                        e.setStatus("En cours");
                        trainingEnrollmentRepository.save(e);
                    }
                }
            }
        }

        if (changed) {
            trainingRepository.saveAll(trainings);
        }
    }

    private LocalDate calculateEndDateFromDuration(LocalDate startDate, String duration) {
        if (startDate == null) return null;
        if (duration == null || duration.isBlank()) return startDate;
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(duration);
            if (matcher.find()) {
                int days = Integer.parseInt(matcher.group(1));
                if (days > 0) {
                    return startDate.plusDays(days - 1);
                }
            }
        } catch (Exception ignored) {}
        return startDate;
    }

    private String determineDynamicStatus(LocalDate startDate, LocalDate endDate, String duration, String currentStatus) {
        if ("Annulée".equalsIgnoreCase(currentStatus)) return "Annulée";
        LocalDate today = LocalDate.now();
        LocalDate effectiveEndDate = endDate;
        if (effectiveEndDate == null && startDate != null) {
            effectiveEndDate = calculateEndDateFromDuration(startDate, duration);
        }

        if (effectiveEndDate != null && effectiveEndDate.isBefore(today)) {
            return "Terminée";
        }
        if (startDate != null && effectiveEndDate != null && !today.isBefore(startDate) && !today.isAfter(effectiveEndDate)) {
            return "En cours";
        }
        return currentStatus != null ? currentStatus : "Disponible";
    }

    @Transactional
    public List<TrainingDTO> getAllTrainings() {
        autoUpdateExpiredTrainings();
        return trainingRepository.findAll().stream()
                .map(this::mapToTrainingDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<TrainingEnrollmentDTO> getEnrollmentsByEmployee(Long employeeId) {
        autoUpdateExpiredTrainings();
        return trainingEnrollmentRepository.findByEmployeeId(employeeId).stream()
                .map(this::mapToEnrollmentDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<TrainingEnrollmentDTO> getTeamEnrollments(Long managerId) {
        autoUpdateExpiredTrainings();
        List<Long> employeeIds = employeeRepository.findByManagerId(managerId).stream()
                .map(Employee::getId)
                .collect(Collectors.toList());
                
        if (employeeIds.isEmpty()) {
            return List.of();
        }

        return trainingEnrollmentRepository.findByEmployeeIdIn(employeeIds).stream()
                .map(this::mapToEnrollmentDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public TrainingEnrollmentDTO enroll(Long employeeId, Long trainingId) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + employeeId));

        Training training = trainingRepository.findById(trainingId)
                .orElseThrow(() -> new RuntimeException("Training not found with id: " + trainingId));

        if (!"Disponible".equals(training.getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "La formation n'est plus disponible.");
        }

        if (training.getAvailableSeats() == null || training.getAvailableSeats() <= 0) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Il ne reste plus de place pour cette formation.");
        }

        Optional<TrainingEnrollment> existing = trainingEnrollmentRepository.findByEmployeeIdAndTrainingId(employeeId, trainingId);
        if (existing.isPresent()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Vous êtes déjà inscrit à cette formation.");
        }

        training.setAvailableSeats(training.getAvailableSeats() - 1);
        if (training.getAvailableSeats() == 0) {
            training.setStatus("Terminée");
        }
        trainingRepository.save(training);

        TrainingEnrollment enrollment = TrainingEnrollment.builder()
                .employee(employee)
                .training(training)
                .enrollmentDate(LocalDate.now())
                .status("INSCRIT")
                .progression(0)
                .build();

        TrainingEnrollment saved = trainingEnrollmentRepository.save(enrollment);
        
        eventPublisher.publishEvent(new TrainingAssignedEvent(trainingId, employeeId, null));
        
        return mapToEnrollmentDTO(saved);
    }

    @Transactional
    public TrainingEnrollmentDTO incrementProgress(Long enrollmentId) {
        TrainingEnrollment enrollment = trainingEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Enrollment not found with id: " + enrollmentId));

        if (enrollment.getProgression() >= 100) {
            return mapToEnrollmentDTO(enrollment);
        }

        int newProgress = enrollment.getProgression() + 15;
        if (newProgress >= 100) {
            newProgress = 100;
            enrollment.setStatus("Terminée");
            
            eventPublisher.publishEvent(new TrainingCompletedEvent(enrollment.getTraining().getId(), enrollment.getEmployee().getId()));
            
            // Generate mock PDF certificate
            String certText = "%PDF-1.4\n" +
                    "1 0 obj\n" +
                    "<< /Type /Catalog /Pages 2 0 R >>\n" +
                    "endobj\n" +
                    "2 0 obj\n" +
                    "<< /Type /Pages /Kids [3 0 R] /Count 1 >>\n" +
                    "endobj\n" +
                    "3 0 obj\n" +
                    "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R >>\n" +
                    "endobj\n" +
                    "4 0 obj\n" +
                    "<< /Length 150 >>\n" +
                    "stream\n" +
                    "BT\n" +
                    "/F1 24 Tf\n" +
                    "100 700 Td (ATTESTATION DE REUSSITE DE FORMATION) Tj\n" +
                    "/F1 14 Tf\n" +
                    "0 -50 Td (Certificat decerne a : " + enrollment.getEmployee().getFirstName() + " " + enrollment.getEmployee().getLastName() + ") Tj\n" +
                    "0 -30 Td (Pour avoir complete avec succes la formation : " + enrollment.getTraining().getTitle() + ") Tj\n" +
                    "0 -30 Td (Formateur : " + enrollment.getTraining().getTrainer() + ") Tj\n" +
                    "0 -30 Td (Date : " + LocalDate.now() + ") Tj\n" +
                    "ET\n" +
                    "endstream\n" +
                    "endobj\n" +
                    "xref\n" +
                    "0 5\n" +
                    "0000000000 65535 f\n" +
                    "trail\n" +
                    "%%EOF";
            String certPath = fileStorageService.storeBytes(certText.getBytes(), "certificate.pdf", "certificates");
            enrollment.setCertificatePath(certPath);
        } else {
            enrollment.setStatus("En cours");
        }

        enrollment.setProgression(newProgress);
        TrainingEnrollment saved = trainingEnrollmentRepository.save(enrollment);
        return mapToEnrollmentDTO(saved);
    }

    @Transactional(readOnly = true)
    public org.springframework.core.io.Resource getCertificateResource(Long enrollmentId) {
        TrainingEnrollment enrollment = trainingEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Enrollment not found with id: " + enrollmentId));

        if (enrollment.getCertificatePath() == null || enrollment.getCertificatePath().isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "No certificate available for this enrollment");
        }

        return fileStorageService.load(enrollment.getCertificatePath());
    }

    @Transactional(readOnly = true)
    public TrainingEnrollment getEnrollmentEntityById(Long id) {
        return trainingEnrollmentRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Enrollment not found with id: " + id));
    }

    private TrainingDTO mapToTrainingDTO(Training t) {
        if (t == null) return null;
        LocalDate endDate = t.getEndDate();
        if (endDate == null && t.getStartDate() != null) {
            endDate = calculateEndDateFromDuration(t.getStartDate(), t.getDuration());
        }

        String status = determineDynamicStatus(t.getStartDate(), endDate, t.getDuration(), t.getStatus());

        return TrainingDTO.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .trainer(t.getTrainer())
                .startDate(t.getStartDate())
                .duration(t.getDuration())
                .location(t.getLocation())
                .category(t.getCategory())
                .level(t.getLevel())
                .endDate(endDate != null ? endDate : t.getEndDate())
                .availableSeats(t.getAvailableSeats())
                .mode(t.getMode())
                .objectives(t.getObjectives())
                .status(status)
                .syllabus(t.getSyllabus())
                .build();
    }

    private TrainingEnrollmentDTO mapToEnrollmentDTO(TrainingEnrollment e) {
        if (e == null) return null;
        TrainingDTO trainingDTO = mapToTrainingDTO(e.getTraining());
        String status = e.getStatus();
        if (trainingDTO != null) {
            if ("Terminée".equalsIgnoreCase(trainingDTO.getStatus())) {
                status = "Terminée";
            } else if ("En cours".equalsIgnoreCase(trainingDTO.getStatus()) && !"Terminée".equalsIgnoreCase(status)) {
                status = "En cours";
            }
        }

        return TrainingEnrollmentDTO.builder()
                .id(e.getId())
                .employeeId(e.getEmployee().getId())
                .employeeName(e.getEmployee().getFirstName() + " " + e.getEmployee().getLastName())
                .employeePosition(e.getEmployee().getPosition())
                .training(trainingDTO)
                .enrollmentDate(e.getEnrollmentDate())
                .status(status)
                .progression("Terminée".equalsIgnoreCase(status) ? Math.max(e.getProgression(), 100) : e.getProgression())
                .hasCertificate(e.getCertificatePath() != null && !e.getCertificatePath().isEmpty())
                .build();
    }

    @Transactional
    public TrainingDTO createTraining(TrainingDTO trainingDTO) {
        LocalDate endDate = trainingDTO.getEndDate();
        if (endDate == null && trainingDTO.getStartDate() != null) {
            endDate = calculateEndDateFromDuration(trainingDTO.getStartDate(), trainingDTO.getDuration());
        }

        String status = determineDynamicStatus(trainingDTO.getStartDate(), endDate, trainingDTO.getDuration(), trainingDTO.getStatus() != null ? trainingDTO.getStatus() : "Disponible");

        Training training = Training.builder()
                .title(trainingDTO.getTitle())
                .description(trainingDTO.getDescription())
                .trainer(trainingDTO.getTrainer())
                .startDate(trainingDTO.getStartDate())
                .duration(trainingDTO.getDuration())
                .location(trainingDTO.getLocation())
                .category(trainingDTO.getCategory())
                .level(trainingDTO.getLevel())
                .endDate(endDate)
                .availableSeats(trainingDTO.getAvailableSeats())
                .mode(trainingDTO.getMode())
                .objectives(trainingDTO.getObjectives())
                .status(status)
                .syllabus(trainingDTO.getSyllabus())
                .build();
        
        Training saved = trainingRepository.save(training);
        return mapToTrainingDTO(saved);
    }

    @Transactional
    public TrainingDTO updateTraining(Long id, TrainingDTO trainingDTO) {
        Training training = trainingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Training not found with id: " + id));
        
        LocalDate endDate = trainingDTO.getEndDate();
        if (endDate == null && trainingDTO.getStartDate() != null) {
            endDate = calculateEndDateFromDuration(trainingDTO.getStartDate(), trainingDTO.getDuration());
        }

        String status = determineDynamicStatus(trainingDTO.getStartDate(), endDate, trainingDTO.getDuration(), trainingDTO.getStatus());

        training.setTitle(trainingDTO.getTitle());
        training.setDescription(trainingDTO.getDescription());
        training.setTrainer(trainingDTO.getTrainer());
        training.setStartDate(trainingDTO.getStartDate());
        training.setDuration(trainingDTO.getDuration());
        training.setLocation(trainingDTO.getLocation());
        training.setCategory(trainingDTO.getCategory());
        training.setLevel(trainingDTO.getLevel());
        training.setEndDate(endDate);
        training.setAvailableSeats(trainingDTO.getAvailableSeats());
        training.setMode(trainingDTO.getMode());
        training.setObjectives(trainingDTO.getObjectives());
        training.setStatus(status);
        training.setSyllabus(trainingDTO.getSyllabus());
        
        Training updated = trainingRepository.save(training);
        return mapToTrainingDTO(updated);
    }

    @Transactional
    public void deleteTraining(Long id) {
        if (!trainingRepository.existsById(id)) {
            throw new RuntimeException("Training not found with id: " + id);
        }
        trainingRepository.deleteById(id);
    }
}
