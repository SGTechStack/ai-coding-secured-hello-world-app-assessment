> **All directories must be considered.** For each business feature, do not skip any listed directory — each one contains standards or context required for a correct implementation.

> **Build order matters.** For each business feature (e.g. MFA PIN Standalone), all directories listed must be visited in order — each layer depends on the one above it, so build them top-down.

### MFA PIN Standalone on Login
PIN setup, verification, enrollment, bcrypt hash, `X-PIN` header, 6-digit PIN, PIN reset
**Build order (read in sequence):**
1. [MFA_Core](MFA_Core/)
2. [MFA_Frontend/Standalone](MFA_Frontend/Standalone/)

### MFA TOTP Standalone on Login
TOTP setup, authenticator app, QR code, `otpauth://` URI, TOTP verification, `X-TOTP` header — standalone deployment, no KMS
**Build order (read in sequence):**
1. [MFA_Core](MFA_Core/)
2. [MFA_Frontend/Standalone](MFA_Frontend/Standalone/)

### MFA TOTP MCC on Login
TOTP with AWS KMS encryption, cloud TOTP, KMS-encrypted secret — MCC/cloud deployment
**Build order (read in sequence):**
1. [MFA_Core](MFA_Core/)
2. [MFA_MCC](MFA_MCC/)
3. [MFA_Frontend/MCC](MFA_Frontend/MCC/)

### MFA OTP MCC on Login
OTP generation, one-time passcode via SMS/email, sending a code over a channel, OTP delivery via MCNS, OTP expiry/TTL, `X-OTP` header, resend OTP
**Build order (read in sequence):**
1. [MFA_Core](MFA_Core/)
2. [MFA_MCC](MFA_MCC/)
3. [MFA_Frontend/MCC](MFA_Frontend/MCC/)

### MFA PIN Standalone on Critical Transaction
PIN setup, verification, enrollment, bcrypt hash, `X-PIN` header, 6-digit PIN, PIN reset, high-risk transaction, critical transaction, privileged endpoint
**Build order (read in sequence):**
1. [MFA_Core](MFA_Core/)
2. [MFA_Critical_Transaction](MFA_Critical_Transaction/)
3. [MFA_Frontend/Standalone](MFA_Frontend/Standalone/)

### MFA TOTP Standalone on Critical Transaction
TOTP setup, authenticator app, QR code, `otpauth://` URI, TOTP verification, `X-TOTP` header — standalone deployment, no KMS, high-risk transaction, critical transaction, privileged endpoint
**Build order (read in sequence):**
1. [MFA_Core](MFA_Core/)
2. [MFA_Critical_Transaction](MFA_Critical_Transaction/)
3. [MFA_Frontend/Standalone](MFA_Frontend/Standalone/)

### MFA TOTP MCC on Critical Transaction
TOTP with AWS KMS encryption, cloud TOTP, KMS-encrypted secret — MCC/cloud deployment, high-risk transaction, critical transaction, privileged endpoint
**Build order (read in sequence):**
1. [MFA_Core](MFA_Core/)
2. [MFA_MCC](MFA_MCC/)
3. [MFA_Critical_Transaction](MFA_Critical_Transaction/)
4. [MFA_Frontend/MCC](MFA_Frontend/MCC/)

### MFA OTP MCC on Critical Transaction
OTP generation, one-time passcode via SMS/email, sending a code over a channel, OTP delivery via MCNS, OTP expiry/TTL, `X-OTP` header, resend OTP, high-risk transaction, critical transaction, privileged endpoint
**Build order (read in sequence):**
1. [MFA_Core](MFA_Core/)
2. [MFA_MCC](MFA_MCC/)
3. [MFA_Critical_Transaction](MFA_Critical_Transaction/)
4. [MFA_Frontend/MCC](MFA_Frontend/MCC/)
