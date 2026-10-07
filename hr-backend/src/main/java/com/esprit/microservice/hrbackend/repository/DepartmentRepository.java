package com.esprit.microservice.hrbackend.repository;



import com.esprit.microservice.hrbackend.entity.Department;
import com.esprit.microservice.hrbackend.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByName(String name);
    boolean existsByName(String name);
    Optional<Department> findByManager(Employee manager);
}
