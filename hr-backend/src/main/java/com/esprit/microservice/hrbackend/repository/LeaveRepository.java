package com.esprit.microservice.hrbackend.repository;

import com.esprit.microservice.hrbackend.entity.Leave;
import com.esprit.microservice.hrbackend.entity.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Repository
public interface LeaveRepository extends JpaRepository<Leave, Long> {
    List<Leave> findByEmployeeIdAndStatus(Long employeeId, LeaveStatus status);

    List<Leave> findByEmployeeId(Long employeeId);

    /** Congés d'une liste d'employés filtrés par statut unique */
    List<Leave> findByEmployeeIdInAndStatus(Collection<Long> employeeIds, LeaveStatus status);

    /** Tous les congés d'une liste d'employés (sans filtre statut) */
    List<Leave> findByEmployeeIdIn(Collection<Long> employeeIds);

    /** Congés d'une liste d'employés filtrés par plusieurs statuts */
    List<Leave> findByEmployeeIdInAndStatusIn(Collection<Long> employeeIds, Collection<LeaveStatus> statuses);

    /** Congés d'un statut donné qui couvrent une date (ex. : congés approuvés en cours aujourd'hui). */
    @Query("select l from Leave l where l.status = :status and l.startDate <= :date and l.endDate >= :date")
    List<Leave> findCoveringDate(@Param("status") LeaveStatus status, @Param("date") LocalDate date);
}
