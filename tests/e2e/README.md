# SaleADS "Mi Negocio" full workflow E2E

This folder contains the Playwright test `saleads_mi_negocio_full_test` that validates:

1. Login with Google
2. Mi Negocio menu expansion
3. "Agregar Negocio" modal fields and actions
4. "Administrar Negocios" account view sections
5. "Información General", "Detalles de la Cuenta", "Tus Negocios"
6. Legal links ("Términos y Condiciones", "Política de Privacidad"), including new-tab handling and return to app
7. Evidence screenshots and final PASS/FAIL JSON report

## Why this works across environments

- The test never hardcodes a specific SaleADS domain.
- The URL is provided via environment variable at runtime.
- Selectors prioritize visible text and semantic roles.

## Prerequisites

```bash
npm install
npm run playwright:install
```

## Run

```bash
SALEADS_URL="https://<current-environment-login-url>" \
SALEADS_GOOGLE_ACCOUNT="juanlucasbarbiergarzon@gmail.com" \
npm run test:e2e:saleads-mi-negocio
```

`SALEADS_GOOGLE_ACCOUNT` is optional; default is `juanlucasbarbiergarzon@gmail.com`.

## Output artifacts

- Playwright report: `playwright-report/`
- Test output and screenshots: `test-results/`
- Attached JSON report in test artifacts as `final-report`
