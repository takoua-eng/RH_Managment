package com.esprit.microservice.hrbackend.controller;

import com.esprit.microservice.hrbackend.dto.ManagerTeamMemberDTO;
import com.esprit.microservice.hrbackend.service.ManagerTeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/manager/team")
@RequiredArgsConstructor
public class ManagerTeamController {

    private final ManagerTeamService managerTeamService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<List<ManagerTeamMemberDTO>> getManagerTeam(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT invalide.");
        }
        String keycloakId = jwt.getSubject();
        List<ManagerTeamMemberDTO> team = managerTeamService.getManagerTeam(keycloakId);
        return ResponseEntity.ok(team);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER', 'ADMIN')")
    public ResponseEntity<ManagerTeamMemberDTO> getTeamMemberDetail(@PathVariable Long id, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token JWT invalide.");
        }
        String keycloakId = jwt.getSubject();
        ManagerTeamMemberDTO member = managerTeamService.getTeamMemberDetail(keycloakId, id);
        return ResponseEntity.ok(member);
    }
}
