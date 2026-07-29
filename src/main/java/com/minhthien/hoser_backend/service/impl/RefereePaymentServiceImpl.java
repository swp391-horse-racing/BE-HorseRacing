package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.dto.response.RefereeRacePaymentResponse;
import com.minhthien.hoser_backend.entity.AdminAuditLog;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RefereeRacePayment;
import com.minhthien.hoser_backend.entity.RefereeSalaryConfig;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.NotificationType;
import com.minhthien.hoser_backend.enums.RefereePaymentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.exception.ResourceNotFoundException;
import com.minhthien.hoser_backend.repository.AdminAuditLogRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RefereeRacePaymentRepository;
import com.minhthien.hoser_backend.repository.RefereeSalaryConfigRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.NotificationService;
import com.minhthien.hoser_backend.service.RefereePaymentService;
import com.minhthien.hoser_backend.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RefereePaymentServiceImpl implements RefereePaymentService {
    private static final String REFERENCE_TYPE = "REFEREE_RACE_PAYMENT";

    private final RefereeRacePaymentRepository paymentRepository;
    private final RefereeSalaryConfigRepository salaryConfigRepository;
    private final RaceRepository raceRepository;
    private final UserRepository userRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final WalletService walletService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public RefereeRacePayment reserveForAssignment(Long adminId, Race race, User referee, Long salaryConfigId) {
        RefereeRacePayment existing = paymentRepository.findByRaceId(race.getId()).orElse(null);
        if (existing != null) {
            if (!existing.getReferee().getId().equals(referee.getId())) {
                throw new BadRequestException("Race referee cannot be changed after salary is reserved");
            }
            if (!existing.getSalaryConfig().getId().equals(salaryConfigId)) {
                throw new BadRequestException("Salary config cannot be changed after salary is reserved");
            }
            return existing;
        }

        RefereeSalaryConfig salaryConfig = salaryConfigRepository.findById(salaryConfigId)
                .orElseThrow(() -> new ResourceNotFoundException("RefereeSalaryConfig", "id", salaryConfigId));
        if (!Boolean.TRUE.equals(salaryConfig.getActive())) {
            throw new BadRequestException("Referee salary config is inactive");
        }
        BigDecimal amount = salaryConfig.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Referee per-race fee must be greater than zero");
        }

        String baseKey = "referee-race-payment:" + race.getId();
        walletService.holdAdmin(amount, WalletTransactionType.REFEREE_PAYOUT,
                REFERENCE_TYPE, String.valueOf(race.getId()), baseKey + ":admin-hold",
                metadata(race, referee), "Referee salary reserved for race");

        LocalDateTime now = LocalDateTime.now();
        RefereeRacePayment payment = paymentRepository.save(RefereeRacePayment.builder()
                .race(race)
                .referee(referee)
                .salaryConfig(salaryConfig)
                .amount(amount)
                .status(RefereePaymentStatus.HELD)
                .holdIdempotencyKey(baseKey + ":admin-hold")
                .captureIdempotencyKey(baseKey + ":admin-capture")
                .creditIdempotencyKey(baseKey + ":referee-credit")
                .heldAt(now)
                .build());
        audit(adminId, "REFEREE_SALARY_HELD", payment, "Referee salary reserved");
        return payment;
    }

    @Override
    @Transactional(noRollbackFor = BadRequestException.class)
    public RefereeRacePayment payForCompletedRace(Race race) {
        RefereeRacePayment payment = paymentRepository.findByRaceId(race.getId())
                .orElseThrow(() -> new BadRequestException(
                        "Referee salary must be reserved before race result is finalized"));
        if (payment.getStatus() == RefereePaymentStatus.PAID) {
            return payment;
        }
        if (payment.getStatus() != RefereePaymentStatus.HELD) {
            throw new BadRequestException("Referee salary is not available for payment");
        }
        if (race.getReferee() == null || !race.getReferee().getId().equals(payment.getReferee().getId())) {
            throw new BadRequestException("Reserved referee salary does not match assigned referee");
        }

        String referenceId = String.valueOf(race.getId());
        String metadata = metadata(race, payment.getReferee());
        walletService.captureAdmin(payment.getAmount(), WalletTransactionType.REFEREE_PAYOUT,
                REFERENCE_TYPE, referenceId, payment.getCaptureIdempotencyKey(),
                metadata, "Referee salary paid for completed race");
        walletService.credit(payment.getReferee().getId(), payment.getAmount(), WalletTransactionType.REFEREE_PAYOUT,
                REFERENCE_TYPE, referenceId, payment.getCreditIdempotencyKey(),
                metadata, "Referee salary received for completed race");

        payment.setStatus(RefereePaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        RefereeRacePayment saved = paymentRepository.save(payment);
        notificationService.notify(payment.getReferee(), NotificationType.REFEREE_PAYOUT_PAID,
                "Referee salary received",
                "You received " + payment.getAmount().toPlainString() + " VND for race " + race.getName(),
                REFERENCE_TYPE, referenceId,
                "{\"amount\":\"%s\",\"status\":\"PAID\"}".formatted(payment.getAmount().toPlainString()));
        return saved;
    }

    @Override
    @Transactional
    public void releaseForCancelledRace(Long adminId, Race race) {
        RefereeRacePayment payment = paymentRepository.findByRaceId(race.getId()).orElse(null);
        if (payment == null || payment.getStatus() != RefereePaymentStatus.HELD) {
            return;
        }
        walletService.releaseAdmin(payment.getAmount(), WalletTransactionType.REFEREE_PAYOUT,
                REFERENCE_TYPE, String.valueOf(race.getId()), payment.getHoldIdempotencyKey() + ":release",
                metadata(race, payment.getReferee()), "Referee salary released after race cancellation");
        payment.setStatus(RefereePaymentStatus.RELEASED);
        payment.setReleasedAt(LocalDateTime.now());
        paymentRepository.save(payment);
        audit(adminId, "REFEREE_SALARY_RELEASED", payment,
                "Referee salary released after race cancellation");
    }

    @Override
    @Transactional(readOnly = true)
    public RefereeRacePaymentResponse getAdminRacePayment(Long adminId, Long raceId) {
        requireRole(adminId, UserRole.ADMIN, "Only admins can view referee payments");
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race", "id", raceId);
        }
        return paymentRepository.findByRaceId(raceId)
                .map(this::map)
                .orElseThrow(() -> new ResourceNotFoundException("RefereeRacePayment", "raceId", raceId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefereeRacePaymentResponse> getRefereePayments(Long refereeId) {
        requireRole(refereeId, UserRole.REFEREE, "Only referees can view their payments");
        return paymentRepository.findByRefereeIdOrderByCreatedAtDesc(refereeId).stream()
                .map(this::map)
                .toList();
    }

    private RefereeRacePaymentResponse map(RefereeRacePayment payment) {
        Race race = payment.getRace();
        return RefereeRacePaymentResponse.builder()
                .id(payment.getId())
                .raceId(race.getId())
                .raceName(race.getName())
                .tournamentId(race.getTournament().getId())
                .tournamentName(race.getTournament().getName())
                .refereeId(payment.getReferee().getId())
                .refereeUsername(payment.getReferee().getUsername())
                .salaryConfigId(payment.getSalaryConfig().getId())
                .salaryConfigName(payment.getSalaryConfig().getName())
                .raceType(payment.getSalaryConfig().getRaceType())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .heldAt(payment.getHeldAt())
                .paidAt(payment.getPaidAt())
                .releasedAt(payment.getReleasedAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private User requireRole(Long userId, UserRole role, String message) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        if (user.getRole() != role) {
            throw new BadRequestException(message);
        }
        return user;
    }

    private void audit(Long adminId, String action, RefereeRacePayment payment, String reason) {
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .referenceType(REFERENCE_TYPE)
                .referenceId(String.valueOf(payment.getRace().getId()))
                .amount(payment.getAmount())
                .reason(reason)
                .metadata(metadata(payment.getRace(), payment.getReferee()))
                .build());
    }

    private String metadata(Race race, User referee) {
        return "{\"raceId\":%d,\"refereeId\":%d}".formatted(race.getId(), referee.getId());
    }
}
