package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RefereeRacePayment;
import com.minhthien.hoser_backend.entity.RefereeSalaryConfig;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.RefereePaymentStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.enums.WalletTransactionType;
import com.minhthien.hoser_backend.exception.BadRequestException;
import com.minhthien.hoser_backend.repository.AdminAuditLogRepository;
import com.minhthien.hoser_backend.repository.RaceRepository;
import com.minhthien.hoser_backend.repository.RefereeRacePaymentRepository;
import com.minhthien.hoser_backend.repository.RefereeSalaryConfigRepository;
import com.minhthien.hoser_backend.repository.UserRepository;
import com.minhthien.hoser_backend.service.NotificationService;
import com.minhthien.hoser_backend.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefereePaymentServiceImplTest {
    @Mock private RefereeRacePaymentRepository paymentRepository;
    @Mock private RefereeSalaryConfigRepository salaryConfigRepository;
    @Mock private RaceRepository raceRepository;
    @Mock private UserRepository userRepository;
    @Mock private AdminAuditLogRepository auditLogRepository;
    @Mock private WalletService walletService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private RefereePaymentServiceImpl service;

    @Test
    void assignmentReservesConfiguredSalaryExactlyOnce() {
        Race race = race();
        User referee = referee();
        RefereeSalaryConfig config = salaryConfig();
        when(paymentRepository.findByRaceId(10L)).thenReturn(Optional.empty());
        when(salaryConfigRepository.findById(40L)).thenReturn(Optional.of(config));
        when(paymentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RefereeRacePayment first = service.reserveForAssignment(1L, race, referee, 40L);
        when(paymentRepository.findByRaceId(10L)).thenReturn(Optional.of(first));
        RefereeRacePayment second = service.reserveForAssignment(1L, race, referee, 40L);

        assertSame(first, second);
        assertEquals(RefereePaymentStatus.HELD, first.getStatus());
        assertEquals(new BigDecimal("500000"), first.getAmount());
        verify(walletService, times(1)).holdAdmin(
                eq(new BigDecimal("500000")), eq(WalletTransactionType.REFEREE_PAYOUT),
                eq("REFEREE_RACE_PAYMENT"), eq("10"), anyString(), anyString(), anyString());
    }

    @Test
    void assignmentRejectsChangingReservedReferee() {
        Race race = race();
        User original = referee();
        User replacement = User.builder().id(3L).role(UserRole.REFEREE).build();
        when(paymentRepository.findByRaceId(10L)).thenReturn(Optional.of(payment(race, original)));

        assertThrows(BadRequestException.class,
                () -> service.reserveForAssignment(1L, race, replacement, 40L));
        verifyNoInteractions(walletService);
    }

    @Test
    void completedRaceCapturesAdminHoldAndCreditsReferee() {
        Race race = race();
        User referee = referee();
        race.setReferee(referee);
        RefereeRacePayment payment = payment(race, referee);
        when(paymentRepository.findByRaceId(10L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(payment)).thenReturn(payment);

        RefereeRacePayment result = service.payForCompletedRace(race);

        assertEquals(RefereePaymentStatus.PAID, result.getStatus());
        assertNotNull(result.getPaidAt());
        verify(walletService).captureAdmin(
                eq(payment.getAmount()), eq(WalletTransactionType.REFEREE_PAYOUT),
                eq("REFEREE_RACE_PAYMENT"), eq("10"), eq(payment.getCaptureIdempotencyKey()),
                anyString(), anyString());
        verify(walletService).credit(
                eq(2L), eq(payment.getAmount()), eq(WalletTransactionType.REFEREE_PAYOUT),
                eq("REFEREE_RACE_PAYMENT"), eq("10"), eq(payment.getCreditIdempotencyKey()),
                anyString(), anyString());
    }

    @Test
    void paidRaceIsIdempotent() {
        Race race = race();
        User referee = referee();
        race.setReferee(referee);
        RefereeRacePayment payment = payment(race, referee);
        payment.setStatus(RefereePaymentStatus.PAID);
        when(paymentRepository.findByRaceId(10L)).thenReturn(Optional.of(payment));

        assertSame(payment, service.payForCompletedRace(race));
        verifyNoInteractions(walletService);
    }

    @Test
    void cancellationReleasesHeldSalary() {
        Race race = race();
        User referee = referee();
        RefereeRacePayment payment = payment(race, referee);
        when(paymentRepository.findByRaceId(10L)).thenReturn(Optional.of(payment));

        service.releaseForCancelledRace(1L, race);

        assertEquals(RefereePaymentStatus.RELEASED, payment.getStatus());
        assertNotNull(payment.getReleasedAt());
        verify(walletService).releaseAdmin(
                eq(payment.getAmount()), eq(WalletTransactionType.REFEREE_PAYOUT),
                eq("REFEREE_RACE_PAYMENT"), eq("10"), anyString(), anyString(), anyString());
    }

    @Test
    void completionRequiresReservedSalary() {
        when(paymentRepository.findByRaceId(10L)).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class, () -> service.payForCompletedRace(race()));
        verifyNoInteractions(walletService);
    }

    private Race race() {
        return Race.builder()
                .id(10L)
                .name("Race 1")
                .tournament(Tournament.builder().id(20L).name("Cup").build())
                .build();
    }

    private User referee() {
        return User.builder().id(2L).username("referee").role(UserRole.REFEREE).build();
    }

    private RefereeRacePayment payment(Race race, User referee) {
        return RefereeRacePayment.builder()
                .id(30L)
                .race(race)
                .referee(referee)
                .salaryConfig(salaryConfig())
                .amount(new BigDecimal("500000"))
                .status(RefereePaymentStatus.HELD)
                .holdIdempotencyKey("hold")
                .captureIdempotencyKey("capture")
                .creditIdempotencyKey("credit")
                .heldAt(java.time.LocalDateTime.now())
                .build();
    }

    private RefereeSalaryConfig salaryConfig() {
        return RefereeSalaryConfig.builder()
                .id(40L)
                .name("Standard")
                .raceType("STANDARD")
                .amount(new BigDecimal("500000"))
                .active(true)
                .createdBy(1L)
                .updatedBy(1L)
                .build();
    }
}
