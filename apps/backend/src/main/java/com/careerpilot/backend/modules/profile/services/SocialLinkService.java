package com.careerpilot.backend.modules.profile.services;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.profile.domain.SocialLinks;
import com.careerpilot.backend.modules.profile.repositories.SocialLinksRepository;
import com.careerpilot.shared.dto.profile.SocialLinksDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SocialLinkService {

    private final SocialLinksRepository socialLinksRepository;
    private final UserRepository userRepository;

    public SocialLinkService(SocialLinksRepository socialLinksRepository, UserRepository userRepository) {
        this.socialLinksRepository = socialLinksRepository;
        this.userRepository = userRepository;
    }

    public SocialLinks getSocialLinks(UUID userId) {
        return socialLinksRepository.findByUserId(userId).orElse(null);
    }

    @Transactional
    public SocialLinks updateSocialLinks(UUID userId, SocialLinksDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        SocialLinks socialLinks = socialLinksRepository.findByUserId(userId)
                .orElseGet(() -> SocialLinks.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .build());

        socialLinks.setLinkedin(dto.getLinkedin());
        socialLinks.setGithub(dto.getGithub());
        socialLinks.setPortfolio(dto.getPortfolio());
        socialLinks.setTwitter(dto.getTwitter());

        return socialLinksRepository.save(socialLinks);
    }
}
