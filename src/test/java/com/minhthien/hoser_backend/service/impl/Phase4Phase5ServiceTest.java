package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.AdminReviewRequest;
import com.minhthien.hoser_backend.dto.request.HorseRequest;
import com.minhthien.hoser_backend.dto.request.HorseUpdateRequest;
import com.minhthien.hoser_backend.dto.request.JockeyInvitationRequest;
import com.minhthien.hoser_backend.dto.request.JockeyProfileRequest;
import com.minhthien.hoser_backend.dto.request.JockeyProfileUpdateRequest;
import com.minhthien.hoser_backend.entity.Horse;
import com.minhthien.hoser_backend.entity.JockeyInvitation;
import com.minhthien.hoser_backend.entity.JockeyProfile;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.AssignmentStatus;
import com.minhthien.hoser_backend.enums.HorseStatus;
import com.minhthien.hoser_backend.enums.JockeyStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.DuplicateResourceException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.exception.UnauthorizedException;
import com.minhthien.hoser_backend.repository.HorseRepository;
import com.minhthien.hoser_backend.repository.JockeyInvitationRepository;
import com.minhthien.hoser_backend.repository.JockeyProfileRepository;
import com.minhthien.hoser_backend.repository.RaceParticipantRepository;
import com.minhthien.hoser_backend.repository.RaceRegistrationRepository;
import com.minhthien.hoser_backend.repository.RaceResultRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.CloudinaryUploadService;
import com.minhthien.hoser_backend.service.FinanceSettingsService;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Phase4Phase5ServiceTest {
    @Mock
    private HorseRepository horseRepository;

    @Mock
    private JockeyProfileRepository jockeyProfileRepository;

    @Mock
    private JockeyInvitationRepository jockeyInvitationRepository;

    @Mock
    private RaceRegistrationRepository raceRegistrationRepository;

    @Mock
    private RaceParticipantRepository raceParticipantRepository;

    @Mock
    private RaceResultRepository raceResultRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CloudinaryUploadService cloudinaryUploadService;

    @Mock
    private WalletService walletService;

    @Mock
    private FinanceSettingsService financeSettingsService;

    @Test
    void ownerCannotUpdateAnotherOwnersHorse() {
        HorseServiceImpl horseService = horseService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(horseRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> horseService.updateHorse(1L, 100L, horseUpdateRequest(), null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Horse not found");

        verify(horseRepository, never()).save(any(Horse.class));
    }

    @Test
    void ownerCannotUpdateApprovedHorse() {
        HorseServiceImpl horseService = horseService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        Horse horse = horse(100L, owner, HorseStatus.APPROVED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(horseRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(horse));

        assertThatThrownBy(() -> horseService.updateHorse(1L, 100L, horseUpdateRequest(), null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Approved or suspended horses cannot be updated by owner");

        verify(horseRepository, never()).save(any(Horse.class));
    }

    @Test
    void adminSuspendHorseStoresReason() {
        HorseServiceImpl horseService = horseService();
        User admin = user(9L, "admin", UserRole.ADMIN);
        User owner = user(1L, "owner-one", UserRole.OWNER);
        Horse horse = horse(100L, owner, HorseStatus.APPROVED);
        AdminReviewRequest request = new AdminReviewRequest();
        request.setReason("Vet document expired");

        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(horseRepository.findById(100L)).thenReturn(Optional.of(horse));
        when(horseRepository.save(any(Horse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(horseService.suspendHorse(100L, 9L, request).getStatus()).isEqualTo(HorseStatus.SUSPENDED);
        assertThat(horse.getReviewReason()).isEqualTo("Vet document expired");
        assertThat(horse.getReviewedBy()).isEqualTo(9L);
    }

    @Test
    void createHorseStoresCloudinaryUrls() {
        HorseServiceImpl horseService = horseService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        MockMultipartFile image = new MockMultipartFile("image", "horse.jpg", "image/jpeg", "img".getBytes());
        MockMultipartFile document = new MockMultipartFile("document", "vet.pdf", "application/pdf", "doc".getBytes());

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(cloudinaryUploadService.uploadImage(image, "hoser/horses/images"))
                .thenReturn("https://cdn.example/horse.jpg");
        when(cloudinaryUploadService.uploadDocument(document, "hoser/horses/documents"))
                .thenReturn("https://cdn.example/vet.pdf");
        when(horseRepository.save(any(Horse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = horseService.createHorse(1L, horseRequest(), image, document);

        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example/horse.jpg");
        assertThat(response.getDocumentUrl()).isEqualTo("https://cdn.example/vet.pdf");
    }

    @Test
    void updateHorseWithoutFilesKeepsExistingUrls() {
        HorseServiceImpl horseService = horseService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        Horse horse = horse(100L, owner, HorseStatus.PENDING);
        horse.setImageUrl("https://cdn.example/existing-horse.jpg");
        horse.setDocumentUrl("https://cdn.example/existing-vet.pdf");

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(horseRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(horse));
        when(horseRepository.save(any(Horse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = horseService.updateHorse(1L, 100L, horseUpdateRequest(), null, null);

        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example/existing-horse.jpg");
        assertThat(response.getDocumentUrl()).isEqualTo("https://cdn.example/existing-vet.pdf");
        verify(cloudinaryUploadService, never()).uploadImage(any(), any());
        verify(cloudinaryUploadService, never()).uploadDocument(any(), any());
    }

    @Test
    void updateHorseKeepsExistingFieldsWhenNotProvided() {
        HorseServiceImpl horseService = horseService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        Horse horse = horse(100L, owner, HorseStatus.REJECTED);
        horse.setBreed("Arabian");
        horse.setAge(5);
        horse.setGender("Male");
        horse.setColor("Bay");
        horse.setHeightCm(new BigDecimal("155.50"));
        horse.setWeightKg(new BigDecimal("430.00"));
        horse.setImageUrl("https://cdn.example/existing-horse.jpg");
        horse.setDocumentUrl("https://cdn.example/existing-vet.pdf");
        horse.setReviewReason("Old reason");
        horse.setReviewedBy(9L);
        horse.setReviewedAt(LocalDateTime.now());

        HorseUpdateRequest request = new HorseUpdateRequest();
        request.setName("Thunder");

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(horseRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(horse));
        when(horseRepository.save(any(Horse.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = horseService.updateHorse(1L, 100L, request, null, null);

        assertThat(response.getName()).isEqualTo("Thunder");
        assertThat(response.getBreed()).isEqualTo("Arabian");
        assertThat(response.getAge()).isEqualTo(5);
        assertThat(response.getGender()).isEqualTo("Male");
        assertThat(response.getColor()).isEqualTo("Bay");
        assertThat(response.getHeightCm()).isEqualByComparingTo("155.50");
        assertThat(response.getWeightKg()).isEqualByComparingTo("430.00");
        assertThat(response.getImageUrl()).isEqualTo("https://cdn.example/existing-horse.jpg");
        assertThat(response.getDocumentUrl()).isEqualTo("https://cdn.example/existing-vet.pdf");
        assertThat(response.getStatus()).isEqualTo(HorseStatus.PENDING);
        assertThat(response.getReviewReason()).isNull();
    }

    @Test
    void ownerDeletesPendingHorseWithoutActivity() {
        HorseServiceImpl horseService = horseService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        Horse horse = horse(100L, owner, HorseStatus.PENDING);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(horseRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(horse));

        horseService.deleteHorse(1L, 100L);

        verify(horseRepository).delete(horse);
    }

    @Test
    void ownerCannotDeleteAnotherOwnersHorse() {
        HorseServiceImpl horseService = horseService();
        User owner = user(1L, "owner-one", UserRole.OWNER);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(horseRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> horseService.deleteHorse(1L, 100L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Horse not found");

        verify(horseRepository, never()).delete(any());
    }

    @Test
    void ownerCannotDeleteApprovedHorse() {
        HorseServiceImpl horseService = horseService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        Horse horse = horse(100L, owner, HorseStatus.APPROVED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(horseRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(horse));

        assertThatThrownBy(() -> horseService.deleteHorse(1L, 100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Only pending or rejected horses can be deleted");

        verify(horseRepository, never()).delete(any());
    }

    @Test
    void ownerCannotDeleteHorseWithInvitationActivity() {
        HorseServiceImpl horseService = horseService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        Horse horse = horse(100L, owner, HorseStatus.REJECTED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(horseRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(horse));
        when(jockeyInvitationRepository.existsByHorseId(100L)).thenReturn(true);

        assertThatThrownBy(() -> horseService.deleteHorse(1L, 100L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot delete horse with activity history");

        verify(horseRepository, never()).delete(any());
    }

    @Test
    void getApprovedHorsesReturnsRepositoryApprovedHorses() {
        HorseServiceImpl horseService = horseService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        Horse horse = horse(100L, owner, HorseStatus.APPROVED);

        when(horseRepository.findByStatusOrderByCreatedAtDesc(HorseStatus.APPROVED))
                .thenReturn(List.of(horse));

        var response = horseService.getApprovedHorses();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getStatus()).isEqualTo(HorseStatus.APPROVED);
        verify(horseRepository).findByStatusOrderByCreatedAtDesc(HorseStatus.APPROVED);
    }

    @Test
    void createJockeyProfileStoresCloudinaryUrls() {
        JockeyProfileServiceImpl jockeyProfileService = jockeyProfileService();
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        MockMultipartFile avatar = new MockMultipartFile("avatar", "avatar.png", "image/png", "img".getBytes());
        MockMultipartFile achievements = new MockMultipartFile(
                "achievements", "achievements.png", "image/png", "achievement".getBytes());
        MockMultipartFile licenseDocument = new MockMultipartFile(
                "licenseDocument", "license.pdf", "application/pdf", "doc".getBytes());
        JockeyProfileRequest request = jockeyProfileRequest();
        request.setAchievements(achievements);

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyProfileRepository.existsByLicenseNumberAndUserIdNot("LIC-2", 2L)).thenReturn(false);
        when(jockeyProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(cloudinaryUploadService.uploadImage(avatar, "hoser/jockeys/avatars"))
                .thenReturn("https://cdn.example/avatar.png");
        when(cloudinaryUploadService.uploadImage(achievements, "hoser/jockeys/achievements"))
                .thenReturn("https://cdn.example/achievements.png");
        when(cloudinaryUploadService.uploadDocument(licenseDocument, "hoser/jockeys/license-documents"))
                .thenReturn("https://cdn.example/license.pdf");
        when(jockeyProfileRepository.save(any(JockeyProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = jockeyProfileService.createMyProfile(2L, request, avatar, licenseDocument);

        assertThat(response.getAvatarUrl()).isEqualTo("https://cdn.example/avatar.png");
        assertThat(response.getLicenseDocumentUrl()).isEqualTo("https://cdn.example/license.pdf");
        assertThat(response.getHirePrice()).isEqualByComparingTo("500.00");
        assertThat(response.getAwards()).isEqualTo("Golden Cup");
        assertThat(response.getAchievements()).isEqualTo("https://cdn.example/achievements.png");
        assertThat(response.getSpecialties()).isEqualTo("Sprint");
        assertThat(response.getStatus()).isEqualTo(JockeyStatus.PENDING);
    }

    @Test
    void jockeyProfileRejectsMissingHirePrice() {
        JockeyProfileServiceImpl jockeyProfileService = jockeyProfileService();
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        JockeyProfileRequest request = jockeyProfileRequest();
        request.setHirePrice(BigDecimal.ZERO);

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(jockeyProfileRepository.existsByLicenseNumberAndUserIdNot("LIC-2", 2L)).thenReturn(false);

        assertThatThrownBy(() -> jockeyProfileService.createMyProfile(2L, request, null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Hire price must be greater than zero");

        verify(jockeyProfileRepository, never()).save(any(JockeyProfile.class));
    }

    @Test
    void createJockeyProfileRejectsExistingProfile() {
        JockeyProfileServiceImpl jockeyProfileService = jockeyProfileService();
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        JockeyProfile profile = jockeyProfile(20L, jockey, JockeyStatus.PENDING);

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> jockeyProfileService.createMyProfile(2L, jockeyProfileRequest(), null, null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Jockey profile already exists");

        verify(jockeyProfileRepository, never()).save(any(JockeyProfile.class));
    }

    @Test
    void updateJockeyProfileWithoutFilesKeepsExistingUrls() {
        JockeyProfileServiceImpl jockeyProfileService = jockeyProfileService();
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        JockeyProfile profile = jockeyProfile(20L, jockey, JockeyStatus.PENDING);
        profile.setAvatarUrl("https://cdn.example/existing-avatar.png");
        profile.setAchievements("https://cdn.example/existing-achievements.png");
        profile.setLicenseDocumentUrl("https://cdn.example/existing-license.pdf");

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyProfileRepository.existsByLicenseNumberAndUserIdNot("LIC-2", 2L)).thenReturn(false);
        when(jockeyProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
        when(jockeyProfileRepository.save(any(JockeyProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = jockeyProfileService.updateMyProfile(2L, jockeyProfileUpdateRequest(), null, null);

        assertThat(response.getAvatarUrl()).isEqualTo("https://cdn.example/existing-avatar.png");
        assertThat(response.getAchievements()).isEqualTo("https://cdn.example/existing-achievements.png");
        assertThat(response.getLicenseDocumentUrl()).isEqualTo("https://cdn.example/existing-license.pdf");
        verify(cloudinaryUploadService, never()).uploadImage(any(), any());
        verify(cloudinaryUploadService, never()).uploadDocument(any(), any());
    }

    @Test
    void updateJockeyProfileStoresUploadedAchievementUrl() {
        JockeyProfileServiceImpl jockeyProfileService = jockeyProfileService();
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        JockeyProfile profile = jockeyProfile(20L, jockey, JockeyStatus.PENDING);
        MockMultipartFile achievements = new MockMultipartFile(
                "achievements", "achievements.png", "image/png", "achievement".getBytes());
        JockeyProfileUpdateRequest request = jockeyProfileUpdateRequest();
        request.setAchievements(achievements);

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyProfileRepository.existsByLicenseNumberAndUserIdNot("LIC-2", 2L)).thenReturn(false);
        when(jockeyProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
        when(cloudinaryUploadService.uploadImage(achievements, "hoser/jockeys/achievements"))
                .thenReturn("https://cdn.example/updated-achievements.png");
        when(jockeyProfileRepository.save(any(JockeyProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = jockeyProfileService.updateMyProfile(2L, request, null, null);

        assertThat(response.getAchievements()).isEqualTo("https://cdn.example/updated-achievements.png");
    }

    @Test
    void updateJockeyProfileKeepsExistingFieldsWhenNotProvided() {
        JockeyProfileServiceImpl jockeyProfileService = jockeyProfileService();
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        JockeyProfile profile = jockeyProfile(20L, jockey, JockeyStatus.REJECTED);
        profile.setExperienceYears(4);
        profile.setHeightCm(new BigDecimal("170.00"));
        profile.setWeightKg(new BigDecimal("60.00"));
        profile.setBio("Old bio");
        profile.setAwards("Golden Cup");
        profile.setAchievements("10 wins");
        profile.setSpecialties("Sprint");
        profile.setAvatarUrl("https://cdn.example/existing-avatar.png");
        profile.setLicenseDocumentUrl("https://cdn.example/existing-license.pdf");
        profile.setReviewReason("Old reason");
        profile.setReviewedBy(9L);
        profile.setReviewedAt(LocalDateTime.now());

        JockeyProfileUpdateRequest request = new JockeyProfileUpdateRequest();
        request.setHirePrice(new BigDecimal("750.00"));

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
        when(jockeyProfileRepository.save(any(JockeyProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = jockeyProfileService.updateMyProfile(2L, request, null, null);

        assertThat(response.getLicenseNumber()).isEqualTo("LIC-2");
        assertThat(response.getExperienceYears()).isEqualTo(4);
        assertThat(response.getHeightCm()).isEqualByComparingTo("170.00");
        assertThat(response.getWeightKg()).isEqualByComparingTo("60.00");
        assertThat(response.getHirePrice()).isEqualByComparingTo("750.00");
        assertThat(response.getBio()).isEqualTo("Old bio");
        assertThat(response.getAwards()).isEqualTo("Golden Cup");
        assertThat(response.getAchievements()).isEqualTo("10 wins");
        assertThat(response.getSpecialties()).isEqualTo("Sprint");
        assertThat(response.getAvatarUrl()).isEqualTo("https://cdn.example/existing-avatar.png");
        assertThat(response.getLicenseDocumentUrl()).isEqualTo("https://cdn.example/existing-license.pdf");
        assertThat(response.getStatus()).isEqualTo(JockeyStatus.PENDING);
        assertThat(response.getReviewReason()).isNull();
    }

    @Test
    void updateJockeyProfileRejectsMissingProfile() {
        JockeyProfileServiceImpl jockeyProfileService = jockeyProfileService();
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jockeyProfileService.updateMyProfile(2L, jockeyProfileUpdateRequest(), null, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("JockeyProfile not found with userId: '2'");

        verify(jockeyProfileRepository, never()).save(any(JockeyProfile.class));
    }

    @Test
    void updateJockeyProfileRejectsSuspendedProfile() {
        JockeyProfileServiceImpl jockeyProfileService = jockeyProfileService();
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        JockeyProfile profile = jockeyProfile(20L, jockey, JockeyStatus.SUSPENDED);

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> jockeyProfileService.updateMyProfile(2L, jockeyProfileUpdateRequest(), null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Suspended jockey profile cannot be updated");

        verify(jockeyProfileRepository, never()).save(any(JockeyProfile.class));
    }

    @Test
    void jockeyProfileRejectsDuplicateLicenseNumber() {
        JockeyProfileServiceImpl jockeyProfileService = jockeyProfileService();
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());
        when(jockeyProfileRepository.existsByLicenseNumberAndUserIdNot("LIC-2", 2L)).thenReturn(true);

        assertThatThrownBy(() -> jockeyProfileService.createMyProfile(2L, jockeyProfileRequest(), null, null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("License number already exists");

        verify(jockeyProfileRepository, never()).save(any(JockeyProfile.class));
    }

    @Test
    void createInvitationRequiresNoDuplicateActiveInvitation() {
        JockeyInvitationServiceImpl invitationService = invitationService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        Horse horse = horse(100L, owner, HorseStatus.APPROVED);
        JockeyProfile profile = jockeyProfile(20L, jockey, JockeyStatus.APPROVED);
        JockeyInvitationRequest request = invitationRequest(100L, 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(horseRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(horse));
        when(jockeyProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
        when(jockeyInvitationRepository.existsByHorseIdAndJockeyIdAndStatusIn(eq(100L), eq(2L), any()))
                .thenReturn(true);

        assertThatThrownBy(() -> invitationService.createInvitation(1L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Active invitation already exists for this horse and jockey");

        verify(jockeyInvitationRepository, never()).save(any(JockeyInvitation.class));
    }

    @Test
    void createInvitationHoldsHirePriceAndStoresTaxSnapshot() {
        JockeyInvitationServiceImpl invitationService = invitationService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        Horse horse = horse(100L, owner, HorseStatus.APPROVED);
        JockeyProfile profile = jockeyProfile(20L, jockey, JockeyStatus.APPROVED);
        JockeyInvitationRequest request = invitationRequest(100L, 2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(horseRepository.findByIdAndOwnerId(100L, 1L)).thenReturn(Optional.of(horse));
        when(jockeyProfileRepository.findByUserId(2L)).thenReturn(Optional.of(profile));
        when(jockeyInvitationRepository.existsByHorseIdAndJockeyIdAndStatusIn(eq(100L), eq(2L), any()))
                .thenReturn(false);
        when(financeSettingsService.getJockeyHireTaxPercent()).thenReturn(new BigDecimal("10.00"));
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> {
            JockeyInvitation invitation = invocation.getArgument(0);
            if (invitation.getId() == null) {
                invitation.setId(50L);
            }
            return invitation;
        });

        var response = invitationService.createInvitation(1L, request);

        assertThat(response.getHirePrice()).isEqualByComparingTo("500.00");
        assertThat(response.getTaxPercent()).isEqualByComparingTo("10.00");
        assertThat(response.getTaxAmount()).isEqualByComparingTo("50.00");
        assertThat(response.getJockeyPayoutAmount()).isEqualByComparingTo("450.00");
        assertThat(response.getFundsHeldAt()).isNotNull();
        verify(walletService).hold(eq(1L), eq(new BigDecimal("500.00")), eq(WalletTransactionType.JOCKEY_HIRE),
                eq("JOCKEY_INVITATION"), eq("50"), eq("jockey-invitation:50:hold"), any(), any());
        verify(walletService, never()).credit(eq(2L), any(BigDecimal.class), eq(WalletTransactionType.JOCKEY_PAYOUT),
                any(), any(), any(), any(), any());
        verify(walletService, never()).creditAdmin(any(BigDecimal.class), eq(WalletTransactionType.JOCKEY_HIRE_TAX),
                any(), any(), any(), any(), any());
    }

    @Test
    void cancelInvitationReleasesHeldHirePrice() {
        JockeyInvitationServiceImpl invitationService = invitationService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        JockeyInvitation invitation = fundedInvitation(50L, owner, jockey, HorseStatus.APPROVED, JockeyStatus.APPROVED);

        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(jockeyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = invitationService.cancelInvitation(1L, 50L);

        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        verify(walletService).release(eq(1L), eq(new BigDecimal("500.00")), eq(WalletTransactionType.JOCKEY_HIRE),
                eq("JOCKEY_INVITATION"), eq("50"), eq("jockey-invitation:50:release"), any(), any());
        verify(walletService, never()).credit(eq(2L), any(BigDecimal.class), eq(WalletTransactionType.JOCKEY_PAYOUT),
                any(), any(), any(), any(), any());
        verify(walletService, never()).creditAdmin(any(BigDecimal.class), eq(WalletTransactionType.JOCKEY_HIRE_TAX),
                any(), any(), any(), any(), any());
    }

    @Test
    void rejectInvitationReleasesHeldHirePrice() {
        JockeyInvitationServiceImpl invitationService = invitationService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        JockeyInvitation invitation = fundedInvitation(50L, owner, jockey, HorseStatus.APPROVED, JockeyStatus.APPROVED);

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = invitationService.rejectInvitation(2L, 50L, null);

        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.REJECTED);
        verify(walletService).release(eq(1L), eq(new BigDecimal("500.00")), eq(WalletTransactionType.JOCKEY_HIRE),
                eq("JOCKEY_INVITATION"), eq("50"), eq("jockey-invitation:50:release"), any(), any());
        verify(walletService, never()).credit(eq(2L), any(BigDecimal.class), eq(WalletTransactionType.JOCKEY_PAYOUT),
                any(), any(), any(), any(), any());
        verify(walletService, never()).creditAdmin(any(BigDecimal.class), eq(WalletTransactionType.JOCKEY_HIRE_TAX),
                any(), any(), any(), any(), any());
    }

    @Test
    void acceptInvitationCapturesHeldHireAndCreditsJockeyAndAdminTax() {
        JockeyInvitationServiceImpl invitationService = invitationService();
        User owner = user(1L, "owner-one", UserRole.OWNER);
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        JockeyInvitation invitation = fundedInvitation(50L, owner, jockey, HorseStatus.APPROVED, JockeyStatus.APPROVED);

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));
        when(jockeyInvitationRepository.save(any(JockeyInvitation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = invitationService.acceptInvitation(2L, 50L, null);

        assertThat(response.getStatus()).isEqualTo(AssignmentStatus.ACCEPTED);
        assertThat(response.getPaidAt()).isNotNull();
        verify(walletService).capture(eq(1L), eq(new BigDecimal("500.00")), eq(WalletTransactionType.JOCKEY_HIRE),
                eq("JOCKEY_INVITATION"), eq("50"), eq("jockey-invitation:50:capture"), any(), any());
        verify(walletService).credit(eq(2L), eq(new BigDecimal("450.00")), eq(WalletTransactionType.JOCKEY_PAYOUT),
                eq("JOCKEY_INVITATION"), eq("50"), eq("jockey-invitation:50:jockey-payout"), any(), any());
        verify(walletService).creditAdmin(eq(new BigDecimal("50.00")), eq(WalletTransactionType.JOCKEY_HIRE_TAX),
                eq("JOCKEY_INVITATION"), eq("50"), eq("jockey-invitation:50:admin-tax"), any(), any());
    }

    @Test
    void jockeyCannotAcceptInvitationForAnotherJockey() {
        JockeyInvitationServiceImpl invitationService = invitationService();
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        User otherJockey = user(3L, "jockey-two", UserRole.JOCKEY);
        User owner = user(1L, "owner-one", UserRole.OWNER);
        JockeyInvitation invitation = invitation(50L, owner, otherJockey, HorseStatus.APPROVED, JockeyStatus.APPROVED);

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> invitationService.acceptInvitation(2L, 50L, null))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Cannot respond to another jockey's invitation");

        verify(jockeyInvitationRepository, never()).save(any(JockeyInvitation.class));
    }

    @Test
    void suspendedJockeyCannotAcceptInvitation() {
        JockeyInvitationServiceImpl invitationService = invitationService();
        User jockey = user(2L, "jockey-one", UserRole.JOCKEY);
        User owner = user(1L, "owner-one", UserRole.OWNER);
        JockeyInvitation invitation = invitation(50L, owner, jockey, HorseStatus.APPROVED, JockeyStatus.SUSPENDED);

        when(userRepository.findById(2L)).thenReturn(Optional.of(jockey));
        when(jockeyInvitationRepository.findById(50L)).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> invitationService.acceptInvitation(2L, 50L, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Jockey profile is no longer approved");

        verify(jockeyInvitationRepository, never()).save(any(JockeyInvitation.class));
    }

    private JockeyInvitationServiceImpl invitationService() {
        return new JockeyInvitationServiceImpl(
                jockeyInvitationRepository,
                horseRepository,
                jockeyProfileRepository,
                userRepository,
                walletService,
                financeSettingsService
        );
    }

    private HorseServiceImpl horseService() {
        return new HorseServiceImpl(horseRepository, userRepository, cloudinaryUploadService,
                jockeyInvitationRepository, raceRegistrationRepository, raceParticipantRepository,
                raceResultRepository);
    }

    private JockeyProfileServiceImpl jockeyProfileService() {
        return new JockeyProfileServiceImpl(jockeyProfileRepository, userRepository, cloudinaryUploadService);
    }

    private HorseRequest horseRequest() {
        HorseRequest request = new HorseRequest();
        request.setName("Lightning");
        return request;
    }

    private HorseUpdateRequest horseUpdateRequest() {
        HorseUpdateRequest request = new HorseUpdateRequest();
        request.setName("Lightning");
        return request;
    }

    private JockeyInvitationRequest invitationRequest(Long horseId, Long jockeyId) {
        JockeyInvitationRequest request = new JockeyInvitationRequest();
        request.setHorseId(horseId);
        request.setJockeyId(jockeyId);
        request.setMessage("Please ride Lightning");
        return request;
    }

    private JockeyProfileRequest jockeyProfileRequest() {
        JockeyProfileRequest request = new JockeyProfileRequest();
        request.setLicenseNumber("LIC-2");
        request.setHirePrice(new BigDecimal("500.00"));
        request.setAwards("Golden Cup");
        request.setSpecialties("Sprint");
        return request;
    }

    private JockeyProfileUpdateRequest jockeyProfileUpdateRequest() {
        JockeyProfileUpdateRequest request = new JockeyProfileUpdateRequest();
        request.setLicenseNumber("LIC-2");
        request.setHirePrice(new BigDecimal("500.00"));
        request.setAwards("Golden Cup");
        request.setSpecialties("Sprint");
        return request;
    }

    private User user(Long id, String username, UserRole role) {
        return User.builder()
                .id(id)
                .username(username)
                .email(username + "@example.com")
                .role(role)
                .build();
    }

    private Horse horse(Long id, User owner, HorseStatus status) {
        return Horse.builder()
                .id(id)
                .owner(owner)
                .name("Lightning")
                .status(status)
                .build();
    }

    private JockeyProfile jockeyProfile(Long id, User jockey, JockeyStatus status) {
        return JockeyProfile.builder()
                .id(id)
                .user(jockey)
                .licenseNumber("LIC-" + jockey.getId())
                .hirePrice(new BigDecimal("500.00"))
                .status(status)
                .build();
    }

    private JockeyInvitation invitation(
            Long id,
            User owner,
            User jockey,
            HorseStatus horseStatus,
            JockeyStatus jockeyStatus) {
        Horse horse = horse(100L, owner, horseStatus);
        JockeyProfile profile = jockeyProfile(20L, jockey, jockeyStatus);
        return JockeyInvitation.builder()
                .id(id)
                .owner(owner)
                .jockey(jockey)
                .horse(horse)
                .jockeyProfile(profile)
                .status(AssignmentStatus.PENDING)
                .build();
    }

    private JockeyInvitation fundedInvitation(
            Long id,
            User owner,
            User jockey,
            HorseStatus horseStatus,
            JockeyStatus jockeyStatus) {
        JockeyInvitation invitation = invitation(id, owner, jockey, horseStatus, jockeyStatus);
        invitation.setHirePrice(new BigDecimal("500.00"));
        invitation.setTaxPercent(new BigDecimal("10.00"));
        invitation.setTaxAmount(new BigDecimal("50.00"));
        invitation.setJockeyPayoutAmount(new BigDecimal("450.00"));
        invitation.setFundsHeldAt(LocalDateTime.now());
        return invitation;
    }
}
