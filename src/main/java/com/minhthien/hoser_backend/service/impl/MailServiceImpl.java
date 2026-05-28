package com.minhthien.hoser_backend.service.impl;

import com.minhthien.hoser_backend.entity.Race;
import com.minhthien.hoser_backend.entity.RaceComplaint;
import com.minhthien.hoser_backend.entity.User;
import com.minhthien.hoser_backend.entity.EmailEventLog;
import com.minhthien.hoser_backend.enums.EmailEventStatus;
import com.minhthien.hoser_backend.enums.UserRole;
import com.minhthien.hoser_backend.repository.EmailEventLogRepository;
import com.minhthien.hoser_backend.service.MailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;

@Service
public class MailServiceImpl implements MailService {
    private static final String BRAND = "HORSE";
    private static final String OTP_SUBJECT = "HORSE - Mã OTP đặt lại mật khẩu";
    private static final String ROLE_APPROVED_SUBJECT = "HORSE - Hồ sơ đăng ký vai trò đã được duyệt";
    private static final String ROLE_REJECTED_SUBJECT = "HORSE - Hồ sơ đăng ký vai trò cần bổ sung";
    private static final int OTP_EXPIRES_IN_MINUTES = 10;
    private static final String RACE_SCHEDULED_SUBJECT = "HORSE - Race schedule published";
    private static final String RACE_REMINDER_SUBJECT = "HORSE - Race reminder";
    private static final String RACE_COMPLAINT_SUBJECT = "HORSE - Race complaint received";
    private static final String REGISTRATION_CREATED_SUBJECT = "HORSE - Race registration received";
    private static final String REGISTRATION_APPROVED_SUBJECT = "HORSE - Race registration approved";
    private static final String REGISTRATION_REJECTED_SUBJECT = "HORSE - Race registration rejected";
    private static final String DEPOSIT_STATUS_SUBJECT = "HORSE - Deposit status";
    private static final String WITHDRAWAL_STATUS_SUBJECT = "HORSE - Withdrawal status";
    private static final String RACE_RESULT_SUBJECT = "HORSE - Race result published";
    private static final DateTimeFormatter RACE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JavaMailSender mailSender;
    private final EmailEventLogRepository emailEventLogRepository;

    @Autowired
    public MailServiceImpl(JavaMailSender mailSender, EmailEventLogRepository emailEventLogRepository) {
        this.mailSender = mailSender;
        this.emailEventLogRepository = emailEventLogRepository;
    }

    public MailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
        this.emailEventLogRepository = null;
    }

    @Override
    public void sendOtp(String email, String otp) {
        String formattedOtp = formatOtp(otp);
        sendHtmlEmail(
                email,
                OTP_SUBJECT,
                "OTP",
                null,
                null,
                buildOtpPlainText(formattedOtp),
                buildOtpHtml(formattedOtp)
        );
    }

    @Override
    public void sendRoleApplicationApproved(User user, UserRole role) {
        sendHtmlEmail(
                user.getEmail(),
                ROLE_APPROVED_SUBJECT,
                "ROLE_APPROVED",
                "USER",
                String.valueOf(user.getId()),
                buildApprovedPlainText(user, role),
                buildApprovedHtml(user, role)
        );
    }

    @Override
    public void sendRoleApplicationRejected(User user, UserRole role, String reason) {
        sendHtmlEmail(
                user.getEmail(),
                ROLE_REJECTED_SUBJECT,
                "ROLE_REJECTED",
                "USER",
                String.valueOf(user.getId()),
                buildRejectedPlainText(user, role, reason),
                buildRejectedHtml(user, role, reason)
        );
    }

    @Override
    public void sendRaceScheduled(Race race, User recipient) {
        sendHtmlEmail(
                recipient.getEmail(),
                RACE_SCHEDULED_SUBJECT,
                "RACE_SCHEDULED",
                "RACE",
                String.valueOf(race.getId()),
                buildRacePlainText(race, recipient, "Race schedule has been published."),
                buildRaceHtml(race, recipient, "Race schedule has been published.",
                        "Please review your race time and assignment.")
        );
    }

    @Override
    public void sendRaceReminder(Race race, User recipient) {
        sendHtmlEmail(
                recipient.getEmail(),
                RACE_REMINDER_SUBJECT,
                "RACE_REMINDER",
                "RACE",
                String.valueOf(race.getId()),
                buildRacePlainText(race, recipient, "Your race is coming soon."),
                buildRaceHtml(race, recipient, "Your race is coming soon.",
                        "This is the 3-day reminder for the scheduled race.")
        );
    }

    @Override
    public void sendRaceComplaintCreated(RaceComplaint complaint) {
        User recipient = complaint.getAccusedOwner();
        sendHtmlEmail(
                recipient.getEmail(),
                RACE_COMPLAINT_SUBJECT,
                "RACE_COMPLAINT",
                "RACE_COMPLAINT",
                String.valueOf(complaint.getId()),
                buildComplaintPlainText(complaint),
                buildComplaintHtml(complaint)
        );
    }

    @Override
    public void sendRegistrationCreated(User recipient, String raceName, String referenceType, String referenceId) {
        sendSimpleStatusEmail(recipient, REGISTRATION_CREATED_SUBJECT, "REGISTRATION_CREATED",
                "Your registration for " + raceName + " has been received.", referenceType, referenceId);
    }

    @Override
    public void sendRegistrationApproved(User recipient, String raceName, String referenceType, String referenceId) {
        sendSimpleStatusEmail(recipient, REGISTRATION_APPROVED_SUBJECT, "REGISTRATION_APPROVED",
                "Your registration for " + raceName + " has been approved.", referenceType, referenceId);
    }

    @Override
    public void sendRegistrationRejected(User recipient, String raceName, String referenceType, String referenceId) {
        sendSimpleStatusEmail(recipient, REGISTRATION_REJECTED_SUBJECT, "REGISTRATION_REJECTED",
                "Your registration for " + raceName + " has been rejected.", referenceType, referenceId);
    }

    @Override
    public void sendDepositStatus(User recipient, String status, String referenceType, String referenceId) {
        sendSimpleStatusEmail(recipient, DEPOSIT_STATUS_SUBJECT, "DEPOSIT_STATUS",
                "Your deposit status is " + status + ".", referenceType, referenceId);
    }

    @Override
    public void sendWithdrawalStatus(User recipient, String status, String referenceType, String referenceId) {
        sendSimpleStatusEmail(recipient, WITHDRAWAL_STATUS_SUBJECT, "WITHDRAWAL_STATUS",
                "Your withdrawal status is " + status + ".", referenceType, referenceId);
    }

    @Override
    public void sendRaceResultPublished(Race race, User recipient, String referenceType, String referenceId) {
        sendHtmlEmail(
                recipient.getEmail(),
                RACE_RESULT_SUBJECT,
                "RACE_RESULT_PUBLISHED",
                referenceType,
                referenceId,
                buildRacePlainText(race, recipient, "Race result has been published."),
                buildRaceHtml(race, recipient, "Race result has been published.",
                        "Please open the race result screen for the official standings.")
        );
    }

    @Override
    public void sendPrizePayout(User recipient, String subject, String message, String referenceType, String referenceId) {
        sendSimpleStatusEmail(recipient, subject, "PRIZE_PAYOUT", message, referenceType, referenceId);
    }

    private void sendSimpleStatusEmail(User recipient, String subject, String templateType, String message,
                                       String referenceType, String referenceId) {
        sendHtmlEmail(recipient.getEmail(), subject, templateType, referenceType, referenceId,
                "HORSE\n\nXin chao %s,\n\n%s\n\nEmail nay duoc gui tu dong tu HORSE."
                        .formatted(displayName(recipient), message),
                layoutHtml(subject, "Notification", subject, HtmlUtils.htmlEscape(message), "", "",
                        "#0f766e", "#d7f7f1"));
    }

    private void sendHtmlEmail(String to, String subject, String templateType, String referenceType,
                               String referenceId, String plainText, String html) {
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    MimeMessageHelper.MULTIPART_MODE_MIXED,
                    StandardCharsets.UTF_8.name()
            );
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(plainText, html);
            mailSender.send(message);
            recordEmail(to, subject, templateType, referenceType, referenceId, EmailEventStatus.SENT, null);
        } catch (MessagingException ex) {
            recordEmail(to, subject, templateType, referenceType, referenceId,
                    EmailEventStatus.FAILED, ex.getMessage());
            throw new MailPreparationException("Could not prepare email", ex);
        } catch (RuntimeException ex) {
            recordEmail(to, subject, templateType, referenceType, referenceId,
                    EmailEventStatus.FAILED, ex.getMessage());
            throw ex;
        }
    }

    private void recordEmail(String to, String subject, String templateType, String referenceType,
                             String referenceId, EmailEventStatus status, String errorMessage) {
        if (emailEventLogRepository == null) {
            return;
        }
        try {
            emailEventLogRepository.save(EmailEventLog.builder()
                    .toEmail(to)
                    .subject(subject)
                    .templateType(templateType)
                    .referenceType(referenceType)
                    .referenceId(referenceId)
                    .status(status)
                    .errorMessage(errorMessage)
                    .sentAt(status == EmailEventStatus.SENT ? java.time.LocalDateTime.now() : null)
                    .build());
        } catch (RuntimeException ignored) {
            // Email logging must not affect the calling workflow.
        }
    }

    private String formatOtp(String otp) {
        return otp == null ? "" : otp.replaceAll("(.)(?=.)", "$1 ");
    }

    private String buildOtpPlainText(String formattedOtp) {
        return """
                HORSE

                Mã OTP đặt lại mật khẩu của bạn là: %s

                Mã này sẽ hết hạn sau %d phút. Vui lòng không chia sẻ mã này với bất kỳ ai.

                Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.
                """.formatted(formattedOtp, OTP_EXPIRES_IN_MINUTES);
    }

    private String buildOtpHtml(String formattedOtp) {
        String safeOtp = HtmlUtils.htmlEscape(formattedOtp);

        return layoutHtml(
                OTP_SUBJECT,
                "Đặt lại mật khẩu",
                "Mã xác thực của bạn",
                "Nhập mã OTP bên dưới để tiếp tục đặt lại mật khẩu tài khoản HORSE.",
                """
                        <div style="display:inline-block;background:#eefcf8;border:1px solid #99f6e4;border-radius:8px;padding:16px 24px;font-size:32px;line-height:40px;font-weight:700;color:#0f766e;letter-spacing:6px;">%s</div>
                        <p style="margin:24px auto 0;max-width:420px;font-size:14px;line-height:22px;color:#526071;">Mã này sẽ hết hạn sau <strong style="color:#172033;">%d phút</strong>. Vui lòng không chia sẻ mã này với bất kỳ ai.</p>
                        """.formatted(safeOtp, OTP_EXPIRES_IN_MINUTES),
                """
                        <div style="background:#fff7ed;border:1px solid #fed7aa;border-radius:8px;padding:14px 16px;font-size:13px;line-height:20px;color:#9a3412;">
                            Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này. Tài khoản của bạn vẫn an toàn.
                        </div>
                        """,
                "#0f766e",
                "#d7f7f1"
        );
    }

    private String buildApprovedPlainText(User user, UserRole role) {
        return """
                HORSE

                Xin chào %s,

                Hồ sơ đăng ký vai trò %s của bạn đã được admin duyệt.

                Bạn có thể đăng nhập lại hoặc tải lại trang để bắt đầu sử dụng các chức năng dành cho vai trò này.

                Email này được gửi tự động từ HORSE.
                """.formatted(displayName(user), roleLabel(role));
    }

    private String buildApprovedHtml(User user, UserRole role) {
        String safeName = HtmlUtils.htmlEscape(displayName(user));
        String safeRole = HtmlUtils.htmlEscape(roleLabel(role));

        return layoutHtml(
                ROLE_APPROVED_SUBJECT,
                "Duyệt hồ sơ vai trò",
                "Hồ sơ của bạn đã được duyệt",
                "Xin chào " + safeName + ", hồ sơ đăng ký vai trò của bạn đã được admin duyệt.",
                """
                        <div style="background:#ecfdf5;border:1px solid #a7f3d0;border-radius:8px;padding:18px 20px;text-align:left;">
                            <div style="font-size:13px;line-height:18px;color:#047857;font-weight:700;text-transform:uppercase;">Vai trò được duyệt</div>
                            <div style="margin-top:6px;font-size:22px;line-height:30px;color:#064e3b;font-weight:700;">%s</div>
                        </div>
                        <a href="#" style="display:inline-block;margin-top:24px;background:#0f766e;color:#ffffff;text-decoration:none;border-radius:8px;padding:12px 18px;font-size:14px;line-height:20px;font-weight:700;">Đăng nhập và bắt đầu sử dụng HORSE</a>
                        <p style="margin:18px auto 0;max-width:420px;font-size:14px;line-height:22px;color:#526071;">Nếu bạn đang đăng nhập, hãy tải lại trang để cập nhật quyền mới.</p>
                        """.formatted(safeRole),
                "",
                "#0f766e",
                "#d7f7f1"
        );
    }

    private String buildRejectedPlainText(User user, UserRole role, String reason) {
        return """
                HORSE

                Xin chào %s,

                Hồ sơ đăng ký vai trò %s của bạn cần bổ sung thông tin.

                Lý do từ admin: %s

                Bạn có thể cập nhật hồ sơ và gửi lại để admin xem xét.

                Email này được gửi tự động từ HORSE.
                """.formatted(displayName(user), roleLabel(role), reason == null ? "" : reason);
    }

    private String buildRejectedHtml(User user, UserRole role, String reason) {
        String safeName = HtmlUtils.htmlEscape(displayName(user));
        String safeRole = HtmlUtils.htmlEscape(roleLabel(role));
        String safeReason = HtmlUtils.htmlEscape(reason == null ? "" : reason);

        return layoutHtml(
                ROLE_REJECTED_SUBJECT,
                "Cần bổ sung hồ sơ",
                "Hồ sơ của bạn cần cập nhật",
                "Xin chào " + safeName + ", hồ sơ đăng ký vai trò " + safeRole + " hiện chưa được duyệt.",
                """
                        <div style="background:#fff7ed;border:1px solid #fed7aa;border-radius:8px;padding:18px 20px;text-align:left;">
                            <div style="font-size:13px;line-height:18px;color:#9a3412;font-weight:700;text-transform:uppercase;">Lý do từ admin</div>
                            <div style="margin-top:8px;font-size:15px;line-height:24px;color:#7c2d12;">%s</div>
                        </div>
                        <p style="margin:24px auto 0;max-width:420px;font-size:14px;line-height:22px;color:#526071;">Bạn có thể chỉnh sửa hồ sơ hoặc gửi lại đăng ký vai trò khác sau khi cập nhật thông tin cần thiết.</p>
                        """.formatted(safeReason),
                "",
                "#b45309",
                "#ffedd5"
        );
    }

    private String buildRacePlainText(Race race, User recipient, String heading) {
        return """
                HORSE

                Xin chao %s,

                %s

                Race: %s
                Tournament: %s
                Location: %s
                Start: %s
                End: %s

                Email nay duoc gui tu dong tu HORSE.
                """.formatted(
                displayName(recipient),
                heading,
                race.getName(),
                race.getTournament().getName(),
                race.getTournament().getLocation(),
                formatRaceTime(race.getScheduledStartAt()),
                formatRaceTime(race.getScheduledEndAt())
        );
    }

    private String buildRaceHtml(Race race, User recipient, String heading, String intro) {
        String safeName = HtmlUtils.htmlEscape(displayName(recipient));
        String safeRace = HtmlUtils.htmlEscape(race.getName());
        String safeTournament = HtmlUtils.htmlEscape(race.getTournament().getName());
        String safeLocation = HtmlUtils.htmlEscape(race.getTournament().getLocation());

        return layoutHtml(
                heading,
                "Race schedule",
                heading,
                "Xin chao " + safeName + ", " + HtmlUtils.htmlEscape(intro),
                """
                        <div style="background:#eef6ff;border:1px solid #bfdbfe;border-radius:8px;padding:18px 20px;text-align:left;">
                            <div style="font-size:13px;line-height:18px;color:#1d4ed8;font-weight:700;text-transform:uppercase;">%s</div>
                            <div style="margin-top:8px;font-size:15px;line-height:24px;color:#172033;">Tournament: %s</div>
                            <div style="font-size:15px;line-height:24px;color:#172033;">Location: %s</div>
                            <div style="font-size:15px;line-height:24px;color:#172033;">Start: %s</div>
                            <div style="font-size:15px;line-height:24px;color:#172033;">End: %s</div>
                        </div>
                        """.formatted(
                        safeRace,
                        safeTournament,
                        safeLocation,
                        HtmlUtils.htmlEscape(formatRaceTime(race.getScheduledStartAt())),
                        HtmlUtils.htmlEscape(formatRaceTime(race.getScheduledEndAt()))
                ),
                "",
                "#1d4ed8",
                "#dbeafe"
        );
    }

    private String buildComplaintPlainText(RaceComplaint complaint) {
        return """
                HORSE

                Xin chao %s,

                Race cua ban vua nhan duoc mot khieu nai. Thong tin nguoi khieu nai duoc an theo quy trinh xu ly.

                Race: %s
                Horse: %s
                Reason: %s

                Admin se xem xet va phan hoi ket qua xu ly.
                """.formatted(
                displayName(complaint.getAccusedOwner()),
                complaint.getRace().getName(),
                complaint.getAccusedParticipant().getHorse().getName(),
                complaint.getReason()
        );
    }

    private String buildComplaintHtml(RaceComplaint complaint) {
        String safeName = HtmlUtils.htmlEscape(displayName(complaint.getAccusedOwner()));
        String safeRace = HtmlUtils.htmlEscape(complaint.getRace().getName());
        String safeHorse = HtmlUtils.htmlEscape(complaint.getAccusedParticipant().getHorse().getName());
        String safeReason = HtmlUtils.htmlEscape(complaint.getReason());

        return layoutHtml(
                RACE_COMPLAINT_SUBJECT,
                "Race complaint",
                "Race cua ban co khieu nai",
                "Xin chao " + safeName + ", thong tin nguoi khieu nai duoc an trong email nay.",
                """
                        <div style="background:#fff7ed;border:1px solid #fed7aa;border-radius:8px;padding:18px 20px;text-align:left;">
                            <div style="font-size:13px;line-height:18px;color:#9a3412;font-weight:700;text-transform:uppercase;">%s</div>
                            <div style="margin-top:8px;font-size:15px;line-height:24px;color:#172033;">Horse: %s</div>
                            <div style="font-size:15px;line-height:24px;color:#172033;">Reason: %s</div>
                        </div>
                        """.formatted(safeRace, safeHorse, safeReason),
                "",
                "#b45309",
                "#ffedd5"
        );
    }

    private String layoutHtml(String title, String subtitle, String heading, String intro, String mainContent,
                              String noticeContent, String headerColor, String subtitleColor) {
        String safeTitle = HtmlUtils.htmlEscape(title);
        String safeSubtitle = HtmlUtils.htmlEscape(subtitle);
        String safeHeading = HtmlUtils.htmlEscape(heading);

        return """
                <!doctype html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background:#f4f7fb;font-family:Arial,'Helvetica Neue',Helvetica,sans-serif;color:#172033;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f4f7fb;margin:0;padding:24px 12px;">
                        <tr>
                            <td align="center">
                                <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background:#ffffff;border-radius:8px;overflow:hidden;border:1px solid #e6edf5;">
                                    <tr>
                                        <td style="background:%s;padding:24px 28px;text-align:center;">
                                            <div style="font-size:24px;line-height:32px;font-weight:700;color:#ffffff;letter-spacing:0;">%s</div>
                                            <div style="font-size:14px;line-height:20px;color:%s;margin-top:4px;">%s</div>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td style="padding:32px 28px 28px;text-align:center;">
                                            <h1 style="margin:0 0 12px;font-size:22px;line-height:30px;color:#172033;font-weight:700;">%s</h1>
                                            <p style="margin:0 auto 24px;max-width:420px;font-size:15px;line-height:24px;color:#526071;">%s</p>
                                            %s
                                        </td>
                                    </tr>
                                    %s
                                    <tr>
                                        <td style="background:#f8fafc;padding:18px 28px;text-align:center;font-size:12px;line-height:18px;color:#7b8794;">
                                            Email này được gửi tự động từ HORSE.
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(
                safeTitle,
                headerColor,
                BRAND,
                subtitleColor,
                safeSubtitle,
                safeHeading,
                intro,
                mainContent,
                noticeRow(noticeContent)
        );
    }

    private String noticeRow(String noticeContent) {
        if (noticeContent == null || noticeContent.isBlank()) {
            return "";
        }
        return """
                <tr>
                    <td style="padding:0 28px 28px;">
                        %s
                    </td>
                </tr>
                """.formatted(noticeContent);
    }

    private String displayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getUsername();
    }

    private String roleLabel(UserRole role) {
        return switch (role) {
            case OWNER -> "Chủ ngựa";
            case JOCKEY -> "Nài ngựa";
            case SPECTATOR -> "Khán giả";
            case REFEREE -> "Trọng tài";
            case ADMIN -> "Quản trị viên";
            case USER -> "Người dùng";
        };
    }

    private String formatRaceTime(java.time.LocalDateTime value) {
        return value == null ? "" : value.format(RACE_TIME_FORMAT);
    }
}
