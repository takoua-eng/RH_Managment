package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.EvaluationDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Evaluation;
import com.esprit.microservice.hrbackend.mapper.EvaluationMapper;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.EvaluationRepository;
import com.esprit.microservice.hrbackend.entity.NotificationType;
import com.esprit.microservice.hrbackend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final EvaluationRepository evaluationRepository;
    private final EmployeeRepository employeeRepository;
    private final NotificationService notificationService;

    @Transactional
    public EvaluationDTO createEvaluation(EvaluationDTO dto) {
        if (!employeeRepository.existsById(dto.getEmployeeId())) {
            throw new IllegalArgumentException("Employé introuvable avec l'id : " + dto.getEmployeeId());
        }
        if (!employeeRepository.existsById(dto.getManagerId())) {
            throw new IllegalArgumentException("Manager introuvable avec l'id : " + dto.getManagerId());
        }

        Evaluation evaluation = EvaluationMapper.toEntity(dto);
        if (evaluation.getDate() == null) {
            evaluation.setDate(LocalDate.now());
        }
        
        Evaluation saved = evaluationRepository.save(evaluation);
        
        notificationService.notifyManager(dto.getEmployeeId(), "Vous avez reçu une nouvelle évaluation de votre manager.", NotificationType.EVALUATION);
        
        return convertToDTOWithName(saved);
    }

    @Transactional(readOnly = true)
    public List<EvaluationDTO> getEvaluationsByManager(Long managerId) {
        return evaluationRepository.findByManagerId(managerId).stream()
                .map(this::convertToDTOWithName)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EvaluationDTO> getEvaluationsByEmployee(Long employeeId) {
        return evaluationRepository.findByEmployeeId(employeeId).stream()
                .map(this::convertToDTOWithName)
                .collect(Collectors.toList());
    }

    @Transactional
    public EvaluationDTO updateEvaluation(Long id, EvaluationDTO dto) {
        Evaluation evaluation = evaluationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Evaluation introuvable : " + id));

        evaluation.setCommunication(dto.getCommunication());
        evaluation.setLeadership(dto.getLeadership());
        evaluation.setTechnical(dto.getTechnical());
        evaluation.setTeamwork(dto.getTeamwork());
        evaluation.setProductivity(dto.getProductivity());
        evaluation.setComments(dto.getComments());

        Evaluation updated = evaluationRepository.save(evaluation);
        return convertToDTOWithName(updated);
    }

    @Transactional
    public void deleteEvaluation(Long id) {
        evaluationRepository.deleteById(id);
    }

    private EvaluationDTO convertToDTOWithName(Evaluation evaluation) {
        EvaluationDTO dto = EvaluationMapper.toDTO(evaluation);
        if (dto != null) {
            employeeRepository.findById(evaluation.getEmployeeId())
                    .ifPresent(emp -> dto.setEmployeeName(emp.getFirstName() + " " + emp.getLastName()));
            employeeRepository.findById(evaluation.getManagerId())
                    .ifPresent(mgr -> dto.setManagerName(mgr.getFirstName() + " " + mgr.getLastName()));
        }
        return dto;
    }
}
