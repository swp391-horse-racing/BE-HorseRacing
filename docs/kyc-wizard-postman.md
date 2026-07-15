# KYC wizard test flow

Set the required `VNPT_EKYC_*` environment variables before starting the backend. All calls below use
`Authorization: Bearer <user-token>`.

Backend environment variables:

```text
VNPT_EKYC_BASE_URL=https://api.idg.vnpt.vn
VNPT_EKYC_TOKEN_ID=<provided-by-VNPT>
VNPT_EKYC_TOKEN_KEY=<provided-by-VNPT>
VNPT_EKYC_ACCESS_TOKEN=<jwt-only-without-bearer-prefix>
VNPT_EKYC_MAC_ADDRESS=TEST1
VNPT_EKYC_DOCUMENT_TYPE=-1
VNPT_EKYC_CROP_PARAM=0.14,0.3
VNPT_EKYC_VALIDATE_POSTCODE=true
VNPT_EKYC_FACE_MATCH_THRESHOLD=80
```

Do not put these credentials in frontend files or GitHub Actions. On Oracle,
store them in `/etc/horse-backend.env` and restart `horse-backend` after updating it.

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

The response status is `DRAFT`. `JOCKEY`, `SPECTATOR`, and `REFEREE` use their
existing request fields and endpoints.

## 2. Upload CCCD

```text
POST /api/v1/role-applications/kyc/ocr
Content-Type: multipart/form-data

requestedRole=OWNER
cccdFront=<front.jpg>
cccdBack=<back.jpg>
```

Successful OCR returns an `OCR_PASSED` verification, masked ID number, name,
date of birth, gender, address and issue date. The full ID number and raw VNPT eKYC
response are never returned.

For `requestedRole=SPECTATOR`, OCR must read a date of birth proving the user
is at least 18 years old. Supported date formats are `dd/MM/yyyy`,
`dd-MM-yyyy`, and `yyyy-MM-dd`; underage or unparseable dates fail KYC before
selfie upload.

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
