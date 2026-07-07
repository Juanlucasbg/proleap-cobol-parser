# SaleADS Mi Negocio Full Test Report

- **Run name:** `saleads_mi_negocio_full_test`
- **Triggered at:** `2026-07-07T02:02:28.813Z`
- **Execution mode:** Browser manual validation (environment-agnostic flow)
- **Environment reached:** `saleads.ai` production pages (no fixed URL was hardcoded in execution logic)

## Summary

- **Overall:** PARTIAL SUCCESS
- **Passed:** 2 / 9
- **Primary blocker:** Google OAuth authentication could not complete in cloud runtime due to missing password/passkey capability.

## Step Results (PASS/FAIL)

| Step | Check | Result | Notes |
|---|---|---|---|
| 1 | Login | FAIL | Google sign-in reached account selection/email stage, but authentication blocked at password/passkey. |
| 2 | Mi Negocio menu | FAIL | Not reachable because login did not complete. |
| 3 | Agregar Negocio modal | FAIL | Not reachable because login did not complete. |
| 4 | Administrar Negocios view | FAIL | Not reachable because login did not complete. |
| 5 | Información General | FAIL | Not reachable because login did not complete. |
| 6 | Detalles de la Cuenta | FAIL | Not reachable because login did not complete. |
| 7 | Tus Negocios | FAIL | Not reachable because login did not complete. |
| 8 | Términos y Condiciones | PASS | Legal page loaded and heading/content were visible. |
| 9 | Política de Privacidad | PASS | Legal page loaded and heading/content were visible. |

## Validation Notes

### Step 1 blocker details

- Account email entered: `juanlucasbarbiergarzon@gmail.com`
- Exact UI error states captured:
  - `"No passkeys available"`
  - `"Something went wrong"`
- Because login is a prerequisite for steps 2-7, authenticated Mi Negocio checks could not be executed.

### Step 8 legal validation

- **URL:** `https://saleads.ai/en/legal/terms-and-conditions`
- Heading/content present for Terms and Conditions.
- Navigation occurred in the same tab.

### Step 9 legal validation

- **URL:** `https://saleads.ai/en/legal/privacy-policy`
- Heading/content present for Privacy Policy.
- Navigation occurred in the same tab.

## Evidence (Screenshots)

Key checkpoint screenshots captured during execution:

1. `/tmp/computer-use/00391.webp` - SaleADS landing/dashboard entry point
2. `/tmp/computer-use/b5f0e.webp` - Login screen with Google option
3. `/tmp/computer-use/aac08.webp` - Google email selector input populated
4. `/tmp/computer-use/78f85.webp` - Password challenge step (authentication blocker)
5. `/tmp/computer-use/d5d6d.webp` - Passkey error modal (`No passkeys available`)
6. `/tmp/computer-use/9b873.webp` - Final Google auth failure state (`Something went wrong`)
7. `/tmp/computer-use/09eb3.webp` - Footer legal links visible
8. `/tmp/computer-use/fdc26.webp` - Terms and Conditions page
9. `/tmp/computer-use/3e71a.webp` - Privacy Policy page

## Recommended follow-up

To validate steps 1-7 end-to-end, provide one of:

- a pre-authenticated browser profile/session for the target environment, or
- a non-interactive test login method that bypasses Google MFA/passkey challenges.
