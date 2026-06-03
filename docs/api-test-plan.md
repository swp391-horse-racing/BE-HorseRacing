# API Test Plan - Hoser Backend

Tai lieu nay dung de test thu cong/Postman va doi chieu voi smoke test hien co cua project.

- Base URL local: `http://localhost:8080`
- API prefix chinh: `/api/v1`
- Webhook ZaloPay: `/api/zalopay`
- Auth header: `Authorization: Bearer {{token}}`
- Response chuan phan lon API: `ApiResponse` co `success`, `message`, `data`
- Smoke test tu dong san co: `src/test/java/com/minhthien/hoser_backend/controller/AllApiSmokeTest.java`

Chay smoke test nhanh tren Windows:

```powershell
.\mvnw.cmd -Dtest=AllApiSmokeTest test
```

## Bien Postman/Bruno Nen Tao

| Bien | Gia tri goi y |
|---|---|
| `baseUrl` | `http://localhost:8080` |
| `userToken` | token cua role `USER` |
| `ownerToken` | token cua role `OWNER` |
| `jockeyToken` | token cua role `JOCKEY` |
| `spectatorToken` | token cua role `SPECTATOR` |
| `refereeToken` | token cua role `REFEREE` |
| `adminToken` | token cua role `ADMIN` |
| `userId`, `ownerId`, `jockeyId`, `refereeId` | id lay tu login/me hoac admin user list |
| `horseId`, `jockeyInvitationId`, `tournamentId`, `raceId` | id sinh trong cac flow ben duoi |
| `participantId`, `registrationId`, `betMarketId`, `betId` | id sinh trong race/betting flow |
| `paymentOrderId`, `referenceCode`, `withdrawalId` | id sinh trong wallet/payment flow |

## Common Assertions

Kiem tra chung cho moi API:

- Status khong duoc la `5xx`.
- API thanh cong nen tra `200 OK` va `success = true`.
- Request thieu token voi API private nen tra `401`.
- Token dung nhung sai role nen tra `403` hoac `400` tuy service validation.
- Body sai validation nen tra `400`.
- Resource khong ton tai nen tra `404` hoac response loi co message ro rang.
- Sau cac action thay doi state, goi lai API list/detail de xac nhan state moi.

## Mau Body Va Form Data

### Auth/User

`RegisterRequest`

```json
{
  "username": "tester01",
  "fullName": "Tester One",
  "email": "tester01@example.com",
  "phone": "0900000001",
  "password": "Password123!"
}
```

`LoginRequest`

```json
{
  "email": "tester01@example.com",
  "password": "Password123!"
}
```

`UpdatePasswordRequest`

```json
{
  "currentPassword": "Password123!",
  "newPassword": "Password456!"
}
```

`UserProfileRequest` JSON

```json
{
  "fullName": "Tester Updated",
  "phone": "0900000002",
  "location": "Ho Chi Minh City"
}
```

`UserProfileRequest` multipart: `fullName`, `phone`, `location`, `avatar`.

### Role Applications

Owner multipart: `stableName`, `experienceYears`, `address`, `bio`, `verificationDocument`.

Jockey multipart: `licenseNumber`, `experienceYears`, `heightCm`, `weightKg`, `hirePrice`, `bio`, `awards`, `specialties`, `avatar`, `achievements`, `licenseDocument`.

Spectator JSON:

```json
{
  "displayName": "Race Fan",
  "phone": "0900000100",
  "location": "Ho Chi Minh City",
  "favoriteHorseBreed": "Thoroughbred",
  "bio": "Like horse racing"
}
```

Referee multipart: `licenseNumber`, `experienceYears`, `specialty`, `bio`, `certificationDocument`.

Admin review JSON:

```json
{
  "reason": "Du thong tin xet duyet"
}
```

### Horse/Jockey

Horse multipart: `name`, `breed`, `age`, `gender`, `color`, `heightCm`, `weightKg`, `image`, `document`.

Jockey invitation:

```json
{
  "horseId": 1,
  "jockeyId": 2,
  "message": "Moi ban tham gia doi dua"
}
```

Invitation decision:

```json
{
  "note": "Dong y"
}
```

### News

News JSON:

```json
{
  "title": "Race News",
  "summary": "Short summary",
  "content": "Full content",
  "category": "Su kien",
  "featured": true,
  "publishedAt": "2026-06-01T08:00:00"
}
```

News multipart: form-data gom `title`, `summary`, `content`, `category`, `featured`, `publishedAt`, `image` tuy chon.

```text
title = Tin co anh
summary = Tom tat co anh
content = Noi dung co anh
category = Su kien
featured = true
publishedAt = 2026-06-01T09:00:00
image = file jpg/png
```

### Tournament/Race

Tournament:

```json
{
  "name": "Summer Cup",
  "description": "Giai dua mua he",
  "location": "Ho Chi Minh City",
  "bannerUrl": "https://cdn.example/summer-cup.jpg",
  "registrationOpenAt": "2026-06-01T08:00:00",
  "registrationCloseAt": "2026-06-02T08:00:00",
  "startAt": "2026-06-03T08:00:00",
  "endAt": "2026-06-03T18:00:00",
  "checkInDeadlineAt": "2026-06-03T07:30:00",
  "minTeams": 1,
  "maxTeams": 8,
  "jockeyChallengeEnabled": true,
  "jockeyChallengeFirstPoints": 3,
  "jockeyChallengeSecondPoints": 2,
  "jockeyChallengeThirdPoints": 1,
  "jockeyChallengePrizes": [
    { "rank": 1, "amount": 100000, "note": "Top jockey" }
  ]
}
```

Race:

```json
{
  "name": "Heat 1",
  "distance": "1200m",
  "scheduledStartAt": "2026-06-03T09:00:00",
  "scheduledEndAt": "2026-06-03T09:20:00",
  "minParticipants": 1,
  "maxParticipants": 8,
  "entryFee": 10000,
  "refereeId": 5,
  "note": "Vong loai",
  "prizes": [
    { "rank": 1, "amount": 50000, "itemName": "Cup", "note": "Winner" }
  ]
}
```

Race registration:

```json
{
  "horseId": 1,
  "jockeyInvitationId": 1,
  "note": "Dang ky thi dau"
}
```

Finalize race result:

```json
{
  "results": [
    {
      "participantId": 1,
      "rank": 1,
      "finishTimeMillis": 72000,
      "status": "FINISHED",
      "note": "Winner"
    }
  ]
}
```

### Betting

Bet market:

```json
{
  "minStake": 1000,
  "maxStake": 100000,
  "note": "Market test"
}
```

Bet:

```json
{
  "participantId": 1,
  "stakeAmount": 10000
}
```

### Wallet/Payment/Withdrawal

Deposit order:

```json
{
  "amount": 10000,
  "provider": "ZALOPAY"
}
```

`currency` khong can gui; backend mac dinh la `VND`.

Deposit callback:

```json
{
  "referenceCode": "DEP-REFERENCE",
  "status": "PAID",
  "callbackToken": "test-callback-token",
  "providerTransactionId": "PROVIDER-TX-1",
  "metadata": "{}"
}
```

User withdrawal:

```json
{
  "amount": 1000,
  "bankName": "Test Bank",
  "bankAccountNumber": "123456789",
  "bankAccountName": "Tester One",
  "reason": "Rut tien test"
}
```

Withdrawal decision:

```json
{
  "note": "Da kiem tra"
}
```

Admin wallet withdrawal:

```json
{
  "amount": 1000,
  "bankName": "Admin Bank",
  "bankAccountNumber": "987654321",
  "bankAccountName": "Admin",
  "reason": "Rut tien vi admin"
}
```

## Endpoint Checklist

Cot `Auth/Role` la role can dung khi test happy path. Cac API public van nen test them voi token va khong token.

### Auth

| ID | Method | Path | Auth/Role | Request | Expected |
|---|---|---|---|---|---|
| AUTH-01 | POST | `/api/v1/auth/register` | Public | `RegisterRequest` | Tao user, tra token |
| AUTH-02 | POST | `/api/v1/auth/login` | Public | `LoginRequest` | Tra token |
| AUTH-03 | GET | `/api/v1/auth/me` | Authenticated | none | Tra user hien tai |
| AUTH-04 | PUT | `/api/v1/auth/password` | Authenticated | `UpdatePasswordRequest` | Doi password, login password moi duoc |
| AUTH-05 | POST | `/api/v1/auth/logout` | Authenticated | none | Clear context, response thanh cong |
| AUTH-06 | POST | `/api/v1/auth/forgot-password` | Public | `{ "email": "..." }` | Tao/sent OTP |
| AUTH-07 | POST | `/api/v1/auth/reset-password` | Public | `{ "email", "otp", "newPassword" }` | Doi password bang OTP |
| AUTH-08 | POST | `/api/v1/auth/google` | Public | `{ "idToken": "..." }` | Token hop le login duoc; token rong khong 5xx |
| AUTH-09 | POST | `/api/v1/auth/facebook` | Public | `{ "accessToken": "..." }` | Token hop le login duoc; token rong khong 5xx |

### User/Admin/Audit/Finance

| ID | Method | Path | Auth/Role | Request | Expected |
|---|---|---|---|---|---|
| USER-01 | GET | `/api/v1/users/me/profile` | Authenticated | none | Tra profile |
| USER-02 | PUT | `/api/v1/users/me/profile` | Authenticated | JSON `UserProfileRequest` | Cap nhat profile |
| USER-03 | PUT | `/api/v1/users/me/profile` | Authenticated | multipart `UserProfileRequest` | Cap nhat profile/avatar |
| ADM-01 | GET | `/api/v1/admin/users` | ADMIN | none | List all users |
| ADM-02 | GET | `/api/v1/admin/users/active` | ADMIN | none | Chi user active |
| ADM-03 | GET | `/api/v1/admin/users/deactivated` | ADMIN | none | Chi user inactive |
| ADM-04 | GET | `/api/v1/admin/users/{id}` | ADMIN | path `id` | Detail user |
| ADM-05 | PUT | `/api/v1/admin/users/{userId}/deactivate` | ADMIN | none | User bi inactive |
| ADM-06 | PUT | `/api/v1/admin/users/{userId}/activate` | ADMIN | none | User active lai |
| ADM-07 | PUT | `/api/v1/admin/users/{userId}/role` | ADMIN | `{ "role": "OWNER" }` | Role duoc cap nhat |
| ADM-08 | GET | `/api/v1/admin/payout-debts` | ADMIN | none | Tong hop no payout |
| AUD-01 | GET | `/api/v1/admin/audit-logs` | ADMIN | query `referenceType`, `referenceId` optional | List audit log |
| FIN-01 | GET | `/api/v1/admin/finance-settings` | ADMIN | none | Tra setting hien tai, gom `jockeyHireTaxPercent`, `betWinningTaxPercent`, `bettingEnabled` |
| FIN-02 | PUT | `/api/v1/admin/finance-settings` | ADMIN | `{ "jockeyHireTaxPercent": 10.00, "betWinningTaxPercent": 10.00, "bettingEnabled": true }` | Setting doi |
| FIN-03 | GET | `/api/v1/admin/finance-settings/race-prize-shares` | ADMIN | none | Tra cau hinh chia giai |
| FIN-04 | PUT | `/api/v1/admin/finance-settings/race-prize-shares` | ADMIN | `RacePrizeShareSettingsRequest` | Cap nhat ty le chia giai |

### Role Applications

| ID | Method | Path | Auth/Role | Request | Expected |
|---|---|---|---|---|---|
| ROLE-01 | POST | `/api/v1/role-applications/owner` | USER | multipart owner | Don owner PENDING |
| ROLE-02 | POST | `/api/v1/role-applications/jockey` | USER | multipart jockey | Don jockey/profile PENDING |
| ROLE-03 | POST | `/api/v1/role-applications/spectator` | USER | JSON spectator | Spectator APPROVED ngay, user doi role SPECTATOR |
| ROLE-04 | POST | `/api/v1/role-applications/referee` | USER | multipart referee | Don referee PENDING |
| ROLE-05 | GET | `/api/v1/role-applications/me` | Authenticated | none | Don cua user hien tai |
| ROLE-06 | GET | `/api/v1/admin/role-applications` | ADMIN | query `role`, `status` optional | List don loc duoc |
| ROLE-07 | GET | `/api/v1/admin/role-applications/role/{role}` | ADMIN | role: `OWNER/JOCKEY/SPECTATOR/REFEREE` | List theo role |
| ROLE-08 | GET | `/api/v1/admin/role-applications/status/{status}` | ADMIN | status: `PENDING/APPROVED/REJECTED` | List theo status |
| ROLE-09 | PUT | `/api/v1/admin/role-applications/{profileId}/approve` | ADMIN | query `role` optional | Duyet don, user doi role |
| ROLE-10 | PUT | `/api/v1/admin/role-applications/{profileId}/reject` | ADMIN | query `role` optional + `AdminReviewRequest` | Tu choi don |

### Horse/Jockey/Invitation/Team

| ID | Method | Path | Auth/Role | Request | Expected |
|---|---|---|---|---|---|
| HORSE-01 | GET | `/api/v1/horses/approved` | Public | none | List ngua APPROVED |
| HORSE-02 | POST | `/api/v1/owner/horses` | OWNER | multipart horse | Tao ngua PENDING |
| HORSE-03 | GET | `/api/v1/owner/horses` | OWNER | none | List ngua cua owner |
| HORSE-04 | GET | `/api/v1/owner/horses/{id}` | OWNER | path `id` | Detail ngua cua owner |
| HORSE-05 | GET | `/api/v1/horses/{id}` | Public | path `id` | Detail ngua xem duoc |
| HORSE-06 | PUT | `/api/v1/owner/horses/{id}` | OWNER | multipart horse update | Cap nhat ngua |
| HORSE-07 | DELETE | `/api/v1/owner/horses/{id}` | OWNER | path `id` | Xoa ngua PENDING/REJECTED chua co activity |
| HORSE-08 | GET | `/api/v1/admin/horses` | ADMIN | query `status`, default `PENDING` | List ngua theo status |
| HORSE-09 | PUT | `/api/v1/admin/horses/{id}/approve` | ADMIN | none | Ngua APPROVED |
| HORSE-10 | PUT | `/api/v1/admin/horses/{id}/reject` | ADMIN | `AdminReviewRequest` | Ngua REJECTED |
| HORSE-11 | PUT | `/api/v1/admin/horses/{id}/suspend` | ADMIN | `AdminReviewRequest` | Ngua SUSPENDED |
| JOCK-01 | GET | `/api/v1/jockey/profile` | JOCKEY | none | Profile jockey hien tai |
| JOCK-02 | PUT | `/api/v1/jockey/profile` | JOCKEY | multipart profile update | Cap nhat profile |
| JOCK-03 | GET | `/api/v1/jockeys/available` | Public | none | List jockey available |
| JOCK-04 | GET | `/api/v1/jockeys/{id}` | Public | path `id` | Detail jockey approved |
| JOCK-05 | GET | `/api/v1/admin/jockey-profiles` | ADMIN | query `status`, default `PENDING` | List profile theo status |
| INV-01 | POST | `/api/v1/owner/jockey-invitations` | OWNER | `JockeyInvitationRequest` | Tao loi moi |
| INV-02 | GET | `/api/v1/owner/jockey-invitations` | OWNER | none | List loi moi cua owner |
| INV-03 | GET | `/api/v1/owner/jockey-invitations/{id}` | OWNER | path `id` | Detail loi moi cua owner |
| INV-04 | GET | `/api/v1/owners/me/jockeys` | OWNER | none | List jockey da accept |
| INV-05 | PUT | `/api/v1/owner/jockey-invitations/{id}/cancel` | OWNER | none | Loi moi CANCELLED |
| INV-06 | GET | `/api/v1/jockey/invitations` | JOCKEY | none | List loi moi cua jockey |
| INV-07 | GET | `/api/v1/jockey/invitations/{id}` | JOCKEY | path `id` | Detail loi moi cua jockey |
| INV-08 | PUT | `/api/v1/jockey/invitations/{id}/accept` | JOCKEY | optional `InvitationDecisionRequest` | Loi moi ACCEPTED |
| INV-09 | PUT | `/api/v1/jockey/invitations/{id}/reject` | JOCKEY | optional `InvitationDecisionRequest` | Loi moi REJECTED |
| TEAM-01 | GET | `/api/v1/owner/horse-teams/eligible` | OWNER | none | List team horse/jockey eligible |
| TEAM-02 | GET | `/api/v1/admin/tournaments/{id}/eligible-horse-teams` | ADMIN | path `id` | List team eligible cho tournament |

### News

| ID | Method | Path | Auth/Role | Request | Expected |
|---|---|---|---|---|---|
| NEWS-01 | POST | `/api/v1/admin/news` | ADMIN | JSON `NewsArticleRequest` | Tao news |
| NEWS-02 | POST | `/api/v1/admin/news` | ADMIN | multipart fields + `image` | Tao news co image |
| NEWS-03 | PUT | `/api/v1/admin/news/{id}` | ADMIN | JSON `NewsArticleUpdateRequest` | Cap nhat news |
| NEWS-04 | PUT | `/api/v1/admin/news/{id}` | ADMIN | multipart fields + `image` | Cap nhat news/image |
| NEWS-05 | DELETE | `/api/v1/admin/news/{id}` | ADMIN | path `id` | Xoa news |
| NEWS-06 | GET | `/api/v1/admin/news` | ADMIN | none | List admin news |
| NEWS-07 | GET | `/api/v1/admin/news/{id}` | ADMIN | path `id` | Detail admin news |
| NEWS-08 | GET | `/api/v1/news` | Public | query `featured`, `category` optional | List public news da publish |
| NEWS-09 | GET | `/api/v1/news/all` | Public | none | List all public news |
| NEWS-10 | GET | `/api/v1/news/{id}` | Public | path `id` | Detail public news |

### Tournament

| ID | Method | Path | Auth/Role | Request | Expected |
|---|---|---|---|---|---|
| TOUR-01 | POST | `/api/v1/admin/tournament-banners` | ADMIN | multipart `banner` | Upload banner, tra URL |
| TOUR-02 | PUT | `/api/v1/admin/tournaments/{id}/banner` | ADMIN | multipart `banner` | Cap nhat banner |
| TOUR-03 | POST | `/api/v1/admin/tournaments` | ADMIN | `TournamentRequest` | Tao tournament DRAFT |
| TOUR-04 | PUT | `/api/v1/admin/tournaments/{id}` | ADMIN | `TournamentUpdateRequest` | Cap nhat tournament |
| TOUR-05 | DELETE | `/api/v1/admin/tournaments/{id}` | ADMIN | path `id` | Xoa tournament DRAFT chua co activity |
| TOUR-06 | POST | `/api/v1/admin/tournaments/{id}/races` | ADMIN | `RaceRequest` | Them race |
| TOUR-07 | PUT | `/api/v1/admin/races/{raceId}` | ADMIN | `RaceRequest` | Cap nhat race theo race id |
| TOUR-08 | DELETE | `/api/v1/admin/races/{raceId}` | ADMIN | path `raceId` | Xoa race DRAFT chua co activity |
| TOUR-09 | PUT | `/api/v1/admin/tournaments/{id}/races` | ADMIN | array `RaceRequest` | Replace races |
| TOUR-10 | PUT | `/api/v1/admin/tournaments/{id}/status` | ADMIN | query `status` | Doi status |
| TOUR-11 | PUT | `/api/v1/admin/tournaments/{id}/open-registration` | ADMIN | none | Status OPEN_REGISTRATION |
| TOUR-12 | PUT | `/api/v1/admin/tournaments/{id}/close-registration` | ADMIN | none | Status REGISTRATION_CLOSED |
| TOUR-13 | PUT | `/api/v1/admin/tournaments/{id}/finalize` | ADMIN | none | Tournament COMPLETED, payout |
| TOUR-14 | GET | `/api/v1/admin/tournaments` | ADMIN | query `status` optional | List admin tournaments |
| TOUR-15 | GET | `/api/v1/admin/tournaments/{id}` | ADMIN | path `id` | Detail admin tournament |
| TOUR-16 | GET | `/api/v1/admin/tournaments/{id}/statistics` | ADMIN | path `id` | Statistics |
| TOUR-17 | GET | `/api/v1/admin/tournaments/{id}/payouts` | ADMIN | path `id` | Payout list |
| TOUR-18 | GET | `/api/v1/tournaments` | Public | none | List public tournament summaries, khong include races/prizes |
| TOUR-19 | GET | `/api/v1/tournaments/{id}` | Public | path `id` | Detail public tournament include races/prizes |
| TOUR-20 | GET | `/api/v1/tournaments/{id}/races` | Public | path `id` | Public race list |
| TOUR-21 | GET | `/api/v1/tournaments/{id}/leaderboard` | Public | path `id` | Leaderboard |

### Race Day

| ID | Method | Path | Auth/Role | Request | Expected |
|---|---|---|---|---|---|
| RACE-01 | POST | `/api/v1/races/{id}/registrations` | OWNER | `RaceRegistrationRequest` | Tao registration PENDING |
| RACE-02 | GET | `/api/v1/owner/race-registrations` | OWNER | none | List registration cua owner |
| RACE-03 | PUT | `/api/v1/owner/race-registrations/{id}/withdraw` | OWNER | optional `RaceRegistrationWithdrawRequest` | Registration WITHDRAWN |
| RACE-04 | GET | `/api/v1/admin/tournaments/{id}/race-registrations` | ADMIN | path `id` | Admin list registrations |
| RACE-05 | PUT | `/api/v1/admin/race-registrations/{id}/approve` | ADMIN | optional `RaceRegistrationReviewRequest` | Registration APPROVED |
| RACE-06 | PUT | `/api/v1/admin/race-registrations/{id}/reject` | ADMIN | optional `RaceRegistrationReviewRequest` | Registration REJECTED |
| RACE-07 | PUT | `/api/v1/admin/tournaments/{id}/schedule` | ADMIN | none | Tao participants/races scheduled |
| RACE-08 | GET | `/api/v1/admin/races/{id}/participants` | ADMIN | path `id` | List participants |
| RACE-09 | PUT | `/api/v1/admin/races/{raceId}/participants/{participantId}/gate` | ADMIN | `{ "gateNumber": 1 }` | Gate updated |
| RACE-10 | PUT | `/api/v1/admin/races/{id}/referee` | ADMIN | `{ "refereeId": 5 }` | Gan referee |
| RACE-11 | GET | `/api/v1/referee/races` | REFEREE | none | List race duoc gan |
| RACE-12 | GET | `/api/v1/referee/races/{id}/participants` | REFEREE | path `id` | Referee xem participants |
| RACE-13 | PUT | `/api/v1/referee/races/{id}/participants/{participantId}/check-in` | REFEREE | `RaceParticipantCheckInRequest` | Participant CHECKED_IN/ABSENT |
| RACE-14 | PUT | `/api/v1/referee/races/{id}/start` | REFEREE | none | Race ONGOING |
| RACE-15 | POST | `/api/v1/referee/races/{id}/results/finalize` | REFEREE | `RaceFinalizeResultRequest` | Race RESULT_CONFIRMED, results saved |
| RACE-16 | GET | `/api/v1/races/{id}/results` | Public | path `id` | Results |
| RACE-17 | POST | `/api/v1/races/{id}/complaints` | OWNER | `RaceComplaintRequest` | Complaint PENDING |
| RACE-18 | GET | `/api/v1/owner/race-complaints` | OWNER | none | Owner complaint list |
| RACE-19 | GET | `/api/v1/admin/race-complaints` | ADMIN | query `status` optional | Admin complaint list |
| RACE-20 | PUT | `/api/v1/admin/race-complaints/{id}/resolve` | ADMIN | `RaceComplaintResolveRequest` | Complaint APPROVED/REJECTED |
| RACE-21 | PUT | `/api/v1/admin/tournaments/{id}/jockey-challenge/finalize` | ADMIN | none | Final standings/prizes |
| RACE-22 | GET | `/api/v1/tournaments/{id}/jockey-challenge` | Public | path `id` | Jockey challenge standings |
| RACE-23 | PUT | `/api/v1/admin/races/{id}/cancel` | ADMIN | optional `{ "note": "..." }` | Race CANCELLED, active registrations refunded, active bets cancelled/released |

### Betting

| ID | Method | Path | Auth/Role | Request | Expected |
|---|---|---|---|---|---|
| BET-01 | POST | `/api/v1/admin/races/{raceId}/bet-market` | ADMIN | `BetMarketRequest` | Tao bet market DRAFT |
| BET-02 | PUT | `/api/v1/admin/bet-markets/{id}/open` | ADMIN | none | Market OPEN |
| BET-03 | PUT | `/api/v1/admin/bet-markets/{id}/close` | ADMIN | none | Market CLOSED |
| BET-04 | GET | `/api/v1/admin/bet-markets` | ADMIN | none | List markets |
| BET-05 | GET | `/api/v1/admin/bet-markets/{id}/bets` | ADMIN | path `id` | List bets trong market |
| BET-06 | GET | `/api/v1/races/{raceId}/bet-market` | Public | path `raceId` | Public open market |
| BET-07 | GET | `/api/v1/users/me/bettable-races` | USER/SPECTATOR | none | List race co the dat cuoc |
| BET-08 | POST | `/api/v1/races/{raceId}/bets` | SPECTATOR | `BetRequest` | Dat cuoc, tru/hold tien |
| BET-09 | GET | `/api/v1/users/me/bets` | Authenticated | none | List bet cua user |
| BET-10 | GET | `/api/v1/bets/{id}` | Authenticated | path `id` | Detail bet cua user |

### Wallet/Payment/Withdrawal

| ID | Method | Path | Auth/Role | Request | Expected |
|---|---|---|---|---|---|
| WAL-01 | GET | `/api/v1/wallets/me` | Authenticated | none | Vi user hien tai |
| WAL-02 | GET | `/api/v1/wallets/me/transactions` | Authenticated | none | Lich su giao dich |
| WAL-03 | GET | `/api/v1/admin/wallet` | ADMIN | none | Vi admin |
| WAL-04 | GET | `/api/v1/admin/wallet/transactions` | ADMIN | none | Lich su vi admin |
| PAY-01 | POST | `/api/v1/wallets/me/deposit-orders` | Authenticated | `CreateDepositOrderRequest` | Tao order nap tien |
| PAY-02 | GET | `/api/v1/wallets/me/deposit-orders` | Authenticated | none | List order cua user |
| PAY-03 | GET | `/api/v1/wallets/me/deposit-orders/{id}` | Authenticated | path `id` | Detail order cua user |
| PAY-04 | POST | `/api/v1/payment-callbacks/deposits` | Public/internal token | `DepositCallbackRequest` | Order PAID, wallet credit |
| PAY-05 | GET | `/api/v1/admin/payment-orders` | ADMIN | none | Admin list payment orders |
| PAY-06 | GET | `/api/v1/admin/payment-orders/{id}` | ADMIN | path `id` | Admin detail order |
| PAY-07 | GET | `/api/v1/admin/payment-callback-logs` | ADMIN | none | Callback logs |
| WD-01 | POST | `/api/v1/wallets/me/withdrawals` | Authenticated | `CreateWithdrawalRequest` | Tao request rut tien PENDING |
| WD-02 | GET | `/api/v1/wallets/me/withdrawals` | Authenticated | none | List withdrawal cua user |
| WD-03 | GET | `/api/v1/wallets/me/withdrawals/{id}` | Authenticated | path `id` | Detail withdrawal cua user |
| WD-04 | GET | `/api/v1/admin/withdrawals` | ADMIN | query `status` optional | Admin list withdrawals |
| WD-05 | GET | `/api/v1/admin/withdrawals/{id}` | ADMIN | path `id` | Admin detail withdrawal |
| WD-06 | PUT | `/api/v1/admin/withdrawals/{id}/approve` | ADMIN | optional `WithdrawalDecisionRequest` | Withdrawal APPROVED |
| WD-07 | PUT | `/api/v1/admin/withdrawals/{id}/reject` | ADMIN | optional `WithdrawalDecisionRequest` | Withdrawal REJECTED, refund neu can |
| WD-08 | PUT | `/api/v1/admin/withdrawals/{id}/mark-paid` | ADMIN | optional `WithdrawalDecisionRequest` | Withdrawal PAID |
| WD-09 | POST | `/api/v1/admin/wallet/withdrawals` | ADMIN | `AdminWalletWithdrawalRequest` | Ghi nhan rut tien vi admin |
| WD-10 | GET | `/api/v1/admin/wallet/withdrawals` | ADMIN | none | List admin wallet withdrawals |

### ZaloPay

| ID | Method | Path | Auth/Role | Request | Expected |
|---|---|---|---|---|---|
| ZALO-01 | GET | `/api/zalopay/return` | Public | query params tu ZaloPay | Tra map ket qua return |
| ZALO-02 | POST | `/api/zalopay/callback` | Public/ZaloPay signed | payload co `data`, `mac`, `type` | Valid mac tra `return_code = 1` |

## End-To-End Test Flows

### Flow 1 - Auth Va Profile

1. `AUTH-01` dang ky user moi.
2. `AUTH-02` login, luu `userToken`.
3. `AUTH-03` xac nhan token dung user.
4. `USER-01` xem profile.
5. `USER-02` cap nhat profile JSON.
6. `USER-03` cap nhat avatar multipart.
7. `AUTH-04` doi password.
8. Login lai bang password cu phai fail, password moi phai pass.
9. `AUTH-05` logout.
10. `AUTH-06` va `AUTH-07` test forgot/reset password.

### Flow 2 - Role Application Va Admin Duyet

1. Tao 4 user role `USER`.
2. Goi `ROLE-01`, `ROLE-02`, `ROLE-03`, `ROLE-04` tu tung user.
3. Goi `ROLE-05` de xac nhan spectator da `APPROVED`, cac role con lai van `PENDING`.
4. Admin goi `ROLE-06`, `ROLE-07`, `ROLE-08` de loc don.
5. Admin approve 1 owner, 1 jockey, 1 referee bang `ROLE-09`; spectator khong can admin approve.
6. Admin reject 1 don khac bang `ROLE-10`.
7. Login/me lai de xac nhan role da doi.

### Flow 3 - Owner, Horse, Jockey Invitation

1. Owner goi `HORSE-02` tao ngua.
2. Admin goi `HORSE-07`, `HORSE-08` approve ngua.
3. Public goi `HORSE-01`, `HORSE-05`.
4. Jockey goi `JOCK-01`, `JOCK-02`; admin/public goi `JOCK-05`, `JOCK-03`, `JOCK-04`.
5. Owner goi `INV-01`, `INV-02`, `INV-03`.
6. Jockey goi `INV-06`, `INV-07`, `INV-08`.
7. Owner goi `INV-04` thay jockey accepted.
8. Tao invitation khac va test `INV-05`, `INV-09`.

### Flow 4 - Tournament, Registration, Race Operation

1. Admin goi `TOUR-01`, `TOUR-03`, `TOUR-05`.
2. Public goi `TOUR-15`, `TOUR-16`, `TOUR-17`.
3. Admin goi `TOUR-08` mo dang ky.
4. Owner goi `TEAM-01`, `RACE-01`, `RACE-02`.
5. Admin goi `RACE-04`, `RACE-05`.
6. Admin goi `TOUR-09`, `RACE-07`.
7. Admin goi `RACE-08`, `RACE-09`, `RACE-10`.
8. Referee goi `RACE-11`, `RACE-12`, `RACE-13`, `RACE-14`, `RACE-15`.
9. Public goi `RACE-16`, `TOUR-18`.
10. Owner goi `RACE-17`, `RACE-18`; admin goi `RACE-19`, `RACE-20`.
11. Admin goi `RACE-21`, public goi `RACE-22`; neu huy race thi admin goi `RACE-23`.
12. Admin goi `TOUR-10`, `TOUR-13`, `TOUR-14`.

### Flow 5 - Betting

1. Admin co the cap nhat thue tien loi thang cuoc bang `FIN-02`:
   ```json
   {
     "betWinningTaxPercent": 10,
     "bettingEnabled": true
   }
   ```
2. Admin tao market bang `BET-01`.
3. Admin open market bang `BET-02`.
4. Public goi `BET-06`.
5. Spectator/user goi `BET-07`.
6. Spectator dat cuoc bang `BET-08`.
7. User goi `BET-09`, `BET-10`.
8. Admin goi `BET-04`, `BET-05`.
9. Admin close market bang `BET-03`.
10. Sau khi race finalize, xac nhan bet status va wallet transaction lien quan.
11. Neu user thang, stake duoc release day du; chi tien loi bi tru `betWinningTaxPercent`.

### Flow 6 - Wallet, Payment, Withdrawal

1. User goi `WAL-01`, `WAL-02`.
2. User tao deposit order bang `PAY-01`, luu `paymentOrderId`, `referenceCode`.
3. User goi `PAY-02`, `PAY-03`.
4. Goi `PAY-04` voi callback token dung, xac nhan wallet credit.
5. Admin goi `PAY-05`, `PAY-06`, `PAY-07`.
6. User goi `WD-01`, `WD-02`, `WD-03`.
7. Admin goi `WD-04`, `WD-05`, `WD-06`, `WD-08`.
8. Tao withdrawal khac va test `WD-07`.
9. Admin goi `WAL-03`, `WAL-04`, `WD-09`, `WD-10`.

### Flow 7 - News Va Webhook

1. Admin goi `NEWS-01` va `NEWS-02`.
2. Public goi `NEWS-08`, `NEWS-09`, `NEWS-10`.
3. Admin goi `NEWS-06`, `NEWS-07`.
4. Admin goi `NEWS-03`, `NEWS-04`.
5. Admin goi `NEWS-05`, public detail id vua xoa phai fail/not found.
6. Test `ZALO-01` voi query params gia lap.
7. Test `ZALO-02` voi payload signed hop le va payload sai `mac`.

## Negative Test Matrix

Nen lap lai cho moi nhom endpoint:

| Case | Du lieu test | Expected |
|---|---|---|
| Khong token | Bo header `Authorization` voi API private | `401` |
| Sai role | USER goi `/api/v1/admin/**`, OWNER goi referee API, JOCKEY goi owner API | `403` hoac business error khong 5xx |
| Id khong ton tai | Dung `999999999` cho `{id}` | `404`/error response khong 5xx |
| Body rong | POST/PUT body `{}` hoac multipart thieu field bat buoc | `400` |
| Enum sai | `status = INVALID`, `role = INVALID` | `400` |
| Amount am/0 | `amount = 0`, `stakeAmount = -1`, `entryFee = -1` | `400` |
| Duplicate | Register email cu, tao profile/don duplicate | `409`/business error |
| State sai | Approve lai record da approve, start race chua check-in, finalize tournament chua du dieu kien | Business error khong 5xx |
| Ownership sai | User A xem/sua resource cua User B | `403`/`404` |

## Enum Values Hay Dung Khi Test

| Enum | Values |
|---|---|
| `UserRole` | `USER`, `OWNER`, `ADMIN`, `JOCKEY`, `SPECTATOR`, `REFEREE` |
| `RoleApprovalStatus` | `NONE`, `PENDING`, `APPROVED`, `REJECTED` |
| `HorseStatus` | `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED` |
| `JockeyStatus` | `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED` |
| `TournamentStatus` | `DRAFT`, `PUBLISHED`, `OPEN_REGISTRATION`, `REGISTRATION_CLOSED`, `SCHEDULED`, `ONGOING`, `COMPLETED`, `CANCELLED` |
| `RaceStatus` | `DRAFT`, `SCHEDULED`, `ONGOING`, `RESULT_CONFIRMED`, `CANCELLED` |
| `RaceRegistrationStatus` | `PENDING`, `APPROVED`, `REJECTED`, `WITHDRAWN`, `CANCELLED` |
| `RaceParticipantStatus` | `REGISTERED`, `CHECKED_IN`, `FINISHED`, `DNF`, `DISQUALIFIED`, `ABSENT` |
| `RaceComplaintStatus` | `PENDING`, `APPROVED`, `REJECTED` |
| `BetMarketStatus` | `DRAFT`, `OPEN`, `CLOSED`, `SETTLED`, `CANCELLED` |
| `BetStatus` | `PLACED`, `LOCKED`, `WON`, `LOST`, `CANCELLED`, `UNPAID` |
| `PaymentProvider` | `ZALOPAY` |
| `PaymentOrderStatus` | `PENDING`, `PAID`, `FAILED`, `EXPIRED`, `CANCELLED` |
| `WithdrawalStatus` | `PENDING`, `APPROVED`, `REJECTED`, `PAID`, `CANCELLED` |
| `PrizeRecipientPolicy` | `OWNER`, `JOCKEY`, `OWNER_AND_JOCKEY` |
| `AdvancementRuleType` | `RANK`, `TIME` |

## Coverage Note

Tai lieu nay duoc lap tu tat ca controller mapping trong `src/main/java/com/minhthien/hoser_backend/controller` tai thoi diem tao file. Tong so endpoint method-level: 144, bao gom duplicate path khac `consumes` nhu JSON va multipart.
