package com.esprit.microservice.hrbackend.repository;

import com.esprit.microservice.hrbackend.entity.Training;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TrainingRepository extends JpaRepository<Training, Long> {
}
