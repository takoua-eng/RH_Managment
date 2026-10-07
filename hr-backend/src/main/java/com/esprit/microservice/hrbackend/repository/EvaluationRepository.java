package com.esprit.microservice.hrbackend.repository;

import com.esprit.microservice.hrbackend.entity.Evaluation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, Long> {
    List<Evaluation> findByEmployeeId(Long employeeId);
    List<Evaluation> findByManagerId(Long managerId);
}
