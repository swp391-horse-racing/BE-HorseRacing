package com.minhthien.hoser_backend.service;

import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceComplaint;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.enums.UserRole;

public interface MailService {

    void sendOtp(String email, String otp);

    void sendRoleApplicationApproved(User user, UserRole role);

    void sendRoleApplicationRejected(User user, UserRole role, String reason);

    void sendRaceScheduled(Race race, User recipient);

    void sendRaceReminder(Race race, User recipient);

    void sendRaceComplaintCreated(RaceComplaint complaint);

}
