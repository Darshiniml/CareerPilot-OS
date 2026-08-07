package com.careerpilot.backend.modules.profile.services;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.profile.domain.Certification;
import com.careerpilot.backend.modules.profile.repositories.CertificationRepository;
import com.careerpilot.shared.dto.profile.CertificationDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class CertificationService {

    private final CertificationRepository certificationRepository;
    private final UserRepository userRepository;

    public CertificationService(CertificationRepository certificationRepository, UserRepository userRepository) {
        this.certificationRepository = certificationRepository;
        this.userRepository = userRepository;
    }

    public Page<Certification> getCertifications(UUID userId, Pageable pageable) {
        return certificationRepository.findByUserId(userId, pageable);
    }

    public List<Certification> getCertificationsList(UUID userId) {
        return certificationRepository.findByUserId(userId);
    }

    @Transactional
    public Certification addCertification(UUID userId, CertificationDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (certificationRepository.findByUserIdAndName(userId, dto.getName()).isPresent()) {
            throw new IllegalArgumentException("Certification with name '" + dto.getName() + "' already exists");
        }

        validateDates(dto.getIssueDate(), dto.getExpirationDate());

        Certification certification = Certification.builder()
                .id(UUID.randomUUID())
                .user(user)
                .name(dto.getName())
                .issuingOrganization(dto.getIssuingOrganization())
                .issueDate(dto.getIssueDate())
                .expirationDate(dto.getExpirationDate())
                .credentialId(dto.getCredentialId())
                .credentialUrl(dto.getCredentialUrl())
                .build();

        return certificationRepository.save(certification);
    }

    @Transactional
    public void deleteCertification(UUID userId, UUID certificationId) {
        Certification certification = certificationRepository.findById(certificationId)
                .orElseThrow(() -> new IllegalArgumentException("Certification not found"));

        if (!certification.getUser().getId().equals(userId)) {
            throw new SecurityException("Unauthorized access to certification");
        }

        certificationRepository.delete(certification);
    }

    private void validateDates(LocalDate issue, LocalDate expiration) {
        if (issue != null && issue.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Issue date cannot be in the future");
        }
        if (issue != null && expiration != null && expiration.isBefore(issue)) {
            throw new IllegalArgumentException("Expiration date cannot precede issue date");
        }
    }
}
