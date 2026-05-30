# SaleADS Mi Negocio E2E

Playwright end-to-end test for validating the complete **Mi Negocio** workflow after login with Google.

## What it validates

The test covers:

1. Login with Google
2. Mi Negocio menu expansion
3. Agregar Negocio modal content
4. Administrar Negocios account page
5. Informacion General section
6. Detalles de la Cuenta section
7. Tus Negocios section
8. Terminos y Condiciones navigation
9. Politica de Privacidad navigation

It captures screenshots on key checkpoints and attaches a final JSON report with PASS/FAIL per section.

## Requirements

- Node.js 18+ (recommended)
- A valid SaleADS login URL for the target environment
- Access to the Google account used in login flow

## Setup

```bash
cd /workspace/saleads-e2e
npm install
npm run install:browsers
```

## Run

```bash
SALEADS_URL="https://<environment-login-url>" npm test
```

Optional headed mode:

```bash
SALEADS_URL="https://<environment-login-url>" npm run test:headed
```

## Evidence output

- Playwright test output folder (screenshots/videos/traces)
- Attached file in test results:
  - `saleads-mi-negocio-final-report` (JSON with PASS/FAIL and legal URLs)

## Notes

- No domain is hardcoded.
- The URL is injected via `SALEADS_URL` so the same test can run on dev/staging/production.
- Selectors prioritize visible text and role-based discovery.
