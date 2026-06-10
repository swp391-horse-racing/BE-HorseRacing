# KYC wizard test flow

Set `FPT_AI_API_KEY` before starting the backend. All calls below use
`Authorization: Bearer <user-token>`.

## 1. Save a role draft

Use the existing multipart endpoint for the selected role, for example:

```text
POST /api/v1/role-applications/owner
stableName=Sunrise Stable
experienceYears=5
address=Ho Chi Minh City
bio=Horse owner profile
verificationDocument=<optional file>
```

The response status is `DRAFT`. `JOCKEY` and `REFEREE` use their existing
request fields and endpoints.

## 2. Upload CCCD

```text
POST /api/v1/role-applications/kyc/ocr
Content-Type: multipart/form-data

requestedRole=OWNER
cccdFront=<front.jpg>
cccdBack=<back.jpg>
```

Successful OCR returns an `OCR_PASSED` verification, masked ID number, name,
date of birth, gender, address and issue date. The full ID number and raw FPT
response are never returned.

## 3. Upload selfie

```text
POST /api/v1/role-applications/kyc/{kycVerificationId}/face-match
Content-Type: multipart/form-data

selfie=<selfie.jpg>
```

When Face Match passes, the response contains `kycStatus=PASSED` and
`applicationStatus=PENDING`. When it fails, the API returns HTTP 400, stores
the failed attempt, and leaves the role profile in `DRAFT`.

## 4. Admin review

Use an admin token:

```text
GET /api/v1/admin/role-applications?status=PENDING
PUT /api/v1/admin/role-applications/{profileId}/approve?role=OWNER
```

Approval is rejected unless the linked KYC verification is `PASSED`.
