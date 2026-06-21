package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.dto.response.RefereeRacePaymentResponse;
import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RefereeRacePayment;
import com.minhthien.hoser_backend.entity.User;

import java.util.List;

public interface RefereePaymentService {
    RefereeRacePayment reserveForAssignment(Long adminId, Race race, User referee, Long salaryConfigId);

    RefereeRacePayment payForCompletedRace(Race race);

    void releaseForCancelledRace(Long adminId, Race race);

    RefereeRacePaymentResponse getAdminRacePayment(Long adminId, Long raceId);

    List<RefereeRacePaymentResponse> getRefereePayments(Long refereeId);
}
