package com.esprit.microservice.hrbackend.repository;

import com.esprit.microservice.hrbackend.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
       List<Candidate> findByJobOfferId(Long jobOfferId);

       List<Candidate> findByAssignedManagerId(Long managerId);

       List<Candidate> findByAssignedManagerKeycloakId(String keycloakId);

       boolean existsByEmailAndJobOfferId(String email, Long jobOfferId);

       @Query("select c from Candidate c where c.jobOffer.id = :jobOfferId order by c.aiScore desc nulls last")
       List<Candidate> findAiRanking(@Param("jobOfferId") Long jobOfferId);
}
