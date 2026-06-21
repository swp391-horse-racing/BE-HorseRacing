package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RefereeSalaryConfigRequest;
import com.minhthien.hoser_backend.entity.RefereeSalaryConfig;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.AdminAuditLogRepository;
import com.minhthien.hoser_backend.repository.RefereeRacePaymentRepository;
import com.minhthien.hoser_backend.repository.RefereeSalaryConfigRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefereeSalaryConfigServiceImplTest {
    @Mock private RefereeSalaryConfigRepository configRepository;
    @Mock private RefereeRacePaymentRepository paymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private AdminAuditLogRepository auditLogRepository;

    @InjectMocks
    private RefereeSalaryConfigServiceImpl service;

    @Test
    void adminCanCreateListReadAndUpdateConfig() {
        User admin = admin();
        RefereeSalaryConfigRequest request = request("Standard", "STANDARD", "500000", true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(configRepository.existsByNameIgnoreCase("Standard")).thenReturn(false);
        when(configRepository.save(any())).thenAnswer(invocation -> {
            RefereeSalaryConfig config = invocation.getArgument(0);
            config.setId(10L);
            return config;
        });

        var created = service.create(1L, request);
        RefereeSalaryConfig entity = config("Standard");
        when(configRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(entity));
        when(configRepository.findById(10L)).thenReturn(Optional.of(entity));
        when(configRepository.existsByNameIgnoreCaseAndIdNot("Premium", 10L)).thenReturn(false);

        var listed = service.getAll(1L);
        var found = service.getById(1L, 10L);
        var updated = service.update(1L, 10L,
                request("Premium", "FINAL", "800000", false));

        assertEquals(new BigDecimal("500000.00"), created.getAmount());
        assertEquals(1, listed.size());
        assertEquals(10L, found.getId());
        assertEquals("Premium", updated.getName());
        assertEquals(new BigDecimal("800000.00"), updated.getAmount());
        assertFalse(updated.getActive());
        verify(auditLogRepository, times(2)).save(any());
    }

    @Test
    void duplicateNameIsRejected() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin()));
        when(configRepository.existsByNameIgnoreCase("Standard")).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> service.create(1L, request("Standard", "STANDARD", "500000", true)));
        verify(configRepository, never()).save(any());
    }

    @Test
    void usedConfigCannotBeDeleted() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin()));
        when(configRepository.findById(10L)).thenReturn(Optional.of(config("Standard")));
        when(paymentRepository.existsBySalaryConfigId(10L)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.delete(1L, 10L));
        verify(configRepository, never()).delete(any());
    }

    @Test
    void unusedConfigCanBeDeleted() {
        RefereeSalaryConfig config = config("Standard");
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin()));
        when(configRepository.findById(10L)).thenReturn(Optional.of(config));
        when(paymentRepository.existsBySalaryConfigId(10L)).thenReturn(false);

        service.delete(1L, 10L);

        verify(configRepository).delete(config);
        verify(auditLogRepository).save(any());
    }

    @Test
    void nonAdminCannotManageConfigs() {
        User referee = User.builder().id(2L).role(UserRole.REFEREE).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(referee));

        assertThrows(BadRequestException.class, () -> service.getAll(2L));
        verifyNoInteractions(configRepository);
    }

    private User admin() {
        return User.builder().id(1L).username("admin").role(UserRole.ADMIN).build();
    }

    private RefereeSalaryConfig config(String name) {
        return RefereeSalaryConfig.builder()
                .id(10L)
                .name(name)
                .raceType("STANDARD")
                .amount(new BigDecimal("500000.00"))
                .active(true)
                .createdBy(1L)
                .updatedBy(1L)
                .build();
    }

    private RefereeSalaryConfigRequest request(String name, String raceType, String amount, boolean active) {
        RefereeSalaryConfigRequest request = new RefereeSalaryConfigRequest();
        request.setName(name);
        request.setRaceType(raceType);
        request.setAmount(new BigDecimal(amount));
        request.setActive(active);
        return request;
    }
}
