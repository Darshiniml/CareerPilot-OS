package com.careerpilot.backend.modules.resume.services;

import com.careerpilot.backend.modules.auth.domain.User;
import com.careerpilot.backend.modules.auth.domain.UserRepository;
import com.careerpilot.backend.modules.resume.domain.Resume;
import com.careerpilot.backend.modules.resume.repositories.ResumeRepository;
import com.careerpilot.backend.modules.resume.repositories.ResumeVersionRepository;
import com.careerpilot.backend.modules.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class ResumeServiceTest {

    private ResumeRepository resumeRepository;
    private ResumeVersionRepository resumeVersionRepository;
    private UserRepository userRepository;
    private StorageService storageService;
    private ApplicationEventPublisher eventPublisher;

    private ResumeService resumeService;
    private UUID userId;
    private User mockUser;

    @BeforeEach
    void setUp() {
        resumeRepository = Mockito.mock(ResumeRepository.class);
        resumeVersionRepository = Mockito.mock(ResumeVersionRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        storageService = Mockito.mock(StorageService.class);
        eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

        resumeService = new ResumeService(
                resumeRepository,
                resumeVersionRepository,
                userRepository,
                storageService,
                eventPublisher
        );

        userId = UUID.randomUUID();
        mockUser = User.builder().id(userId).email("user@example.com").build();
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    }

    @Test
    void testUploadResumeInvalidMimeType_ThrowsException() {
        byte[] bytes = "dummy file content".getBytes();
        assertThrows(IllegalArgumentException.class, () ->
                resumeService.uploadResume(userId, "My Resume", "resume.png", "image/png", bytes)
        );
    }

    @Test
    void testUploadResumeExceedsLimitCount_ThrowsException() {
        byte[] bytes = "dummy file content".getBytes();
        Mockito.when(resumeRepository.countActiveResumesByUserId(userId)).thenReturn(5L);
        Mockito.when(resumeRepository.findByUserIdAndTitleAndNotDeleted(userId, "New Resume"))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                resumeService.uploadResume(userId, "New Resume", "resume.pdf", "application/pdf", bytes)
        );
    }

    @Test
    void testSoftDeleteResume_Success() {
        UUID resumeId = UUID.randomUUID();
        Resume resume = Resume.builder()
                .id(resumeId)
                .user(mockUser)
                .title("My Resume")
                .isDefault(true)
                .isDeleted(false)
                .build();

        Mockito.when(resumeRepository.findActiveById(resumeId)).thenReturn(Optional.of(resume));

        resumeService.softDeleteResume(userId, resumeId);

        assertTrue(resume.isDeleted());
        assertFalse(resume.isDefault());
        assertNotNull(resume.getDeletedAt());
        assertEquals(userId, resume.getDeletedBy());
        Mockito.verify(resumeRepository, Mockito.times(1)).save(resume);
    }

    @Test
    void testSetDefaultResume_Success() {
        UUID resumeId = UUID.randomUUID();
        Resume resume = Resume.builder()
                .id(resumeId)
                .user(mockUser)
                .title("My Resume")
                .isDefault(false)
                .build();

        UUID otherResumeId = UUID.randomUUID();
        Resume currentDefault = Resume.builder()
                .id(otherResumeId)
                .user(mockUser)
                .title("Current Default")
                .isDefault(true)
                .build();

        Mockito.when(resumeRepository.findActiveById(resumeId)).thenReturn(Optional.of(resume));
        Mockito.when(resumeRepository.findDefaultByUserId(userId)).thenReturn(Optional.of(currentDefault));

        resumeService.setDefaultResume(userId, resumeId);

        assertTrue(resume.isDefault());
        assertFalse(currentDefault.isDefault());
        Mockito.verify(resumeRepository, Mockito.times(1)).save(resume);
        Mockito.verify(resumeRepository, Mockito.times(1)).save(currentDefault);
    }
}
