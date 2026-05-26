# Horse Racing Backend - Lịch Sử Luồng Đã Làm Và API Hiện Có

Ngày cập nhật: 2026-05-26

Tài liệu này là bản tổng hợp hiện trạng implementation trong project `hoser_backend`. Mục tiêu là lưu lại những luồng nghiệp vụ đã làm, API đang có trong controller, và những phần còn là roadmap/chưa có API production.

## 1. Tổng Quan Đã Triển Khai

Hệ thống hiện đã có các nhóm nghiệp vụ chính:

- Auth, JWT, role guard, current user, đổi mật khẩu, reset password.
- User profile và role application cho owner, jockey, spectator, referee.
- Admin quản lý user, role application, audit log, finance settings.
- Wallet core với user wallet, admin wallet, ledger, deposit, withdrawal.
- ZaloPay/payment callback và payment callback log.
- Horse profile cho owner và admin approve/reject/suspend.
- Jockey profile và owner-jockey invitation có money flow.
- Tournament setup, race setup, prize config, public tournament/race listing.
- Race registration, approve/reject/withdraw, participant, gate, referee assignment.
- Race day: referee check-in, start race, finalize race result.
- Prize payout qua wallet, payout debt khi admin wallet không đủ tiền.
- Race complaint trong 24 giờ sau result confirmed.
- Jockey challenge standings/finalize/payout.
- Phase 10: admin finalize tournament, leaderboard snapshot, payout view, tournament statistics.
- News CRUD/admin và public news.

Các nhóm chưa có API production:

- Spectator prediction.
- Betting bằng ví.
- Item marketplace/inventory.
- Notification API và mark read.
- WebSocket nghiệp vụ chi tiết cho race/result/leaderboard.
- Tournament round CRUD/advancement controller riêng.

## 2. Actor Chính

- `Guest`: đăng ký, đăng nhập, xem dữ liệu public.
- `USER`: quản lý profile, wallet, deposit/withdrawal, role application.
- `OWNER`: quản lý horse, mời jockey, đăng ký race, tạo complaint.
- `JOCKEY`: quản lý jockey profile, accept/reject invitation, tham gia race.
- `REFEREE`: xem race được phân công, check-in participant, start race, finalize result.
- `ADMIN`: quản trị user, finance, horse/jockey, tournament, race, complaint, payout, statistics.
- `SYSTEM`: ledger, callback, scheduler reminder, payout retry.
- `Payment Provider/ZaloPay`: xử lý thanh toán và callback.

## 3. Luồng Nghiệp Vụ Đã Làm

### 3.1 Auth Và Role

1. Guest đăng ký hoặc đăng nhập.
2. System cấp JWT token.
3. User gọi `/auth/me` để lấy thông tin hiện tại.
4. User có thể đổi mật khẩu hoặc reset password bằng OTP.
5. User gửi role application để xin role `OWNER`, `JOCKEY`, `SPECTATOR`, `REFEREE`.
6. Admin xem, lọc, approve hoặc reject role application.
7. Khi role được duyệt, user có thể dùng API tương ứng với role.

### 3.2 Wallet, Deposit, Withdrawal

1. User gọi API wallet; system tạo wallet nếu chưa có.
2. User tạo deposit order.
3. Callback hợp lệ với status paid sẽ credit user wallet và admin wallet.
4. Mọi biến động tiền ghi vào `WalletTransaction`.
5. User tạo withdrawal request; system hold tiền user.
6. Admin approve, reject hoặc mark-paid withdrawal.
7. Reject sẽ release hold về user.
8. Mark-paid sẽ capture hold user và debit admin wallet.
9. Admin có thể tạo và xem admin wallet withdrawal.

### 3.3 Horse, Jockey, Invitation

1. Owner tạo/cập nhật horse profile.
2. Admin approve/reject/suspend horse.
3. Jockey cập nhật jockey profile.
4. Owner tạo invitation thuê jockey cho horse.
5. System hold tiền thuê từ owner wallet.
6. Owner cancel invitation pending thì release hold.
7. Jockey reject invitation thì release hold.
8. Jockey accept invitation thì capture tiền owner, payout jockey, credit admin fee nếu có.
9. Horse team hợp lệ là `horse APPROVED + owner + jockey invitation ACCEPTED`.

### 3.4 Tournament Setup

1. Admin tạo tournament ở `DRAFT`.
2. Admin cấu hình thông tin giải, thời gian đăng ký, thời gian thi đấu, min/max teams.
3. Admin thêm hoặc replace race trong tournament.
4. Race có schedule, min/max participants, entry fee, referee, prize theo rank.
5. Admin có thể bật jockey challenge và cấu hình điểm/prize.
6. Admin publish/open registration khi cấu hình hợp lệ.
7. Public chỉ xem được tournament ở trạng thái public.

### 3.5 Race Registration Và Scheduling

1. Owner đăng ký race khi tournament `OPEN_REGISTRATION`.
2. System kiểm tra owner role, horse approved, jockey invitation accepted, duplicate, schedule overlap, owner ban.
3. Nếu race có entry fee, system debit owner wallet và credit admin wallet.
4. Registration tạo ở `PENDING`.
5. Admin approve registration để tạo participant và gate.
6. Admin reject registration để refund entry fee.
7. Owner withdraw registration khi còn `PENDING`.
8. Admin schedule tournament khi đủ participant và race hợp lệ.
9. Tournament chuyển `SCHEDULED`, race chuyển `SCHEDULED`.
10. System gửi email scheduled/reminder cho owner, jockey, referee.

### 3.6 Race Day, Result, Complaint

1. Referee xem race được phân công.
2. Referee check-in participant khi race `SCHEDULED`.
3. Check-in status có thể là `CHECKED_IN`, `ABSENT`, `DISQUALIFIED`.
4. Referee start race khi đủ participant checked-in.
5. Race chuyển `ONGOING`.
6. Referee finalize race result.
7. Result phải gồm mọi participant trong race.
8. Chỉ participant `FINISHED` mới có rank.
9. Rank không được trùng trong race.
10. System tạo `RaceResult`, tính prize, chia owner/jockey share.
11. System payout prize nếu admin wallet đủ tiền.
12. Nếu admin wallet thiếu tiền, payout status là `UNPAID`.
13. Race chuyển `RESULT_CONFIRMED`.
14. Owner trong race có thể tạo complaint trong 24 giờ sau result confirmed.
15. Admin resolve complaint:
    - `REJECTED`: không thay đổi ví/ban.
    - `APPROVED`: adjustment ví, owner prize return/fine, có thể set ban.

### 3.7 Jockey Challenge

1. Nếu tournament bật jockey challenge, system tính điểm jockey từ `RaceResult`.
2. Điểm lấy từ config first/second/third của tournament.
3. Admin finalize jockey challenge sau khi mọi race đã `RESULT_CONFIRMED` hoặc `CANCELLED`.
4. System tạo standings snapshot trong `JockeyChallengeResult`.
5. System payout prize jockey challenge nếu admin wallet đủ.
6. Nếu thiếu tiền, status là `UNPAID`.
7. Public xem jockey challenge standings.

### 3.8 Phase 10 Finalize Tournament

1. Admin gọi finalize tournament.
2. System kiểm tra tournament tồn tại, user là admin, tournament không `CANCELLED`.
3. Mọi race trong tournament phải là `RESULT_CONFIRMED` hoặc `CANCELLED`.
4. Không được còn race `DRAFT`, `SCHEDULED`, `ONGOING`.
5. Race confirmed phải có `RaceResult`.
6. Payout status phải rõ ràng: `PAID`, `UNPAID`, `NOT_ELIGIBLE`.
7. Complaint `PENDING` không chặn finalize.
8. Nếu jockey challenge enabled, system finalize jockey challenge.
9. System tạo leaderboard snapshot từ toàn bộ race result trong tournament.
10. Snapshot lưu cố định horse name, owner username, jockey username tại thời điểm finalize.
11. Tournament chuyển `COMPLETED`.
12. Tournament lưu `finalizedAt`, `finalizedBy`, `pendingComplaintCountAtFinalize`.
13. Gọi finalize lại khi đã completed và có snapshot sẽ trả dữ liệu cũ, không duplicate snapshot.
14. Public leaderboard đọc từ snapshot, không đọc realtime profile.
15. Admin xem statistics và payout view theo tournament.

## 4. Trạng Thái Chính

### TournamentStatus

- `DRAFT`
- `PUBLISHED`
- `OPEN_REGISTRATION`
- `REGISTRATION_CLOSED`
- `SCHEDULED`
- `ONGOING`
- `COMPLETED`
- `CANCELLED`

### RaceStatus

- `DRAFT`
- `SCHEDULED`
- `ONGOING`
- `RESULT_CONFIRMED`
- `CANCELLED`

### RaceRegistrationStatus

- `PENDING`
- `APPROVED`
- `REJECTED`
- `WITHDRAWN`
- `CANCELLED`

### RaceParticipantStatus

- `REGISTERED`
- `CHECKED_IN`
- `FINISHED`
- `DNF`
- `DISQUALIFIED`
- `ABSENT`

### RacePayoutStatus

- `NOT_ELIGIBLE`
- `PENDING`
- `PAID`
- `UNPAID`

### RaceComplaintStatus

- `PENDING`
- `APPROVED`
- `REJECTED`

## 5. Danh Sách API Hiện Có

Base chính: `/api/v1`

### 5.1 Auth

| Method | Path | Mục đích |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | Đăng ký |
| POST | `/api/v1/auth/login` | Đăng nhập |
| GET | `/api/v1/auth/me` | Current user |
| PUT | `/api/v1/auth/password` | Đổi mật khẩu |
| POST | `/api/v1/auth/logout` | Logout |
| POST | `/api/v1/auth/forgot-password` | Gửi OTP reset password |
| POST | `/api/v1/auth/reset-password` | Reset password |
| POST | `/api/v1/auth/google` | Google login |
| POST | `/api/v1/auth/facebook` | Facebook login |

### 5.2 User, Admin, Audit, Finance

| Method | Path | Mục đích |
| --- | --- | --- |
| GET | `/api/v1/users/me/profile` | Xem profile |
| PUT | `/api/v1/users/me/profile` | Cập nhật profile JSON/multipart |
| GET | `/api/v1/admin/users` | Danh sách user |
| GET | `/api/v1/admin/users/active` | User active |
| GET | `/api/v1/admin/users/deactivated` | User inactive |
| GET | `/api/v1/admin/users/{id}` | Chi tiết user |
| PUT | `/api/v1/admin/users/{userId}/deactivate` | Deactivate user |
| PUT | `/api/v1/admin/users/{userId}/activate` | Activate user |
| PUT | `/api/v1/admin/users/{userId}/role` | Đổi role user |
| GET | `/api/v1/admin/audit-logs` | Audit logs |
| GET | `/api/v1/admin/finance-settings` | Finance settings |
| PUT | `/api/v1/admin/finance-settings` | Cập nhật finance settings |
| GET | `/api/v1/admin/finance-settings/race-prize-shares` | Cấu hình chia prize race |
| PUT | `/api/v1/admin/finance-settings/race-prize-shares` | Cập nhật chia prize race |
| GET | `/api/v1/admin/payout-debts` | Payout debt đang `UNPAID` |

### 5.3 Role Application

| Method | Path | Mục đích |
| --- | --- | --- |
| POST | `/api/v1/role-applications/owner` | Nộp hồ sơ owner |
| POST | `/api/v1/role-applications/jockey` | Nộp hồ sơ jockey |
| POST | `/api/v1/role-applications/spectator` | Nộp hồ sơ spectator |
| POST | `/api/v1/role-applications/referee` | Nộp hồ sơ referee |
| GET | `/api/v1/role-applications/me` | Xem hồ sơ của mình |
| GET | `/api/v1/admin/role-applications` | Admin xem applications |
| GET | `/api/v1/admin/role-applications/role/{role}` | Filter theo role |
| GET | `/api/v1/admin/role-applications/status/{status}` | Filter theo status |
| PUT | `/api/v1/admin/role-applications/{profileId}/approve` | Approve application |
| PUT | `/api/v1/admin/role-applications/{profileId}/reject` | Reject application |

### 5.4 Wallet, Payment, Withdrawal

| Method | Path | Mục đích |
| --- | --- | --- |
| GET | `/api/v1/wallets/me` | User xem ví |
| GET | `/api/v1/wallets/me/transactions` | User xem giao dịch ví |
| GET | `/api/v1/admin/wallet` | Admin wallet |
| GET | `/api/v1/admin/wallet/transactions` | Admin wallet transactions |
| POST | `/api/v1/wallets/me/deposit-orders` | Tạo deposit order |
| GET | `/api/v1/wallets/me/deposit-orders` | User xem deposit orders |
| GET | `/api/v1/wallets/me/deposit-orders/{id}` | Detail deposit order |
| POST | `/api/v1/payment-callbacks/deposits` | Deposit callback nội bộ/manual |
| GET | `/api/v1/admin/payment-orders` | Admin xem payment orders |
| GET | `/api/v1/admin/payment-orders/{id}` | Admin xem payment order detail |
| GET | `/api/v1/admin/payment-callback-logs` | Callback logs |
| POST | `/api/v1/wallets/me/withdrawals` | Tạo withdrawal |
| GET | `/api/v1/wallets/me/withdrawals` | User xem withdrawals |
| GET | `/api/v1/wallets/me/withdrawals/{id}` | Detail withdrawal |
| GET | `/api/v1/admin/withdrawals` | Admin xem withdrawals |
| GET | `/api/v1/admin/withdrawals/{id}` | Detail withdrawal admin |
| PUT | `/api/v1/admin/withdrawals/{id}/approve` | Approve withdrawal |
| PUT | `/api/v1/admin/withdrawals/{id}/reject` | Reject withdrawal |
| PUT | `/api/v1/admin/withdrawals/{id}/mark-paid` | Mark paid withdrawal |
| POST | `/api/v1/admin/wallet/withdrawals` | Tạo admin wallet withdrawal |
| GET | `/api/v1/admin/wallet/withdrawals` | Xem admin wallet withdrawals |
| GET | `/api/zalopay/return` | ZaloPay return |
| POST | `/api/zalopay/callback` | ZaloPay callback |

### 5.5 News

| Method | Path | Mục đích |
| --- | --- | --- |
| POST | `/api/v1/admin/news` | Admin tạo news JSON/multipart |
| PUT | `/api/v1/admin/news/{id}` | Admin cập nhật news JSON/multipart |
| DELETE | `/api/v1/admin/news/{id}` | Admin xóa news |
| GET | `/api/v1/admin/news` | Admin xem news |
| GET | `/api/v1/admin/news/{id}` | Admin xem detail |
| GET | `/api/v1/news` | Public xem news/filter |
| GET | `/api/v1/news/all` | Public xem tất cả news |
| GET | `/api/v1/news/{id}` | Public news detail |

### 5.6 Horse

| Method | Path | Mục đích |
| --- | --- | --- |
| GET | `/api/v1/horses/approved` | Public horse approved |
| POST | `/api/v1/owner/horses` | Owner tạo horse |
| GET | `/api/v1/owner/horses` | Owner xem horse |
| GET | `/api/v1/owner/horses/{id}` | Owner horse detail |
| PUT | `/api/v1/owner/horses/{id}` | Owner cập nhật horse |
| GET | `/api/v1/horses/{id}` | Public horse detail |
| GET | `/api/v1/admin/horses` | Admin xem horses |
| PUT | `/api/v1/admin/horses/{id}/approve` | Approve horse |
| PUT | `/api/v1/admin/horses/{id}/reject` | Reject horse |
| PUT | `/api/v1/admin/horses/{id}/suspend` | Suspend horse |

### 5.7 Jockey Profile Và Invitation

| Method | Path | Mục đích |
| --- | --- | --- |
| GET | `/api/v1/jockey/profile` | Jockey xem profile |
| PUT | `/api/v1/jockey/profile` | Jockey cập nhật profile |
| GET | `/api/v1/jockeys/available` | Public jockey available |
| GET | `/api/v1/jockeys/{id}` | Public jockey detail |
| GET | `/api/v1/admin/jockey-profiles` | Admin xem jockey profiles |
| POST | `/api/v1/owner/jockey-invitations` | Owner tạo invitation |
| GET | `/api/v1/owner/jockey-invitations` | Owner xem invitations |
| GET | `/api/v1/owner/jockey-invitations/{id}` | Owner invitation detail |
| GET | `/api/v1/owners/me/jockeys` | Owner xem jockey accepted |
| PUT | `/api/v1/owner/jockey-invitations/{id}/cancel` | Owner cancel invitation |
| GET | `/api/v1/jockey/invitations` | Jockey xem invitations |
| GET | `/api/v1/jockey/invitations/{id}` | Jockey invitation detail |
| PUT | `/api/v1/jockey/invitations/{id}/accept` | Jockey accept |
| PUT | `/api/v1/jockey/invitations/{id}/reject` | Jockey reject |

### 5.8 Horse Team

| Method | Path | Mục đích |
| --- | --- | --- |
| GET | `/api/v1/owner/horse-teams/eligible` | Owner xem horse team đủ điều kiện |
| GET | `/api/v1/admin/tournaments/{id}/eligible-horse-teams` | Admin xem horse team đủ điều kiện cho tournament |

### 5.9 Tournament

| Method | Path | Mục đích |
| --- | --- | --- |
| POST | `/api/v1/admin/tournaments` | Admin tạo tournament JSON/multipart |
| PUT | `/api/v1/admin/tournaments/{id}` | Admin cập nhật tournament JSON/multipart |
| POST | `/api/v1/admin/tournaments/{id}/races` | Thêm race |
| PUT | `/api/v1/admin/tournaments/{id}/races` | Replace races |
| PUT | `/api/v1/admin/tournaments/{id}/status` | Đổi status |
| PUT | `/api/v1/admin/tournaments/{id}/open-registration` | Mở đăng ký |
| PUT | `/api/v1/admin/tournaments/{id}/close-registration` | Đóng đăng ký |
| PUT | `/api/v1/admin/tournaments/{id}/finalize` | Finalize tournament Phase 10 |
| GET | `/api/v1/admin/tournaments` | Admin list tournaments |
| GET | `/api/v1/admin/tournaments/{id}` | Admin tournament detail |
| GET | `/api/v1/admin/tournaments/{id}/statistics` | Tournament statistics |
| GET | `/api/v1/admin/tournaments/{id}/payouts` | Tournament payout view |
| GET | `/api/v1/tournaments` | Public tournaments |
| GET | `/api/v1/tournaments/{id}` | Public tournament detail |
| GET | `/api/v1/tournaments/{id}/races` | Public tournament races |
| GET | `/api/v1/tournaments/{id}/leaderboard` | Public leaderboard snapshot |

### 5.10 Race Day, Result, Complaint

| Method | Path | Mục đích |
| --- | --- | --- |
| POST | `/api/v1/races/{id}/registrations` | Owner đăng ký race |
| GET | `/api/v1/owner/race-registrations` | Owner xem registrations |
| PUT | `/api/v1/owner/race-registrations/{id}/withdraw` | Owner withdraw registration |
| GET | `/api/v1/admin/tournaments/{id}/race-registrations` | Admin xem registrations theo tournament |
| PUT | `/api/v1/admin/race-registrations/{id}/approve` | Approve registration |
| PUT | `/api/v1/admin/race-registrations/{id}/reject` | Reject registration |
| PUT | `/api/v1/admin/tournaments/{id}/schedule` | Schedule tournament |
| GET | `/api/v1/admin/races/{id}/participants` | Admin xem participants |
| PUT | `/api/v1/admin/races/{raceId}/participants/{participantId}/gate` | Update gate |
| PUT | `/api/v1/admin/races/{id}/referee` | Assign referee |
| GET | `/api/v1/referee/races` | Referee xem races |
| PUT | `/api/v1/referee/races/{id}/participants/{participantId}/check-in` | Check-in participant |
| PUT | `/api/v1/referee/races/{id}/start` | Start race |
| POST | `/api/v1/referee/races/{id}/results/finalize` | Finalize race result |
| GET | `/api/v1/races/{id}/results` | Public race result |
| POST | `/api/v1/races/{id}/complaints` | Owner tạo complaint |
| GET | `/api/v1/owner/race-complaints` | Owner xem complaints |
| GET | `/api/v1/admin/race-complaints` | Admin xem complaints |
| PUT | `/api/v1/admin/race-complaints/{id}/resolve` | Resolve complaint |
| PUT | `/api/v1/admin/tournaments/{id}/jockey-challenge/finalize` | Finalize jockey challenge |
| GET | `/api/v1/tournaments/{id}/jockey-challenge` | Public jockey challenge standings |

## 6. Ghi Chú Kỹ Thuật Quan Trọng

- Money flow luôn đi qua `WalletService` và `WalletTransaction`.
- Race prize payout hiện xảy ra lúc referee finalize race result.
- Phase 10 không payout lại race prize; Phase 10 tạo snapshot, statistics và payout view.
- `UNPAID` nghĩa là admin/system đang nợ người nhận prize.
- Payout view tách rõ `ownerPrizeAmount`, `jockeyPrizeAmount`, `unpaidOwnerAmount`, `unpaidJockeyAmount`.
- Complaint pending không chặn tournament finalize.
- Complaint approved sau finalize không sửa leaderboard snapshot; chỉ tạo wallet adjustment/ban/audit.
- Public leaderboard sau finalize đọc từ `TournamentLeaderboardSnapshot`.
- Project hiện dùng `spring.jpa.hibernate.ddl-auto=update`; chưa có Flyway/Liquibase migration.

## 7. Checklist Snapshot

- [x] Auth/JWT/current user.
- [x] User profile.
- [x] Role application.
- [x] Admin user management.
- [x] Wallet core và ledger.
- [x] Deposit order/callback.
- [x] Withdrawal user/admin.
- [x] Finance settings.
- [x] Horse profile.
- [x] Jockey profile.
- [x] Jockey invitation money flow.
- [x] Eligible horse team.
- [x] Tournament setup.
- [x] Race setup/prize config.
- [x] Race registration.
- [x] Tournament scheduling.
- [x] Referee check-in/start/finalize result.
- [x] Race prize payout/debt.
- [x] Race complaint.
- [x] Jockey challenge.
- [x] Tournament finalize Phase 10.
- [x] Leaderboard snapshot.
- [x] Tournament statistics.
- [x] Tournament payout view.
- [ ] Spectator prediction API.
- [ ] Betting API.
- [ ] Marketplace/item/inventory API.
- [ ] Notification API.
- [ ] Production migration bằng Flyway/Liquibase.
