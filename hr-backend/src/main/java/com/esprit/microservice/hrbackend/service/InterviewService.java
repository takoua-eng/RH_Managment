package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.InterviewFeedbackDTO;
import com.esprit.microservice.hrbackend.dto.InterviewNotesDTO;
import com.esprit.microservice.hrbackend.dto.InterviewRequestDTO;
import com.esprit.microservice.hrbackend.dto.InterviewResponseDTO;
import com.esprit.microservice.hrbackend.entity.*;
import com.esprit.microservice.hrbackend.event.EntretienPlanifieEvent;
import com.esprit.microservice.hrbackend.event.InterviewUpdatedEvent;
import com.esprit.microservice.hrbackend.event.InterviewCancelledEvent;
import com.esprit.microservice.hrbackend.event.InterviewFeedbackSubmittedEvent;
import com.esprit.microservice.hrbackend.exception.EmailException;
import com.esprit.microservice.hrbackend.mapper.InterviewMapper;
import com.esprit.microservice.hrbackend.repository.CandidateRepository;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.InterviewHistoryRepository;
import com.esprit.microservice.hrbackend.repository.InterviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final InterviewHistoryRepository interviewHistoryRepository;
    private final CandidateRepository candidateRepository;
    private final EmployeeRepository employeeRepository;
    private final CandidateService candidateService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public InterviewResponseDTO scheduleInterview(InterviewRequestDTO dto, Authentication authentication) {
        if (dto.getCandidateId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID candidat obligatoire");
        }

        // 1. Récupérer le candidat
        Candidate candidate = candidateRepository.findById(dto.getCandidateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidat non trouvé"));

        // 2. Vérifier que la candidature appartient au Manager (ou à son département)
        candidateService.verifyCandidateAccess(candidate, authentication);

        // 3. Récupérer le caller Manager (Employee)
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT token");
        }

        String keycloakId = jwt.getSubject();
        Employee manager = employeeRepository.findByKeycloakId(keycloakId)
                .orElseGet(() -> {
                    // Fallback to assigned manager if caller is Admin/RH
                    if (candidate.getAssignedManager() != null) {
                        return candidate.getAssignedManager();
                    }
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Manager non trouvé dans la base de données");
                });

        // 4. Vérifier que la candidature est éligible à un entretien
        if (candidate.getStatus() == CandidateStatus.ACCEPTEE || candidate.getStatus() == CandidateStatus.REFUSEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La candidature est déjà clôturée (" + candidate.getStatus() + "). Impossible de planifier un entretien.");
        }

        JobOffer offer = candidate.getJobOffer();
        if (offer == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune offre associée à cette candidature");
        }

        // 5. Créer l'entretien & associer (Candidat, Manager, Offre)
        Interview interview = Interview.builder()
                .candidate(candidate)
                .manager(manager)
                .jobOffer(offer)
                .interviewDate(dto.getInterviewDate())
                .interviewTime(dto.getInterviewTime())
                .durationMinutes(dto.getDurationMinutes())
                .type(dto.getType())
                .locationOrLink(dto.getLocationOrLink())
                .comment(dto.getComment())
                .status(InterviewStatus.PLANIFIE)
                .emailSent(false)
                .aiQuestionsStatus(StatutAnalyse.EN_ATTENTE)
                .build();

        Interview savedInterview = interviewRepository.save(interview);

        // 5 bis. Génération des questions IA en arrière-plan, après l'enregistrement définitif
        eventPublisher.publishEvent(new EntretienPlanifieEvent(savedInterview.getId()));
        String scheduledBy = authentication != null ? authentication.getName() : null;
        eventPublisher.publishEvent(new com.esprit.microservice.hrbackend.event.EntretienAttribueEvent(savedInterview.getId(), scheduledBy));

        // 6. Changer le statut candidature en ENTRETIEN_PLANIFIE
        candidate.setStatus(CandidateStatus.ENTRETIEN_PLANIFIE);
        candidateRepository.save(candidate);

        // 7. Envoyer automatiquement un email au candidat
        try {
            sendInterviewInvitationEmail(candidate, offer, manager, savedInterview);
            savedInterview.setEmailSent(true);
            savedInterview.setEmailSentAt(LocalDateTime.now());
            savedInterview = interviewRepository.save(savedInterview);
        } catch (Exception e) {
            log.error("Échec de l'envoi de l'email d'invitation à l'entretien au candidat [{}]: {}. L'entretien a toutefois bien été planifié.", candidate.getEmail(), e.getMessage());
            savedInterview.setEmailSent(false);
            savedInterview = interviewRepository.save(savedInterview);
        }

        return InterviewMapper.toResponseDTO(savedInterview);
    }

    public InterviewResponseDTO resendInterviewEmail(Long interviewId, Authentication authentication) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entretien non trouvé"));

        candidateService.verifyCandidateAccess(interview.getCandidate(), authentication);

        try {
            sendInterviewInvitationEmail(interview.getCandidate(), interview.getJobOffer(), interview.getManager(), interview);
            interview.setEmailSent(true);
            interview.setEmailSentAt(LocalDateTime.now());
            Interview updated = interviewRepository.save(interview);
            return InterviewMapper.toResponseDTO(updated);
        } catch (Exception e) {
            log.error("Échec de la réexpédition de l'email au candidat [{}]: {}", interview.getCandidate().getEmail(), e.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Échec de l'envoi de l'email au candidat (" + interview.getCandidate().getEmail() + "). Veuillez vérifier votre serveur SMTP. Erreur : " + e.getMessage()
            );
        }
    }

    public InterviewResponseDTO updateInterview(Long interviewId, InterviewRequestDTO dto, Authentication authentication) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entretien non trouvé"));

        // 1. Vérifier les accès du Manager
        candidateService.verifyCandidateAccess(interview.getCandidate(), authentication);

        // 2. Enregistrer l'ancien état dans l'historique (InterviewHistory)
        String managerName = interview.getManager() != null ? interview.getManager().getFirstName() + " " + interview.getManager().getLastName() : "Manager";
        InterviewHistory historyEntry = InterviewHistory.builder()
                .interviewId(interview.getId())
                .previousInterviewDate(interview.getInterviewDate())
                .previousInterviewTime(interview.getInterviewTime())
                .previousDurationMinutes(interview.getDurationMinutes())
                .previousType(interview.getType())
                .previousLocationOrLink(interview.getLocationOrLink())
                .previousComment(interview.getComment())
                .modifiedBy(managerName)
                .build();
        interviewHistoryRepository.save(historyEntry);

        // 3. Mettre à jour les champs de l'entretien
        interview.setInterviewDate(dto.getInterviewDate());
        interview.setInterviewTime(dto.getInterviewTime());
        interview.setDurationMinutes(dto.getDurationMinutes());
        interview.setType(dto.getType());
        interview.setLocationOrLink(dto.getLocationOrLink());
        interview.setComment(dto.getComment());
        interview.setModificationCount((interview.getModificationCount() != null ? interview.getModificationCount() : 0) + 1);
        interview.setUpdatedAt(LocalDateTime.now());

        Interview saved = interviewRepository.save(interview);

        // Publish event for notifications
        Long actorEmployeeId = getActorEmployeeId(authentication);
        eventPublisher.publishEvent(new InterviewUpdatedEvent(saved.getId(), actorEmployeeId));

        // 4. Envoyer automatiquement un nouvel email d'actualisation au candidat
        try {
            sendInterviewUpdateEmail(saved.getCandidate(), saved.getJobOffer(), saved.getManager(), saved);
            saved.setEmailSent(true);
            saved.setEmailSentAt(LocalDateTime.now());
            saved = interviewRepository.save(saved);
        } catch (Exception e) {
            log.error("Échec de l'envoi de l'email d'actualisation au candidat [{}]: {}. Les modifications ont été sauvegardées.", saved.getCandidate().getEmail(), e.getMessage());
            saved.setEmailSent(false);
            saved = interviewRepository.save(saved);
        }

        return InterviewMapper.toResponseDTO(saved);
    }

    public InterviewResponseDTO cancelInterview(Long interviewId, String reason, Authentication authentication) {
        // Trace pour identifier toute annulation inattendue
        log.warn("ANNULATION de l'entretien {} demandée par {} (motif : {})",
                interviewId, authentication != null ? authentication.getName() : "inconnu", reason);

        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entretien non trouvé"));

        candidateService.verifyCandidateAccess(interview.getCandidate(), authentication);

        interview.setStatus(InterviewStatus.ANNULE);
        interview.setCancelledAt(LocalDateTime.now());
        interview.setCancellationReason(reason);

        // Une génération de questions en attente n'a plus de sens pour un entretien annulé
        if (interview.getAiQuestionsStatus() == StatutAnalyse.EN_ATTENTE) {
            interview.setAiQuestionsStatus(null);
        }

        Interview saved = interviewRepository.save(interview);

        Long actorEmployeeId = getActorEmployeeId(authentication);
        eventPublisher.publishEvent(new InterviewCancelledEvent(saved.getId(), actorEmployeeId, reason));

        // Envoyer automatiquement l'email d'annulation au candidat
        try {
            sendInterviewCancellationEmail(saved.getCandidate(), saved.getJobOffer(), saved.getManager(), saved);
        } catch (Exception e) {
            log.error("Échec de l'envoi de l'email d'annulation au candidat [{}]: {}", saved.getCandidate().getEmail(), e.getMessage());
        }

        return InterviewMapper.toResponseDTO(saved);
    }

    /** Relance la génération des questions IA d'un entretien (erreur, ancien entretien, banque enrichie). */
    public void regenerateQuestions(Long interviewId, Authentication authentication) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entretien non trouvé"));

        candidateService.verifyCandidateAccess(interview.getCandidate(), authentication);

        if (interview.getStatus() == InterviewStatus.ANNULE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Impossible de générer des questions pour un entretien annulé");
        }

        interview.setAiQuestionsStatus(StatutAnalyse.EN_ATTENTE);
        interviewRepository.save(interview);
        eventPublisher.publishEvent(new EntretienPlanifieEvent(interviewId));
    }

    @Transactional(readOnly = true)
    public List<InterviewResponseDTO> getInterviewsByCandidate(Long candidateId, Authentication authentication) {
        Candidate candidate = candidateRepository.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidat non trouvé"));

        candidateService.verifyCandidateAccess(candidate, authentication);

        return interviewRepository.findByCandidateId(candidateId).stream()
                .map(InterviewMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<InterviewResponseDTO> getMyInterviews(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT token");
        }

        String keycloakId = jwt.getSubject();
        Employee manager = employeeRepository.findByKeycloakId(keycloakId).orElse(null);

        java.util.Set<Interview> interviews = new java.util.HashSet<>(interviewRepository.findByManagerKeycloakId(keycloakId));
        if (manager != null && manager.getId() != null) {
            interviews.addAll(interviewRepository.findByManagerId(manager.getId()));
        }

        return interviews.stream()
                .map(InterviewMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public InterviewResponseDTO getInterviewById(Long id, Authentication authentication) {
        Interview interview = interviewRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entretien non trouvé"));

        candidateService.verifyCandidateAccess(interview.getCandidate(), authentication);

        return InterviewMapper.toResponseDTO(interview);
    }

    public InterviewResponseDTO updateInterviewStatus(Long interviewId, InterviewStatus status, Authentication authentication) {
        // Trace pour identifier tout changement de statut inattendu
        log.warn("Changement de statut de l'entretien {} vers {} demandé par {}",
                interviewId, status, authentication != null ? authentication.getName() : "inconnu");

        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entretien non trouvé"));

        candidateService.verifyCandidateAccess(interview.getCandidate(), authentication);

        interview.setStatus(status);
        Interview updated = interviewRepository.save(interview);
        return InterviewMapper.toResponseDTO(updated);
    }

    public InterviewResponseDTO saveNotes(Long interviewId, InterviewNotesDTO dto, Authentication authentication) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entretien non trouvé"));

        candidateService.verifyCandidateAccess(interview.getCandidate(), authentication);

        if (interview.getStatus() == InterviewStatus.ANNULE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible d'enregistrer des notes pour un entretien annulé.");
        }

        try {
            String notesJson = objectMapper.writeValueAsString(dto);
            interview.setInterviewNotes(notesJson);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Erreur lors de la sérialisation des notes en JSON : " + e.getMessage());
        }

        Interview saved = interviewRepository.save(interview);
        return InterviewMapper.toResponseDTO(saved);
    }

    public InterviewResponseDTO submitFeedback(Long interviewId, InterviewFeedbackDTO dto, Authentication authentication) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entretien non trouvé"));

        Candidate candidate = interview.getCandidate();
        candidateService.verifyCandidateAccess(candidate, authentication);

        if (interview.getStatus() == InterviewStatus.ANNULE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible d'enregistrer un avis pour un entretien annulé.");
        }

        if (candidate.getStatus() == CandidateStatus.ACCEPTEE || candidate.getStatus() == CandidateStatus.REFUSEE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La décision RH a déjà été prise pour cette candidature. L'avis ne peut plus être modifié.");
        }

        interview.setManagerRecommendation(dto.getRecommendation());
        interview.setManagerRating(dto.getRating());
        interview.setManagerFeedback(dto.getComment());
        interview.setFeedbackSubmittedAt(LocalDateTime.now());
        interview.setStatus(InterviewStatus.TERMINE);

        // Passer la candidature à ENTRETIEN_TERMINE
        candidate.setStatus(CandidateStatus.ENTRETIEN_TERMINE);
        candidateRepository.save(candidate);

        Interview saved = interviewRepository.save(interview);

        // Publish event for notifications
        Long managerId = interview.getManager() != null ? interview.getManager().getId() : null;
        eventPublisher.publishEvent(new InterviewFeedbackSubmittedEvent(saved.getId(), managerId));

        return InterviewMapper.toResponseDTO(saved);
    }

    private Long getActorEmployeeId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String keycloakId = jwt.getSubject();
            return employeeRepository.findByKeycloakId(keycloakId).map(Employee::getId).orElse(null);
        }
        return null;
    }

    private void sendInterviewInvitationEmail(Candidate candidate, JobOffer offer, Employee manager, Interview interview) {
        if (candidate.getEmail() == null || candidate.getEmail().isBlank()) {
            throw new EmailException("L'adresse email du candidat est vide ou invalide.");
        }

        String subject = "Entretien – " + offer.getTitle();
        String candidateFullName = candidate.getFirstName() + " " + candidate.getLastName();
        String managerFullName = manager.getFirstName() + " " + manager.getLastName();
        String formattedDate = interview.getInterviewDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String typeLabel = switch (interview.getType()) {
            case PRESENTIEL -> "Présentiel";
            case VISIO -> "Visioconférence";
            case TELEPHONE -> "Téléphonique";
        };

        String locationLinkStr = (interview.getLocationOrLink() != null && !interview.getLocationOrLink().isBlank())
                ? interview.getLocationOrLink() : "Non spécifié";
        String commentStr = (interview.getComment() != null && !interview.getComment().isBlank())
                ? interview.getComment() : "Aucune";
        String deptStr = (offer.getDepartment() != null && !offer.getDepartment().isBlank())
                ? offer.getDepartment() : "Non spécifié";

        String htmlBody = "<!DOCTYPE html>" +
                "<html><head><meta charset=\"UTF-8\">" +
                "<style>" +
                "  body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f6f9; color: #333; margin: 0; padding: 20px; }" +
                "  .container { max-width: 600px; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.08); margin: 0 auto; }" +
                "  .header { background-color: #0078d4; color: #ffffff; padding: 20px 25px; text-align: left; }" +
                "  .header h2 { margin: 0; font-size: 20px; font-weight: 600; }" +
                "  .content { padding: 25px; font-size: 14px; line-height: 1.6; color: #333; }" +
                "  .details-box { background-color: #f8f9fa; border-left: 4px solid #0078d4; padding: 18px; border-radius: 4px; margin: 20px 0; }" +
                "  .details-row { margin-bottom: 8px; font-size: 14px; }" +
                "  .details-label { font-weight: 600; color: #555555; display: inline-block; width: 210px; }" +
                "  .footer { padding-top: 15px; border-top: 1px solid #eee; margin-top: 25px; color: #555; }" +
                "</style></head>" +
                "<body>" +
                "  <div class=\"container\">" +
                "    <div class=\"header\">" +
                "      <h2>Convocation à un entretien d'embauche</h2>" +
                "    </div>" +
                "    <div class=\"content\">" +
                "      <p>Bonjour <strong>" + escapeHtml(candidateFullName) + "</strong>,</p>" +
                "      <p>Nous avons le plaisir de vous informer que votre candidature pour le poste de <strong>" + escapeHtml(offer.getTitle()) + "</strong> a été retenue pour un entretien.</p>" +
                "      " +
                "      <p><strong>Détails :</strong></p>" +
                "      " +
                "      <div class=\"details-box\">" +
                "        <div class=\"details-row\"><span class=\"details-label\">Poste :</span> <strong>" + escapeHtml(offer.getTitle()) + "</strong></div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Département :</span> " + escapeHtml(deptStr) + "</div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Date :</span> <strong>" + escapeHtml(formattedDate) + "</strong></div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Heure :</span> <strong>" + escapeHtml(interview.getInterviewTime()) + "</strong></div>" +
                "        <div class=\"info-row\"><span class=\"details-label\">Durée :</span> " + interview.getDurationMinutes() + " minutes</div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Type :</span> " + escapeHtml(typeLabel) + "</div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Lieu / lien :</span> " + escapeHtml(locationLinkStr) + "</div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Interviewer :</span> " + escapeHtml(managerFullName) + "</div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Informations complémentaires :</span> " + escapeHtml(commentStr) + "</div>" +
                "      </div>" +
                "      " +
                "      <div class=\"footer\">" +
                "        <p>Cordialement,<br><strong>Équipe RH</strong></p>" +
                "      </div>" +
                "    </div>" +
                "  </div>" +
                "</body></html>";

        emailService.sendHtmlEmail(candidate.getEmail(), subject, htmlBody);
    }

    private void sendInterviewUpdateEmail(Candidate candidate, JobOffer offer, Employee manager, Interview interview) {
        if (candidate.getEmail() == null || candidate.getEmail().isBlank()) {
            throw new EmailException("L'adresse email du candidat est vide ou invalide.");
        }

        String subject = "Modification d'entretien – " + offer.getTitle();
        String candidateFullName = candidate.getFirstName() + " " + candidate.getLastName();
        String managerFullName = manager.getFirstName() + " " + manager.getLastName();
        String formattedDate = interview.getInterviewDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String typeLabel = switch (interview.getType()) {
            case PRESENTIEL -> "Présentiel";
            case VISIO -> "Visioconférence";
            case TELEPHONE -> "Téléphonique";
        };

        String locationLinkStr = (interview.getLocationOrLink() != null && !interview.getLocationOrLink().isBlank())
                ? interview.getLocationOrLink() : "Non spécifié";
        String commentStr = (interview.getComment() != null && !interview.getComment().isBlank())
                ? interview.getComment() : "Aucune";
        String deptStr = (offer.getDepartment() != null && !offer.getDepartment().isBlank())
                ? offer.getDepartment() : "Non spécifié";

        String htmlBody = "<!DOCTYPE html>" +
                "<html><head><meta charset=\"UTF-8\">" +
                "<style>" +
                "  body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f6f9; color: #333; margin: 0; padding: 20px; }" +
                "  .container { max-width: 600px; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.08); margin: 0 auto; }" +
                "  .header { background-color: #d97706; color: #ffffff; padding: 20px 25px; text-align: left; }" +
                "  .header h2 { margin: 0; font-size: 20px; font-weight: 600; }" +
                "  .content { padding: 25px; font-size: 14px; line-height: 1.6; color: #333; }" +
                "  .alert-box { background-color: #fffbebfb; border-left: 4px solid #d97706; padding: 12px 15px; border-radius: 4px; margin-bottom: 20px; font-weight: 600; color: #92400e; }" +
                "  .details-box { background-color: #f8f9fa; border-left: 4px solid #0078d4; padding: 18px; border-radius: 4px; margin: 20px 0; }" +
                "  .details-row { margin-bottom: 8px; font-size: 14px; }" +
                "  .details-label { font-weight: 600; color: #555555; display: inline-block; width: 210px; }" +
                "  .footer { padding-top: 15px; border-top: 1px solid #eee; margin-top: 25px; color: #555; }" +
                "</style></head>" +
                "<body>" +
                "  <div class=\"container\">" +
                "    <div class=\"header\">" +
                "      <h2>Mise à jour d'entretien d'embauche</h2>" +
                "    </div>" +
                "    <div class=\"content\">" +
                "      <p>Bonjour <strong>" + escapeHtml(candidateFullName) + "</strong>,</p>" +
                "      <div class=\"alert-box\">⚠️ Les informations de votre entretien pour le poste de " + escapeHtml(offer.getTitle()) + " ont été modifiées.</div>" +
                "      <p>Voici les nouvelles informations de votre entretien :</p>" +
                "      " +
                "      <p><strong>Nouvelles informations :</strong></p>" +
                "      " +
                "      <div class=\"details-box\">" +
                "        <div class=\"details-row\"><span class=\"details-label\">Poste :</span> <strong>" + escapeHtml(offer.getTitle()) + "</strong></div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Département :</span> " + escapeHtml(deptStr) + "</div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Date :</span> <strong>" + escapeHtml(formattedDate) + "</strong></div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Heure :</span> <strong>" + escapeHtml(interview.getInterviewTime()) + "</strong></div>" +
                "        <div class=\"info-row\"><span class=\"details-label\">Durée :</span> " + interview.getDurationMinutes() + " minutes</div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Type :</span> " + escapeHtml(typeLabel) + "</div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Lieu / lien :</span> " + escapeHtml(locationLinkStr) + "</div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Interviewer :</span> " + escapeHtml(managerFullName) + "</div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Informations complémentaires :</span> " + escapeHtml(commentStr) + "</div>" +
                "      </div>" +
                "      " +
                "      <div class=\"footer\">" +
                "        <p>Cordialement,<br><strong>Équipe RH</strong></p>" +
                "      </div>" +
                "    </div>" +
                "  </div>" +
                "</body></html>";

        emailService.sendHtmlEmail(candidate.getEmail(), subject, htmlBody);
    }

    private void sendInterviewCancellationEmail(Candidate candidate, JobOffer offer, Employee manager, Interview interview) {
        if (candidate.getEmail() == null || candidate.getEmail().isBlank()) {
            return;
        }

        String subject = "Annulation d'entretien – " + offer.getTitle();
        String candidateFullName = candidate.getFirstName() + " " + candidate.getLastName();
        String formattedDate = interview.getInterviewDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String reasonStr = (interview.getCancellationReason() != null && !interview.getCancellationReason().isBlank())
                ? interview.getCancellationReason() : "Non spécifié";

        String htmlBody = "<!DOCTYPE html>" +
                "<html><head><meta charset=\"UTF-8\">" +
                "<style>" +
                "  body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f6f9; color: #333; margin: 0; padding: 20px; }" +
                "  .container { max-width: 600px; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.08); margin: 0 auto; }" +
                "  .header { background-color: #d9381e; color: #ffffff; padding: 20px 25px; text-align: left; }" +
                "  .header h2 { margin: 0; font-size: 20px; font-weight: 600; }" +
                "  .content { padding: 25px; font-size: 14px; line-height: 1.6; color: #333; }" +
                "  .cancel-box { background-color: #fdf2f2; border-left: 4px solid #d9381e; padding: 15px; border-radius: 4px; margin: 20px 0; }" +
                "  .details-row { margin-bottom: 8px; font-size: 14px; }" +
                "  .details-label { font-weight: 600; color: #555555; display: inline-block; width: 180px; }" +
                "  .footer { padding-top: 15px; border-top: 1px solid #eee; margin-top: 25px; color: #555; }" +
                "</style></head>" +
                "<body>" +
                "  <div class=\"container\">" +
                "    <div class=\"header\">" +
                "      <h2>Annulation d'entretien</h2>" +
                "    </div>" +
                "    <div class=\"content\">" +
                "      <p>Bonjour <strong>" + escapeHtml(candidateFullName) + "</strong>,</p>" +
                "      <p>Nous sommes au regret de vous informer que votre entretien initialement prévu pour le poste de <strong>" + escapeHtml(offer.getTitle()) + "</strong> a été annulé.</p>" +
                "      " +
                "      <div class=\"cancel-box\">" +
                "        <div class=\"details-row\"><span class=\"details-label\">Date d'origine :</span> " + escapeHtml(formattedDate) + " à " + escapeHtml(interview.getInterviewTime()) + "</div>" +
                "        <div class=\"details-row\"><span class=\"details-label\">Motif d'annulation :</span> <em>" + escapeHtml(reasonStr) + "</em></div>" +
                "      </div>" +
                "      " +
                "      <p>Nous vous remercions de l'intérêt porté à notre entreprise et vous contacterons si une nouvelle date devait être planifiée.</p>" +
                "      " +
                "      <div class=\"footer\">" +
                "        <p>Cordialement,<br><strong>Équipe RH</strong></p>" +
                "      </div>" +
                "    </div>" +
                "  </div>" +
                "</body></html>";

        emailService.sendHtmlEmail(candidate.getEmail(), subject, htmlBody);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}