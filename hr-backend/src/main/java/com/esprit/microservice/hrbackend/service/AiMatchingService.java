package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.ia.MatchRequest;
import com.esprit.microservice.hrbackend.dto.ia.OffreIa;
import com.esprit.microservice.hrbackend.dto.ia.QuestionsRequest;
import com.esprit.microservice.hrbackend.entity.Candidate;
import com.esprit.microservice.hrbackend.entity.Interview;
import com.esprit.microservice.hrbackend.entity.InterviewStatus;
import com.esprit.microservice.hrbackend.entity.JobOffer;
import com.esprit.microservice.hrbackend.entity.StatutAnalyse;
import com.esprit.microservice.hrbackend.event.CandidatureSoumiseEvent;
import com.esprit.microservice.hrbackend.event.EntretienPlanifieEvent;
import com.esprit.microservice.hrbackend.repository.CandidateRepository;
import com.esprit.microservice.hrbackend.repository.InterviewRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import com.esprit.microservice.hrbackend.event.CandidateAiAnalysisCompletedEvent;
import com.esprit.microservice.hrbackend.event.CandidateAiAnalysisErrorEvent;
import com.esprit.microservice.hrbackend.event.InterviewAiQuestionsReadyEvent;

@Service
public class AiMatchingService {

    private static final Logger log = LoggerFactory.getLogger(AiMatchingService.class);

    private final RestClient aiRestClient;
    private final CandidateRepository candidateRepository;
    private final InterviewRepository interviewRepository;
    private final ObjectMapper objectMapper;
    private final Path uploadsRoot;
    private final ApplicationEventPublisher eventPublisher;

    public AiMatchingService(RestClient aiRestClient,
                             CandidateRepository candidateRepository,
                             InterviewRepository interviewRepository,
                             ObjectMapper objectMapper,
                             @Value("${app.upload-dir}") String uploadsDir,
                             ApplicationEventPublisher eventPublisher) {
        this.aiRestClient = aiRestClient;
        this.candidateRepository = candidateRepository;
        this.interviewRepository = interviewRepository;
        this.objectMapper = objectMapper;
        this.uploadsRoot = Paths.get(uploadsDir).toAbsolutePath().normalize();
        this.eventPublisher = eventPublisher;
    }

    // =====================================================================
    // 1. Analyse d'une candidature (/match)
    // =====================================================================

    /** Déclenché automatiquement une fois la candidature enregistrée en base. */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCandidatureSoumise(CandidatureSoumiseEvent event) {
        analyser(event.candidateId());
    }

    /** Bouton « Relancer l'analyse » de l'admin. */
    @Async
    @Transactional
    public void relancer(Long candidateId) {
        analyser(candidateId);
    }

    private void analyser(Long candidateId) {
        Candidate c = candidateRepository.findById(candidateId).orElse(null);
        if (c == null) {
            return;
        }
        if (c.getCvPath() == null || c.getCvPath().isBlank()) {
            enregistrerErreur(c, "Aucun CV associé à cette candidature");
            return;
        }
        try {
            String json = aiRestClient.post()
                    .uri("/match")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(construireRequete(c))
                    .retrieve()
                    .body(String.class);

            ObjectNode resultat = (ObjectNode) objectMapper.readTree(json);
            c.setAiScore(resultat.get("score").asDouble());
            c.setAiRecommendation(resultat.get("recommandation").asText());
            c.setCvText(texteOuNull(resultat, "texte_cv"));
            c.setMotivationLetterText(texteOuNull(resultat, "texte_lettre"));

            // les textes ont leurs propres colonnes : on ne les duplique pas dans le JSON
            resultat.remove(List.of("texte_cv", "texte_lettre"));
            c.setAiAnalysis(objectMapper.writeValueAsString(resultat));

            c.setAiError(null);
            c.setAiStatus(StatutAnalyse.TERMINEE);
            c.setAiAnalysisDate(LocalDateTime.now());
            candidateRepository.save(c);
            log.info("Candidat {} analysé : score {} ({})", candidateId, c.getAiScore(), c.getAiRecommendation());

            eventPublisher.publishEvent(new CandidateAiAnalysisCompletedEvent(candidateId));

        } catch (RestClientResponseException e) {
            // le microservice a répondu avec une erreur (401, 422...)
            enregistrerErreur(c, "Erreur " + e.getStatusCode().value() + " : " + e.getResponseBodyAsString());
        } catch (Exception e) {
            // microservice arrêté, délai dépassé, réponse illisible...
            log.error("Analyse IA impossible pour le candidat {}", candidateId, e);
            enregistrerErreur(c, "Erreur lors de l'appel au microservice IA : " + e.getMessage());
        }
    }

    private void enregistrerErreur(Candidate c, String message) {
        c.setAiError(message);
        c.setAiStatus(StatutAnalyse.ERREUR);
        c.setAiAnalysisDate(LocalDateTime.now());
        candidateRepository.save(c);
        log.warn("Analyse IA en erreur pour le candidat {} : {}", c.getId(), message);

        eventPublisher.publishEvent(new CandidateAiAnalysisErrorEvent(c.getId(), message));
    }

    // =====================================================================
    // 2. Questions d'entretien (/questions)
    // =====================================================================

    /** Déclenché après la planification d'un entretien (ou une demande de régénération). */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEntretienPlanifie(EntretienPlanifieEvent event) {
        Interview interview = interviewRepository.findById(event.interviewId()).orElse(null);
        if (interview == null) {
            return;
        }
        // Inutile de préparer des questions pour un entretien annulé
        if (interview.getStatus() == InterviewStatus.ANNULE) {
            log.info("Entretien {} annulé : génération des questions ignorée", interview.getId());
            return;
        }
        Candidate c = interview.getCandidate();
        try {
            // Le texte du CV a déjà été extrait lors de l'analyse : on l'envoie directement.
            // S'il manque (analyse en erreur), le microservice relit le fichier.
            QuestionsRequest requete = new QuestionsRequest(
                    c.getId(),
                    c.getCvText() == null ? cheminRelatif(c.getCvPath(), "cvs") : null,
                    c.getCvText(),
                    construireOffre(interview.getJobOffer())
            );
            String json = aiRestClient.post()
                    .uri("/questions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requete)
                    .retrieve()
                    .body(String.class);

            interview.setAiQuestions(json);
            interview.setAiQuestionsStatus(StatutAnalyse.TERMINEE);
            interview.setAiQuestionsError(null);
            interview.setAiQuestionsGeneratedAt(LocalDateTime.now());
            interviewRepository.save(interview);
            log.info("Questions d'entretien générées pour l'entretien {}", interview.getId());

            eventPublisher.publishEvent(new InterviewAiQuestionsReadyEvent(interview.getId()));

        } catch (RestClientResponseException e) {
            interview.setAiQuestionsStatus(StatutAnalyse.ERREUR);
            interview.setAiQuestionsError("Erreur " + e.getStatusCode().value() + " : " + e.getResponseBodyAsString());
            log.warn("Questions IA refusées pour l'entretien {} : {}", interview.getId(), interview.getAiQuestionsError());
        } catch (Exception e) {
            interview.setAiQuestionsStatus(StatutAnalyse.ERREUR);
            interview.setAiQuestionsError("Erreur lors de l'appel au microservice IA : " + e.getMessage());
            log.error("Questions IA impossibles pour l'entretien {}", interview.getId(), e);
        }
        interviewRepository.save(interview);
    }

    // =====================================================================
    // 3. Construction des requêtes envoyées au microservice
    // =====================================================================

    /** Utilisé à la fois par /match et par /questions. */
    private OffreIa construireOffre(JobOffer o) {
        return new OffreIa(
                o.getTitle(),
                o.getDescription(),
                typeOffre(o.getContractType()),
                enListe(o.getRequiredSkills()),
                experienceMin(o.getExperienceLevel()),
                o.getMinEducationLevel() == null ? 0 : o.getMinEducationLevel(),
                enListe(o.getRequiredLanguages())
        );
    }

    private MatchRequest construireRequete(Candidate c) {
        return new MatchRequest(
                c.getId(),
                cheminRelatif(c.getCvPath(), "cvs"),
                cheminRelatif(c.getMotivationLetterPath(), "motivation-letters"),
                construireOffre(c.getJobOffer())
        );
    }

    /** Un contrat de stage, PFE, internship ou alternance est traité comme un stage. */
    private String typeOffre(String contractType) {
        if (contractType == null) {
            return "EMPLOI";
        }
        String t = contractType.toLowerCase();
        boolean stage = t.contains("stage") || t.contains("intern") || t.contains("pfe")
                || t.contains("alternance") || t.contains("apprenti");
        return stage ? "STAGE" : "EMPLOI";
    }

    /** "2-3 ans" -> 2, "5 years" -> 5, "Senior" -> 5, "Confirmé" -> 2, "Junior" -> 0 */
    private double experienceMin(String experienceLevel) {
        if (experienceLevel == null || experienceLevel.isBlank()) {
            return 0;
        }
        Matcher m = Pattern.compile("(\\d+(?:[.,]\\d+)?)").matcher(experienceLevel);
        if (m.find()) {
            return Double.parseDouble(m.group(1).replace(',', '.'));
        }
        String e = experienceLevel.toLowerCase();
        if (e.contains("senior") || e.contains("expert") || e.contains("lead")) {
            return 5;
        }
        if (e.contains("confirm") || e.contains("mid") || e.contains("interm")) {
            return 2;
        }
        return 0;
    }

    /** "Java, Spring Boot; SQL" -> ["Java", "Spring Boot", "SQL"] */
    private List<String> enListe(String valeur) {
        if (valeur == null || valeur.isBlank()) {
            return List.of();
        }
        return Arrays.stream(valeur.split("[,;\\n]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Gère "cvs/nom.pdf", "nom.pdf" ou un chemin absolu situé dans le dossier uploads. */
    private String cheminRelatif(String valeur, String sousDossier) {
        if (valeur == null || valeur.isBlank()) {
            return null;
        }
        Path chemin = Paths.get(valeur);
        if (chemin.isAbsolute()) {
            return uploadsRoot.relativize(chemin.normalize()).toString().replace('\\', '/');
        }
        String v = valeur.replace('\\', '/');
        return v.contains("/") ? v : sousDossier + "/" + v;
    }

    private String texteOuNull(JsonNode noeud, String champ) {
        JsonNode valeur = noeud.get(champ);
        return (valeur == null || valeur.isNull()) ? null : valeur.asText();
    }
}