# Horse Racing Backend - Tài Liệu Tổng Hợp Hoàn Chỉnh

## 1. Mục tiêu hệ thống

Hệ thống horse racing backend quản lý toàn bộ vòng đời giải đua ngựa: người dùng, vai trò, hồ sơ ngựa, jockey, tournament, đăng ký tham gia, lịch race, check-in, kết quả, giải thưởng, ví, thanh toán, prediction/betting, marketplace, notification và vận hành production.

Tài liệu này tổng hợp các file:

- `docs/horse-racing-tournament-spec.md`
- `docs/horse-racing-flow-revised-plan.md`
- `docs/horse-racing-phase-implementation-plan.md`
- `docs/horse-racing-all-phase-flows.md`
- `docs/role-screen-functions.md`
- `docs/plan.md`
- `docs/plan_wallet.md`

Mục tiêu là có một file duy nhất để đọc nhanh nhưng vẫn đủ định hướng triển khai backend và UI theo role.

## 2. Actor và vai trò

| Actor | Mục tiêu chính | Chức năng trọng tâm |
| --- | --- | --- |
| `GUEST` | Chưa đăng nhập | Xem tournament/race/kết quả public, đăng ký, đăng nhập |
| `USER` | Tài khoản mới | Chọn role, cập nhật profile, dùng ví cơ bản |
| `OWNER` | Chủ ngựa | Quản lý ngựa, thuê jockey, đăng ký tournament, xem race/kết quả/giải thưởng |
| `JOCKEY` | Nài ngựa | Quản lý profile/license, nhận lời mời, tham gia race, xem thành tích |
| `REFEREE` | Trọng tài | Check-in participant, ghi violation, nhập draft result, gửi report |
| `SPECTATOR` | Khán giả | Xem race, prediction, betting nếu bật, shop/inventory |
| `ADMIN` | Ban tổ chức | Quản trị user, duyệt hồ sơ, tạo giải, duyệt registration, schedule race, duyệt kết quả, tài chính, audit |
| `SYSTEM` | Tác vụ tự động | Wallet ledger, scheduler, notification/email, advancement, payout, idempotency |
| `PAYMENT_PROVIDER` | Cổng thanh toán | Callback xác nhận deposit paid/failed |

Phase đầu giữ mô hình một user có một role. Nếu cần multi-role về sau, thêm bảng `user_roles` và màn hình chuyển workspace/role.

## 3. Nguyên tắc thiết kế chung

- Backend dùng entity, repository, service, controller, DTO, validation, security guard, migration và test rõ ràng.
- Tất cả tiền dùng `BigDecimal`, mặc định currency `VND`; không dùng `double` hoặc `float`.
- Không update số dư trực tiếp ngoài `WalletService` hoặc service ledger tương đương.
- Tất cả money movement phải có transaction, `referenceType`, `referenceId`, audit fields và idempotency key khi cần.
- Ví user ghi nhận số dư/quyền sở hữu tiền trong app; ví admin/system giữ tiền thật/custody.
- Entry fee/deposit, jockey hire, betting stake và withdrawal dùng cơ chế `hold -> capture/release` khi nghiệp vụ cần chờ duyệt/kết quả.
- Betting tiền thật để sau prediction và phải có feature flag.
- Mọi flow nhiều bước phải dùng status enum thay vì boolean rời rạc.
- API public chỉ trả dữ liệu đã publish/approved theo trạng thái nghiệp vụ.
- Admin action nhạy cảm phải có reason/reference và audit log.
- Notification/email/WebSocket không được leak dữ liệu nhạy cảm qua topic public.

## 4. Domain model cốt lõi

### Identity và profile

- `User`: thông tin đăng nhập, email, phone, role, active, avatar, provider.
- `UserProfile`: thông tin cá nhân, KYC, bank info nếu có withdraw tiền thật.
- `AdminAuditLog`: ghi thao tác admin quan trọng.

### Horse, jockey và quan hệ owner-jockey

- `Horse`: ngựa thuộc owner, có ảnh/tài liệu, thông tin sức khỏe và lifecycle.
- `JockeyProfile`: license, chiều cao, cân nặng, kinh nghiệm, giá thuê, thành tích.
- `JockeyInvitation` hoặc `OwnerJockey`: owner mời jockey, jockey accept/reject.
- `RaceJockeyAssignment`: assignment cụ thể theo race nếu cần xác nhận riêng.

### Tournament và race

- `Tournament`: giải đấu, thời gian đăng ký, thời gian thi đấu, địa điểm, entry fee/deposit, `minTeams`, `maxTeams`, status.
- `TournamentRound`: vòng loại, bán kết, chung kết hoặc cấu trúc tùy giải.
- `TournamentPrize`: cấu hình giải thưởng, rank, amount/item, recipient policy.
- `TournamentRegistration`: owner đăng ký horse team vào tournament.
- `Race`: heat/race thuộc tournament round, có lịch, trạng thái và referee.
- `RaceParticipant`: horse team trong race, gate number, check-in status, final status.

### Result, advancement và statistics

- `RaceCheckIn`: dữ liệu check-in participant.
- `RaceViolation`: vi phạm, penalty, note.
- `RaceResultDraft`: kết quả nháp do referee nhập.
- `RaceResult`: kết quả chính thức sau khi admin approve.
- `RoundAdvancement`: đội thắng/đi tiếp qua round.
- `LeaderboardSnapshot`: snapshot bảng xếp hạng sau khi result confirmed.
- `TournamentResult`: kết quả cuối giải.
- `TournamentStatistics`: số liệu participant, registration, result, payout và tài chính.

### Wallet, payment, prediction, betting, marketplace

- `Wallet`: ví user/admin với `availableBalance`, `holdBalance`, `totalBalance`.
- `WalletTransaction`: ledger bất biến cho mọi biến động tiền.
- `PaymentOrder`: lệnh nạp tiền qua provider/manual/bank transfer.
- `WithdrawalRequest`: user rút tiền, cần admin xử lý.
- `AdminWalletWithdrawal`: admin rút tiền từ ví admin, bắt buộc audit.
- `Prediction`: dự đoán miễn phí hoặc reward nhỏ.
- `Bet`: cược bằng ví khi feature flag bật.
- `Item`, `InventoryItem`, `ItemOrder`: marketplace và inventory.
- `Notification`: in-app notification; có thể kết hợp email/WebSocket event.

## 5. Status enum quan trọng

```text
TournamentStatus:
DRAFT -> PUBLISHED -> OPEN_REGISTRATION -> REGISTRATION_CLOSED -> SCHEDULED -> ONGOING -> COMPLETED
Any state -> CANCELLED
```

```text
TournamentRoundStatus:
DRAFT -> SCHEDULED -> ONGOING -> COMPLETED
Any state -> CANCELLED
```

```text
RegistrationStatus:
PENDING -> APPROVED
PENDING -> REJECTED
PENDING/APPROVED -> WITHDRAWN hoặc CANCELLED theo policy
```

```text
RaceStatus:
DRAFT -> SCHEDULED -> CHECK_IN_OPEN -> READY -> ONGOING -> PENDING_RESULT -> RESULT_CONFIRMED -> COMPLETED
Any pre-result state -> CANCELLED
```

```text
WalletTransactionStatus:
PENDING -> SUCCESS
PENDING -> FAILED
SUCCESS -> REVERSED
```

```text
WithdrawalStatus:
PENDING -> APPROVED -> PAID
PENDING -> REJECTED
PENDING -> CANCELLED
```

Các enum khác cần có: `WalletOwnerType`, `WalletStatus`, `WalletTransactionType`, `PaymentOrderStatus`, `HorseStatus`, `JockeyStatus`, `AssignmentStatus`, `PrizePayoutStatus`, `PredictionStatus`, `BetStatus`.

## 6. Luồng nghiệp vụ end-to-end

### 6.1. Luồng giải đấu chuẩn

1. Guest/User đăng ký, đăng nhập và chọn role.
2. Owner tạo hồ sơ cá nhân, tạo horse profile và chờ admin duyệt.
3. Jockey tạo profile/license và chờ admin duyệt.
4. Owner nạp tiền vào ví nếu cần entry fee hoặc thuê jockey.
5. Owner xem jockey marketplace và gửi invitation.
6. System hold tiền thuê jockey từ ví owner.
7. Jockey accept thì system capture tiền hold, payout net cho jockey và fee/tax cho admin nếu có; reject/cancel thì release hold.
8. Admin tạo tournament, round, `minTeams`, `maxTeams`, entry fee/deposit, prize và advancement rule.
9. Admin publish/open registration khi setup hợp lệ.
10. Owner đăng ký `horse team = horse + owner + jockey accepted`.
11. System hold entry fee/deposit và gửi notification/email registration created.
12. Admin approve registration thì capture tiền; reject/cancel thì release hold.
13. Khi approved teams đạt `minTeams`, admin generate race/heat.
14. Admin gán participant, gate number, referee và lịch race.
15. System gửi notification/email race scheduled và reminder trước lịch thi đấu 3 ngày cho owner, jockey, referee.
16. Referee check-in participant, ghi violation nếu có, nhập draft result và submit report.
17. Admin review và approve result chính thức.
18. System cập nhật leaderboard, chọn winner/qualifier theo advancement rule và tạo seed cho round tiếp theo.
19. Race -> result -> advancement lặp đến final.
20. Khi final completed, system xác định ranking, payout prize qua wallet và ghi ledger.
21. Admin xem statistics theo tournament, round, race, prize, payout và dòng tiền.

### 6.2. Luồng tiền chuẩn

1. User có `UserWallet`; hệ thống có `AdminWallet/SystemWallet`.
2. User deposit: tạo `PaymentOrder` -> provider/manual xác nhận paid -> credit user wallet và admin wallet -> tạo ledger hai phía cùng reference.
3. User dùng tiền:
   - Entry fee/deposit: hold khi tạo registration, capture khi approve, release khi reject/cancel.
   - Jockey hire: hold khi tạo invitation, capture/payout khi accept, release khi reject/cancel.
   - Bet stake: hold/debit theo policy, settle sau result confirmed.
   - Item purchase: debit user wallet, tăng inventory.
4. User nhận tiền:
   - Prize payout.
   - Bet payout nếu thắng.
   - Prediction reward nếu có.
   - Item sale.
   - Refund/release.
5. User withdraw: tạo request -> hold user available -> admin approve/reject -> mark-paid mới trừ user hold và admin wallet.
6. Admin withdraw: trừ admin wallet trực tiếp, không cần approval, nhưng bắt buộc audit reason.

### 6.3. Luồng referee

1. Referee được admin phân công race.
2. Referee nhận schedule notification và reminder trước 3 ngày.
3. Đến ngày race, referee mở check-in.
4. Referee check-in từng participant, ghi sức khỏe/giấy tờ/trạng thái.
5. Referee đánh dấu absent/disqualified nếu rule cho phép.
6. Referee start race khi điều kiện tối thiểu đạt.
7. Referee ghi violation, penalty, draft result, finish order/time.
8. Referee submit report.
9. Admin approve thì result mới chính thức.

### 6.4. Luồng spectator

1. Spectator xem tournament/race public.
2. Spectator prediction trước race start.
3. Race start thì prediction bị lock.
4. Result confirmed thì prediction settled.
5. Nếu betting feature flag bật, spectator/user có thể bet bằng ví trước race start.
6. Bet locked khi race start, won/lost/refund khi result confirmed/cancelled.

## 7. Public API nhóm chính

### Auth/User/Profile

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me`
- `PUT /api/v1/auth/me/role`
- `GET /api/v1/users/me/profile`
- `PUT /api/v1/users/me/profile`

### Wallet/Payment/Withdraw

- `GET /api/v1/wallets/me`
- `GET /api/v1/wallets/me/transactions`
- `POST /api/v1/wallets/me/deposit-orders`
- `GET /api/v1/wallets/me/deposit-orders/{id}`
- `POST /api/v1/payments/{provider}/callback`
- `POST /api/v1/wallets/me/withdrawals`
- `GET /api/v1/wallets/me/withdrawals`
- `GET /api/v1/admin/wallet`
- `GET /api/v1/admin/wallet/transactions`
- `POST /api/v1/admin/wallet/withdrawals`
- `GET /api/v1/admin/withdrawals`
- `PUT /api/v1/admin/withdrawals/{id}/approve`
- `PUT /api/v1/admin/withdrawals/{id}/reject`
- `PUT /api/v1/admin/withdrawals/{id}/mark-paid`

### Horse/Jockey/Invitation

- `POST /api/v1/horses`
- `GET /api/v1/horses/me`
- `GET /api/v1/horses/{id}`
- `PUT /api/v1/horses/{id}`
- `PUT /api/v1/admin/horses/{id}/status`
- `POST /api/v1/jockey-profiles`
- `GET /api/v1/jockey-profiles/me`
- `PUT /api/v1/jockey-profiles/me`
- `GET /api/v1/jockeys?status=AVAILABLE`
- `PUT /api/v1/admin/jockey-profiles/{id}/status`
- `POST /api/v1/owner-jockey-invitations`
- `GET /api/v1/jockey-invitations/me`
- `PUT /api/v1/jockey-invitations/{id}/accept`
- `PUT /api/v1/jockey-invitations/{id}/reject`
- `PUT /api/v1/owner-jockey-invitations/{id}/cancel`

### Tournament/Registration

- `POST /api/v1/admin/tournaments`
- `PUT /api/v1/admin/tournaments/{id}`
- `POST /api/v1/admin/tournaments/{id}/rounds`
- `PUT /api/v1/admin/tournaments/{id}/rounds/{roundId}`
- `PUT /api/v1/admin/tournaments/{id}/prizes`
- `PUT /api/v1/admin/tournaments/{id}/publish`
- `PUT /api/v1/admin/tournaments/{id}/open-registration`
- `PUT /api/v1/admin/tournaments/{id}/close-registration`
- `GET /api/v1/tournaments`
- `GET /api/v1/tournaments/{id}`
- `POST /api/v1/owner/tournament-registrations`
- `GET /api/v1/owner/tournament-registrations`
- `GET /api/v1/admin/tournaments/{id}/registrations`
- `PUT /api/v1/admin/tournament-registrations/{id}/approve`
- `PUT /api/v1/admin/tournament-registrations/{id}/reject`

### Race/Result/Statistics

- `POST /api/v1/admin/tournaments/{id}/races/generate`
- `POST /api/v1/admin/races`
- `PUT /api/v1/admin/races/{id}/schedule`
- `PUT /api/v1/admin/races/{id}/assign-referee`
- `GET /api/v1/races`
- `GET /api/v1/races/{id}`
- `GET /api/v1/owners/me/races`
- `GET /api/v1/jockeys/me/races`
- `GET /api/v1/referees/me/races`
- `PUT /api/v1/referee/races/{id}/open-check-in`
- `PUT /api/v1/referee/race-participants/{id}/check-in`
- `PUT /api/v1/referee/races/{id}/start`
- `POST /api/v1/referee/races/{id}/violations`
- `POST /api/v1/referee/races/{id}/draft-results`
- `POST /api/v1/referee/races/{id}/report`
- `PUT /api/v1/admin/races/{id}/results/approve`
- `PUT /api/v1/admin/races/{id}/results/request-changes`
- `PUT /api/v1/admin/tournaments/{id}/advance-round`
- `GET /api/v1/tournaments/{id}/leaderboard`
- `GET /api/v1/admin/tournaments/{id}/statistics`

### Prediction/Betting/Marketplace/Notification

- `POST /api/v1/races/{id}/predictions`
- `GET /api/v1/users/me/predictions`
- `GET /api/v1/races/{id}/bet-options`
- `POST /api/v1/races/{id}/bets`
- `GET /api/v1/users/me/bets`
- `GET /api/v1/bets/{id}`
- `GET /api/v1/items`
- `POST /api/v1/items/{id}/purchase`
- `GET /api/v1/users/me/inventory`
- `POST /api/v1/inventory/{id}/sell`
- `POST /api/v1/admin/items`
- `PUT /api/v1/admin/items/{id}`
- `GET /api/v1/notifications`
- `PUT /api/v1/notifications/{id}/read`

## 8. Roadmap phase tổng hợp

| Phase | Mục tiêu | Actor chính | Kết quả cần đạt |
| --- | --- | --- | --- |
| 0 | Siết nền tảng auth/security/error/docs | Guest, User, Admin | Login/register/me ổn, role guard, error chuẩn, Swagger, seed admin |
| 1 | Wallet core + ví admin trung tâm | User, Admin, System | User wallet, admin wallet, ledger, credit/debit/hold/release/capture/refund |
| 2 | Payment deposit MVP | User, Payment Provider, System | Deposit order, callback verify, credit user/admin wallet, idempotency |
| 3 | Withdraw + audit tài chính | User, Admin | User withdrawal hold/approve/reject/mark-paid, admin withdrawal audit |
| 4 | Horse và jockey profile | Owner, Jockey, Admin | Owner CRUD horse, jockey profile/license, admin approve/reject/suspend |
| 5 | Owner-jockey invitation | Owner, Jockey, System | Invitation, hold hire price, accept capture/payout, reject/cancel release |
| 6 | Tournament setup + round/prize | Admin | Tournament, round, `minTeams`, `maxTeams`, entry fee, prize, open registration |
| 7 | Registration + deposit hold | Owner, Admin, System | Horse team registration, entry fee hold/capture/release, notification |
| 8 | Race scheduling + reminder | Admin, Referee, Owner, Jockey | Generate race, participant, gate, referee, reminder trước 3 ngày |
| 9 | Check-in + result + advancement | Referee, Admin, System | Check-in, violation, draft result, admin approve, round advancement |
| 10 | Final result + prize + statistics | Admin, System | Leaderboard snapshot, prize payout, tournament statistics |
| 11 | Spectator prediction | Spectator, System | Prediction miễn phí/reward nhỏ, lock/start, settle/result |
| 12 | Betting bằng ví | User, Admin, System | Feature flag, stake, odds, lock, settle won/lost/refund |
| 13 | Item marketplace | User, Admin, System | Item CRUD, purchase/sale, inventory, ledger |
| 14 | Notification/WebSocket/Email | All roles, System | Notification, email, WebSocket race/result/leaderboard |
| 15 | Production hardening | Developer, Operator, Admin | Migration, rate limit, audit, monitoring, index, test coverage |

## 9. Màn hình gợi ý theo role

### Guest/User

- Login/Register/Forgot Password.
- Public tournaments, races, results, leaderboard.
- Choose Role.
- Profile.
- Wallet, deposit, withdrawal history.

### Owner

- Dashboard owner.
- My Horses.
- Jockey Marketplace.
- Invitations.
- Tournaments.
- Registrations.
- My Races.
- Wallet.
- Prizes.
- Notifications.

### Jockey

- Dashboard jockey.
- Profile/license.
- Invitations received.
- My Races.
- Performance.
- Wallet.
- Notifications.

### Referee

- Dashboard referee.
- Assigned Races.
- Check-in.
- Violations.
- Draft Results.
- Reports.
- Notifications.

### Spectator

- Tournaments.
- Races.
- Predictions.
- Betting nếu feature flag bật.
- Wallet.
- Shop.
- Inventory.
- Leaderboard.
- Notifications.

### Admin

- Dashboard.
- Users/Roles.
- Horse Approval.
- Jockey Approval.
- Tournaments.
- Registrations.
- Race Scheduling.
- Result Approval.
- Finance/Admin Wallet.
- Betting Management.
- Item Management.
- Notifications/Realtime.
- Audit Logs.
- Settings/Health.

## 10. Business rules quan trọng

### Auth và role

- `GUEST` không phải role lưu DB.
- User không tự chọn `ADMIN`.
- User thường không gọi được admin API.
- Endpoint nhạy cảm phải có role guard.

### Horse/Jockey

- Owner chỉ sửa horse thuộc sở hữu của mình.
- Horse/jockey chưa approved không được dùng để đăng ký tournament/race.
- Jockey suspended không thể accept invitation hoặc race assignment.
- Invitation duplicate active cho cùng horse/jockey/context bị chặn.

### Tournament/Registration

- “Đội tham gia” = `horse team = horse + owner + jockey accepted`.
- `minTeams` và `maxTeams` tính theo số horse team.
- Tournament không open nếu thiếu thông tin cơ bản, round config, min/max team hoặc prize config bắt buộc.
- `minTeams >= 2` và không vượt `maxTeams`.
- Entry fee/prize amount không âm.
- Duplicate registration cùng horse/tournament bị chặn.
- Tournament đóng đăng ký hoặc vượt `maxTeams` thì không tạo registration mới.

### Race/Result

- Race chỉ schedule từ registration approved.
- Tournament chưa đủ `minTeams` không được generate race.
- Gate number không trùng trong cùng race.
- Jockey/referee không trùng lịch.
- Race chỉ start khi participant check-in đạt điều kiện tối thiểu.
- Draft result không duplicate rank.
- Admin approve result chỉ một lần.
- Result chỉ chính thức sau khi admin approve.
- Advancement phải chọn đúng số team theo rule.

### Wallet/Payment

- Không cho balance âm.
- Không hold/debit vượt available.
- Không capture vượt hold.
- Callback duplicate không cộng tiền lần hai.
- Deposit paid cộng cả user wallet và admin wallet.
- Withdrawal tạo request thì hold user balance, admin wallet chưa bị trừ.
- Withdrawal reject thì release hold.
- Withdrawal mark-paid thì trừ user hold và admin wallet.
- Admin withdraw không cần approval nhưng phải audit.
- Prize payout, bet payout, refund phải idempotent.

### Prediction/Betting/Marketplace

- Prediction không tạo rủi ro tiền thật nếu chưa có reward bằng ví.
- Không prediction/bet sau race start hoặc sau thời điểm khóa.
- Betting API bị chặn khi feature flag off.
- Bet settlement không được chạy hai lần.
- Item inactive/out of stock không mua được.
- User không sở hữu item thì không bán được.

## 11. Notification, email và realtime

Event cần notification/email:

- Invitation created/accepted/rejected/cancelled.
- Deposit paid/failed.
- Withdrawal approved/rejected/paid.
- Horse/jockey approved/rejected/suspended.
- Registration created/approved/rejected.
- Race scheduled hoặc schedule changed.
- Reminder trước lịch thi đấu 3 ngày cho owner, jockey, referee.
- Result published.
- Advancement vào vòng tiếp theo.
- Prize payout success/failed.
- Prediction/bet settled.

WebSocket topic gợi ý:

- `/topic/races/{raceId}/status`
- `/topic/races/{raceId}/results`
- `/topic/tournaments/{tournamentId}/leaderboard`

Nguyên tắc:

- User chỉ xem notification của mình.
- Public topic không chứa dữ liệu admin/private.
- Reminder và event phải có idempotency/reference để không gửi trùng.

## 12. Package và module gợi ý

```text
com.minhthien.hoser_backend
  config
  controller
    auth
    user
    horse
    jockey
    tournament
    race
    wallet
    admin
  dto
    request
    response
  entity
  enums
  exception
  mapper
  repository
  security
  service
    impl
  websocket
```

Mỗi module nên có:

- Entity + enum status.
- Repository.
- DTO request/response.
- Mapper.
- Service interface/impl.
- Controller.
- Validation.
- Security rule.
- Unit test service.
- Integration test controller.
- Swagger annotation.

## 13. API response và error code

Response thành công:

```json
{
  "success": true,
  "message": "Race result confirmed",
  "data": {},
  "timestamp": "2026-05-15T14:00:00"
}
```

Response lỗi:

```json
{
  "success": false,
  "message": "Wallet balance is insufficient",
  "errorCode": "WALLET_INSUFFICIENT_BALANCE",
  "details": [],
  "timestamp": "2026-05-15T14:00:00"
}
```

Error code gợi ý:

- `AUTH_UNAUTHORIZED`
- `AUTH_FORBIDDEN`
- `USER_NOT_FOUND`
- `HORSE_NOT_FOUND`
- `HORSE_NOT_OWNED_BY_USER`
- `JOCKEY_TIME_CONFLICT`
- `REFEREE_TIME_CONFLICT`
- `TOURNAMENT_NOT_FOUND`
- `TOURNAMENT_REGISTRATION_CLOSED`
- `REGISTRATION_DUPLICATED`
- `RACE_NOT_FOUND`
- `RACE_ALREADY_STARTED`
- `WALLET_NOT_FOUND`
- `WALLET_LOCKED`
- `WALLET_INSUFFICIENT_BALANCE`
- `PAYMENT_CALLBACK_INVALID`
- `BET_CLOSED`
- `BET_ALREADY_SETTLED`

## 14. MVP nên chốt

Nếu mục tiêu là nhanh có demo tốt, MVP nên gồm:

1. Auth/user/role.
2. Wallet nội bộ và admin wallet, có ledger.
3. Deposit manual/bank transfer MVP.
4. Owner CRUD horse.
5. Jockey profile và invitation.
6. Admin CRUD tournament/round/prize.
7. Owner registration và entry fee hold.
8. Admin approve/reject registration.
9. Race scheduling và referee assignment.
10. Referee check-in, draft result, report.
11. Admin confirm result.
12. Public leaderboard.
13. Prize payout qua wallet.
14. Notification cơ bản.
15. Prediction miễn phí.

Sau MVP mới mở rộng:

- Payment provider thật VNPay/MoMo/PayPal/Stripe.
- Withdraw thật và KYC.
- Betting tiền thật.
- Item marketplace.
- WebSocket realtime chi tiết.
- Dashboard thống kê nâng cao.

## 15. Thứ tự code khuyến nghị

1. Siết nền auth/user/security/error/Swagger.
2. Wallet core, admin wallet, ledger và operation nội bộ.
3. Deposit MVP và idempotency.
4. Withdrawal/audit.
5. Horse CRUD và approval.
6. Jockey profile và invitation có hold/capture/release.
7. Tournament setup, round, prize, min/max teams.
8. Registration + entry fee hold/capture/release.
9. Race scheduling, participant, gate, referee.
10. Referee check-in, violation, draft result, report.
11. Result approval, advancement, leaderboard.
12. Final result, prize payout, statistics.
13. Prediction.
14. Betting feature flag.
15. Marketplace.
16. Notification/email/WebSocket.
17. Production hardening.

Wallet nên làm sớm vì registration fee, jockey hire, betting, prize, item và withdraw đều phụ thuộc cùng một cơ chế tiền.

## 16. Production checklist

### Backend

- Dùng Flyway/Liquibase cho migration.
- Không dùng `ddl-auto=update` ở production.
- Tất cả tiền dùng `BigDecimal`.
- Money flow dùng `@Transactional`.
- Wallet update cần optimistic/pessimistic locking để tránh race condition.
- Payment callback verify chữ ký provider, timestamp, replay protection, IP allowlist nếu có.
- Idempotency cho callback, withdraw, bet settlement, prize payout, notification reminder.
- Không trả entity trực tiếp, chỉ trả response DTO.
- Pagination/sorting/filtering cho list API quan trọng.
- Không lưu secret trong `application.properties` commit lên git.

### Security

- `@PreAuthorize` theo role.
- CORS theo domain frontend thật.
- Rate limit login, forgot password, payment callback, betting, withdrawal, registration.
- Refresh token nếu app cần session dài.
- KYC trước withdraw nếu dùng tiền thật.
- Admin action cần audit.

### Data integrity

Unique constraint nên có:

- `users.email`
- `users.username`
- `wallet.user_id + currency`
- `tournament_registration.tournament_id + horse_id`
- `race_participant.race_id + horse_id`
- `wallet_transaction.idempotency_key`

Index nên có:

- `race.scheduled_at`
- `race.tournament_id`
- `wallet_transaction.user_id, created_at`
- `wallet_transaction.reference_type, reference_id`
- `bet.race_id, status`
- `prediction.race_id, status`
- `tournament_registration.tournament_id, status`

### Testing

- Auth service và role permission.
- API error response chuẩn.
- Seed admin không trùng.
- Wallet credit/debit/hold/release/capture/refund.
- Wallet insufficient balance.
- Wallet concurrent debit.
- Deposit callback duplicate.
- Withdrawal approve/reject/mark-paid.
- Admin withdraw audit và không làm wallet âm.
- Horse ownership.
- Jockey invitation duplicate và accept/reject.
- Registration duplicate và entry fee ledger.
- Race scheduling conflict.
- Check-in và draft result duplicate rank.
- Result confirmation idempotency.
- Prize payout idempotency.
- Prediction/bet settlement.
- Payment callback replay protection.
- Migration chạy sạch từ DB trống.

## 17. Definition of Done cho mỗi phase

Một phase chỉ nên coi là xong khi có:

- Entity và migration.
- API command/query chính.
- DTO request/response.
- Validation nghiệp vụ.
- Role permission.
- Service rule được test.
- Swagger hiển thị request/response.
- Integration test endpoint chính.
- Wallet transaction không update balance trực tiếp ngoài wallet service.
- Audit/reference/idempotency cho thao tác tiền hoặc admin action nhạy cảm.
- Acceptance flow chạy được từ đầu đến cuối qua Swagger/Postman.

## 18. Các quyết định còn cần chốt

- Entry fee có hoàn lại không khi owner rút đăng ký sau approved?
- Prize chia cho owner, jockey hay cả hai?
- Prize chia theo rank cố định, phần trăm prize pool hay cấu hình riêng từng tournament?
- DNF/DQ tính điểm và rank như thế nào?
- Jockey có cần KYC/license approval bắt buộc trước khi nhận invitation không?
- Provider thanh toán ưu tiên: manual/bank transfer, VNPay, MoMo, PayPal hay Stripe?
- Có yêu cầu KYC trước withdraw không?
- Min/max deposit, withdraw, bet stake là bao nhiêu?
- Betting dùng fixed odds hay pool betting?
- Prediction reward là điểm, vật phẩm hay tiền ví?
- Media upload dùng Cloudinary, S3 hay storage khác?

## 19. Checklist cuối cùng

- Có đủ actor: guest, user, owner, jockey, referee, spectator, admin, system, payment provider.
- Có đủ Phase 0-15.
- Wallet/payment/withdraw nằm sớm ở Phase 1-3.
- Deposit cộng cả user wallet và admin wallet.
- User withdraw hold trước, mark-paid mới trừ admin wallet.
- Admin withdraw không cần duyệt nhưng phải audit.
- `horse team = horse + owner + jockey accepted`.
- `minTeams` và `maxTeams` tính theo horse team.
- Entry fee/deposit dùng `hold -> capture/release`.
- Race chỉ generate khi approved teams đạt `minTeams`.
- Reminder trước lịch thi đấu 3 ngày.
- Result chỉ chính thức sau khi admin approve.
- Final result tạo leaderboard snapshot.
- Prize payout trace qua ledger.
- Betting có feature flag và có thể tắt hoàn toàn.
- Marketplace đồng bộ wallet và inventory.
- Notification/WebSocket/Email không leak dữ liệu nhạy cảm.
- Production có migration, audit, monitoring, rate limit, index và test coverage.
