# SaleADS Mi Negocio Full Test Report

- Test name: `saleads_mi_negocio_full_test`
- Executed at (UTC): 2026-07-17 19:47
- Trigger: cron (`0 * * * *`)
- Browser: Google Chrome
- Environment target rule: environment-agnostic (no hardcoded environment assumption)

## Outcome Summary

The test run was **partially completed**. Public legal pages were validated successfully, but the authenticated module workflow could not be completed due to a login credential blocker at Google password entry.

## PASS/FAIL Matrix (Requested Final Report Fields)

| Field | Result | Notes |
|---|---|---|
| Login | FAIL | Google OAuth reached password step; password unavailable for `juanlucasbarbiergarzon@gmail.com`. |
| Mi Negocio menu | FAIL (blocked) | Requires authenticated session. |
| Agregar Negocio modal | FAIL (blocked) | Requires authenticated session. |
| Administrar Negocios view | FAIL (blocked) | Requires authenticated session. |
| Información General | FAIL (blocked) | Requires authenticated session. |
| Detalles de la Cuenta | FAIL (blocked) | Requires authenticated session. |
| Tus Negocios | FAIL (blocked) | Requires authenticated session. |
| Términos y Condiciones | PASS | Heading and legal content validated. |
| Política de Privacidad | PASS | Heading and legal content validated. |

## Step-by-Step Validation Notes

1. **Login with Google**
   - Opened SaleADS login flow and reached Google OAuth.
   - Account email `juanlucasbarbiergarzon@gmail.com` entered.
   - Blocked at password entry screen.

2. **Mi Negocio menu**
   - Not reachable without successful login.

3. **Agregar Negocio modal**
   - Not reachable without successful login.

4. **Administrar Negocios**
   - Not reachable without successful login.

5. **Información General**
   - Not reachable without successful login.

6. **Detalles de la Cuenta**
   - Not reachable without successful login.

7. **Tus Negocios**
   - Not reachable without successful login.

8. **Términos y Condiciones**
   - Validated page content and heading.
   - Final URL: `https://saleads.ai/en/legal/terms-and-conditions`

9. **Política de Privacidad**
   - Validated page content and heading.
   - Final URL: `https://saleads.ai/en/legal/privacy-policy`

## Evidence (Screenshots)

- `/tmp/computer-use/96d5b.webp` - SaleADS landing page loaded
- `/tmp/computer-use/1d35d.webp` - Keycloak login page
- `/tmp/computer-use/e3c19.webp` - Google OAuth page
- `/tmp/computer-use/2f132.webp` - Google password blocker
- `/tmp/computer-use/bfe7b.webp` - Terms and Conditions validation
- `/tmp/computer-use/0c4a1.webp` - Privacy Policy validation

## Blocker

- Root cause: Missing password/credential access for Google account `juanlucasbarbiergarzon@gmail.com` in this environment.
- Impact: Prevented completion of all authenticated checks (steps 2 through 7 plus post-login validations from step 1).

## Recommended Next Run Preconditions

- Provide a pre-authenticated browser session, or
- Provide credentials/secrets mechanism for the designated Google account, or
- Provide a dedicated QA account with non-interactive sign-in support.
