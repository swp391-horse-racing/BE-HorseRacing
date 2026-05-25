# Full API Test Guide

Huong dan nay dung de test tu DB trang sau khi ban recreate database.

Base URL mac dinh:

```text
http://localhost:8080
```

Header chung cho API can login:

```http
Authorization: Bearer {{token}}
Content-Type: application/json
```

Voi API upload file, dung Postman `form-data`, khong set tay `Content-Type`; Postman se tu set multipart boundary.

Admin seed mac dinh khi app start:

```json
{
  "email": "admin@example.local",
  "password": "Admin12345!"
}
```

## 1. Cach chay va test nhanh

1. Start backend.
2. Login admin bang API `POST /api/v1/auth/login`, luu `accessToken` vao `{{adminToken}}`.
3. Register cac account test: owner, jockey, referee, spectator.
4. Dung admin doi role nhanh bang `PUT /api/v1/admin/users/{userId}/role`, hoac test dung flow application approve.
5. Owner tao horse, admin approve horse.
6. Jockey tao profile, admin approve profile neu can.
7. Owner moi jockey, jockey accept.
8. Admin tao tournament + race, open registration.
9. Owner dang ky race, admin approve tao participant.
10. Admin schedule tournament, doi gate/referee neu can.
11. Referee check-in, start race, finalize result.
12. Owner tao complaint trong 24 gio sau result, admin resolve.

Goi test unit/smoke:

```powershell
.\mvnw.cmd test
```

Swagger/OpenAPI neu app dang bat springdoc:

```text
http://localhost:8080/swagger-ui/index.html
http://localhost:8080/v3/api-docs
```

## 2. Bien Postman nen tao

```text
baseUrl = http://localhost:8080
adminToken =
ownerToken =
jockeyToken =
refereeToken =
spectatorToken =

ownerUserId =
jockeyUserId =
refereeUserId =
horseId =
jockeyId =
jockeyInvitationId =
tournamentId =
raceId =
raceRegistrationId =
participantId1 =
participantId2 =
complaintId =
paymentOrderId =
withdrawalId =
```

## 3. Auth APIs

### Register user

`POST /api/v1/auth/register`

```json
{
  "username": "owner01",
  "fullName": "Owner One",
  "email": "owner01@example.com",
  "phone": "0900000001",
  "password": "Password123!"
}
```

Tao them user mau:

```json
{
  "username": "jockey01",
  "fullName": "Jockey One",
  "email": "jockey01@example.com",
  "phone": "0900000002",
  "password": "Password123!"
}
```

```json
{
  "username": "referee01",
  "fullName": "Referee One",
  "email": "referee01@example.com",
  "phone": "0900000003",
  "password": "Password123!"
}
```

### Login

`POST /api/v1/auth/login`

```json
{
  "email": "owner01@example.com",
  "password": "Password123!"
}
```

Response tra token. Luu token theo role.

### Current user

`GET /api/v1/auth/me`

Body: none.

### Update password

`PUT /api/v1/auth/password`

```json
{
  "currentPassword": "Password123!",
  "newPassword": "NewPassword123!"
}
```

### Logout

`POST /api/v1/auth/logout`

Body: none.

### Forgot password

`POST /api/v1/auth/forgot-password`

```json
{
  "email": "owner01@example.com"
}
```

### Reset password

`POST /api/v1/auth/reset-password`

```json
{
  "email": "owner01@example.com",
  "otp": "123456",
  "newPassword": "Password123!"
}
```

### Google login

`POST /api/v1/auth/google`

```json
{
  "idToken": "GOOGLE_ID_TOKEN"
}
```

### Facebook login

`POST /api/v1/auth/facebook`

```json
{
  "accessToken": "FACEBOOK_ACCESS_TOKEN"
}
```

## 4. User/Admin APIs

### Get/update my profile

`GET /api/v1/users/me/profile`

Body: none.

`PUT /api/v1/users/me/profile` with JSON:

```json
{
  "fullName": "Owner One Updated",
  "phone": "0900000099",
  "location": "Ho Chi Minh City"
}
```

`PUT /api/v1/users/me/profile` with multipart form-data:

```text
fullName = Owner One Updated
phone = 0900000099
location = Ho Chi Minh City
avatar = file
```

### Admin user list/detail/status/role

`GET /api/v1/admin/users`

`GET /api/v1/admin/users/active`

`GET /api/v1/admin/users/deactivated`

`GET /api/v1/admin/users/{id}`

`PUT /api/v1/admin/users/{userId}/deactivate`

`PUT /api/v1/admin/users/{userId}/activate`

`PUT /api/v1/admin/users/{userId}/role`

```json
{
  "role": "OWNER"
}
```

Role hop le:

```text
USER, OWNER, ADMIN, JOCKEY, SPECTATOR, REFEREE
```

`GET /api/v1/admin/payout-debts`

## 5. Role Application APIs

Neu muon test day du flow approve role, dung cac API nay thay vi admin set role truc tiep.

### Owner application

`POST /api/v1/role-applications/owner` multipart form-data:

```text
stableName = Star Stable
experienceYears = 5
address = 123 Nguyen Trai, HCMC
bio = Owner bio
verificationDocument = file
```

### Jockey application

`POST /api/v1/role-applications/jockey` multipart form-data:

```text
licenseNumber = JOCKEY-LIC-001
experienceYears = 3
heightCm = 165
weightKg = 55
hirePrice = 500000
bio = Jockey bio
awards = Local cup 2025
specialties = Sprint
avatar = file
achievements = file
licenseDocument = file
```

### Spectator application

`POST /api/v1/role-applications/spectator`

```json
{
  "displayName": "Spectator One",
  "phone": "0900000004",
  "location": "HCMC",
  "favoriteHorseBreed": "Arabian",
  "bio": "Love horse racing"
}
```

### Referee application

`POST /api/v1/role-applications/referee` multipart form-data:

```text
licenseNumber = REF-LIC-001
experienceYears = 6
specialty = Race referee
bio = Certified referee
certificationDocument = file
```

### My role application

`GET /api/v1/role-applications/me`

### Admin role application review

`GET /api/v1/admin/role-applications`

Query optional:

```text
?role=OWNER&status=PENDING
```

`GET /api/v1/admin/role-applications/role/{role}`

`GET /api/v1/admin/role-applications/status/{status}`

Status hop le:

```text
NONE, PENDING, APPROVED, REJECTED
```

`PUT /api/v1/admin/role-applications/{profileId}/approve`

Body: none.

`PUT /api/v1/admin/role-applications/{profileId}/reject`

```json
{
  "reason": "Document is not clear"
}
```

## 6. Horse APIs

### Owner create horse

`POST /api/v1/owner/horses` multipart form-data:

```text
name = Thunder
breed = Arabian
age = 4
gender = Male
color = Black
heightCm = 160
weightKg = 450
image = file
document = file
```

### Owner get/update horse

`GET /api/v1/owner/horses`

`GET /api/v1/owner/horses/{id}`

`GET /api/v1/horses/{id}`

`GET /api/v1/horses/approved`

`PUT /api/v1/owner/horses/{id}` multipart form-data:

```text
name = Thunder Updated
breed = Arabian
age = 5
gender = Male
color = Black
heightCm = 162
weightKg = 455
image = file
document = file
```

### Admin horse review

`GET /api/v1/admin/horses?status=PENDING`

Horse status:

```text
PENDING, APPROVED, REJECTED, SUSPENDED
```

`PUT /api/v1/admin/horses/{id}/approve`

Body: none.

`PUT /api/v1/admin/horses/{id}/reject`

```json
{
  "reason": "Horse document is invalid"
}
```

`PUT /api/v1/admin/horses/{id}/suspend`

```json
{
  "reason": "Horse temporarily suspended"
}
```

## 7. Jockey Profile and Invitation APIs

### Jockey profile

`GET /api/v1/jockey/profile`

`POST /api/v1/jockey/profile` multipart form-data:

```text
licenseNumber = JOCKEY-LIC-001
experienceYears = 3
heightCm = 165
weightKg = 55
hirePrice = 500000
bio = Jockey bio
awards = Local cup 2025
specialties = Sprint
avatar = file
achievements = file
licenseDocument = file
```

`PUT /api/v1/jockey/profile` multipart form-data:

```text
licenseNumber = JOCKEY-LIC-001-UPD
experienceYears = 4
heightCm = 166
weightKg = 56
hirePrice = 600000
bio = Updated bio
awards = Updated awards
specialties = Sprint, endurance
avatar = file
achievements = file
licenseDocument = file
```

`GET /api/v1/jockeys/available`

`GET /api/v1/jockeys/{id}`

`GET /api/v1/admin/jockey-profiles?status=PENDING`

Jockey status:

```text
PENDING, APPROVED, REJECTED, SUSPENDED
```

### Owner invite jockey

`POST /api/v1/owner/jockey-invitations`

```json
{
  "horseId": 1,
  "jockeyId": 2,
  "message": "Please ride Thunder"
}
```

`GET /api/v1/owner/jockey-invitations`

`GET /api/v1/owner/jockey-invitations/{id}`

`GET /api/v1/owners/me/jockeys`

`PUT /api/v1/owner/jockey-invitations/{id}/cancel`

Body: none.

### Jockey accept/reject invitation

`GET /api/v1/jockey/invitations`

`GET /api/v1/jockey/invitations/{id}`

`PUT /api/v1/jockey/invitations/{id}/accept`

```json
{
  "note": "Accepted"
}
```

`PUT /api/v1/jockey/invitations/{id}/reject`

```json
{
  "note": "Not available"
}
```

### Eligible horse teams

`GET /api/v1/owner/horse-teams/eligible`

`GET /api/v1/admin/tournaments/{id}/eligible-horse-teams`

## 8. Finance, Wallet, Payment, Withdrawal APIs

### Admin finance settings

`GET /api/v1/admin/finance-settings`

`PUT /api/v1/admin/finance-settings`

```json
{
  "jockeyHireTaxPercent": 10.00
}
```

`GET /api/v1/admin/finance-settings/race-prize-shares`

`PUT /api/v1/admin/finance-settings/race-prize-shares`

```json
{
  "shares": [
    {
      "rank": 1,
      "jockeyPercent": 20.00
    },
    {
      "rank": 2,
      "jockeyPercent": 15.00
    },
    {
      "rank": 3,
      "jockeyPercent": 10.00
    }
  ]
}
```

### Wallet

`GET /api/v1/wallets/me`

`GET /api/v1/wallets/me/transactions`

`GET /api/v1/admin/wallet`

`GET /api/v1/admin/wallet/transactions`

### Deposit order

`POST /api/v1/wallets/me/deposit-orders`

```json
{
  "amount": 5000000,
  "currency": "VND",
  "provider": "PAYOS"
}
```

Provider hop le hien tai:

```text
PAYOS
```

`GET /api/v1/wallets/me/deposit-orders`

`GET /api/v1/wallets/me/deposit-orders/{id}`

### Deposit callback

`POST /api/v1/payment-callbacks/deposits`

Can dung `referenceCode` tra ve tu deposit order va callback token dung config local.

```json
{
  "referenceCode": "DEP-CHANGE-ME",
  "status": "PAID",
  "callbackToken": "CHANGE_ME",
  "providerTransactionId": "TXN-001",
  "metadata": "manual test"
}
```

Payment order status thuong dung:

```text
PENDING, PAID, FAILED, EXPIRED, CANCELLED
```

### payOS webhook

`POST /api/v1/wallets/top-up/payos/webhook`

`POST /api/payos/webhook`

Body mau toi thieu phu thuoc model SDK payOS. Dung webhook that tu payOS la chuan nhat. Mau tham khao:

```json
{
  "code": "00",
  "desc": "success",
  "success": true,
  "data": {
    "orderCode": 123456,
    "amount": 5000000,
    "description": "DEP-CHANGE-ME",
    "accountNumber": "12345678",
    "reference": "PAYOS-REF-001",
    "transactionDateTime": "2026-06-01 10:00:00",
    "currency": "VND",
    "paymentLinkId": "link-id",
    "code": "00",
    "desc": "success"
  },
  "signature": "PAYOS_SIGNATURE"
}
```

### Admin payment

`GET /api/v1/admin/payment-orders`

`GET /api/v1/admin/payment-orders/{id}`

`GET /api/v1/admin/payment-callback-logs`

### User withdrawal

`POST /api/v1/wallets/me/withdrawals`

```json
{
  "amount": 100000,
  "bankName": "VCB",
  "bankAccountNumber": "0123456789",
  "bankAccountName": "OWNER ONE",
  "reason": "Withdraw test"
}
```

`GET /api/v1/wallets/me/withdrawals`

`GET /api/v1/wallets/me/withdrawals/{id}`

### Admin withdrawal

`GET /api/v1/admin/withdrawals`

Optional:

```text
?status=PENDING
```

`GET /api/v1/admin/withdrawals/{id}`

`PUT /api/v1/admin/withdrawals/{id}/approve`

```json
{
  "note": "Approved"
}
```

`PUT /api/v1/admin/withdrawals/{id}/reject`

```json
{
  "note": "Rejected due to invalid bank account"
}
```

`PUT /api/v1/admin/withdrawals/{id}/mark-paid`

```json
{
  "note": "Transferred"
}
```

`POST /api/v1/admin/wallet/withdrawals`

```json
{
  "amount": 100000,
  "bankName": "VCB",
  "bankAccountNumber": "0123456789",
  "bankAccountName": "ADMIN",
  "reason": "Admin wallet settlement"
}
```

`GET /api/v1/admin/wallet/withdrawals`

## 9. Tournament APIs

### Create tournament

`POST /api/v1/admin/tournaments`

```json
{
  "name": "Saigon Race Day 2026",
  "description": "Test tournament from empty DB",
  "location": "District 7, HCMC",
  "registrationOpenAt": "2026-06-01T08:00:00",
  "registrationCloseAt": "2026-06-05T18:00:00",
  "startAt": "2026-06-10T08:00:00",
  "endAt": "2026-06-10T18:00:00",
  "checkInDeadlineAt": "2026-06-10T07:30:00",
  "minTeams": 1,
  "maxTeams": 10,
  "jockeyChallengeEnabled": true,
  "jockeyChallengeFirstPoints": 3,
  "jockeyChallengeSecondPoints": 2,
  "jockeyChallengeThirdPoints": 1,
  "jockeyChallengePrizes": [
    {
      "rank": 1,
      "amount": 1000000,
      "note": "Best jockey of the day"
    }
  ]
}
```

### Update tournament

`PUT /api/v1/admin/tournaments/{id}`

```json
{
  "name": "Saigon Race Day 2026 Updated",
  "description": "Updated description",
  "location": "District 7, HCMC",
  "registrationOpenAt": "2026-06-01T08:00:00",
  "registrationCloseAt": "2026-06-05T18:00:00",
  "startAt": "2026-06-10T08:00:00",
  "endAt": "2026-06-10T18:00:00",
  "checkInDeadlineAt": "2026-06-10T07:30:00",
  "minTeams": 1,
  "maxTeams": 10,
  "jockeyChallengeEnabled": true,
  "jockeyChallengeFirstPoints": 3,
  "jockeyChallengeSecondPoints": 2,
  "jockeyChallengeThirdPoints": 1,
  "jockeyChallengePrizes": [
    {
      "rank": 1,
      "amount": 1000000,
      "note": "Best jockey"
    }
  ]
}
```

### Add race

`POST /api/v1/admin/tournaments/{id}/races`

```json
{
  "name": "Race 1200m",
  "distance": "1200m",
  "scheduledStartAt": "2026-06-10T09:00:00",
  "scheduledEndAt": "2026-06-10T09:30:00",
  "minParticipants": 1,
  "maxParticipants": 8,
  "entryFee": 100000,
  "refereeId": 3,
  "note": "Morning race",
  "prizes": [
    {
      "rank": 1,
      "amount": 2000000,
      "itemName": "Gold cup",
      "note": "Champion"
    },
    {
      "rank": 2,
      "amount": 1000000,
      "itemName": "Silver medal",
      "note": "Runner up"
    }
  ]
}
```

### Replace all races

`PUT /api/v1/admin/tournaments/{id}/races`

```json
[
  {
    "name": "Race 1200m",
    "distance": "1200m",
    "scheduledStartAt": "2026-06-10T09:00:00",
    "scheduledEndAt": "2026-06-10T09:30:00",
    "minParticipants": 1,
    "maxParticipants": 8,
    "entryFee": 100000,
    "refereeId": 3,
    "note": "Morning race",
    "prizes": [
      {
        "rank": 1,
        "amount": 2000000,
        "itemName": "Gold cup",
        "note": "Champion"
      }
    ]
  }
]
```

### Tournament status and public read

`PUT /api/v1/admin/tournaments/{id}/status?status=PUBLISHED`

Body: none.

Tournament status:

```text
DRAFT, PUBLISHED, OPEN_REGISTRATION, REGISTRATION_CLOSED, SCHEDULED, ONGOING, COMPLETED, CANCELLED
```

`PUT /api/v1/admin/tournaments/{id}/open-registration`

`PUT /api/v1/admin/tournaments/{id}/close-registration`

`GET /api/v1/admin/tournaments`

Optional:

```text
?status=OPEN_REGISTRATION
```

`GET /api/v1/admin/tournaments/{id}`

`GET /api/v1/tournaments`

`GET /api/v1/tournaments/{id}`

`GET /api/v1/tournaments/{id}/races`

## 10. Race Registration, Scheduling, Operation APIs

### Owner register race

Dieu kien: tournament da `OPEN_REGISTRATION`, horse da `APPROVED`, jockey invitation da `ACCEPTED`, owner co du tien entry fee neu race co fee.

`POST /api/v1/races/{id}/registrations`

```json
{
  "horseId": 1,
  "jockeyInvitationId": 1,
  "note": "Register Thunder"
}
```

`GET /api/v1/owner/race-registrations`

`PUT /api/v1/owner/race-registrations/{id}/withdraw`

```json
{
  "note": "Owner withdraws registration"
}
```

### Admin review registration

`GET /api/v1/admin/tournaments/{id}/race-registrations`

`PUT /api/v1/admin/race-registrations/{id}/approve`

```json
{
  "note": "Approved",
  "gateNumber": 1
}
```

`PUT /api/v1/admin/race-registrations/{id}/reject`

```json
{
  "note": "Invalid team",
  "gateNumber": null
}
```

### Phase 8 schedule APIs

`PUT /api/v1/admin/tournaments/{id}/schedule`

Body: none.

Rule chinh:

```text
ADMIN only
Tournament status phai OPEN_REGISTRATION hoac REGISTRATION_CLOSED
Approved participant count >= minTeams va <= maxTeams
Race khong vuot maxParticipants
Gate > 0 va unique trong race
Referee role REFEREE va khong overlap lich
Sau schedule tournament thanh SCHEDULED
```

`GET /api/v1/admin/races/{id}/participants`

`PUT /api/v1/admin/races/{raceId}/participants/{participantId}/gate`

```json
{
  "gateNumber": 2
}
```

`PUT /api/v1/admin/races/{id}/referee`

```json
{
  "refereeId": 3
}
```

### Referee race operation APIs

`GET /api/v1/referee/races`

`PUT /api/v1/referee/races/{id}/participants/{participantId}/check-in`

```json
{
  "status": "CHECKED_IN",
  "note": "Ready"
}
```

Check-in status hop le:

```text
CHECKED_IN, ABSENT, DISQUALIFIED
```

`PUT /api/v1/referee/races/{id}/start`

Body: none.

Rule: race dang `SCHEDULED`, referee phai la assigned referee, so participant `CHECKED_IN` >= `minParticipants`.

`POST /api/v1/referee/races/{id}/results/finalize`

```json
{
  "results": [
    {
      "participantId": 1,
      "rank": 1,
      "finishTimeMillis": 72000,
      "status": "FINISHED",
      "note": "Winner"
    },
    {
      "participantId": 2,
      "rank": 2,
      "finishTimeMillis": 76000,
      "status": "FINISHED",
      "note": "Second"
    }
  ]
}
```

Result participant status hop le:

```text
FINISHED, DNF, DISQUALIFIED, ABSENT
```

Rule: race phai `ONGOING`; finalize xong race thanh `RESULT_CONFIRMED` va payout ngay.

`GET /api/v1/races/{id}/results`

### Jockey challenge APIs

`PUT /api/v1/admin/tournaments/{id}/jockey-challenge/finalize`

Body: none.

`GET /api/v1/tournaments/{id}/jockey-challenge`

## 11. Race Complaint APIs

### Owner create complaint

Dieu kien: owner cung race, race da co result, trong 24 gio sau result.

`POST /api/v1/races/{id}/complaints`

```json
{
  "accusedParticipantId": 2,
  "reason": "Horse/jockey violated race rule",
  "evidenceUrl": "https://example.com/evidence/video-001"
}
```

### Owner view complaints

`GET /api/v1/owner/race-complaints`

Tra ve complaint minh tao va complaint minh bi khieu nai. Thong tin complainant duoc an danh voi owner bi khieu nai.

### Admin view/filter complaints

`GET /api/v1/admin/race-complaints`

Optional:

```text
?status=PENDING
```

Complaint status:

```text
PENDING, APPROVED, REJECTED
```

### Admin resolve complaint

Reject:

`PUT /api/v1/admin/race-complaints/{id}/resolve`

```json
{
  "status": "REJECTED",
  "banUntil": null,
  "fineAmount": 0,
  "adminNote": "Not enough evidence"
}
```

Approve:

```json
{
  "status": "APPROVED",
  "banUntil": "2026-07-10T00:00:00",
  "fineAmount": 500000,
  "adminNote": "Complaint approved after review"
}
```

Rule: approve se ban owner bi khieu nai den `banUntil`, tru lai owner prize da nhan va tru them `fineAmount`. Jockey prize khong bi anh huong.

## 12. Admin audit APIs

`GET /api/v1/admin/audit-logs`

Optional:

```text
?referenceType=RACE_COMPLAINT&referenceId=1
```

## 13. Thu tu test mau tu DB trang

### Step A - Login admin

`POST /api/v1/auth/login`

```json
{
  "email": "admin@example.local",
  "password": "Admin12345!"
}
```

### Step B - Register 3 users

Owner:

```json
{
  "username": "owner01",
  "fullName": "Owner One",
  "email": "owner01@example.com",
  "phone": "0900000001",
  "password": "Password123!"
}
```

Jockey:

```json
{
  "username": "jockey01",
  "fullName": "Jockey One",
  "email": "jockey01@example.com",
  "phone": "0900000002",
  "password": "Password123!"
}
```

Referee:

```json
{
  "username": "referee01",
  "fullName": "Referee One",
  "email": "referee01@example.com",
  "phone": "0900000003",
  "password": "Password123!"
}
```

### Step C - Gan role nhanh bang admin

`PUT /api/v1/admin/users/{{ownerUserId}}/role`

```json
{
  "role": "OWNER"
}
```

`PUT /api/v1/admin/users/{{jockeyUserId}}/role`

```json
{
  "role": "JOCKEY"
}
```

`PUT /api/v1/admin/users/{{refereeUserId}}/role`

```json
{
  "role": "REFEREE"
}
```

### Step D - Tao horse, tao/approve jockey profile

Owner token: `POST /api/v1/owner/horses` form-data nhu muc Horse.

Admin token: `PUT /api/v1/admin/horses/{{horseId}}/approve`.

Jockey token: `POST /api/v1/jockey/profile` form-data nhu muc Jockey Profile.

Neu profile status con pending, admin check:

```text
GET /api/v1/admin/jockey-profiles?status=PENDING
```

### Step E - Owner invite jockey, jockey accept

Owner:

```json
{
  "horseId": 1,
  "jockeyId": 2,
  "message": "Please ride Thunder"
}
```

Jockey accept:

```json
{
  "note": "Accepted"
}
```

### Step F - Nap tien owner neu race co entry fee

Tao deposit order:

```json
{
  "amount": 5000000,
  "currency": "VND",
  "provider": "PAYOS"
}
```

Neu local callback token dung config, callback paid:

```json
{
  "referenceCode": "DEP-CHANGE-ME",
  "status": "PAID",
  "callbackToken": "CHANGE_ME",
  "providerTransactionId": "TXN-001",
  "metadata": "manual test"
}
```

### Step G - Tao tournament va race

Admin tao tournament nhu muc Tournament. Sau do tao race nhu muc Add race.

Open registration:

```text
PUT /api/v1/admin/tournaments/{{tournamentId}}/open-registration
```

### Step H - Dang ky race va approve

Owner:

```json
{
  "horseId": 1,
  "jockeyInvitationId": 1,
  "note": "Register Thunder"
}
```

Admin approve:

```json
{
  "note": "Approved",
  "gateNumber": 1
}
```

### Step I - Schedule tournament

Neu registration van open, co the close truoc:

```text
PUT /api/v1/admin/tournaments/{{tournamentId}}/close-registration
```

Schedule:

```text
PUT /api/v1/admin/tournaments/{{tournamentId}}/schedule
```

Lay participant:

```text
GET /api/v1/admin/races/{{raceId}}/participants
```

### Step J - Referee check-in, start, finalize

Referee check-in:

```json
{
  "status": "CHECKED_IN",
  "note": "Ready"
}
```

Start:

```text
PUT /api/v1/referee/races/{{raceId}}/start
```

Finalize:

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

### Step K - Complaint

Can co it nhat 2 participants trong cung race neu muon owner A khieu nai owner B.

Owner tao complaint:

```json
{
  "accusedParticipantId": 2,
  "reason": "Violation evidence",
  "evidenceUrl": "https://example.com/evidence"
}
```

Admin approve complaint:

```json
{
  "status": "APPROVED",
  "banUntil": "2026-07-10T00:00:00",
  "fineAmount": 500000,
  "adminNote": "Approved after review"
}
```

## 14. Luu y khi test tu DB trang

- Nhieu API GET se tra list rong luc ban moi tao DB; do la dung.
- Cac API `OWNER`, `JOCKEY`, `REFEREE`, `ADMIN` se tra 403 neu token sai role.
- API schedule can participant duoc tao tu registration approved; chi tao registration chua du.
- API start race can participant `CHECKED_IN` dat `minParticipants`.
- API finalize result can race dang `ONGOING`.
- Complaint chi tao duoc trong 24 gio sau khi race co official result.
- Mot so webhook payment can secret/signature dung config nen test local co the fail business validation neu token/signature sai; dung deposit callback manual de nap tien nhanh hon.
