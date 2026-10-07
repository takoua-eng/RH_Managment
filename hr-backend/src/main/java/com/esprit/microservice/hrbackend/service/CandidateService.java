package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.CandidateRequestDTO;
import com.esprit.microservice.hrbackend.dto.CandidateResponseDTO;
import com.esprit.microservice.hrbackend.entity.Candidate;
import com.esprit.microservice.hrbackend.entity.CandidateStatus;
import com.esprit.microservice.hrbackend.entity.JobOffer;
import com.esprit.microservice.hrbackend.mapper.CandidateMapper;
import com.esprit.microservice.hrbackend.repository.CandidateRepository;
import com.esprit.microservice.hrbackend.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.esprit.microservice.hrbackend.event.CandidatureSoumiseEvent;
import org.springframework.context.ApplicationEventPublisher;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.esprit.microservice.hrbackend.dto.EmailAttachment;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDate;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CandidateService {


    private final CandidateRepository candidateRepository;
    private final JobOfferRepository jobOfferRepository;
    private final EmployeeRepository employeeRepository;
    private final EmailService emailService;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;

    public CandidateResponseDTO apply(CandidateRequestDTO requestDTO, MultipartFile cv, MultipartFile motivationLetter) {
        JobOffer jobOffer = jobOfferRepository.findById(requestDTO.getJobOfferId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job offer not found"));

        if (!"PUBLISHED".equals(jobOffer.getStatus().name())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cette offre d'emploi n'est pas ouverte aux candidatures");
        }

        if (jobOffer.getApplicationDeadline() != null && java.time.LocalDate.now().isAfter(jobOffer.getApplicationDeadline())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date limite pour postuler à cette offre est dépassée");
        }

        if (candidateRepository.existsByEmailAndJobOfferId(requestDTO.getEmail(), jobOffer.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vous avez déjà postulé à cette offre");
        }

        if (cv == null || cv.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le CV est obligatoire");
        }

        if (motivationLetter == null || motivationLetter.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lettre de motivation est obligatoire");
        }

        Candidate candidate = CandidateMapper.toEntity(requestDTO);
        candidate.setJobOffer(jobOffer);
        candidate.setStatus(CandidateStatus.NOUVELLE);

        candidate.setCvFileName(cv.getOriginalFilename());
        candidate.setCvContentType(cv.getContentType());
        candidate.setCvPath(fileStorageService.store(cv, "cvs"));

        candidate.setMotivationLetterFileName(motivationLetter.getOriginalFilename());
        candidate.setMotivationLetterContentType(motivationLetter.getContentType());
        candidate.setMotivationLetterPath(fileStorageService.store(motivationLetter, "motivation-letters"));

        Candidate savedCandidate = candidateRepository.save(candidate);

        // Analyse IA en arrière-plan, après l'enregistrement définitif en base
        eventPublisher.publishEvent(new CandidatureSoumiseEvent(savedCandidate.getId()));

        return CandidateMapper.toResponseDTO(savedCandidate);
    }

    public org.springframework.core.io.Resource getCvResource(Long id) {
        Candidate candidate = getCandidateEntity(id);
        if (candidate.getCvPath() == null || candidate.getCvPath().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No CV found for this candidate");
        }
        return fileStorageService.load(candidate.getCvPath());
    }

    public org.springframework.core.io.Resource getMotivationLetterResource(Long id) {
        Candidate candidate = getCandidateEntity(id);
        if (candidate.getMotivationLetterPath() == null || candidate.getMotivationLetterPath().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No motivation letter found for this candidate");
        }
        return fileStorageService.load(candidate.getMotivationLetterPath());
    }

    public CandidateResponseDTO updateStatus(Long id, CandidateStatus status) {
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));

        candidate.setStatus(status);
        if (status == CandidateStatus.ACCEPTEE || status == CandidateStatus.REFUSEE) {
            candidate.setDecisionDate(java.time.LocalDate.now());
        }
        Candidate updatedCandidate = candidateRepository.save(candidate);
        return CandidateMapper.toResponseDTO(updatedCandidate);
    }

    public CandidateResponseDTO transmitToManager(Long id, Long managerId) {
        // 1. Récupérer la candidature
        Candidate candidate = candidateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidature non trouvée avec l'ID: " + id));

        // 2. Récupérer l'offre
        JobOffer offer = candidate.getJobOffer();
        if (offer == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Offre d'emploi non trouvée pour cette candidature");
        }

        // 3. Récupérer le département
        String departmentName = offer.getDepartment() != null ? offer.getDepartment() : "Non spécifié";

        // 4. Récupérer le Manager responsable
        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Manager non trouvé avec l'ID: " + managerId));

        // 5. Enregistrer la transmission et le Manager assigné
        candidate.setAssignedManager(manager);
        candidate.setTransmissionDate(LocalDate.now());
        candidate.setStatus(CandidateStatus.TRANSMISE_MANAGER);
        Candidate updatedCandidate = candidateRepository.save(candidate);

        // 5 bis. Publier l'événement de transmission pour notification in-app du manager
        eventPublisher.publishEvent(new com.esprit.microservice.hrbackend.event.CandidatureTransmiseEvent(updatedCandidate.getId(), managerId));

        // 6. Préparer un email HTML professionnel
        String candidateFullName = candidate.getFirstName() + " " + candidate.getLastName();
        String managerFullName = manager.getFirstName() + " " + manager.getLastName();
        String subject = "Nouvelle candidature – " + offer.getTitle() + " – " + candidateFullName;
        String htmlContent = buildManagerTransmissionEmailHtml(candidate, offer, managerFullName, departmentName);

        // 7 & 8. Joindre le CV et la Lettre de motivation
        List<EmailAttachment> attachments = new ArrayList<>();
        if (candidate.getCvPath() != null && !candidate.getCvPath().isEmpty()) {
            try {
                byte[] cvBytes = fileStorageService.load(candidate.getCvPath()).getInputStream().readAllBytes();
                String cvName = candidate.getCvFileName() != null && !candidate.getCvFileName().isBlank() 
                        ? candidate.getCvFileName() : "CV_" + candidate.getLastName() + ".pdf";
                String cvType = candidate.getCvContentType() != null ? candidate.getCvContentType() : "application/pdf";
                attachments.add(new EmailAttachment(cvName, cvType, cvBytes));
            } catch (Exception e) {
                log.warn("Could not read CV bytes for attachment: {}", e.getMessage());
            }
        }
        if (candidate.getMotivationLetterPath() != null && !candidate.getMotivationLetterPath().isEmpty()) {
            try {
                byte[] mlBytes = fileStorageService.load(candidate.getMotivationLetterPath()).getInputStream().readAllBytes();
                String mlName = candidate.getMotivationLetterFileName() != null && !candidate.getMotivationLetterFileName().isBlank() 
                        ? candidate.getMotivationLetterFileName() : "Lettre_Motivation_" + candidate.getLastName() + ".pdf";
                String mlType = candidate.getMotivationLetterContentType() != null ? candidate.getMotivationLetterContentType() : "application/pdf";
                attachments.add(new EmailAttachment(mlName, mlType, mlBytes));
            } catch (Exception e) {
                log.warn("Could not read motivation letter bytes for attachment: {}", e.getMessage());
            }
        }

        // 9. Envoyer l'email au Manager (en cas d'erreur SMTP, enregistrer les logs sans bloquer la transmission)
        String managerEmail = manager.getEmail();
        if (managerEmail != null && managerEmail.contains("@")) {
            try {
                emailService.sendEmailWithAttachments(
                        managerEmail.trim(),
                        subject,
                        htmlContent,
                        true,
                        attachments
                );
            } catch (Exception e) {
                log.error("Échec de l'envoi de l'email de candidature au Manager [{}]: {}. La transmission a toutefois bien été enregistrée.", managerEmail, e.getMessage());
            }
        } else {
            log.warn("L'adresse email du Manager ({}) n'est pas configurée ou invalide. La candidature a été transmise dans l'application sans envoi d'email.", managerEmail);
        }

        return CandidateMapper.toResponseDTO(updatedCandidate);
    }

    private String buildManagerTransmissionEmailHtml(Candidate candidate, JobOffer offer, String managerName, String departmentName) {
        String candidateName = candidate.getFirstName() + " " + candidate.getLastName();
        String phone = candidate.getPhone() != null ? candidate.getPhone() : "Non renseigné";
        String education = candidate.getEducation() != null ? candidate.getEducation() : "Non renseignée";
        String experience = candidate.getExperience() != null ? candidate.getExperience() : "Non renseignée";
        String skills = candidate.getSkills() != null ? candidate.getSkills() : "Non renseignées";
        String appDate = candidate.getApplicationDate() != null ? candidate.getApplicationDate().toString() : LocalDate.now().toString();
        String offerSummary = offer.getDescription() != null ? offer.getDescription() : "Aucun descriptif disponible";

        return "<!DOCTYPE html>" +
                "<html>" +
                "<head><meta charset=\"UTF-8\">" +
                "<style>" +
                "  body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f4f6f9; color: #333; margin: 0; padding: 20px; }" +
                "  .container { max-width: 650px; background: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.08); margin: 0 auto; }" +
                "  .header { background-color: #0078d4; color: #ffffff; padding: 25px; text-align: left; }" +
                "  .header h2 { margin: 0; font-size: 20px; font-weight: 600; }" +
                "  .header p { margin: 5px 0 0 0; font-size: 14px; opacity: 0.9; }" +
                "  .content { padding: 25px; }" +
                "  .section-title { font-size: 15px; font-weight: 600; color: #0078d4; border-bottom: 2px solid #e1dfdd; padding-bottom: 6px; margin-top: 20px; margin-bottom: 15px; }" +
                "  .info-table { width: 100%; border-collapse: collapse; margin-bottom: 15px; }" +
                "  .info-table td { padding: 7px 5px; vertical-align: top; font-size: 14px; }" +
                "  .info-label { font-weight: 600; color: #555555; width: 170px; }" +
                "  .info-value { color: #222222; }" +
                "  .badge { display: inline-block; background-color: #eff6fc; color: #0078d4; padding: 4px 10px; border-radius: 4px; font-weight: 600; font-size: 13px; }" +
                "  .box { background-color: #f8f9fa; border-left: 4px solid #0078d4; padding: 15px; border-radius: 4px; margin-top: 10px; font-size: 14px; line-height: 1.5; white-space: pre-line; }" +
                "  .footer { background-color: #f3f2f1; padding: 15px 25px; font-size: 12px; color: #666666; text-align: center; border-top: 1px solid #e1dfdd; }" +
                "</style></head>" +
                "<body>" +
                "  <div class=\"container\">" +
                "    <div class=\"header\">" +
                "      <h2>Nouvelle candidature transmise</h2>" +
                "      <p>Portail RH & Management des Talents</p>" +
                "    </div>" +
                "    <div class=\"content\">" +
                "      <p>Bonjour <strong>" + escapeHtml(managerName) + "</strong>,</p>" +
                "      <p>Une nouvelle candidature a été transmise à votre attention pour évaluation concernant l'offre ci-dessous.</p>" +

                "      <div class=\"section-title\">Profil du Candidat</div>" +
                "      <table class=\"info-table\">" +
                "        <tr><td class=\"info-label\">Nom & Prénom :</td><td class=\"info-value\"><strong>" + escapeHtml(candidateName) + "</strong></td></tr>" +
                "        <tr><td class=\"info-label\">Email :</td><td class=\"info-value\"><a href=\"mailto:" + escapeHtml(candidate.getEmail()) + "\">" + escapeHtml(candidate.getEmail()) + "</a></td></tr>" +
                "        <tr><td class=\"info-label\">Téléphone :</td><td class=\"info-value\">" + escapeHtml(phone) + "</td></tr>" +
                "        <tr><td class=\"info-label\">Formation :</td><td class=\"info-value\">" + escapeHtml(education) + "</td></tr>" +
                "        <tr><td class=\"info-label\">Expérience :</td><td class=\"info-value\">" + escapeHtml(experience) + "</td></tr>" +
                "        <tr><td class=\"info-label\">Compétences :</td><td class=\"info-value\">" + escapeHtml(skills) + "</td></tr>" +
                "        <tr><td class=\"info-label\">Date de candidature :</td><td class=\"info-value\">" + escapeHtml(appDate) + "</td></tr>" +
                "      </table>" +

                "      <div class=\"section-title\">Offre d'Emploi Concernée</div>" +
                "      <table class=\"info-table\">" +
                "        <tr><td class=\"info-label\">Poste concerné :</td><td class=\"info-value\"><strong>" + escapeHtml(offer.getTitle()) + "</strong></td></tr>" +
                "        <tr><td class=\"info-label\">Département :</td><td class=\"info-value\"><span class=\"badge\">" + escapeHtml(departmentName) + "</span></td></tr>" +
                "      </table>" +

                "      <div class=\"section-title\">Résumé de l'Offre</div>" +
                "      <div class=\"box\">" + escapeHtml(offerSummary) + "</div>" +

                "      <p style=\"margin-top: 25px;\">📎 Le <strong>CV</strong> et la <strong>Lettre de Motivation</strong> du candidat sont joints à cet email pour votre étude.</p>" +
                "    </div>" +
                "    <div class=\"footer\">" +
                "      Cet email a été envoyé automatiquement par le Portail RH. Merci de ne pas y répondre directement." +
                "    </div>" +
                "  </div>" +
                "</body></html>";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }

    @Transactional(readOnly = true)
    public List<CandidateResponseDTO> getAllCandidates() {
        return candidateRepository.findAll().stream()
                .map(CandidateMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CandidateResponseDTO> getCandidatesByJobOffer(Long offerId) {
        return candidateRepository.findByJobOfferId(offerId).stream()
                .map(CandidateMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Candidate getCandidateEntity(Long id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
    }

    @Transactional(readOnly = true)
    public CandidateResponseDTO getCandidateById(Long id) {
        Candidate candidate = getCandidateEntity(id);
        return CandidateMapper.toResponseDTO(candidate);
    }

    public CandidateResponseDTO updateCandidate(Long id, CandidateRequestDTO requestDTO) {
        Candidate candidate = getCandidateEntity(id);

        if (requestDTO.getFirstName() != null) candidate.setFirstName(requestDTO.getFirstName());
        if (requestDTO.getLastName() != null) candidate.setLastName(requestDTO.getLastName());
        if (requestDTO.getEmail() != null) candidate.setEmail(requestDTO.getEmail());
        if (requestDTO.getPhone() != null) candidate.setPhone(requestDTO.getPhone());
        if (requestDTO.getAddress() != null) candidate.setAddress(requestDTO.getAddress());
        if (requestDTO.getEducation() != null) candidate.setEducation(requestDTO.getEducation());
        if (requestDTO.getExperience() != null) candidate.setExperience(requestDTO.getExperience());
        if (requestDTO.getSkills() != null) candidate.setSkills(requestDTO.getSkills());

        Candidate updatedCandidate = candidateRepository.save(candidate);
        return CandidateMapper.toResponseDTO(updatedCandidate);
    }

    @Transactional(readOnly = true)
    public List<CandidateResponseDTO> getCandidatesAssignedToManager(org.springframework.security.core.Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT Token");
        }

        String keycloakId = jwt.getSubject();
        List<Candidate> candidates = candidateRepository.findByAssignedManagerKeycloakId(keycloakId);
        
        // Secondary fallback: if assignedManager was matched by department
        Employee caller = employeeRepository.findByKeycloakId(keycloakId).orElse(null);
        if (caller != null && caller.getDepartment() != null && caller.getDepartment().getName() != null) {
            String deptName = caller.getDepartment().getName();
            List<Candidate> allCandidates = candidateRepository.findAll();
            for (Candidate c : allCandidates) {
                if (!candidates.contains(c) && c.getJobOffer() != null && deptName.equalsIgnoreCase(c.getJobOffer().getDepartment())) {
                    candidates.add(c);
                }
            }
        }

        return candidates.stream()
                .map(CandidateMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    public void verifyCandidateAccess(Candidate candidate, org.springframework.security.core.Authentication authentication) {
        if (authentication == null) return;

        boolean isAdminOrRh = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_RH"));

        if (isAdminOrRh) {
            return; // Admin & RH have full access
        }

        if (!(authentication.getPrincipal() instanceof org.springframework.security.oauth2.jwt.Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid JWT Token");
        }

        String keycloakId = jwt.getSubject();

        // Check assigned manager keycloak ID
        if (candidate.getAssignedManager() != null && keycloakId.equals(candidate.getAssignedManager().getKeycloakId())) {
            return;
        }

        // Check caller department vs candidate offer department
        Employee caller = employeeRepository.findByKeycloakId(keycloakId).orElse(null);
        if (caller != null && caller.getDepartment() != null && candidate.getJobOffer() != null) {
            if (caller.getDepartment().getName().equalsIgnoreCase(candidate.getJobOffer().getDepartment())) {
                return;
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : vous n'avez pas l'autorisation d'accéder aux candidatures d'un autre département ou non attribuées.");
    }
}
