package com.esprit.microservice.hrbackend.repository;

import com.esprit.microservice.hrbackend.entity.TrainingEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrainingEnrollmentRepository extends JpaRepository<TrainingEnrollment, Long> {
    List<TrainingEnrollment> findByEmployeeId(Long employeeId);
    Optional<TrainingEnrollment> findByEmployeeIdAndTrainingId(Long employeeId, Long trainingId);
    List<TrainingEnrollment> findByTrainingId(Long trainingId);

    /** Inscriptions de formation pour une liste d'employés */
    List<TrainingEnrollment> findByEmployeeIdIn(Collection<Long> employeeIds);
    List<TrainingEnrollment> findByEmployee_Id(Long employeeId);
}
