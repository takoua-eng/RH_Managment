package com.esprit.microservice.hrbackend.repository;

import com.esprit.microservice.hrbackend.entity.JobOffer;
import com.esprit.microservice.hrbackend.entity.JobOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobOfferRepository extends JpaRepository<JobOffer, Long> {
    List<JobOffer> findByStatus(JobOfferStatus status);
}
