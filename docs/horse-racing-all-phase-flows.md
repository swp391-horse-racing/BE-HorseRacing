# Luồng Thực Hiện Tất Cả Phase Horse Racing

## Mục tiêu tài liệu

Tài liệu này mô tả các luồng nghiệp vụ khi triển khai toàn bộ Phase 0-15 của hệ thống horse racing, dựa trên `docs/horse-racing-flow-revised-plan.md`.

Tài liệu này dùng để đọc theo luồng thực tế: ai làm gì, trạng thái thay đổi ra sao, tiền đi qua ví như thế nào, notification/email được gửi lúc nào, và điều kiện nào sẽ chặn người dùng hoặc admin tiếp tục thao tác.

## Actor chính

- `Guest`: xem thông tin public, đăng ký/đăng nhập.
- `User/Owner`: quản lý ví, hồ sơ cá nhân, ngựa, thuê jockey, đăng ký giải, check-in và nhận kết quả/giải thưởng.
- `Jockey`: quản lý hồ sơ jockey, nhận hoặc từ chối lời mời, tham gia race/heat đã được phân công.
- `Referee`: nhận lịch race/heat, check-in participant, ghi vi phạm, nhập draft result và gửi report.
- `Admin`: cấu hình hệ thống, ví admin, duyệt hồ sơ, tạo tournament, duyệt registration, schedule race, duyệt kết quả, payout và xem thống kê.
- `System`: xử lý wallet ledger, scheduler, notification/email, advancement, payout, idempotency và audit.
- `Payment Provider`: gửi callback xác nhận lệnh nạp tiền.
- `Spectator`: xem tournament/race public, dự đoán kết quả hoặc tham gia betting nếu feature được bật.

## 4 luồng nghiệp vụ chính

### Luồng 1 - Admin cấu hình thông tin giải đấu

1. Admin tạo giải đấu với thông tin cơ bản: tên, mô tả, địa điểm, thời gian đăng ký, thời gian thi đấu và trạng thái publish.
2. Admin xác định `minTeams` và `maxTeams` cho toàn giải để biết số participant/horse team tối thiểu và tối đa được tham gia.
3. Admin xem danh sách ngựa/horse team đủ điều kiện, gồm ngựa đã approved và jockey đã accepted với owner.
4. Admin cấu hình nhiều vòng đấu cho tournament: vòng loại, bán kết, chung kết hoặc cấu trúc round tùy giải.
5. Trong mỗi round, admin cấu hình số lượng race/heat, số participant tối thiểu/tối đa trong từng race/heat và thứ tự thi đấu.
6. Admin cấu hình `advancement rule`: số participant đi tiếp từ từng race/heat hoặc từng round, cách chọn theo thứ hạng, thời gian chạy nhanh nhất hoặc rule nghiệp vụ của giải.
7. Admin phân chia participant/horse team vào race/heat phù hợp theo sức chứa, lịch thi đấu, referee và điều kiện tổ chức.
8. Admin cấu hình phí đăng ký/tiền đặt cọc, chính sách capture/release và deadline check-in.
9. Admin cấu hình giải thưởng: hạng nhất, hạng nhì, hạng ba hoặc các hạng khác, số tiền/vật phẩm, điều kiện nhận giải và người nhận payout.
10. Tournament chỉ được publish/open registration khi thông tin giải đấu, `minTeams`, `maxTeams`, round/race config, advancement rule và prize config hợp lệ.

Ví dụ cấu hình giải đấu 40 ngựa:

1. Vòng loại có 40 participant, chia thành 8 race/heat, mỗi race/heat có 5 participant.
2. Mỗi race/heat vòng loại chọn 2 participant đi tiếp, tổng cộng 16 participant vào bán kết.
3. Bán kết chia thành 2 race/heat, mỗi race/heat có 8 participant.
4. Mỗi race/heat bán kết chọn 4 participant nhanh nhất đi tiếp, tổng cộng 8 participant vào chung kết.
5. Chung kết có 8 participant thi đấu để xác định ranking cuối cùng: hạng nhất, hạng nhì, hạng ba và các hạng còn lại nếu giải có cấu hình.

### Luồng 2 - Chủ ngựa quản lý profile cá nhân và ngựa

1. Owner quản lý thông tin cá nhân, thông tin liên hệ, avatar và dữ liệu dùng cho notification/email.
2. Owner tạo, xem, cập nhật hồ sơ ngựa của mình.
3. Owner upload ảnh, giấy tờ hoặc tài liệu liên quan của ngựa qua storage.
4. Admin duyệt, từ chối hoặc suspend hồ sơ ngựa, kèm lý do nếu reject/suspend.
5. Ngựa có lifecycle rõ: `DRAFT/PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`.
6. Chỉ ngựa `APPROVED` mới được đăng ký tham gia tournament.

### Luồng 3 - Chủ ngựa tham gia và theo dõi giải đấu

#### B1 - Tham gia giải đấu

1. Owner xem danh sách giải đấu đang mở đăng ký hoặc đang diễn ra theo rule public.
2. Owner chọn một giải đấu đang diễn ra hoặc đang mở đăng ký.
3. Owner xem chi tiết thông tin giải đấu, bước này có thể skip nếu UI cho phép đăng ký nhanh.
4. Owner chọn ngựa tham gia; horse team hợp lệ là `horse + owner + jockey đã accepted`.
5. Owner gửi registration và đặt cọc/đóng entry fee cho system.
6. System hold tiền đặt cọc/entry fee từ ví owner theo flow `hold -> capture/release`.
7. System gửi email và notification xác nhận registration đã được ghi nhận.
8. Admin duyệt registration; approve thì capture tiền, reject/cancel thì release tiền hold về owner.
9. Trước lịch thi đấu 3 ngày, system gửi email và notification nhắc owner, jockey và referee.
10. Đến ngày thi đấu, participant check-in theo race/heat của round.
11. Referee ghi nhận check-in, violation và draft result; admin xác nhận kết quả chính thức.
12. System dựa trên kết quả đã xác nhận để chọn participant/horse team thắng hoặc đi tiếp vào vòng tiếp theo.
13. Quy trình check-in -> result -> advancement lặp lại qua từng vòng đến vòng cuối.
14. Ở vòng cuối, system xác định winner chính thức và trigger trao giải/payout.

#### B2 - Theo dõi giải đấu

1. Customer/spectator vào màn hình danh sách giải đấu.
2. Customer/spectator chọn giải đấu muốn xem.
3. Customer/spectator xem danh sách race/heat trong giải.
4. Customer/spectator chọn race/heat muốn theo dõi.
5. Customer/spectator xem lịch thi đấu, participant, horse, jockey, referee, trạng thái race/heat, kết quả và leaderboard khi đã được publish.

### Luồng 4 - Admin xem thống kê kết quả giải đấu

1. Admin xem thống kê tổng quan theo tournament: số lượng customer/owner tham gia, số horse team, số registration và trạng thái giải đấu.
2. Admin xem thông tin jockey tham gia, lịch sử race và kết quả liên quan.
3. Admin xem thông tin referee/trọng tài được phân công và report/result đã xử lý.
4. Admin xem thống kê theo round/race/heat: participant, check-in, absent, disqualified, result, winner và participant đi tiếp.
5. Admin xem thống kê giải thưởng: prize config, winner, trạng thái payout và transaction liên quan.
6. Admin xem thống kê tài chính: entry fee/deposit hold, capture, release/refund, prize payout và audit ledger.
7. Admin có thể lọc theo tournament, round, race/heat, registration status, payout status và khoảng thời gian.

## Tổng quan end-to-end flow

1. Guest đăng ký tài khoản, đăng nhập và được hệ thống xác định role.
2. User/Owner có ví nội bộ, nạp tiền qua payment/manual transfer và tiền được ghi nhận vào cả user wallet lẫn admin/system wallet.
3. Owner tạo hồ sơ ngựa; jockey tạo hồ sơ jockey; admin duyệt để hồ sơ đủ điều kiện tham gia giải.
4. Owner gửi lời mời thuê jockey cho ngựa của mình. Tiền thuê được hold từ ví owner, sau đó capture khi jockey accept hoặc release khi invitation bị cancel/reject.
5. Admin tạo tournament, cấu hình round, race/heat, `minTeams`, `maxTeams`, entry fee/deposit, prize và rule chọn participant đi tiếp.
6. Owner đăng ký `horse team = horse + owner + jockey accepted` vào tournament đang mở. Entry fee/deposit dùng flow `hold -> capture/release`.
7. Admin duyệt registration. Khi approve thì capture tiền theo policy; khi reject/cancel thì release tiền hold về ví owner.
8. Khi đủ `minTeams`, admin generate race/heat theo round config, phân participant vào race/heat, phân gate, phân referee và publish lịch thi đấu.
9. System gửi notification/email khi race/heat được scheduled và gửi reminder trước lịch thi đấu 3 ngày cho owner, jockey và referee.
10. Referee check-in participant trong ngày thi đấu, ghi violation nếu có và nhập draft result.
11. Admin duyệt result chính thức. System chọn winner/qualifier theo advancement rule của round hiện tại và tạo seed cho round tiếp theo.
12. Quy trình race/heat -> result -> advancement lặp lại qua vòng loại, bán kết và các vòng khác đến chung kết.
13. Khi chung kết completed, system xác định ranking cuối cùng, tạo leaderboard snapshot, payout prize qua wallet và ghi ledger đầy đủ.
14. Admin xem statistics theo tournament, round, race/heat, participant, prize, payout và dòng tiền.
15. Các phase mở rộng gồm prediction, betting bằng ví, marketplace, notification/websocket/email và production hardening.

## Luồng liên phase quan trọng

### Owner nạp tiền, thuê jockey, đăng ký và nhận giải

1. Owner đăng ký/đăng nhập và gọi wallet API để tạo hoặc xem ví.
2. Owner tạo deposit order. Khi payment callback hợp lệ, system credit `UserWallet.availableBalance` và `AdminWallet.availableBalance`.
3. Owner tạo horse profile và chờ admin approve.
4. Jockey tạo profile và chờ admin approve.
5. Owner gửi invitation tới jockey approved. System hold tiền thuê từ ví owner.
6. Jockey accept invitation. System capture tiền hold, credit phần net cho jockey và credit tax/fee cho admin nếu có policy.
7. Owner chọn horse team hợp lệ để đăng ký tournament.
8. System hold entry fee/deposit từ ví owner và gửi notification/email xác nhận registration created.
9. Admin approve registration. System capture entry fee/deposit theo policy và ghi ledger.
10. Owner, jockey nhận lịch race/heat và reminder trước 3 ngày.
11. Participant check-in, thi đấu và nhận result published.
12. Nếu participant/horse team thắng hoặc vào vòng sau, system gửi notification advancement.
13. Nếu participant/horse team đạt prize ở chung kết, system payout prize qua ví theo recipient policy và ghi ledger `PRIZE_PAYOUT`.

### Admin tạo giải, vận hành và xem thống kê

1. Admin đăng nhập bằng token có role admin.
2. Admin tạo tournament ở trạng thái `DRAFT`.
3. Admin cấu hình registration window, race time, round structure, race/heat trong từng round, `minTeams`, `maxTeams`, entry fee/deposit, check-in deadline và prize.
4. Admin publish/open registration khi setup hợp lệ.
5. Admin duyệt hoặc từ chối tournament registration.
6. Khi đủ điều kiện schedule, admin generate race/heat từ registration approved theo round config.
7. Admin phân participant vào race/heat, phân referee, chỉnh lịch race/heat nếu cần và publish lịch.
8. Admin duyệt draft result do referee gửi.
9. Admin trigger hoặc xác nhận advancement qua từng round theo rule đã cấu hình.
10. Ở chung kết, admin xác nhận final ranking, prize payout và tournament completed.
11. Admin xem statistics theo tournament, round/race/heat, registration, check-in, result, prize payout và finance ledger.

### Referee nhận lịch, check-in và nhập kết quả

1. Referee có profile/account hợp lệ và được admin assign vào race/heat.
2. Referee nhận notification/email khi race/heat scheduled.
3. Trước ngày thi đấu 3 ngày, referee nhận reminder.
4. Đến ngày race/heat, referee mở hoặc thực hiện check-in theo trạng thái race/heat.
5. Referee check-in từng participant, ghi chú sức khỏe, giấy tờ, absent hoặc disqualified nếu rule cho phép.
6. Referee ghi violation, penalty và draft result theo finish order/time.
7. Referee submit report để admin review.
8. Referee không tự tạo result chính thức; result chỉ chính thức sau khi admin approve.

### System xử lý tiền qua wallet ledger

1. Mọi money movement dùng `BigDecimal`, currency mặc định `VND`.
2. Mỗi wallet operation có transaction DB, reference type, reference id, status, metadata và idempotency key khi cần.
3. Deposit callback paid credit cả user wallet và admin/system wallet.
4. Withdrawal request hold tiền user trước; reject thì release, mark-paid thì capture/trừ hold và giảm admin wallet.
5. Jockey invitation hold tiền owner khi tạo, release khi cancel/reject, capture và payout khi accept.
6. Tournament registration hold entry fee/deposit khi tạo, capture khi approve, release khi reject/cancel.
7. Prize payout debit admin/system wallet và credit người nhận theo prize recipient policy.
8. Betting, marketplace và refund đều phải trace được qua ledger.

## Phase 0 - Siết nền tảng hiện có

### Flow chính

1. Guest đăng ký hoặc đăng nhập.
2. System xác thực credential, phát token và trả thông tin current user.
3. User gọi API theo role. Role guard kiểm tra quyền trước khi vào service.
4. Admin dùng token admin để gọi API quản trị.
5. API lỗi trả response thống nhất cho validation, unauthorized, forbidden, not found và business rule.
6. Swagger/OpenAPI hiển thị endpoint, schema và bearer auth để dev/test dễ kiểm tra.

### Trạng thái và điều kiện chặn

- Token thiếu hoặc sai thì trả unauthorized.
- Token hợp lệ nhưng role không đủ thì trả forbidden.
- Validation sai thì trả lỗi có field/message rõ ràng.
- API không được lộ stack trace ra client.

### Acceptance flow

- Admin và user đăng nhập được.
- User thường không gọi được admin API.
- Swagger mở được và có bearer auth.
- Seed admin không tạo trùng khi chạy lại.

## Phase 1 - Wallet core với ví admin trung tâm

### Flow chính

1. Khi user được tạo hoặc lần đầu gọi wallet API, system tạo `UserWallet`.
2. System có một `AdminWallet` hoặc `SystemWallet` mặc định cho currency `VND`.
3. Wallet lưu `availableBalance`, `holdBalance`, `totalBalance`.
4. Service cung cấp operation `credit`, `debit`, `hold`, `release`, `capture`, `refund`.
5. Mỗi operation tạo wallet transaction có reference, type, status, metadata và audit fields.

### Trạng thái và điều kiện chặn

- Không cho `availableBalance` hoặc `holdBalance` âm.
- Không cho hold/debit vượt available.
- Không cho capture vượt hold.
- Nếu ghi ledger lỗi thì rollback toàn bộ thay đổi balance.

### Tiền và ledger

- `credit`: tăng available.
- `debit`: giảm available.
- `hold`: chuyển available sang hold.
- `release`: chuyển hold về available.
- `capture`: trừ hold, coi như tiền đã được thu.
- `refund`: trả tiền về available theo reference gốc.

### Acceptance flow

- User xem được ví và lịch sử giao dịch của mình.
- Admin xem được ví admin và transaction admin.
- Mọi biến động tiền trace được qua ledger.

## Phase 2 - Payment deposit MVP

### Flow chính

1. User tạo deposit order với amount, currency và provider.
2. Nếu dùng manual/bank transfer, system trả thông tin chuyển khoản và mã tham chiếu.
3. Payment provider gửi callback.
4. System verify chữ ký/token, timestamp và reference.
5. Callback paid hợp lệ credit `UserWallet.availableBalance`.
6. Cùng callback đó credit `AdminWallet.availableBalance`.
7. System tạo ledger hai phía với cùng reference để đối soát.

### Trạng thái và điều kiện chặn

- Callback sai chữ ký bị từ chối.
- Callback trùng không được cộng tiền lần hai.
- Deposit order expired/cancelled không được paid lại nếu policy không cho phép.
- Nếu credit một ví lỗi thì rollback toàn bộ.

### Notification/email

- Gửi notification khi deposit order được tạo nếu cần.
- Gửi notification/email khi deposit paid hoặc failed.

### Acceptance flow

- User tạo được lệnh nạp và xem trạng thái.
- Callback thành công tạo ledger user/admin.
- Admin wallet phản ánh tổng tiền thật user đã nạp.

## Phase 3 - Withdraw và audit tài chính

### Flow chính

1. User tạo withdrawal request với amount và bank info.
2. System hold tiền user từ available sang hold.
3. Admin xem withdrawal pending.
4. Admin approve để xác nhận sẽ xử lý ngoài hệ thống.
5. Nếu admin reject, system release hold về available của user.
6. Nếu admin mark-paid, system capture/trừ user hold và trừ `AdminWallet.availableBalance`.
7. Admin withdraw trực tiếp từ admin wallet phải nhập reason và ghi audit log.

### Trạng thái và điều kiện chặn

- User không đủ available thì không tạo được withdrawal.
- Withdrawal đã paid/rejected không xử lý lại.
- Admin wallet không được âm khi mark-paid hoặc admin withdraw.
- Admin action nhạy cảm phải có audit log.

### Tiền và ledger

- Create withdrawal: `hold`.
- Reject: `release`.
- Mark-paid: `capture` user hold và `debit` admin wallet.
- Admin withdraw: `debit` admin wallet kèm audit reason.

### Acceptance flow

- Withdraw chạy được từ pending đến paid hoặc rejected.
- Admin wallet chỉ giảm khi mark-paid hoặc admin withdraw trực tiếp.
- Ledger và audit đủ để đối soát.

## Phase 4 - Horse và Jockey profile

### Flow chính

1. Owner tạo và cập nhật horse profile của mình.
2. Owner upload ảnh, giấy tờ hoặc tài liệu liên quan qua storage.
3. Jockey tạo hoặc cập nhật profile, license, avatar, tài liệu, giá thuê, awards, achievements và specialties.
4. Admin duyệt, từ chối hoặc suspend horse.
5. Admin duyệt, từ chối hoặc suspend jockey.
6. Public API chỉ hiển thị horse/jockey đã approved theo policy.

### Trạng thái và điều kiện chặn

- Horse lifecycle: `DRAFT/PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`.
- Jockey lifecycle tương tự: pending, approved, rejected, suspended.
- Owner không được sửa horse của owner khác.
- Horse chưa `APPROVED` không được đăng ký tournament.
- Jockey chưa `APPROVED` không được nhận invitation hợp lệ hoặc assignment.

### Notification/email

- Gửi notification khi admin approve/reject/suspend horse hoặc jockey.
- Reject/suspend cần có reason để user biết cách xử lý.

### Acceptance flow

- Owner quản lý được ngựa của mình.
- Jockey approved mới xuất hiện cho owner thuê.
- Admin có thể kiểm soát hồ sơ không hợp lệ.

## Phase 5 - Owner-Jockey invitation

### Flow chính

1. Owner chọn horse approved của mình và jockey approved.
2. Owner gửi invitation với context phù hợp.
3. System snapshot hire price và hold tiền thuê từ ví owner.
4. Jockey nhận notification/email invitation.
5. Jockey accept hoặc reject.
6. Nếu reject, system release toàn bộ tiền hold về owner.
7. Nếu owner cancel khi pending, system release toàn bộ tiền hold về owner.
8. Nếu accept, system capture hold, credit net cho jockey và credit tax/fee cho admin nếu có.

### Trạng thái và điều kiện chặn

- Invitation active duplicate cho cùng horse/jockey/context bị chặn.
- Jockey không được accept invitation của người khác.
- Invitation rejected/cancelled không dùng được cho tournament registration.
- Owner không đủ tiền thì không tạo được invitation.

### Tiền và ledger

- Create invitation: hold owner wallet.
- Cancel/reject: release owner hold.
- Accept: capture owner hold, payout jockey, credit admin fee/tax nếu có.

### Acceptance flow

- Jockey chỉ nhận tiền sau khi accept.
- Owner cancel pending được hoàn toàn bộ tiền hold.
- Invitation accepted tạo quan hệ hợp lệ cho registration.

## Phase 6 - Tournament setup + round/prize configuration

### Flow chính

1. Admin tạo tournament với tên, mô tả, địa điểm, thời gian đăng ký, thời gian thi đấu.
2. Admin cấu hình registration window, entry fee/deposit amount và check-in deadline.
3. Admin cấu hình `minTeams`, `maxTeams` cho toàn tournament và có thể cấu hình giới hạn participant cho từng round/race nếu cần.
4. Admin cấu hình round structure: vòng loại, bán kết, chung kết hoặc cấu trúc tùy giải.
5. Admin cấu hình số race/heat trong từng round và số participant tối thiểu/tối đa của mỗi race/heat.
6. Admin cấu hình advancement rule: số participant thắng hoặc đi tiếp từ mỗi race/heat/round, ví dụ chọn theo rank hoặc thời gian chạy nhanh nhất.
7. Admin cấu hình prize: hạng nhất, hạng nhì, hạng ba hoặc hạng khác, amount/item, recipient policy và note.
8. Admin publish hoặc open registration khi setup hợp lệ.
9. Public/owner/spectator xem được tournament đã publish/open/scheduled/ongoing.

### Trạng thái và điều kiện chặn

- Tournament chưa đủ basic info, round config, race/heat config, `minTeams`/`maxTeams` hoặc prize config bắt buộc thì không publish/open.
- `minTeams` phải từ 2 đội trở lên.
- `minTeams` không được vượt `maxTeams`.
- Sức chứa race/heat và advancement rule phải tạo được đường đi hợp lệ từ vòng đầu đến chung kết.
- Entry fee/deposit và prize amount không được âm.
- Public API không hiển thị tournament draft/private.

### Notification/email

- Có thể gửi notification khi tournament được mở đăng ký.
- Admin action cần audit nếu thay đổi cấu hình quan trọng.

### Acceptance flow

- Admin tạo được giải có vòng đấu, race/heat, số participant, rule đi tiếp và giải thưởng rõ ràng.
- Tournament đi được từ `DRAFT` đến `PUBLISHED` và `OPEN_REGISTRATION`.

## Phase 7 - Tournament registration + deposit hold

### Flow chính

1. Owner xem danh sách tournament đang open hoặc ongoing theo rule public.
2. Owner mở detail tournament để kiểm tra điều kiện.
3. Owner chọn horse team hợp lệ: `horse team = horse + owner + jockey accepted`.
4. System kiểm tra horse approved, horse thuộc owner và jockey invitation/assignment accepted.
5. System kiểm tra tournament còn trong registration window và chưa vượt `maxTeams`.
6. System hold entry fee/deposit từ ví owner.
7. Registration được tạo ở trạng thái `PENDING`.
8. System gửi notification/email cho owner và jockey.
9. Admin approve registration thì system capture tiền hold theo policy.
10. Admin reject/cancel registration thì system release toàn bộ tiền hold về owner.

### Trạng thái và điều kiện chặn

- Tournament chưa open hoặc đã đóng đăng ký thì bị chặn.
- Tournament vượt `maxTeams` thì bị chặn.
- Horse không approved hoặc không thuộc owner thì bị chặn.
- Jockey chưa accepted invitation thì bị chặn.
- Duplicate registration cùng horse/tournament bị chặn.
- Owner không đủ tiền thì không tạo được registration.

### Tiền và ledger

- Registration created: entry fee/deposit dùng `hold -> capture/release`.
- Approve: `capture`.
- Reject/cancel/withdraw theo policy: `release` hoặc refund nếu đã capture.

### Acceptance flow

- Registration có flow pending/approved/rejected rõ ràng.
- Entry fee/deposit có ledger hold/capture/release đầy đủ.
- Owner nhận notification/email ở các trạng thái chính.

## Phase 8 - Race scheduling/check-in reminder

### Flow chính

1. Admin generate race/heat từ registration approved theo round config của tournament.
2. System chỉ schedule tournament khi số horse team approved đạt `minTeams`.
3. System tạo participant list cho từng race/heat, bảo đảm không vượt quá sức chứa đã cấu hình.
4. Admin có thể xem danh sách participant/horse team và điều chỉnh phân bổ race/heat phù hợp trước khi publish lịch.
5. Admin hoặc system gán gate number không trùng trong race/heat.
6. Admin phân công referee cho race/heat.
7. System kiểm tra jockey/referee không trùng lịch cùng thời điểm.
8. Race/heat chuyển sang `SCHEDULED`; tournament/round chuyển trạng thái phù hợp.
9. System gửi notification/email race scheduled cho owner, jockey và referee.
10. Scheduler gửi reminder trước lịch thi đấu 3 ngày cho owner, jockey và referee.
11. Public/spectator xem được lịch race đã publish.

### Trạng thái và điều kiện chặn

- Registration chưa approved không được schedule.
- Tournament chưa đủ `minTeams` không được generate race.
- Race/heat vượt sức chứa participant đã cấu hình thì bị chặn.
- Gate number duplicate trong cùng race/heat bị chặn.
- Jockey hoặc referee trùng lịch bị chặn.
- Reminder cùng race/heat/recipient không được gửi trùng.

### Notification/email

- Race scheduled notification/email cho owner, jockey, referee.
- Reminder trước lịch thi đấu 3 ngày.
- Update nếu admin đổi lịch hoặc đổi referee.

### Acceptance flow

- Race/heat có participant list, gate number, referee và lịch rõ ràng.
- Các bên liên quan nhận notification/email đúng thời điểm.

## Phase 9 - Check-in + result recording + round advancement

### Flow chính

1. Referee mở check-in hoặc thực hiện check-in khi race/heat đúng trạng thái.
2. Referee check-in từng participant.
3. Referee ghi chú sức khỏe, giấy tờ, trạng thái, absent hoặc disqualified nếu rule cho phép.
4. Race/heat chỉ start khi số participant check-in đạt điều kiện tối thiểu.
5. Referee ghi violation, penalty và draft result theo finish order/time.
6. Referee submit report/draft result.
7. Admin review và approve/reject draft result.
8. Khi result approved, race/heat chuyển `RESULT_CONFIRMED` hoặc `COMPLETED`.
9. System dùng advancement rule của round hiện tại để chọn winner/qualifier vào round tiếp theo.
10. Nếu còn vòng tiếp theo, system tạo participant seed đúng số lượng để admin schedule tiếp.
11. System gửi notification khi result published và khi participant/horse team được vào vòng tiếp theo.

### Trạng thái và điều kiện chặn

- Không start race/heat đã completed/cancelled.
- Participant chưa check-in bị xử lý theo rule.
- Draft result không được duplicate rank.
- Admin approve result chỉ một lần.
- Advancement phải chọn đúng số participant/horse team theo rule đã cấu hình cho race/heat hoặc round.

### Notification/email

- Result published notification cho owner, jockey, referee và spectator liên quan.
- Advancement notification cho participant/horse team đi tiếp.
- Disqualified/absent notification nếu cần.

### Acceptance flow

- Kết quả race/heat được xác nhận chính thức.
- Participant/horse team thắng hoặc đi tiếp trace được qua advancement record.
- Quy trình lặp được qua nhiều vòng, nhiều race/heat đến chung kết.

## Phase 10 - Final result + prize payout + tournament statistics

### Flow chính

1. Khi chung kết completed, system xác định winner và final ranking.
2. System tạo leaderboard snapshot cho tournament.
3. System tính prize theo prize config đã snapshot.
4. Admin review hoặc xác nhận final result/prize payout nếu policy yêu cầu.
5. System payout prize qua wallet service theo recipient policy.
6. Prize payout trace qua ledger với reference tournament/result/prize.
7. System gửi notification/email cho winner, owner, jockey và admin.
8. Tournament chuyển `COMPLETED` khi result và payout hoàn tất hoặc được admin xác nhận.
9. Admin xem statistics tổng quan: owner/customer count, horse team count, jockey count, referee count, registration count.
10. Admin xem statistics round/race/heat: scheduled, completed, cancelled, check-in, absent, disqualified, winner.
11. Admin xem finance statistics: entry fee captured, deposit released/refunded, prize payout và admin/system wallet transaction.

### Trạng thái và điều kiện chặn

- Final result chỉ confirm một lần.
- Prize payout không trả hai lần khi retry.
- Leaderboard snapshot không đổi sau confirm, trừ khi có admin adjustment có audit.
- Tournament chưa đủ result/payout theo policy thì chưa completed.

### Tiền và ledger

- Prize payout debit admin/system wallet và credit recipient wallet.
- Mọi payout có `referenceType`, `referenceId`, status, metadata và idempotency.
- Failed payout được ghi trạng thái để retry an toàn.

### Acceptance flow

- Tournament hoàn tất có winner, leaderboard, prize payout và statistics.
- Mọi payout và fee trace được qua ledger.

## Phase 11 - Spectator prediction

### Flow chính

1. Spectator xem race public.
2. Spectator chọn participant để dự đoán trước giờ race start.
3. System lock prediction khi race bắt đầu.
4. Khi result confirmed, system settle prediction.
5. Nếu có reward nhỏ, system credit wallet hoặc inventory theo policy.

### Trạng thái và điều kiện chặn

- Không cho dự đoán sau race start.
- Một spectator không tạo nhiều prediction active cho cùng race nếu policy là một lần.
- Prediction không ảnh hưởng betting tiền thật.

### Notification/email

- Gửi notification khi prediction settled.
- Gửi notification nếu spectator nhận reward.

### Acceptance flow

- Prediction hoạt động độc lập với betting.
- Không tạo rủi ro tiền thật nếu chưa có policy reward bằng ví.

## Phase 12 - Betting bằng ví

### Flow chính

1. Admin hoặc config bật feature flag betting.
2. User xem race còn mở betting.
3. User đặt bet trước race start với stake và odds.
4. System hold hoặc debit stake theo betting policy.
5. Khi race start, system lock bet.
6. Khi result confirmed, system settle won/lost.
7. Nếu race cancelled hoặc result voided, system refund stake.
8. Tất cả stake, payout, lost, refund ghi ledger đầy đủ.

### Trạng thái và điều kiện chặn

- Betting có feature flag và mặc định có thể tắt hoàn toàn.
- Feature flag off thì API betting bị chặn.
- Không cho bet sau race start.
- Không settle cùng bet hai lần.
- Không payout nếu result chưa confirmed.

### Tiền và ledger

- Stake: hold/debit theo policy.
- Lost: capture stake hoặc giữ debit.
- Won: payout qua wallet service.
- Cancelled/voided: refund/release stake.

### Acceptance flow

- Khi betting tắt, toàn bộ betting flow không hoạt động.
- Khi betting bật, mọi dòng tiền betting trace được qua ledger.

## Phase 13 - Item marketplace

### Flow chính

1. Admin tạo item, giá, trạng thái active và stock nếu có.
2. User xem marketplace.
3. User mua item bằng ví.
4. System debit user wallet và tăng inventory.
5. Nếu policy cho phép bán lại, user bán item.
6. System giảm inventory và credit user wallet theo giá/policy.

### Trạng thái và điều kiện chặn

- Item inactive không mua được.
- Out of stock không mua được nếu có quản lý stock.
- User không đủ tiền thì không mua được.
- User không sở hữu item thì không bán được.

### Tiền và ledger

- Item purchase: debit user wallet, tạo item order và inventory record.
- Item sale: giảm inventory, credit user wallet.
- Refund/cancel order nếu có phải trace qua ledger.

### Acceptance flow

- Marketplace đồng bộ wallet ledger và inventory.
- Admin kiểm soát được item active/inactive và giá.

## Phase 14 - Notification/WebSocket/Email

### Flow chính

1. System tạo notification cho invitation, payment, withdraw, registration, race status, check-in, result, advancement và prize payout.
2. System gửi email cho registration created/approved/rejected, race reminder trước 3 ngày, result published và prize payout.
3. User gọi API lấy danh sách notification.
4. User mark notification as read.
5. WebSocket publish update cho race status, result và leaderboard.
6. Scheduler/idempotency đảm bảo reminder và event không gửi trùng.

### Trạng thái và điều kiện chặn

- User chỉ xem notification của chính mình.
- Public WebSocket topic không được leak dữ liệu nhạy cảm.
- Reminder cùng race/recipient/reference không gửi duplicate.
- Email failed cần log trạng thái để retry hoặc điều tra.

### Acceptance flow

- Người dùng nhận update cần thiết mà không phải refresh liên tục.
- Email/notification gửi đúng recipient và đúng sự kiện chính.
- Reminder trước 3 ngày hoạt động ổn định.

## Phase 15 - Production hardening

### Flow chính

1. Dùng Flyway hoặc Liquibase cho schema migration.
2. List API quan trọng có pagination, sorting và filtering.
3. Rate limit auth, payment callback, betting, withdrawal và registration.
4. Payment callback có signature, timestamp, replay protection và IP allowlist nếu provider hỗ trợ.
5. Admin action, tournament setup, result approval và money movement có audit log.
6. Logging có correlation id/reference id.
7. Monitoring/health check cho DB, payment provider, email scheduler và service quan trọng.
8. Rà soát DB index cho query list và lookup theo reference/status.
9. Bổ sung test coverage cho money flow, role guard, migration và idempotency.

### Trạng thái và điều kiện chặn

- Migration phải chạy sạch từ DB trống.
- Callback replay phải bị chặn.
- Endpoint nhạy cảm phải có rate limit và role guard.
- Log không chứa secret, token hoặc dữ liệu nhạy cảm không cần thiết.

### Acceptance flow

- Backend deploy được với migration rõ ràng.
- Log/audit đủ để điều tra sự cố.
- Test coverage tốt cho flow rủi ro cao.

## Checklist kiểm tra sau khi hoàn thành tất cả phase

- Auth/role guard hoạt động cho guest, user, owner, jockey, referee và admin.
- Wallet core có đầy đủ `credit`, `debit`, `hold`, `release`, `capture`, `refund`.
- Deposit credit cả user wallet và admin/system wallet.
- Withdrawal hold trước, mark-paid mới trừ admin wallet.
- Horse/jockey phải approved trước khi dùng trong tournament.
- Invitation accepted mới tạo horse team hợp lệ.
- `horse team = horse + owner + jockey accepted`.
- Tournament setup có round, race/heat, `minTeams`, `maxTeams`, entry fee/deposit, prize và advancement rule.
- Entry fee/deposit dùng `hold -> capture/release`.
- Race/heat chỉ generate khi approved teams đạt `minTeams`.
- Gate number không trùng trong cùng race/heat.
- Reminder trước lịch thi đấu 3 ngày được gửi đúng owner, jockey và referee.
- Result chỉ chính thức sau khi admin approve.
- Advancement tạo được participant seed cho vòng tiếp theo.
- Final result tạo leaderboard snapshot.
- Prize payout trace qua ledger.
- Betting có feature flag và có thể tắt hoàn toàn.
- Marketplace đồng bộ wallet và inventory.
- Notification/email/websocket không leak dữ liệu nhạy cảm.
- Production hardening có migration, audit, logging, monitoring, rate limit và index.

## Ghi chú triển khai

- Tài liệu này chỉ mô tả luồng nghiệp vụ và trình tự thực hiện phase, không định nghĩa chi tiết code backend.
- Khi implement, ưu tiên giữ ledger và idempotency cho tất cả money flow.
- Các API public chỉ hiển thị dữ liệu đã publish/approved theo đúng trạng thái nghiệp vụ.
- Các thao tác admin nhạy cảm cần audit để truy vết sau này.
