# SaleADS Mi Negocio E2E

This suite validates the full "Mi Negocio" workflow in a SaleADS environment without hardcoding a specific domain.

## Setup

```bash
npm install
npx playwright install chromium
```

## Run

Set the target environment URL dynamically:

```bash
SALEADS_APP_URL="https://<your-saleads-environment>" npm run test:mi-negocio
```

## Coverage

The test `tests/saleads-mi-negocio.spec.ts` performs:

1. Login with Google.
2. Mi Negocio menu expansion checks.
3. "Agregar Negocio" modal checks.
4. "Administrar Negocios" page checks.
5. "Información General" checks.
6. "Detalles de la Cuenta" checks.
7. "Tus Negocios" checks.
8. "Términos y Condiciones" checks (handles new tab or same tab).
9. "Política de Privacidad" checks (handles new tab or same tab).
10. Final PASS/FAIL JSON report attachment.

## Evidence

Screenshots are captured at major checkpoints and stored in Playwright test output.
