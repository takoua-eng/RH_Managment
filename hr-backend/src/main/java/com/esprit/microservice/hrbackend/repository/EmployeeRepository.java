package com.esprit.microservice.hrbackend.repository;

import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByKeycloakId(String keycloakId);

    Optional<Employee> findByEmail(String email);

    Optional<Employee> findByEmailIgnoreCase(String email);

    List<Employee> findByManagerIsNull(); // pour trouver les racines de l'organigramme

    List<Employee> findByDepartmentId(Long departmentId);

    /** Retourne les subordonnés directs d'un manager par son ID en base */
    List<Employee> findByManagerId(Long managerId);

    /** Variante paginée pour le endpoint Mon Équipe */
    Page<Employee> findByManagerId(Long managerId, Pageable pageable);

    /** Vérifie qu'un employé appartient bien à l'équipe du manager (sécurité) */
    boolean existsByIdAndManagerId(Long employeeId, Long managerId);

    /**
     * Retrouve les employés dont le keycloakId est dans la liste (pour les
     * managers)
     */
    List<Employee> findByKeycloakIdIn(Collection<String> keycloakIds);

    /** Retrouve tous les employés ayant un des rôles spécifiés */
    List<Employee> findByRoleIn(Collection<Role> roles);
}
