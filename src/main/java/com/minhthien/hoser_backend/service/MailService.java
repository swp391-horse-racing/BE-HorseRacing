package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceComplaint;
import com.minhthien.hoser_backend.entity.Tournament;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;

public interface MailService {

    void sendOtp(String email, String otp);

    void sendRoleApplicationApproved(User user, UserRole role);

    void sendRoleApplicationRejected(User user, UserRole role, String reason);

    void sendRaceScheduled(Race race, User recipient);

    void sendRaceReminder(Race race, User recipient);

    void sendRaceComplaintCreated(RaceComplaint complaint);

    default void sendTournamentRegistrationOpen(Tournament tournament, User recipient) {
    }

    default void sendTwoFactorOtp(User recipient, String otp) {
    }

    default void sendRegistrationCreated(User recipient, String raceName, String referenceType, String referenceId) {
    }

    default void sendRegistrationApproved(User recipient, String raceName, String referenceType, String referenceId) {
    }

    default void sendRegistrationRejected(User recipient, String raceName, String referenceType, String referenceId) {
    }

    default void sendDepositStatus(User recipient, String status, String referenceType, String referenceId) {
    }

    default void sendWithdrawalStatus(User recipient, String status, String referenceType, String referenceId) {
    }

    default void sendRaceResultPublished(Race race, User recipient, String referenceType, String referenceId) {
    }

    default void sendPrizePayout(User recipient, String subject, String message, String referenceType, String referenceId) {
    }

    default void sendAnnouncement(User recipient, String subject, String message,
                                  String referenceType, String referenceId) {
    }
}
