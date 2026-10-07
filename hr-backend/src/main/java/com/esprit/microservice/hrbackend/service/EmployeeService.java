package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.EmployeeRequestDTO;
import com.esprit.microservice.hrbackend.dto.EmployeeResponseDTO;
import com.esprit.microservice.hrbackend.entity.Department;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Leave;
import com.esprit.microservice.hrbackend.entity.LeaveStatus;
import com.esprit.microservice.hrbackend.entity.Role;
import com.esprit.microservice.hrbackend.entity.Status;
import com.esprit.microservice.hrbackend.event.EmployeeAddedToTeamEvent;
import com.esprit.microservice.hrbackend.event.EmployeeRemovedFromTeamEvent;
import com.esprit.microservice.hrbackend.exception.EmployeeNotFoundException;
import com.esprit.microservice.hrbackend.exception.InvalidFileException;
import com.esprit.microservice.hrbackend.exception.PhotoNotFoundException;
import com.esprit.microservice.hrbackend.mapper.EmployeeMapper;
import com.esprit.microservice.hrbackend.repository.DepartmentRepository;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import com.esprit.microservice.hrbackend.repository.LeaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final FileStorageService fileStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final LeaveRepository leaveRepository;

    public Page<EmployeeResponseDTO> getAllEmployees(Pageable pageable, String search) {
        if (pageable == null || pageable.getSort().isUnsorted()) {
            int page = pageable != null ? pageable.getPageNumber() : 0;
            int size = pageable != null ? pageable.getPageSize() : 10;
            pageable = PageRequest.of(page, size, Sort.by("id").ascending());
        }
        Specification<Employee> spec = (root, query, cb) -> {
            if (search == null || search.trim().isEmpty()) {
                return null;
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("firstName")), pattern),
                    cb.like(cb.lower(root.get("lastName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)
            );
        };

        // Congés en cours aujourd'hui : calculés une seule fois pour toute la page
        Map<Long, LocalDate> retours = retoursDeConge();

        return employeeRepository.findAll(spec, pageable)
                .map(e -> avecStatutConge(EmployeeMapper.toResponseDTO(e), e.getId(), retours));
    }

    public EmployeeResponseDTO getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + id));
        return avecStatutConge(EmployeeMapper.toResponseDTO(employee), employee.getId(), retoursDeConge());
    }

    @Transactional
    public EmployeeResponseDTO createEmployee(EmployeeRequestDTO dto) {
        Employee employee = EmployeeMapper.toEntity(dto);

        Department department = null;
        if (dto.getDepartmentId() != null) {
            department = departmentRepository.findById(dto.getDepartmentId()).orElse(null);
        }
        if (department == null && dto.getDepartment() != null && !dto.getDepartment().trim().isEmpty()) {
            String deptName = dto.getDepartment().trim();
            department = departmentRepository.findByName(deptName)
                    .orElseGet(() -> departmentRepository.save(
                            Department.builder()
                                    .name(deptName)
                                    .description("Département " + deptName)
                                    .build()
                    ));
        }

        if (dto.getManagerId() != null) {
            Employee manager = employeeRepository.findById(dto.getManagerId()).orElse(null);
            employee.setManager(manager);
        }

        employee.setDepartment(department);
        employee.setStatus(Status.ACTIVE);

        Employee saved = employeeRepository.save(employee);

        if (saved.getManager() != null) {
            eventPublisher.publishEvent(new EmployeeAddedToTeamEvent(saved.getId(), saved.getManager().getId()));
        }

        return EmployeeMapper.toResponseDTO(saved);
    }

    @Transactional
    public EmployeeResponseDTO updateEmployee(Long id, EmployeeRequestDTO dto) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + id));

        Long oldManagerId = employee.getManager() != null ? employee.getManager().getId() : null;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            String tokenKeycloakId = jwt.getSubject();
            String tokenEmail = jwt.getClaimAsString("email");

            if (employee.getKeycloakId() == null || employee.getKeycloakId().isBlank()) {
                if (tokenEmail != null && tokenEmail.equalsIgnoreCase(employee.getEmail())) {
                    employee.setKeycloakId(tokenKeycloakId);
                }
            }
        }

        boolean isFullAdminOrRh = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_ADMIN") || auth.equals("ROLE_RH"));

        if (!isFullAdminOrRh) {
            if (dto.getFirstName() != null && !dto.getFirstName().isBlank()) {
                employee.setFirstName(dto.getFirstName());
            }
            if (dto.getLastName() != null && !dto.getLastName().isBlank()) {
                employee.setLastName(dto.getLastName());
            }
            if (dto.getPhone() != null) {
                employee.setPhone(dto.getPhone());
            }
            if (dto.getAddress() != null) {
                employee.setAddress(dto.getAddress());
            }
        } else {
            // Full admin/RH update
            Department department = null;
            if (dto.getDepartmentId() != null) {
                department = departmentRepository.findById(dto.getDepartmentId()).orElse(null);
            }
            if (department == null && dto.getDepartment() != null && !dto.getDepartment().trim().isEmpty()) {
                String deptName = dto.getDepartment().trim();
                department = departmentRepository.findByName(deptName)
                        .orElseGet(() -> departmentRepository.save(
                                Department.builder()
                                        .name(deptName)
                                        .description("Département " + deptName)
                                        .build()
                        ));
            }

            EmployeeMapper.updateEntityFromDTO(dto, employee);
            if (department != null) {
                employee.setDepartment(department);
            }

            if (dto.getManagerId() != null) {
                Employee manager = employeeRepository.findById(dto.getManagerId()).orElse(null);
                employee.setManager(manager);
            }
        }

        Employee updated = employeeRepository.save(employee);
        Long newManagerId = updated.getManager() != null ? updated.getManager().getId() : null;

        if (!Objects.equals(oldManagerId, newManagerId)) {
            if (oldManagerId != null) {
                eventPublisher.publishEvent(new EmployeeRemovedFromTeamEvent(updated.getId(), oldManagerId, newManagerId));
            }
            if (newManagerId != null) {
                eventPublisher.publishEvent(new EmployeeAddedToTeamEvent(updated.getId(), newManagerId));
            }
        }

        return EmployeeMapper.toResponseDTO(updated);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + id));

        if (employee.getPhotoPath() != null) {
            fileStorageService.delete(employee.getPhotoPath());
        }

        // 1. Dissocier l'employé s'il est manager d'un département
        departmentRepository.findByManager(employee).ifPresent(dept -> {
            dept.setManager(null);
            departmentRepository.save(dept);
        });

        // 2. Dissocier l'employé s'il est manager d'autres employés (subordonnés)
        if (employee.getSubordinates() != null) {
            employee.getSubordinates().forEach(sub -> sub.setManager(null));
            employeeRepository.saveAll(employee.getSubordinates());
        }

        employeeRepository.delete(employee);
    }

    @Transactional
    public void uploadPhoto(Long id, MultipartFile file) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + id));

        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file cannot be empty");
        }

        if (employee.getPhotoPath() != null) {
            fileStorageService.delete(employee.getPhotoPath());
        }

        String photoPath = fileStorageService.store(file, "photos");
        employee.setPhotoPath(photoPath);
        employee.setPhotoContentType(file.getContentType());
        employeeRepository.save(employee);
    }

    public Resource getPhotoResource(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + id));

        if (employee.getPhotoPath() == null || employee.getPhotoPath().isEmpty()) {
            throw new PhotoNotFoundException("Employee with id " + id + " does not have a photo");
        }
        return fileStorageService.load(employee.getPhotoPath());
    }

    public String getPhotoContentType(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + id));

        if (employee.getPhotoContentType() == null || employee.getPhotoPath() == null) {
            throw new PhotoNotFoundException("Employee with id " + id + " does not have a photo");
        }
        return employee.getPhotoContentType();
    }

    public EmployeeResponseDTO getEmployeeByKeycloakId(String keycloakId) {
        Employee employee = employeeRepository
                .findByKeycloakId(keycloakId)
                .orElseThrow(() ->
                        new EmployeeNotFoundException(
                                "Employee not found with Keycloak ID: " + keycloakId
                        )
                );

        return avecStatutConge(EmployeeMapper.toResponseDTO(employee), employee.getId(), retoursDeConge());
    }


    // =========================================================
    // AFFECTER / CHANGER / RETIRER LE MANAGER D'UN EMPLOYÉ
    // =========================================================

    /**
     * Affecte ou change le manager d'un employé.
     *
     * @param employeeId ID de l'employé à modifier
     * @param managerId  ID du nouveau manager (null = désaffectation)
     */
    @Transactional
    public EmployeeResponseDTO assignManager(Long employeeId, Long managerId) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(
                        "Employé introuvable avec l'id : " + employeeId
                ));

        Long oldManagerId = employee.getManager() != null ? employee.getManager().getId() : null;

        if (managerId == null) {
            // Désaffectation
            employee.setManager(null);
        } else {
            if (managerId.equals(employeeId)) {
                throw new IllegalArgumentException(
                        "Un employé ne peut pas être son propre manager."
                );
            }

            Employee manager = employeeRepository.findById(managerId)
                    .orElseThrow(() -> new EmployeeNotFoundException(
                            "Manager introuvable avec l'id : " + managerId
                    ));

            employee.setManager(manager);
        }

        Employee updated = employeeRepository.save(employee);
        Long newManagerId = updated.getManager() != null ? updated.getManager().getId() : null;

        if (!Objects.equals(oldManagerId, newManagerId)) {
            if (oldManagerId != null) {
                eventPublisher.publishEvent(new EmployeeRemovedFromTeamEvent(employeeId, oldManagerId, newManagerId));
            }
            if (newManagerId != null) {
                eventPublisher.publishEvent(new EmployeeAddedToTeamEvent(employeeId, newManagerId));
            }
        }

        return EmployeeMapper.toResponseDTO(updated);
    }


    // =========================================================
    // LISTE DES MANAGERS (pour le dropdown)
    // =========================================================

    /**
     * Retourne les employés qui ont le rôle MANAGER, qui ont au moins un subordonné,
     * ou dont le poste contient "manager".
     */
    @Transactional(readOnly = true)
    public List<EmployeeResponseDTO> getManagers() {
        return employeeRepository.findAll()
                .stream()
                .filter(e -> (e.getRole() != null && e.getRole() == Role.MANAGER)
                        || (e.getSubordinates() != null && !e.getSubordinates().isEmpty())
                        || (e.getPosition() != null && e.getPosition().toLowerCase().contains("manager")))
                .map(EmployeeMapper::toResponseDTO)
                .collect(Collectors.toList());
    }


    // =========================================================
    // LISTE ÉQUIPE D'UN MANAGER
    // =========================================================

    /**
     * Retourne tous les employés directement rattachés au manager donné,
     * avec leur statut "en congé" du jour.
     */
    @Transactional(readOnly = true)
    public List<EmployeeResponseDTO> getManagerTeam(Long managerId) {
        if (!employeeRepository.existsById(managerId)) {
            throw new EmployeeNotFoundException("Manager introuvable avec l'id : " + managerId);
        }
        Map<Long, LocalDate> retours = retoursDeConge();
        return employeeRepository.findByManagerId(managerId)
                .stream()
                .map(e -> avecStatutConge(EmployeeMapper.toResponseDTO(e), e.getId(), retours))
                .collect(Collectors.toList());
    }


    // =========================================================
    // STATUT "EN CONGÉ" (calculé, jamais enregistré)
    // =========================================================

    /**
     * Employés en congé approuvé aujourd'hui → date de retour (lendemain de la fin du congé).
     * Une seule requête pour toute la liste.
     */
    private Map<Long, LocalDate> retoursDeConge() {
        LocalDate today = LocalDate.now();
        return leaveRepository.findCoveringDate(LeaveStatus.APPROVED, today).stream()
                .collect(Collectors.toMap(
                        Leave::getEmployeeId,
                        l -> l.getEndDate().plusDays(1),
                        (a, b) -> a.isAfter(b) ? a : b   // plusieurs congés : on garde le retour le plus tardif
                ));
    }

    /** Complète le DTO avec le statut "en congé" calculé. */
    private EmployeeResponseDTO avecStatutConge(EmployeeResponseDTO dto, Long employeeId,
                                                Map<Long, LocalDate> retours) {
        dto.setOnLeaveToday(retours.containsKey(employeeId));
        dto.setLeaveReturnDate(retours.get(employeeId));
        return dto;
    }
}