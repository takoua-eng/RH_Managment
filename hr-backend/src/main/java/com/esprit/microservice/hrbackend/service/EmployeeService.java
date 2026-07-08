package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.EmployeeRequestDTO;
import com.esprit.microservice.hrbackend.dto.EmployeeResponseDTO;
import com.esprit.microservice.hrbackend.entity.Employee;
import com.esprit.microservice.hrbackend.entity.Status;
import com.esprit.microservice.hrbackend.exception.EmployeeNotFoundException;
import com.esprit.microservice.hrbackend.exception.InvalidFileException;
import com.esprit.microservice.hrbackend.exception.PhotoNotFoundException;
import com.esprit.microservice.hrbackend.mapper.EmployeeMapper;
import com.esprit.microservice.hrbackend.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public Page<EmployeeResponseDTO> getAllEmployees(Pageable pageable, String search) {
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
        return employeeRepository.findAll(spec, pageable)
                .map(EmployeeMapper::toResponseDTO);
    }

    public EmployeeResponseDTO getEmployeeById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + id));
        return EmployeeMapper.toResponseDTO(employee);
    }

    @Transactional
    public EmployeeResponseDTO createEmployee(EmployeeRequestDTO dto) {
        Employee employee = EmployeeMapper.toEntity(dto);
        employee.setStatus(Status.ACTIVE); // default status for newly created employees
        Employee saved = employeeRepository.save(employee);
        return EmployeeMapper.toResponseDTO(saved);
    }

    @Transactional
    public EmployeeResponseDTO updateEmployee(Long id, EmployeeRequestDTO dto) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + id));
        
        EmployeeMapper.updateEntityFromDTO(dto, employee);
        Employee updated = employeeRepository.save(employee);
        return EmployeeMapper.toResponseDTO(updated);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        if (!employeeRepository.existsById(id)) {
            throw new EmployeeNotFoundException("Employee not found with id: " + id);
        }
        employeeRepository.deleteById(id);
    }

    @Transactional
    public void uploadPhoto(Long id, MultipartFile file) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + id));

        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Uploaded file cannot be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidFileException("Uploaded file must be an image");
        }

        // Limit size to 2 MB (2 * 1024 * 1024 = 2097152 bytes)
        long maxSizeBytes = 2 * 1024 * 1024;
        if (file.getSize() > maxSizeBytes) {
            throw new InvalidFileException("Uploaded photo size must not exceed 2 MB");
        }

        try {
            employee.setPhoto(file.getBytes());
            employee.setPhotoContentType(contentType);
            employeeRepository.save(employee);
        } catch (IOException e) {
            throw new InvalidFileException("Error occurred while reading file bytes: " + e.getMessage());
        }
    }

    public byte[] getPhoto(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + id));
        
        if (employee.getPhoto() == null || employee.getPhoto().length == 0) {
            throw new PhotoNotFoundException("Employee with id " + id + " does not have a photo");
        }
        return employee.getPhoto();
    }

    public String getPhotoContentType(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new EmployeeNotFoundException("Employee not found with id: " + id));

        if (employee.getPhotoContentType() == null || employee.getPhoto() == null) {
            throw new PhotoNotFoundException("Employee with id " + id + " does not have a photo");
        }
        return employee.getPhotoContentType();
    }
}
