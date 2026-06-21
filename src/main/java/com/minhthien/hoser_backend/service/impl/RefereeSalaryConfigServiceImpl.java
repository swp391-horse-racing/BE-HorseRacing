package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.request.RefereeSalaryConfigRequest;
import com.minhthien.hoser_backend.dto.response.RefereeSalaryConfigResponse;
import com.minhthien.hoser_backend.entity.AdminAuditLog;
import com.minhthien.hoser_backend.entity.RefereeSalaryConfig;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.AdminAuditLogRepository;
import com.minhthien.hoser_backend.repository.RefereeRacePaymentRepository;
import com.minhthien.hoser_backend.repository.RefereeSalaryConfigRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.RefereeSalaryConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefereeSalaryConfigServiceImpl implements RefereeSalaryConfigService {
    private static final String REFERENCE_TYPE = "REFEREE_SALARY_CONFIG";

    private final RefereeSalaryConfigRepository configRepository;
    private final RefereeRacePaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public RefereeSalaryConfigResponse create(Long adminId, RefereeSalaryConfigRequest request) {
        requireAdmin(adminId);
        String name = normalizeName(request.getName());
        if (configRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("Referee salary config name already exists");
        }
        RefereeSalaryConfig config = configRepository.save(RefereeSalaryConfig.builder()
                .name(name)
                .raceType(normalizeRaceType(request.getRaceType()))
                .amount(normalizeAmount(request.getAmount()))
                .active(request.getActive())
                .createdBy(adminId)
                .updatedBy(adminId)
                .build());
        audit(adminId, "REFEREE_SALARY_CONFIG_CREATED", config);
        return map(config);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefereeSalaryConfigResponse> getAll(Long adminId) {
        requireAdmin(adminId);
        return configRepository.findAllByOrderByCreatedAtDesc().stream().map(this::map).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RefereeSalaryConfigResponse getById(Long adminId, Long id) {
        requireAdmin(adminId);
        return map(requireConfig(id));
    }

    @Override
    @Transactional
    public RefereeSalaryConfigResponse update(Long adminId, Long id, RefereeSalaryConfigRequest request) {
        requireAdmin(adminId);
        RefereeSalaryConfig config = requireConfig(id);
        String name = normalizeName(request.getName());
        if (configRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new BadRequestException("Referee salary config name already exists");
        }
        config.setName(name);
        config.setRaceType(normalizeRaceType(request.getRaceType()));
        config.setAmount(normalizeAmount(request.getAmount()));
        config.setActive(request.getActive());
        config.setUpdatedBy(adminId);
        RefereeSalaryConfig saved = configRepository.save(config);
        audit(adminId, "REFEREE_SALARY_CONFIG_UPDATED", saved);
        return map(saved);
    }

    @Override
    @Transactional
    public void delete(Long adminId, Long id) {
        requireAdmin(adminId);
        RefereeSalaryConfig config = requireConfig(id);
        if (paymentRepository.existsBySalaryConfigId(id)) {
            throw new BadRequestException(
                    "Referee salary config is already in use; deactivate it instead of deleting it");
        }
        audit(adminId, "REFEREE_SALARY_CONFIG_DELETED", config);
        configRepository.delete(config);
    }

    private RefereeSalaryConfig requireConfig(Long id) {
        return configRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RefereeSalaryConfig", "id", id));
    }

    private User requireAdmin(Long adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", adminId));
        if (admin.getRole() != UserRole.ADMIN) {
            throw new BadRequestException("Only admins can manage referee salary configs");
        }
        return admin;
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Salary config name is required");
        }
        return value.trim();
    }

    private String normalizeRaceType(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Race type is required");
        }
        return value.trim();
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Salary amount must be greater than zero");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private RefereeSalaryConfigResponse map(RefereeSalaryConfig config) {
        return RefereeSalaryConfigResponse.builder()
                .id(config.getId())
                .name(config.getName())
                .raceType(config.getRaceType())
                .amount(config.getAmount())
                .active(config.getActive())
                .createdBy(config.getCreatedBy())
                .updatedBy(config.getUpdatedBy())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private void audit(Long adminId, String action, RefereeSalaryConfig config) {
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .referenceType(REFERENCE_TYPE)
                .referenceId(config.getId() == null ? null : String.valueOf(config.getId()))
                .amount(config.getAmount())
                .reason(action)
                .metadata("name=%s;raceType=%s;active=%s"
                        .formatted(config.getName(), config.getRaceType(), config.getActive()))
                .build());
    }
}
