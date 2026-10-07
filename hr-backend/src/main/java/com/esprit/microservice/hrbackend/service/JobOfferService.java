package com.esprit.microservice.hrbackend.service;

import com.esprit.microservice.hrbackend.dto.JobOfferRequestDTO;
import com.esprit.microservice.hrbackend.dto.JobOfferResponseDTO;
import com.esprit.microservice.hrbackend.entity.JobOffer;
import com.esprit.microservice.hrbackend.entity.JobOfferStatus;
import com.esprit.microservice.hrbackend.mapper.JobOfferMapper;
import com.esprit.microservice.hrbackend.repository.JobOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import com.esprit.microservice.hrbackend.event.JobOfferPublishedEvent;

@Service
@RequiredArgsConstructor
@Transactional
public class JobOfferService {

    private final JobOfferRepository jobOfferRepository;
    private final ApplicationEventPublisher eventPublisher;

    public JobOfferResponseDTO createJobOffer(JobOfferRequestDTO requestDTO) {
        JobOffer jobOffer = JobOfferMapper.toEntity(requestDTO);
        JobOffer savedOffer = jobOfferRepository.save(jobOffer);
        if (savedOffer.getStatus() == JobOfferStatus.PUBLISHED) {
            eventPublisher.publishEvent(new JobOfferPublishedEvent(savedOffer.getId(), savedOffer.getTitle(), savedOffer.getDepartment()));
        }
        return JobOfferMapper.toResponseDTO(savedOffer);
    }

    public JobOfferResponseDTO updateJobOffer(Long id, JobOfferRequestDTO requestDTO) {
        JobOffer existingOffer = jobOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job offer not found"));

        existingOffer.setTitle(requestDTO.getTitle());
        existingOffer.setDescription(requestDTO.getDescription());
        existingOffer.setDepartment(requestDTO.getDepartment());
        existingOffer.setContractType(requestDTO.getContractType());
        existingOffer.setExperienceLevel(requestDTO.getExperienceLevel());
        existingOffer.setRequiredSkills(requestDTO.getRequiredSkills());
        existingOffer.setLocation(requestDTO.getLocation());
        existingOffer.setSalaryRange(requestDTO.getSalaryRange());
        existingOffer.setApplicationDeadline(requestDTO.getApplicationDeadline());
        
        if (requestDTO.getNumberOfPositions() != null) {
            existingOffer.setNumberOfPositions(requestDTO.getNumberOfPositions());
        }
        if (requestDTO.getStatus() != null) {
            existingOffer.setStatus(requestDTO.getStatus());
        }

        JobOffer updatedOffer = jobOfferRepository.save(existingOffer);
        return JobOfferMapper.toResponseDTO(updatedOffer);
    }

    @Transactional(readOnly = true)
    public List<JobOfferResponseDTO> getAllJobOffers() {
        return jobOfferRepository.findAll().stream()
                .map(JobOfferMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<JobOfferResponseDTO> getPublishedJobOffers() {
        return jobOfferRepository.findByStatus(JobOfferStatus.PUBLISHED).stream()
                .map(JobOfferMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public JobOfferResponseDTO getJobOfferById(Long id) {
        JobOffer offer = jobOfferRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job offer not found"));
        return JobOfferMapper.toResponseDTO(offer);
    }

    public void deleteJobOffer(Long id) {
        if (!jobOfferRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job offer not found");
        }
        jobOfferRepository.deleteById(id);
    }
}
