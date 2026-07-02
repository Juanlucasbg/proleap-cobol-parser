# SaleADS Mi Negocio Full Test Report

- Test name: `saleads_mi_negocio_full_test`
- Trigger time (UTC): 2026-07-02 08:01
- Execution mode: Manual browser workflow (computer-use)
- Result summary: **BLOCKED BY AUTHENTICATION / INFRASTRUCTURE**

## PASS / FAIL by required field

| Field | Status | Notes |
| --- | --- | --- |
| Login | FAIL | Google OAuth could not be completed (no password/passkey, device not recognized). |
| Mi Negocio menu | FAIL | Prerequisite failed: dashboard/left sidebar never reached. |
| Agregar Negocio modal | FAIL | Prerequisite failed: cannot access Mi Negocio submenu. |
| Administrar Negocios view | FAIL | Prerequisite failed: cannot navigate to account view. |
| Información General | FAIL | Prerequisite failed: Administrar Negocios page inaccessible. |
| Detalles de la Cuenta | FAIL | Prerequisite failed: Administrar Negocios page inaccessible. |
| Tus Negocios | FAIL | Prerequisite failed: Administrar Negocios page inaccessible. |
| Términos y Condiciones | FAIL | Prerequisite failed: legal section in app not reachable. |
| Política de Privacidad | FAIL | Prerequisite failed: legal section in app not reachable. |

## Detailed execution notes

1. Opened SaleADS site and clicked Sign In.
2. Reached Google OAuth flow and entered `juanlucasbarbiergarzon@gmail.com`.
3. Blocked at Google authentication:
   - Password prompt required but credentials unavailable.
   - Passkey flow attempted -> "No passkeys available".
   - Alternate verification path attempted -> "Couldn't sign you in" due to unrecognized device.
4. Attempted direct app access to continue workflow, but `app.saleads.ai` returned Cloudflare Error 525 (SSL handshake failed).

Because login could not be completed, all downstream validations were blocked.

## Evidence (screenshots)

- `/tmp/computer-use/00d84.webp` (SaleADS landing page)
- `/tmp/computer-use/e5846.webp` (Google email entry)
- `/tmp/computer-use/fe6af.webp` (Google password prompt)
- `/tmp/computer-use/280ab.webp` (Passkey dialog)
- `/tmp/computer-use/ae2c9.webp` (No passkeys available)
- `/tmp/computer-use/d4042.webp` (Google sign-in blocked)
- `/tmp/computer-use/fd4db.webp` (Cloudflare 525 SSL handshake error)
- `/tmp/computer-use/74c43.webp` (No local fallback app server)

## Legal page URL capture

- Términos y Condiciones final URL: **N/A** (not reachable due to login prerequisite failure)
- Política de Privacidad final URL: **N/A** (not reachable due to login prerequisite failure)

## Required remediation for future successful runs

At least one of the following must be provided before rerunning this workflow:

1. Pre-authenticated SaleADS browser session (valid cookies), or
2. Valid interactive Google authentication capability for this environment, or
3. Test/staging authentication bypass specifically for automation.
