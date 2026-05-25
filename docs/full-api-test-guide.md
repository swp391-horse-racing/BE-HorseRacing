# Full API Test Guide

Base URL:

```text
http://localhost:8080
```

Common headers for JSON APIs:

```http
Content-Type: application/json
Authorization: Bearer {{token}}
```

Suggested Postman variables:

```text
baseUrl=http://localhost:8080
userToken=
ownerToken=
jockeyToken=
refereeToken=
adminToken=
userId=1
ownerId=2
jockeyId=3
refereeId=4
horseId=1
jockeyInvitationId=1
tournamentId=1
raceId=1
registrationId=1
participantId=1
complaintId=1
paymentOrderId=1
withdrawalId=1
roleApplicationId=1
```

All response bodies normally follow:

```json
{
  "success": true,
  "message": "OK",
  "data": {}
}
```

The only exception is the ZaloPay callback endpoint, which returns:

```json
{
  "return_code": 1,
  "return_message": "success"
}
```

## Auth

### Register

```http
POST {{baseUrl}}/api/v1/auth/register
```

```json
{
  "username": "testuser",
  "fullName": "Test User",
  "email": "testuser@example.com",
  "phone": "0900000000",
  "password": "Password123!"
}
```

Save `data.token` as `userToken`.

### Login

```http
POST {{baseUrl}}/api/v1/auth/login
```

```json
{
  "email": "testuser@example.com",
  "password": "Password123!"
}
```

### Current User

```http
GET {{baseUrl}}/api/v1/auth/me
Authorization: Bearer {{userToken}}
```

No body.

### Update Password

```http
PUT {{baseUrl}}/api/v1/auth/password
Authorization: Bearer {{userToken}}
```

```json
{
  "currentPassword": "Password123!",
  "newPassword": "Password456!"
}
```

### Logout

```http
POST {{baseUrl}}/api/v1/auth/logout
Authorization: Bearer {{userToken}}
```

No body.

### Forgot Password

```http
POST {{baseUrl}}/api/v1/auth/forgot-password
```

```json
{
  "email": "testuser@example.com"
}
```

### Reset Password

```http
POST {{baseUrl}}/api/v1/auth/reset-password
```

```json
{
  "email": "testuser@example.com",
  "otp": "123456",
  "newPassword": "Password789!"
}
```

### Google Login

```http
POST {{baseUrl}}/api/v1/auth/google
```

```json
{
  "idToken": "GOOGLE_ID_TOKEN"
}
```

### Facebook Login

```http
POST {{baseUrl}}/api/v1/auth/facebook
```

```json
{
  "accessToken": "FACEBOOK_ACCESS_TOKEN"
}
```

## User Profile

### Get My Profile

```http
GET {{baseUrl}}/api/v1/users/me/profile
Authorization: Bearer {{userToken}}
```

No body.

### Update My Profile JSON

```http
PUT {{baseUrl}}/api/v1/users/me/profile
Authorization: Bearer {{userToken}}
Content-Type: application/json
```

```json
{
  "fullName": "Test User Updated",
  "phone": "0900000001",
  "location": "Ho Chi Minh City"
}
```

### Update My Profile With Avatar

```http
PUT {{baseUrl}}/api/v1/users/me/profile
Authorization: Bearer {{userToken}}
Content-Type: multipart/form-data
```

Form-data:

```text
fullName=Test User Updated
phone=0900000001
location=Ho Chi Minh City
avatar=@avatar.png
```

## Role Applications

### Submit Owner Application

```http
POST {{baseUrl}}/api/v1/role-applications/owner
Authorization: Bearer {{userToken}}
Content-Type: multipart/form-data
```

Form-data:

```text
stableName=Victory Stable
experienceYears=5
address=District 1, Ho Chi Minh City
bio=Owner bio
verificationDocument=@owner-document.pdf
```

### Submit Jockey Application

```http
POST {{baseUrl}}/api/v1/role-applications/jockey
Authorization: Bearer {{userToken}}
Content-Type: multipart/form-data
```

Form-data:

```text
licenseNumber=JOCKEY-001
experienceYears=3
heightCm=170
weightKg=58
hirePrice=50000
bio=Jockey bio
awards=Local champion
specialties=Sprint
avatar=@avatar.png
achievements=@achievement.png
licenseDocument=@license.pdf
```

### Submit Spectator Application

```http
POST {{baseUrl}}/api/v1/role-applications/spectator
Authorization: Bearer {{userToken}}
```

```json
{
  "displayName": "Horse Fan",
  "phone": "0900000002",
  "location": "Ho Chi Minh City",
  "favoriteHorseBreed": "Thoroughbred",
  "bio": "I love horse racing"
}
```

### Submit Referee Application

```http
POST {{baseUrl}}/api/v1/role-applications/referee
Authorization: Bearer {{userToken}}
Content-Type: multipart/form-data
```

Form-data:

```text
licenseNumber=REF-001
experienceYears=4
specialty=Race referee
bio=Referee bio
certificationDocument=@certificate.pdf
```

### Get My Role Application

```http
GET {{baseUrl}}/api/v1/role-applications/me
Authorization: Bearer {{userToken}}
```

No body.

## Admin Role Applications

### List Applications

```http
GET {{baseUrl}}/api/v1/admin/role-applications?role=OWNER&status=PENDING
Authorization: Bearer {{adminToken}}
```

No body.

### List By Role

```http
GET {{baseUrl}}/api/v1/admin/role-applications/role/OWNER
Authorization: Bearer {{adminToken}}
```

No body.

### List By Status

```http
GET {{baseUrl}}/api/v1/admin/role-applications/status/PENDING
Authorization: Bearer {{adminToken}}
```

No body.

### Approve Application

```http
PUT {{baseUrl}}/api/v1/admin/role-applications/{{roleApplicationId}}/approve?role=OWNER
Authorization: Bearer {{adminToken}}
```

No body.

`role` is recommended because different profile tables can all have the same `profileId` such as `1`.
Allowed values: `OWNER`, `JOCKEY`, `SPECTATOR`, `REFEREE`.

### Reject Application

```http
PUT {{baseUrl}}/api/v1/admin/role-applications/{{roleApplicationId}}/reject?role=OWNER
Authorization: Bearer {{adminToken}}
```

```json
{
  "reason": "Documents are not valid"
}
```

`role` is recommended here too. Allowed values: `OWNER`, `JOCKEY`, `SPECTATOR`, `REFEREE`.

## Horses

### Public Approved Horses

```http
GET {{baseUrl}}/api/v1/horses/approved
```

No body.

### Owner Create Horse

```http
POST {{baseUrl}}/api/v1/owner/horses
Authorization: Bearer {{ownerToken}}
Content-Type: multipart/form-data
```

Form-data:

```text
name=Thunder
breed=Thoroughbred
age=4
gender=MALE
color=Bay
heightCm=160
weightKg=480
image=@horse.png
document=@horse-document.pdf
```

### Owner List Horses

```http
GET {{baseUrl}}/api/v1/owner/horses
Authorization: Bearer {{ownerToken}}
```

No body.

### Owner Get Horse

```http
GET {{baseUrl}}/api/v1/owner/horses/{{horseId}}
Authorization: Bearer {{ownerToken}}
```

No body.

### Public Get Horse

```http
GET {{baseUrl}}/api/v1/horses/{{horseId}}
```

No body.

### Owner Update Horse

```http
PUT {{baseUrl}}/api/v1/owner/horses/{{horseId}}
Authorization: Bearer {{ownerToken}}
Content-Type: multipart/form-data
```

Form-data:

```text
name=Thunder Updated
breed=Thoroughbred
age=5
gender=MALE
color=Bay
heightCm=162
weightKg=485
image=@horse-new.png
document=@horse-document-new.pdf
```

### Admin List Horses

```http
GET {{baseUrl}}/api/v1/admin/horses?status=PENDING
Authorization: Bearer {{adminToken}}
```

No body. `status`: `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`.

### Admin Approve Horse

```http
PUT {{baseUrl}}/api/v1/admin/horses/{{horseId}}/approve
Authorization: Bearer {{adminToken}}
```

No body.

### Admin Reject Horse

```http
PUT {{baseUrl}}/api/v1/admin/horses/{{horseId}}/reject
Authorization: Bearer {{adminToken}}
```

```json
{
  "reason": "Horse document is not valid"
}
```

### Admin Suspend Horse

```http
PUT {{baseUrl}}/api/v1/admin/horses/{{horseId}}/suspend
Authorization: Bearer {{adminToken}}
```

```json
{
  "reason": "Health issue"
}
```

## Jockey Profiles

### Jockey Get My Profile

```http
GET {{baseUrl}}/api/v1/jockey/profile
Authorization: Bearer {{jockeyToken}}
```

No body.

### Jockey Update Profile

```http
PUT {{baseUrl}}/api/v1/jockey/profile
Authorization: Bearer {{jockeyToken}}
Content-Type: multipart/form-data
```

Form-data:

```text
licenseNumber=JOCKEY-001
experienceYears=4
heightCm=171
weightKg=59
hirePrice=60000
bio=Updated jockey bio
awards=Updated awards
specialties=Endurance
avatar=@avatar-new.png
achievements=@achievement-new.png
licenseDocument=@license-new.pdf
```

### Public Available Jockeys

```http
GET {{baseUrl}}/api/v1/jockeys/available
```

No body.

### Public Get Jockey

```http
GET {{baseUrl}}/api/v1/jockeys/{{jockeyId}}
```

No body.

### Admin List Jockey Profiles

```http
GET {{baseUrl}}/api/v1/admin/jockey-profiles?status=PENDING
Authorization: Bearer {{adminToken}}
```

No body. `status`: `PENDING`, `APPROVED`, `REJECTED`, `SUSPENDED`.

## Jockey Invitations

### Owner Invite Jockey

```http
POST {{baseUrl}}/api/v1/owner/jockey-invitations
Authorization: Bearer {{ownerToken}}
```

```json
{
  "horseId": 1,
  "jockeyId": 3,
  "message": "Please join my horse team"
}
```

### Owner List Invitations

```http
GET {{baseUrl}}/api/v1/owner/jockey-invitations
Authorization: Bearer {{ownerToken}}
```

No body.

### Owner Get Invitation

```http
GET {{baseUrl}}/api/v1/owner/jockey-invitations/{{jockeyInvitationId}}
Authorization: Bearer {{ownerToken}}
```

No body.

### Owner Accepted Jockeys

```http
GET {{baseUrl}}/api/v1/owners/me/jockeys
Authorization: Bearer {{ownerToken}}
```

No body.

### Owner Cancel Invitation

```http
PUT {{baseUrl}}/api/v1/owner/jockey-invitations/{{jockeyInvitationId}}/cancel
Authorization: Bearer {{ownerToken}}
```

No body.

### Jockey List Invitations

```http
GET {{baseUrl}}/api/v1/jockey/invitations
Authorization: Bearer {{jockeyToken}}
```

No body.

### Jockey Get Invitation

```http
GET {{baseUrl}}/api/v1/jockey/invitations/{{jockeyInvitationId}}
Authorization: Bearer {{jockeyToken}}
```

No body.

### Jockey Accept Invitation

```http
PUT {{baseUrl}}/api/v1/jockey/invitations/{{jockeyInvitationId}}/accept
Authorization: Bearer {{jockeyToken}}
```

```json
{
  "note": "Accepted"
}
```

### Jockey Reject Invitation

```http
PUT {{baseUrl}}/api/v1/jockey/invitations/{{jockeyInvitationId}}/reject
Authorization: Bearer {{jockeyToken}}
```

```json
{
  "note": "Not available"
}
```

## Wallets, ZaloPay Deposits, Withdrawals

### My Wallet

```http
GET {{baseUrl}}/api/v1/wallets/me
Authorization: Bearer {{userToken}}
```

No body.

### My Wallet Transactions

```http
GET {{baseUrl}}/api/v1/wallets/me/transactions
Authorization: Bearer {{userToken}}
```

No body.

### Create ZaloPay Deposit Order

```http
POST {{baseUrl}}/api/v1/wallets/me/deposit-orders
Authorization: Bearer {{userToken}}
```

```json
{
  "amount": 10000,
  "currency": "VND",
  "provider": "ZALOPAY"
}
```

Expected important fields:

```json
{
  "data": {
    "provider": "ZALOPAY",
    "status": "PENDING",
    "checkoutUrl": "https://qcgateway.zalopay.vn/openinapp?...",
    "paymentLinkId": "260525_1",
    "qrCode": null
  }
}
```

Open `data.checkoutUrl` in browser to test ZaloPay sandbox. When using ngrok, ZaloPay redirects and calls back through the public ngrok URL, and the backend updates the order status automatically.

### List My Deposit Orders

```http
GET {{baseUrl}}/api/v1/wallets/me/deposit-orders
Authorization: Bearer {{userToken}}
```

No body.

### Get My Deposit Order

```http
GET {{baseUrl}}/api/v1/wallets/me/deposit-orders/{{paymentOrderId}}
Authorization: Bearer {{userToken}}
```

No body.

### ZaloPay Return

```http
GET {{baseUrl}}/api/zalopay/return?apptransid=260525_1&status=1&checksum=VALID_CHECKSUM
```

No body. ZaloPay redirects the browser here after sandbox payment. The backend verifies the redirect checksum, calls ZaloPay query API, then credits the wallet if the query says paid.

### ZaloPay Callback

```http
POST {{baseUrl}}/api/zalopay/callback
```

```json
{
  "data": "{\"app_id\":2554,\"app_trans_id\":\"260525_1\",\"amount\":10000,\"zp_trans_id\":100000001}",
  "mac": "VALID_MAC",
  "type": 1
}
```

`mac` must be `HMAC_SHA256(zalopay.key2, data)`. Localhost callback is mostly for manual testing because ZaloPay servers cannot call your local machine without a public tunnel.

Expected success:

```json
{
  "return_code": 1,
  "return_message": "success"
}
```

### Manual Deposit Callback

This endpoint still exists for internal/dev callback testing.

```http
POST {{baseUrl}}/api/v1/payment-callbacks/deposits
```

```json
{
  "referenceCode": "DEP-EXAMPLE",
  "status": "PAID",
  "callbackToken": "dev-callback-token",
  "providerTransactionId": "DEV-CALLBACK-001",
  "metadata": "{\"source\":\"manual-test\"}"
}
```

### Admin List Payment Orders

```http
GET {{baseUrl}}/api/v1/admin/payment-orders
Authorization: Bearer {{adminToken}}
```

No body.

### Admin Get Payment Order

```http
GET {{baseUrl}}/api/v1/admin/payment-orders/{{paymentOrderId}}
Authorization: Bearer {{adminToken}}
```

No body.

### Admin Payment Callback Logs

```http
GET {{baseUrl}}/api/v1/admin/payment-callback-logs
Authorization: Bearer {{adminToken}}
```

No body.

### User Request Withdrawal

```http
POST {{baseUrl}}/api/v1/wallets/me/withdrawals
Authorization: Bearer {{userToken}}
```

```json
{
  "amount": 1000,
  "bankName": "VCB",
  "bankAccountNumber": "123456789",
  "bankAccountName": "TEST USER",
  "reason": "Withdraw test"
}
```

### User List Withdrawals

```http
GET {{baseUrl}}/api/v1/wallets/me/withdrawals
Authorization: Bearer {{userToken}}
```

No body.

### User Get Withdrawal

```http
GET {{baseUrl}}/api/v1/wallets/me/withdrawals/{{withdrawalId}}
Authorization: Bearer {{userToken}}
```

No body.

### Admin List Withdrawals

```http
GET {{baseUrl}}/api/v1/admin/withdrawals?status=PENDING
Authorization: Bearer {{adminToken}}
```

No body. `status`: `PENDING`, `APPROVED`, `REJECTED`, `PAID`, `CANCELLED`.

### Admin Get Withdrawal

```http
GET {{baseUrl}}/api/v1/admin/withdrawals/{{withdrawalId}}
Authorization: Bearer {{adminToken}}
```

No body.

### Admin Approve Withdrawal

```http
PUT {{baseUrl}}/api/v1/admin/withdrawals/{{withdrawalId}}/approve
Authorization: Bearer {{adminToken}}
```

```json
{
  "note": "Approved"
}
```

### Admin Reject Withdrawal

```http
PUT {{baseUrl}}/api/v1/admin/withdrawals/{{withdrawalId}}/reject
Authorization: Bearer {{adminToken}}
```

```json
{
  "note": "Rejected"
}
```

### Admin Mark Withdrawal Paid

```http
PUT {{baseUrl}}/api/v1/admin/withdrawals/{{withdrawalId}}/mark-paid
Authorization: Bearer {{adminToken}}
```

```json
{
  "note": "Bank transfer completed"
}
```

### Admin Wallet

```http
GET {{baseUrl}}/api/v1/admin/wallet
Authorization: Bearer {{adminToken}}
```

No body.

### Admin Wallet Transactions

```http
GET {{baseUrl}}/api/v1/admin/wallet/transactions
Authorization: Bearer {{adminToken}}
```

No body.

### Admin Wallet Withdrawal

```http
POST {{baseUrl}}/api/v1/admin/wallet/withdrawals
Authorization: Bearer {{adminToken}}
```

```json
{
  "amount": 1000,
  "bankName": "VCB",
  "bankAccountNumber": "987654321",
  "bankAccountName": "ADMIN WALLET",
  "reason": "Admin wallet withdrawal test"
}
```

### Admin Wallet Withdrawal List

```http
GET {{baseUrl}}/api/v1/admin/wallet/withdrawals
Authorization: Bearer {{adminToken}}
```

No body.

## Admin Users And Audit

### Users

```http
GET {{baseUrl}}/api/v1/admin/users
GET {{baseUrl}}/api/v1/admin/users/active
GET {{baseUrl}}/api/v1/admin/users/deactivated
GET {{baseUrl}}/api/v1/admin/users/{{userId}}
```

Header:

```http
Authorization: Bearer {{adminToken}}
```

No body.

### Deactivate User

```http
PUT {{baseUrl}}/api/v1/admin/users/{{userId}}/deactivate
Authorization: Bearer {{adminToken}}
```

No body.

### Activate User

```http
PUT {{baseUrl}}/api/v1/admin/users/{{userId}}/activate
Authorization: Bearer {{adminToken}}
```

No body.

### Update User Role

```http
PUT {{baseUrl}}/api/v1/admin/users/{{userId}}/role
Authorization: Bearer {{adminToken}}
```

```json
{
  "role": "OWNER"
}
```

`role`: `USER`, `OWNER`, `ADMIN`, `JOCKEY`, `SPECTATOR`, `REFEREE`.

### Payout Debts

```http
GET {{baseUrl}}/api/v1/admin/payout-debts
Authorization: Bearer {{adminToken}}
```

No body.

### Audit Logs

```http
GET {{baseUrl}}/api/v1/admin/audit-logs?referenceType=ADMIN_WALLET_WITHDRAWAL&referenceId=1
Authorization: Bearer {{adminToken}}
```

No body.

## Finance Settings

### Get Finance Settings

```http
GET {{baseUrl}}/api/v1/admin/finance-settings
Authorization: Bearer {{adminToken}}
```

No body.

### Update Finance Settings

```http
PUT {{baseUrl}}/api/v1/admin/finance-settings
Authorization: Bearer {{adminToken}}
```

```json
{
  "jockeyHireTaxPercent": 10.0
}
```

### Get Race Prize Shares

```http
GET {{baseUrl}}/api/v1/admin/finance-settings/race-prize-shares
Authorization: Bearer {{adminToken}}
```

No body.

### Update Race Prize Shares

```http
PUT {{baseUrl}}/api/v1/admin/finance-settings/race-prize-shares
Authorization: Bearer {{adminToken}}
```

```json
{
  "shares": [
    {
      "rank": 1,
      "jockeyPercent": 20.0
    },
    {
      "rank": 2,
      "jockeyPercent": 15.0
    },
    {
      "rank": 3,
      "jockeyPercent": 10.0
    }
  ]
}
```

## Tournaments And Races

### Admin Create Tournament

```http
POST {{baseUrl}}/api/v1/admin/tournaments
Authorization: Bearer {{adminToken}}
```

```json
{
  "name": "Spring Cup",
  "description": "Spring racing tournament",
  "location": "Ho Chi Minh City",
  "registrationOpenAt": "2026-06-01T08:00:00",
  "registrationCloseAt": "2026-06-05T18:00:00",
  "startAt": "2026-06-10T08:00:00",
  "endAt": "2026-06-10T18:00:00",
  "checkInDeadlineAt": "2026-06-10T07:30:00",
  "minTeams": 2,
  "maxTeams": 8,
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

### Admin Update Tournament

```http
PUT {{baseUrl}}/api/v1/admin/tournaments/{{tournamentId}}
Authorization: Bearer {{adminToken}}
```

```json
{
  "name": "Spring Cup Updated",
  "description": "Updated description",
  "location": "Ho Chi Minh City",
  "registrationOpenAt": "2026-06-01T08:00:00",
  "registrationCloseAt": "2026-06-05T18:00:00",
  "startAt": "2026-06-10T08:00:00",
  "endAt": "2026-06-10T18:00:00",
  "checkInDeadlineAt": "2026-06-10T07:30:00",
  "minTeams": 2,
  "maxTeams": 10,
  "jockeyChallengeEnabled": true,
  "jockeyChallengeFirstPoints": 3,
  "jockeyChallengeSecondPoints": 2,
  "jockeyChallengeThirdPoints": 1,
  "jockeyChallengePrizes": [
    {
      "rank": 1,
      "amount": 1200000,
      "note": "Best jockey updated"
    }
  ]
}
```

### Admin Add Tournament Race

```http
POST {{baseUrl}}/api/v1/admin/tournaments/{{tournamentId}}/races
Authorization: Bearer {{adminToken}}
```

```json
{
  "name": "Race 1",
  "distance": "1200m",
  "scheduledStartAt": "2026-06-10T09:00:00",
  "scheduledEndAt": "2026-06-10T10:00:00",
  "minParticipants": 2,
  "maxParticipants": 8,
  "entryFee": 10000,
  "refereeId": 4,
  "note": "Opening race",
  "prizes": [
    {
      "rank": 1,
      "amount": 1000000,
      "itemName": "Gold medal",
      "note": "Winner"
    },
    {
      "rank": 2,
      "amount": 500000,
      "itemName": "Silver medal",
      "note": "Runner up"
    }
  ]
}
```

### Admin Replace Tournament Races

```http
PUT {{baseUrl}}/api/v1/admin/tournaments/{{tournamentId}}/races
Authorization: Bearer {{adminToken}}
```

```json
[
  {
    "name": "Race 1",
    "distance": "1200m",
    "scheduledStartAt": "2026-06-10T09:00:00",
    "scheduledEndAt": "2026-06-10T10:00:00",
    "minParticipants": 2,
    "maxParticipants": 8,
    "entryFee": 10000,
    "refereeId": 4,
    "note": "Race 1",
    "prizes": [
      {
        "rank": 1,
        "amount": 1000000,
        "itemName": "Gold medal",
        "note": "Winner"
      }
    ]
  }
]
```

### Admin Update Tournament Status

```http
PUT {{baseUrl}}/api/v1/admin/tournaments/{{tournamentId}}/status?status=PUBLISHED
Authorization: Bearer {{adminToken}}
```

No body. `status`: `DRAFT`, `PUBLISHED`, `OPEN_REGISTRATION`, `REGISTRATION_CLOSED`, `SCHEDULED`, `ONGOING`, `COMPLETED`, `CANCELLED`.

### Admin Open Registration

```http
PUT {{baseUrl}}/api/v1/admin/tournaments/{{tournamentId}}/open-registration
Authorization: Bearer {{adminToken}}
```

No body.

### Admin Close Registration

```http
PUT {{baseUrl}}/api/v1/admin/tournaments/{{tournamentId}}/close-registration
Authorization: Bearer {{adminToken}}
```

No body.

### Admin List Tournaments

```http
GET {{baseUrl}}/api/v1/admin/tournaments?status=PUBLISHED
Authorization: Bearer {{adminToken}}
```

No body.

### Admin Get Tournament

```http
GET {{baseUrl}}/api/v1/admin/tournaments/{{tournamentId}}
Authorization: Bearer {{adminToken}}
```

No body.

### Public Tournaments

```http
GET {{baseUrl}}/api/v1/tournaments
GET {{baseUrl}}/api/v1/tournaments/{{tournamentId}}
GET {{baseUrl}}/api/v1/tournaments/{{tournamentId}}/races
```

No body.

### Register For Race

```http
POST {{baseUrl}}/api/v1/races/{{raceId}}/registrations
Authorization: Bearer {{ownerToken}}
```

```json
{
  "horseId": 1,
  "jockeyInvitationId": 1,
  "note": "Register horse team"
}
```

### Owner Race Registrations

```http
GET {{baseUrl}}/api/v1/owner/race-registrations
Authorization: Bearer {{ownerToken}}
```

No body.

### Owner Withdraw Race Registration

```http
PUT {{baseUrl}}/api/v1/owner/race-registrations/{{registrationId}}/withdraw
Authorization: Bearer {{ownerToken}}
```

```json
{
  "note": "Cannot join"
}
```

### Admin Race Registrations

```http
GET {{baseUrl}}/api/v1/admin/tournaments/{{tournamentId}}/race-registrations
Authorization: Bearer {{adminToken}}
```

No body.

### Admin Approve Race Registration

```http
PUT {{baseUrl}}/api/v1/admin/race-registrations/{{registrationId}}/approve
Authorization: Bearer {{adminToken}}
```

```json
{
  "note": "Approved",
  "gateNumber": 1
}
```

### Admin Reject Race Registration

```http
PUT {{baseUrl}}/api/v1/admin/race-registrations/{{registrationId}}/reject
Authorization: Bearer {{adminToken}}
```

```json
{
  "note": "Rejected",
  "gateNumber": null
}
```

### Admin Schedule Tournament

```http
PUT {{baseUrl}}/api/v1/admin/tournaments/{{tournamentId}}/schedule
Authorization: Bearer {{adminToken}}
```

No body.

### Admin Race Participants

```http
GET {{baseUrl}}/api/v1/admin/races/{{raceId}}/participants
Authorization: Bearer {{adminToken}}
```

No body.

### Admin Update Participant Gate

```http
PUT {{baseUrl}}/api/v1/admin/races/{{raceId}}/participants/{{participantId}}/gate
Authorization: Bearer {{adminToken}}
```

```json
{
  "gateNumber": 2
}
```

### Admin Assign Race Referee

```http
PUT {{baseUrl}}/api/v1/admin/races/{{raceId}}/referee
Authorization: Bearer {{adminToken}}
```

```json
{
  "refereeId": 4
}
```

### Referee Races

```http
GET {{baseUrl}}/api/v1/referee/races
Authorization: Bearer {{refereeToken}}
```

No body.

### Referee Check In Participant

```http
PUT {{baseUrl}}/api/v1/referee/races/{{raceId}}/participants/{{participantId}}/check-in
Authorization: Bearer {{refereeToken}}
```

```json
{
  "status": "CHECKED_IN",
  "note": "Checked in at gate"
}
```

### Referee Start Race

```http
PUT {{baseUrl}}/api/v1/referee/races/{{raceId}}/start
Authorization: Bearer {{refereeToken}}
```

No body.

### Referee Finalize Race Results

```http
POST {{baseUrl}}/api/v1/referee/races/{{raceId}}/results/finalize
Authorization: Bearer {{refereeToken}}
```

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
      "finishTimeMillis": 73500,
      "status": "FINISHED",
      "note": "Second"
    }
  ]
}
```

### Public Race Results

```http
GET {{baseUrl}}/api/v1/races/{{raceId}}/results
```

No body.

### Create Race Complaint

```http
POST {{baseUrl}}/api/v1/races/{{raceId}}/complaints
Authorization: Bearer {{ownerToken}}
```

```json
{
  "accusedParticipantId": 2,
  "reason": "Unsafe behavior during race",
  "evidenceUrl": "https://example.com/evidence.mp4"
}
```

### Owner Race Complaints

```http
GET {{baseUrl}}/api/v1/owner/race-complaints
Authorization: Bearer {{ownerToken}}
```

No body.

### Admin Race Complaints

```http
GET {{baseUrl}}/api/v1/admin/race-complaints?status=PENDING
Authorization: Bearer {{adminToken}}
```

No body. `status`: `PENDING`, `APPROVED`, `REJECTED`.

### Admin Resolve Race Complaint

```http
PUT {{baseUrl}}/api/v1/admin/race-complaints/{{complaintId}}/resolve
Authorization: Bearer {{adminToken}}
```

```json
{
  "status": "APPROVED",
  "banUntil": "2026-07-01T00:00:00",
  "fineAmount": 100000,
  "adminNote": "Complaint accepted"
}
```

### Admin Finalize Jockey Challenge

```http
PUT {{baseUrl}}/api/v1/admin/tournaments/{{tournamentId}}/jockey-challenge/finalize
Authorization: Bearer {{adminToken}}
```

No body.

### Public Jockey Challenge Standings

```http
GET {{baseUrl}}/api/v1/tournaments/{{tournamentId}}/jockey-challenge
```

No body.

## Horse Teams

### Owner Eligible Horse Teams

```http
GET {{baseUrl}}/api/v1/owner/horse-teams/eligible
Authorization: Bearer {{ownerToken}}
```

No body.

### Admin Eligible Horse Teams For Tournament

```http
GET {{baseUrl}}/api/v1/admin/tournaments/{{tournamentId}}/eligible-horse-teams
Authorization: Bearer {{adminToken}}
```

No body.

## Recommended Smoke Flow

1. Register/login users for `admin`, `owner`, `jockey`, `referee`.
2. Use admin role update API to assign roles if needed.
3. Owner creates horse, admin approves horse.
4. Jockey creates profile, admin approves profile through role/profile flow.
5. Owner invites jockey, jockey accepts.
6. User creates ZaloPay deposit order and opens `checkoutUrl`.
7. Admin creates tournament and races.
8. Open registration, owner registers horse team, admin approves registration.
9. Schedule tournament, referee checks in participant, starts race, finalizes result.
10. Test complaints, withdrawals, admin wallet, audit logs.

## Automated Verification

Run all tests:

```powershell
.\mvnw.cmd test
```

Run only the broad API/payment checks:

```powershell
.\mvnw.cmd "-Dtest=PaymentServiceImplTest,AllApiSmokeTest,WalletControllerSecurityTest" test
```
